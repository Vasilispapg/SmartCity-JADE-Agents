package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class CitizenAgent extends Agent {

  // TODO: make it work with importance of actions like
  // if nurse == agent and got a message from citizen then go to him
  // if has no road/sidewalk or crosswalk in neighbors it cannot move

  // Variables for the citizen
  private boolean isInjured = false;
  private boolean ownsCar;
  private Vehicle vehicle;
  private Point position;
  private MapFrame mapFrame;
  private CityMap cityMap;
  private Color color;
  private Boolean inAction = false;

  // Variables for finding nurses
  private AID nurseNearby;
  private boolean helpRequested = false;

  // Retry parameters
  private int retryCounter = 0;
  private final int maxRetries = 5;

  // Variables for movement
  private Point destination;
  private boolean usingVehicle;
  private boolean inTraffic;

  // Pathfinding and movement variables
  private LinkedList<Point> path = new LinkedList<>();
  private boolean destinationReached = false;

  // Behaviours
  private MovementBehaviour movementBehaviour;
  private DailyActivities dailyActivities;
  private RequestHelp requestHelp = new RequestHelp();
  private ReceiveHealingConfirmation receiveHealingConfirmation = new ReceiveHealingConfirmation();

  // initialize the citizen agent
  CountDownLatch latch;

  public CitizenAgent() {
    // this.ownsCar = new Random().nextBoolean();
    this.ownsCar = true;
    if (this.ownsCar) {
      this.vehicle = new Vehicle("Bicycle");
    }
    this.usingVehicle = false;
    this.inTraffic = false;
  }

  protected void setup() {
    Object[] args = getArguments();
    System.out.println(
      "Hello! Citizen-agent " + getAID().getName() + " is ready."
    );
    if (args != null && args.length == 5) {
      this.cityMap = (CityMap) args[0];
      this.mapFrame = (MapFrame) args[1];
      this.position = (Point) args[2];
      this.color = (Color) args[3];
      this.latch = (CountDownLatch) args[4];
    } else {
      agentSays("Error initializing CitizenAgent. Arguments are invalid.");
      takeDown();
      return;
    }

    if (
      this.mapFrame == null ||
      this.cityMap == null ||
      this.position == null ||
      this.color == null
    ) {
      agentSays("Error initializing CitizenAgent. Arguments are null.");
      takeDown(); // Terminate agent if not properly initialized
      return;
    }

    agentSays(
      "Citizen-agent " +
      getAID().getLocalName() +
      " is ready at position " +
      position
    );

    position.setLocation(new Point(0, 10));
    if (cityMap.getCell(position.x, position.y).contains("Road")) {
      spawnVehicle(position);
      usingVehicle = true;
    }

    mapFrame.updatePosition(getAID().getLocalName(), position, color);

    dailyActivities = new DailyActivities(this, 10000);

    movementBehaviour = new MovementBehaviour(this, 1000);
    destination = new Point(10, 10);
    // TODO if it has a vehicle and the destination is sidewalk make it to use foot
    agentSays("-------------------------------");

    addBehaviour(movementBehaviour);
    addBehaviour(dailyActivities);
    addBehaviour(requestHelp);
    addBehaviour(receiveHealingConfirmation);
    agentSays("-------------------------------2");
  }

  protected class DailyActivities extends TickerBehaviour {

    public DailyActivities(Agent a, long period) {
      super(a, period);
    }

    public void onTick() {
      double chance = Math.random();
      if (chance < 0.005 && !(myAgent instanceof NurseAgent) && !isInjured) {
        isInjured = true;
        agentSays("I am injured");
      }
    }
  }

  protected class RequestHelp extends CyclicBehaviour {

    public void action() {
      if (isInjured && !helpRequested) {
        if (nurseNearby == null && retryCounter < maxRetries) {
          findNurses();
          retryCounter++;
        }
        if (nurseNearby != null) {
          sendHelpRequest();
          helpRequested = true; // Mark that help request is sent
        } else if (retryCounter >= maxRetries) {
          agentSays("No nurse found after maximum retries.");
          block(10000); // Stop retrying and wait for other events
          takeDown(); // Terminate the agent
        }
      }
    }

    private void sendHelpRequest() {
      ACLMessage helpRequest = new ACLMessage(ACLMessage.REQUEST);
      helpRequest.addReceiver(nurseNearby);
      helpRequest.setContent(
        "Need medical assistance at position: " + position.x + "," + position.y
      );
      send(helpRequest);
      agentSays(
        "Requesting help from " +
        nurseNearby.getLocalName() +
        " at " +
        position.x +
        "," +
        position.y
      );
    }
  }

  protected Behaviour[] getBehaviours() {
    return new Behaviour[] {
      movementBehaviour,
      dailyActivities,
      requestHelp,
      receiveHealingConfirmation,
    };
  }

  protected void takeDown() {
    for (Behaviour b : getBehaviours()) {
      if (b != null) removeBehaviour(b);
    }
    System.out.println(getLocalName() + ": terminating.");
  }

  protected class MovementBehaviour extends TickerBehaviour {

    public MovementBehaviour(Agent a, long period) {
      super(a, period);
      initializeDestinationAndMode();
    }

    protected void onTick() {
      try {
        if (isInjured) return;
        if (destinationReached || position.equals(destination)) {
          agentSays("Destination reached at " + position);
          if (usingVehicle) {
            agentSays("Parking the vehicle at " + position);
            // Simulate parking the vehicle
            cityMap.clearVehiclePosition(position);
            usingVehicle = false;
          }
          destinationReached = false; // Reset the destination flag for next destination
          initializeDestinationAndMode();
          return;
        }

        if (path.isEmpty()) {
          agentSays("PATH IS EMPTY");
          path = calculatePath(position, destination);
        }

        mapFrame.setPath(path); // Update the path on the map

        if (!path.isEmpty()) {
          Point nextStep = path.poll();
          if (
            nextStep != null && isValidMove(position, nextStep, usingVehicle)
          ) {
            position.setLocation(nextStep);
            agentSays(
              "Moved to " +
              position +
              " using " +
              (usingVehicle ? "vehicle" : "foot")
            );
            mapFrame.updatePosition(getAID().getLocalName(), position, color);
            setPosition(nextStep);
          } else {
            path.clear();
          }
        }
      } catch (Exception e) {
        System.err.println("Exception in behavior tick: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  private void spawnVehicle(Point pos) {
    agentSays("Vehicle spawned at " + pos);
    cityMap.setVehiclePosition(pos);
  }

  // -----------------
  // FINDING THE NURSES OF THE AGENTS
  // -----------------
  protected void findNurses() {
    DFAgentDescription template = new DFAgentDescription();
    ServiceDescription sd = new ServiceDescription();
    sd.setType("nurse");
    template.addServices(sd);

    try {
      DFAgentDescription[] results = DFService.search(this, template);
      if (results.length > 0) {
        for (DFAgentDescription dfd : results) {
          AID nurseAID = dfd.getName();
          if (!nurseAID.equals(getAID())) { // Check if it's not the current agent
            agentSays("Found nurse: " + nurseAID.getLocalName());
            nurseNearby = nurseAID;
            break;
          }
        }
      }
      if (nurseNearby == null) {
        agentSays("Not avaliable nursers right now");
      }
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  private class ReceiveHealingConfirmation extends CyclicBehaviour {

    public void action() {
      MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
      ACLMessage msg = myAgent.receive(mt);
      if (msg != null) {
        String content = msg.getContent();
        if (content.contains("Healed at")) {
          handleHealing(msg);
        }
      } else {
        block();
      }
    }

    private void handleHealing(ACLMessage msg) {
      isInjured = false; // Citizen is no longer injured
      agentSays(
        "I have been healed by " + msg.getSender().getLocalName() + "."
      );
      helpRequested = false;
      retryCounter = 0;
      nurseNearby = null;
    }
  }

  // -----------------
  // END OF FINDING THE NURSES OF THE AGENTS
  // -----------------

  protected void agentSays(String message) {
    System.out.println(getAID().getLocalName() + ": " + message);
  }

  // -----------------
  // FIND DESTINATION PATH
  // -----------------
  private void initializeDestinationAndMode() {
    if (isInjured) {
      // findHospitalDestination();
    } else {
      if (ownsCar) {
        findDestination("Road");
        initializeMovementToVehicle();
      } else {
        findDestination("Sidewalk");
        usingVehicle = false;
        path = calculatePath(position, destination);
      }
    }
  }

  private void findDestination(String type) {
    Point dest;
    do {
      int x = new Random().nextInt(cityMap.size);
      int y = new Random().nextInt(cityMap.size);
      dest = new Point(x, y);
    } while (!cityMap.getCell(dest.x, dest.y).contains(type));

    destination = dest;
    agentSays(type + " destination set to " + destination);
  }

  private void initializeMovementToVehicle() {
    if (!usingVehicle) {
      Point nearestSidewalkNextToRoad = findNearestRoadOrSidewalk(
        position,
        true
      ); // Find sidewalk next to a road

      if (nearestSidewalkNextToRoad == null) {
        agentSays("No suitable road or sidewalk found from " + position);
        return;
      }
      agentSays(
        "Moving to nearest sidewalk next to road at " +
        nearestSidewalkNextToRoad
      );

      path = calculatePath(position, nearestSidewalkNextToRoad);
      agentSays("Path to sidewalk: " + path);
      followPath(); // A method to follow the calculated path
      // Move to the vehicle
      findClosestRoad();
    } else {
      // Directly move to vehicle if already on a suitable road
      usingVehicle = true;
      if (!cityMap.getCell(position.x, position.y).contains("Road")) {
        agentSays("Not on a road. Finding the nearest road.");
        path =
          calculatePath(position, findNearestRoadOrSidewalk(position, false));
        followPath();
      }
    }
  }

  private void findClosestRoad() {
    Point nextStep = null;
    for (Point p : getNeighbors(position)) {
      if (cityMap.getCell(p.x, p.y).contains("Road")) {
        nextStep = p;
        break;
      }
    }
    if (nextStep == null) {
      agentSays("No road found nearby");
      return;
    }
    position.setLocation(nextStep);
    mapFrame.updatePosition(getAID().getLocalName(), position, color);
    setPosition(nextStep);
    // agentSays("Moved to " + nextStep);
    spawnVehicle(nextStep);
    usingVehicle = true;
  }

  private void followPath() {
    while (!path.isEmpty()) {
      Point nextStep = path.poll();
      if (nextStep.equals(position)) {
        continue; // Skip if the next step is the same as the current position
      }

      if (isValidMove(position, nextStep, usingVehicle)) {
        position.setLocation(nextStep);
        mapFrame.updatePosition(getAID().getLocalName(), position, color);
        setPosition(nextStep);
        agentSays("Moved to " + nextStep);
      } else {
        agentSays("Recalculating path due to an obstacle at " + nextStep);
        path = calculatePath(position, destination); // Recalculate if blocked
      }
    }
    if (path.isEmpty()) {
      agentSays("Reached destination or next transition point.");
    }
  }

  private Point findNearestRoadOrSidewalk(Point start, boolean needsSidewalk) {
    LinkedList<Point> queue = new LinkedList<>();
    Set<Point> visited = new HashSet<>();
    queue.add(start);
    visited.add(start);

    while (!queue.isEmpty()) {
      Point current = queue.poll();
      String cellType = cityMap.getCell(current.x, current.y);
      // agentSays("Visiting cell " + current + " of type " + cellType);

      if (cellType.contains("Road") && !needsSidewalk) {
        agentSays("Nearest road found at " + current);
        return current; // Found the nearest road
      }

      // Check if it's a sidewalk and has a road neighbor if sidewalks are needed
      if (
        "Sidewalk".equals(cellType) && needsSidewalk && hasRoadNeighbor(current)
      ) {
        agentSays("Nearest sidewalk next to road found at " + current);
        return current; // Found the nearest sidewalk adjacent to a road
      }

      for (Point neighbor : getNeighbors(current)) {
        if (!visited.contains(neighbor)) {
          visited.add(neighbor);
          queue.add(neighbor);
        }
      }
    }
    agentSays("No suitable road or sidewalk found from " + start);
    return null; // No suitable road or sidewalk found
  }

  private boolean hasRoadNeighbor(Point point) {
    for (Point neighbor : getNeighbors(point)) {
      if (cityMap.getCell(neighbor.x, neighbor.y).contains("Road")) {
        return true;
      }
    }
    return false;
  }

  private LinkedList<Point> calculatePath(Point start, Point end) {
    Map<Point, Node> openList = new HashMap<>();
    Set<Point> closedList = new HashSet<>();
    Map<Point, Point> cameFrom = new HashMap<>();
    Map<Point, Integer> gScore = new HashMap<>();
    PriorityQueue<Node> priorityQueue = new PriorityQueue<>(
      Comparator.comparingInt(n -> n.f)
    );
    agentSays("Calculating path from " + start + " to " + end);
    Node startNode = new Node(start, 0, manhattanDistance(start, end));

    openList.put(start, startNode);
    priorityQueue.add(startNode);
    gScore.put(start, 0);
    while (!priorityQueue.isEmpty()) {
      Node current = priorityQueue.poll();

      if (current.position.equals(end)) {
        // agentSays("Path found to destination: " + end);
        return reconstructPath(cameFrom, end);
      }

      openList.remove(current.position);
      closedList.add(current.position);

      for (Point neighbor : getNeighbors(current.position)) {
        if (
          closedList.contains(neighbor) ||
          !isValidMove(current.position, neighbor, usingVehicle)
        ) {
          continue;
        }
        int tentativeGScore = gScore.get(current.position) + 1; // Assuming uniform cost
        if (
          !gScore.containsKey(neighbor) ||
          tentativeGScore < gScore.get(neighbor)
        ) {
          cameFrom.put(neighbor, current.position);
          gScore.put(neighbor, tentativeGScore);
          int fScore = tentativeGScore + manhattanDistance(neighbor, end);
          if (!openList.containsKey(neighbor)) {
            Node neighborNode = new Node(neighbor, tentativeGScore, fScore);
            openList.put(neighbor, neighborNode);
            priorityQueue.add(neighborNode);
          }
        }
      }
    }

    agentSays("Failed to find a path from " + start + " to " + end);
    return new LinkedList<>(); // Path not found
  }

  private int manhattanDistance(Point p1, Point p2) {
    if (p1 == null || p2 == null) return Integer.MAX_VALUE;
    return Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y);
  }

  private List<Point> getNeighbors(Point p) {
    List<Point> neighbors = new ArrayList<>();
    int[] dx = { -1, 1, 0, 0 }; // Left, right movements
    int[] dy = { 0, 0, -1, 1 }; // Up, down movements

    for (int i = 0; i < dx.length; i++) {
      int newX = p.x + dx[i];
      int newY = p.y + dy[i];

      // Check bounds
      if (
        newX >= 0 && newX < cityMap.size && newY >= 0 && newY < cityMap.size
      ) {
        Point neighbor = new Point(newX, newY);
        neighbors.add(neighbor);
      }
    }
    return neighbors;
  }

  private LinkedList<Point> reconstructPath(
    Map<Point, Point> cameFrom,
    Point current
  ) {
    LinkedList<Point> path = new LinkedList<>();
    path.add(current);
    while (cameFrom.containsKey(current)) {
      current = cameFrom.get(current);
      path.addFirst(current);
    }
    return path;
  }

  private boolean isValidMove(
    Point currentPosition,
    Point nextPosition,
    boolean usingVehicle
  ) {
    String currentCellType = cityMap.getCell(
      currentPosition.x,
      currentPosition.y
    );
    String nextCellType = cityMap.getCell(nextPosition.x, nextPosition.y);

    if (currentPosition.equals(nextPosition)) return true;
    // Check if the next position is within bounds and is traversable
    if (!cityMap.withinBounds(nextPosition)) {
      agentSays(
        "Move to " + nextPosition + " is out of bounds or not traversable."
      );
      return false;
    }

    // Check vehicle-specific movement rules
    if (usingVehicle) {
      return isValidVehicleMove(
        currentCellType,
        nextCellType,
        nextPosition,
        currentPosition
      );
    } else {
      return isValidPedestrianMove(currentCellType, nextCellType, nextPosition);
    }
  }

  private void debugCase(
    String cases,
    String nextCellType,
    Point nextPosition,
    Point position,
    int dx,
    int dy,
    boolean isValid
  ) {
    // agentSays("case: " + cases);
    // agentSays("Next cell type: " + nextCellType);
    // agentSays("nextPosition: " + nextPosition + " position: " + position);
    // agentSays("dx: " + dx + " dy: " + dy);
    // agentSays("isValid: " + isValid);
    // agentSays("-----------------");
  }

  private boolean isValidVehicleMove(
    String currentCellType,
    String nextCellType,
    Point nextPosition,
    Point position
  ) {
    int dx = nextPosition.x - position.x;
    int dy = nextPosition.y - position.y;

    switch (currentCellType) {
      case "Road - Go Up Only":
        debugCase(
          "Road - Go Up Only",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          (dy == -1 && dx == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          (dy == -1 && dx == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Down Only":
        debugCase(
          "Road - Go Down Only",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          (dy == 1 && dx == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          (dy == 1 && dx == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Left Only":
        debugCase(
          "Road - Go Left Only",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          (dx == -1 && dy == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          (dx == -1 && dy == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Right Only":
        debugCase(
          "Road - Go Right Only",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          (dx == 1 && dy == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          (dx == 1 && dy == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Straight Only":
        debugCase(
          "Road - Straight Only",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Straight Only or Turn Right":
        debugCase(
          "Road - Straight Only or Turn Right",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Straight Only or Turn Left":
        debugCase(
          "Road - Straight Only or Turn Left",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Up or Turn Right":
        debugCase(
          "Road - Go Up or Turn Right",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          ((dy == -1 && dx == 0) || (dy == 0 && dx == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          ((dy == -1 && dx == 0) || (dy == 0 && dx == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Up or Turn Left":
        debugCase(
          "Road - Go Up or Turn Left",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          ((dy == -1 && dx == 0) || (dy == 0 && dx == -1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          ((dy == -1 && dx == 0) || (dy == 0 && dx == -1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Down or Turn Right":
        debugCase(
          "Road - Go Down or Turn Right",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          ((dy == 1 && dx == 0) || (dy == 0 && dx == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          ((dy == 1 && dx == 0) || (dy == 0 && dx == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Down or Turn Left":
        debugCase(
          "Road - Go Down or Turn Left",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          ((dy == 1 && dx == 0) || (dy == 0 && dx == -1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
        return (
          ((dy == 1 && dx == 0) || (dy == 0 && dx == -1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Crosswalk":
        debugCase(
          "Crosswalk",
          nextCellType,
          nextPosition,
          position,
          dx,
          dy,
          (nextCellType.contains("Road") || nextCellType.equals("Crosswalk"))
        );
        return (
          nextCellType.contains("Road") || nextCellType.equals("Crosswalk")
        );
      default:
        return false;
    }
  }

  private boolean isValidPedestrianMove(
    String currentCellType,
    String nextCellType,
    Point nextPosition
  ) {
    return (
      nextCellType.equals("Sidewalk") ||
      nextCellType.equals("Crosswalk") ||
      nextCellType.equals("Hospital") ||
      nextCellType.equals("House") ||
      nextCellType.equals("Police Station") ||
      nextCellType.equals("Fire Station")
    );
  }

  private class Node {

    Point position;
    int g; // Cost from start
    int f; // Total estimated cost

    Node(Point position, int g, int h) {
      this.position = position;
      this.g = g;
      this.f = g + h;
    }
  }

  // -----------------
  // END FIND DESTINATION PATH
  // -----------------

  // -----------------
  // GETTERS AND SETTERS
  // -----------------

  public boolean isInjured() {
    return isInjured;
  }

  public void setInjured(boolean isInjured) {
    this.isInjured = isInjured;
  }

  public boolean ownsCar() {
    return ownsCar;
  }

  public void setOwnsCar(boolean ownsCar) {
    this.ownsCar = ownsCar;
  }

  public Vehicle getVehicle() {
    return vehicle;
  }

  public void setVehicle(Vehicle vehicle) {
    this.vehicle = vehicle;
  }

  public Point getPosition() {
    return position;
  }

  public void setPosition(Point position) {
    this.position = position;
  }

  public MapFrame getMapFrame() {
    return mapFrame;
  }

  public void setMapFrame(MapFrame mapFrame) {
    this.mapFrame = mapFrame;
  }

  public CityMap getCityMap() {
    return cityMap;
  }

  public void setCityMap(CityMap cityMap) {
    this.cityMap = cityMap;
  }

  public Color getColor() {
    return color;
  }

  public void setColor(Color color) {
    this.color = color;
  }

  public Boolean getInAction() {
    return inAction;
  }

  public void setInAction(Boolean inAction) {
    this.inAction = inAction;
  }

  public AID getNurseNearby() {
    return nurseNearby;
  }

  public void setNurseNearby(AID nurseNearby) {
    this.nurseNearby = nurseNearby;
  }

  public boolean isHelpRequested() {
    return helpRequested;
  }

  public void setHelpRequested(boolean helpRequested) {
    this.helpRequested = helpRequested;
  }

  public int getRetryCounter() {
    return retryCounter;
  }

  public void setRetryCounter(int retryCounter) {
    this.retryCounter = retryCounter;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public Point getDestination() {
    return destination;
  }

  public void setDestination(Point destination) {
    this.destination = destination;
  }

  public boolean isUsingVehicle() {
    return usingVehicle;
  }

  public void setUsingVehicle(boolean usingVehicle) {
    this.usingVehicle = usingVehicle;
  }

  public boolean isInTraffic() {
    return inTraffic;
  }

  public void setInTraffic(boolean inTraffic) {
    this.inTraffic = inTraffic;
  }

  public LinkedList<Point> getPath() {
    return path;
  }

  public void setPath(LinkedList<Point> path) {
    this.path = path;
  }

  public boolean isDestinationReached() {
    return destinationReached;
  }

  public void setDestinationReached(boolean destinationReached) {
    this.destinationReached = destinationReached;
  }

  public MovementBehaviour getMovementBehaviour() {
    return movementBehaviour;
  }

  public void setMovementBehaviour(MovementBehaviour movementBehaviour) {
    this.movementBehaviour = movementBehaviour;
  }

  public DailyActivities getDailyActivities() {
    return dailyActivities;
  }

  public void setDailyActivities(DailyActivities dailyActivities) {
    this.dailyActivities = dailyActivities;
  }

  public RequestHelp getRequestHelp() {
    return requestHelp;
  }

  public void setRequestHelp(RequestHelp requestHelp) {
    this.requestHelp = requestHelp;
  }

  public ReceiveHealingConfirmation getReceiveHealingConfirmation() {
    return receiveHealingConfirmation;
  }

  public void setReceiveHealingConfirmation(
    ReceiveHealingConfirmation receiveHealingConfirmation
  ) {
    this.receiveHealingConfirmation = receiveHealingConfirmation;
  }

  public CountDownLatch getLatch() {
    return latch;
  }

  public void setLatch(CountDownLatch latch) {
    this.latch = latch;
  }
  // -----------------
  // END GETTERS AND SETTERS
  // -----------------
}

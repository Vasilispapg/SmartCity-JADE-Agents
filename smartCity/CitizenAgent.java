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

  // Variables for the citizen
  private boolean isInjured = false;
  private boolean ownsCar;
  private Point home;
  private Vehicle vehicle;
  private Point position;
  private MapFrame mapFrame;
  private CityMap cityMap;
  private Color color;
  private Boolean inAction = false;
  private double balance = 1000;
  private double previousBalance = balance;
  private double awarenessRange = 50.0;

  private Object[] args;

  // Variables for the citizen's state
  private Boolean isHome = true;

  // Variables for finding nurses
  private AID hospitalNearby;
  private AID nurseNearby;
  private boolean helpRequested = false;

  // Retry parameters
  private int retryCounter = 0;
  private final int maxRetries = 5;

  // Variables for movement
  private Point destination;
  private boolean usingVehicle;
  private boolean inTraffic;
  private boolean moveToBehaviourActive = false;

  // Pathfinding and movement variables
  private LinkedList<Point> path = new LinkedList<>();
  private boolean destinationReached = false;

  // Behaviours
  private MovementBehaviour movementBehaviour;
  private DailyActivities dailyActivities;
  private RequestHelp requestHelp = new RequestHelp();
  private ReceiveHealingConfirmation receiveHealingConfirmation = new ReceiveHealingConfirmation();

  // Initialize the citizen agent
  CountDownLatch latch;

  public CitizenAgent() {
    this.ownsCar = new Random().nextBoolean();
    if (this.ownsCar) {
      this.vehicle = new Vehicle("Bicycle");
    }
    this.usingVehicle = false;
    this.inTraffic = false;
  }

  protected void setup() {
    Object[] args = getArguments();
    this.args = args;
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
      this.color == null ||
      this.latch == null
    ) {
      agentSays("Error initializing CitizenAgent. Arguments are null.");
      takeDown(); // Terminate agent if not properly initialized
      return;
    }

    AgentRegistry.registerAgent(this, getAID().getLocalName());
    setHome(position);
    // Ensure the agent is spawned on a road
    if (cityMap.getCell(position.x, position.y).contains("Road")) {
      spawnVehicle(position);
      usingVehicle = true;
      inTraffic = true;
    }

    mapFrame.updatePosition(getAID().getLocalName(), position, color);

    dailyActivities =
      new DailyActivities(this, (int) (Math.random() * 700 + 1200));
    movementBehaviour =
      new MovementBehaviour(this, (int) (Math.random() * 700 + 1200));

    addBehaviour(movementBehaviour);
    addBehaviour(dailyActivities);
    addBehaviour(requestHelp);
    addBehaviour(new WaitForHospitalResponse());
    addBehaviour(receiveHealingConfirmation);
    addLocationHandlingBehaviour();

    // Add the agent behavior to check if the agent is home
    addBehaviour(
      new TickerBehaviour(this, (int) (Math.random() * 700 + 1200)) {
        protected void onTick() {
          if (getPosition() == getHome() && !isHome) {
            isHome = true;
          }
        }
      }
    );
  }

  protected class DailyActivities extends TickerBehaviour {

    public DailyActivities(Agent a, long period) {
      super(a, period);
    }

    public void onTick() {
      double chance = Math.random();
      if (chance < 0.0005 && !(myAgent instanceof NurseAgent) && !isInjured) {
        isInjured = true;
        agentSays("I am injured");
      }
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
    AgentRegistry.deregisterAgent(this);
  }

  // -----------------
  // FINDING THE NURSES OF THE AGENTS
  // -----------------
  protected void findHospital() {
    DFAgentDescription template = new DFAgentDescription();
    ServiceDescription sd = new ServiceDescription();
    sd.setType("hospital"); // Change to hospital
    template.addServices(sd);

    try {
      DFAgentDescription[] results = DFService.search(this, template);
      if (results.length > 0) {
        // Choose the first hospital found (or implement a better selection mechanism)
        // TODO MAKE IT TO CHOOSE THE CLOSEST HOSPITAL
        hospitalNearby = results[0].getName();
        agentSays("Found hospital: " + hospitalNearby.getLocalName());
      } else {
        agentSays("No available hospitals right now");
        hospitalNearby = null;
      }
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  private class WaitForHospitalResponse extends CyclicBehaviour {

    public void action() {
      MessageTemplate mt = MessageTemplate.and(
        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
        MessageTemplate.MatchSender(hospitalNearby)
      );
      ACLMessage msg = myAgent.receive(mt);
      if (msg != null) {
        String content = msg.getContent();
        String conversationId = msg.getConversationId();
        agentSays("Received message: " + content);

        if (content.contains("No nurses are available")) {
          handleNoNursesAvailable(conversationId);
        }
      } else {
        block();
      }
    }

    private void handleNoNursesAvailable(String conversationId) {
      agentSays(
        "No nurses available, looking for another hospital for request ID: " +
        conversationId
      );
      findHospital(); // Optionally modify to retry finding a hospital or handle differently
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

  protected class RequestHelp extends CyclicBehaviour {

    public void action() {
      if (isInjured && !helpRequested) {
        agentSays("Requesting help from nearby hospitals.");

        if (hospitalNearby == null && retryCounter < maxRetries) {
          findHospital();
          retryCounter++;
        }
        if (hospitalNearby != null) {
          sendHelpRequestToHospital();
          helpRequested = true; // Mark that help request is sent
        } else if (retryCounter >= maxRetries) {
          agentSays("No hospital found after maximum retries.");
          block(10000); // Stop retrying and wait for other events
          takeDown(); // Terminate the agent
        }
      }
    }

    private void sendHelpRequestToHospital() {
      ACLMessage helpRequest = new ACLMessage(ACLMessage.REQUEST);
      helpRequest.addReceiver(hospitalNearby);
      helpRequest.setContent(
        "Need medical assistance at position: " + position.x + "," + position.y
      );
      helpRequest.setConversationId(
        "injury-report-" + System.currentTimeMillis()
      ); // Unique ID for tracking
      send(helpRequest);
      agentSays(
        "Requesting help from " +
        hospitalNearby.getLocalName() +
        " at " +
        position.x +
        "," +
        position.y +
        ". Conversation ID: " +
        helpRequest.getConversationId()
      );
    }
  }

  // -----------------
  // FINDING THE NURSES OF THE AGENTS
  // -----------------

  // -----------------
  // FIND DESTINATION PATH
  // -----------------

  protected class MoveToBehaviour extends TickerBehaviour {

    private Point target;
    private Runnable onComplete;
    private MovementBehaviour parentBehaviour;

    public MoveToBehaviour(
      Agent a,
      long period,
      Point target,
      Runnable onComplete,
      MovementBehaviour parentBehaviour
    ) {
      super(a, period);
      this.target = target;
      this.onComplete = onComplete;
      this.parentBehaviour = parentBehaviour;
    }

    protected void onTick() {
      if (isInjured) return;
      if (!path.isEmpty()) {
        Point nextStep = path.poll();
        if (isValidMove(position, nextStep, usingVehicle)) {
          mapFrame.setPath(path);
          position.setLocation(nextStep);
          mapFrame.updatePosition(getAID().getLocalName(), position, color);
          setPosition(nextStep);
          // agentSays("Moved to " + nextStep);

          // Check if we need to switch to walking
          if (
            usingVehicle &&
            (
              cityMap.getCell(nextStep.x, nextStep.y).contains("Sidewalk") ||
              cityMap.getCell(nextStep.x, nextStep.y).contains("Hospital") ||
              cityMap.getCell(nextStep.x, nextStep.y).contains("House") ||
              cityMap
                .getCell(nextStep.x, nextStep.y)
                .contains("Police Station") ||
              cityMap.getCell(nextStep.x, nextStep.y).contains("Fire Station")
            )
          ) {
            usingVehicle = false;
            inTraffic = false;
            // agentSays("Switching to walking.");
          }
        } else {
          // agentSays("Recalculating path due to an obstacle at " + nextStep);
          path = calculatePath(position, target); // Recalculate if blocked
        }
      } else {
        // agentSays("Target " + target + ".");
        // agentSays("Position " + position + ".");
        // agentSays("Reached the end of the path.");
        stop();
        if (onComplete != null) {
          onComplete.run();
        }
        parentBehaviour.setMoveToBehaviourActive(false); // Mark that MoveToBehaviour is no longer active
      }
    }
  }

  protected class MovementBehaviour extends TickerBehaviour {

    public MovementBehaviour(Agent a, long period) {
      super(a, period);
      findDestination();
    }

    protected void onTick() {
      try {
        if (isInjured) return;

        if (destinationReached || position.equals(destination)) {
          agentSays("Destination reached at " + position);
          if (usingVehicle) {
            // agentSays("Parking the vehicle at " + position);
            cityMap.clearVehiclePosition(position);
            usingVehicle = false;
            inTraffic = false;
          }
          destinationReached = false;
          clearDestination();
          return;
        }

        if (destination == null) findDestination();
        // agentSays("Moving from " + position + " to " + destination);

        String typeOfDestination = cityMap.getCell(
          destination.x,
          destination.y
        );

        if (typeOfDestination.contains("Road") && !ownsCar) {
          // agentSays("I don't have a car so I can't go to the road");
          clearDestination();
          return;
        }

        if (manhattanDistance(position, destination) > 14) {
          if (ownsCar) {
            if (
              typeOfDestination.equals("Crosswalk") ||
              typeOfDestination.contains("Road")
            ) {
              if (usingVehicle) {
                // I'm on the road and the destination is a road, so I'll keep going
                transitionToMoveToBehaviour(destination);
              } else {
                // I'm not on the road, so I'll go to the nearest road
                Point nearestRoad = findNearestRoadOrSidewalk(position, false);
                Point nearestSidewalk = findNearestRoadOrSidewalk(
                  nearestRoad,
                  true
                );
                transitionToMoveToBehaviour(
                  nearestSidewalk,
                  () -> {
                    // agentSays(
                    // "Reached the vehicle. Now driving to the destination."
                    // );
                    usingVehicle = true;
                    inTraffic = true;
                    spawnVehicle(nearestRoad);
                    setPosition(nearestRoad);
                    transitionToMoveToBehaviour(destination);
                  }
                );
              }
            } else {
              // The destination is not a road, so I'll go to the nearest road and drive
              if (usingVehicle) {
                Point nearestRoadToPark = findNearestRoadOrSidewalk(
                  destination,
                  false
                );
                transitionToMoveToBehaviour(
                  nearestRoadToPark,
                  () -> {
                    // agentSays(
                    // "Reached the nearest Road. Now parking the vehicle."
                    // );
                    usingVehicle = false;
                    inTraffic = false;
                    Point nearestSidewalk = findNearestRoadOrSidewalk(
                      destination,
                      true
                    );
                    setPosition(nearestSidewalk);
                    transitionToMoveToBehaviour(destination);
                  }
                );
              } else {
                usingVehicle = false;
                inTraffic = false;
                Point nearestRoad = findNearestRoadOrSidewalk(position, false);
                Point nearestSidewalkToRoad = findNearestRoadOrSidewalk(
                  nearestRoad,
                  true
                );
                transitionToMoveToBehaviour(
                  nearestSidewalkToRoad,
                  () -> {
                    // agentSays(
                    //   "Reached the nearest Road. Now driving to the destination."
                    // );
                    usingVehicle = true;
                    inTraffic = true;
                    spawnVehicle(nearestRoad);
                    setPosition(nearestRoad);
                    // Now that we're on the road, we can drive to the destination
                    // the destination is not a road, so I'll go to the nearest road and drive
                    Point nearestRoadToPark = findNearestRoadOrSidewalk(
                      destination,
                      false
                    );
                    transitionToMoveToBehaviour(
                      nearestRoadToPark,
                      () -> {
                        // agentSays(
                        //   "Reached the nearest Road. Now parking the vehicle."
                        // );
                        usingVehicle = false;
                        inTraffic = false;
                        Point nearestSidewalk = findNearestRoadOrSidewalk(
                          nearestRoadToPark,
                          true
                        );
                        setPosition(nearestSidewalk);
                        transitionToMoveToBehaviour(destination);
                      }
                    );
                  }
                );
              }
            }
          } else {
            usingVehicle = false;
            inTraffic = false;
            transitionToMoveToBehaviour(destination);
          }
        } else {
          // It's near, so I'm going to walk
          if (!usingVehicle) {
            if (typeOfDestination.contains("Road") && ownsCar) {
              Point nearestSidewalk = findNearestRoadOrSidewalk(
                destination,
                true
              );
              transitionToMoveToBehaviour(
                nearestSidewalk,
                () -> {
                  agentSays(
                    "Reached the vehicle. Now driving to the destination."
                  );
                  usingVehicle = true;
                  inTraffic = true;
                  spawnVehicle(destination);
                  setPosition(destination);
                }
              );
            } else {
              transitionToMoveToBehaviour(destination);
            }
          } else {
            // If it's near and I'm on the road I have to park
            Point nearestRoadToPark = findNearestRoadOrSidewalk(
              destination,
              false
            );
            Point nearestSidewalkToStep = findNearestRoadOrSidewalk(
              destination,
              true
            );
            transitionToMoveToBehaviour(
              nearestRoadToPark,
              () -> {
                // agentSays("Reached the nearest Road. Now parking the vehicle.");
                usingVehicle = false;
                inTraffic = false;
                setPosition(nearestSidewalkToStep);
                transitionToMoveToBehaviour(destination);
              }
            );
          }
        }
      } catch (Exception e) {
        System.err.println("Exception in behavior tick: " + e.getMessage());
        e.printStackTrace();
      }
    }

    private void transitionToMoveToBehaviour(Point target) {
      transitionToMoveToBehaviour(target, null);
    }

    public void setMoveToBehaviourActive(boolean isActive) {
      moveToBehaviourActive = isActive;
    }

    private void transitionToMoveToBehaviour(
      Point target,
      Runnable onComplete
    ) {
      if (!moveToBehaviourActive) { // Ensure only one MoveToBehaviour runs at a time
        path = calculatePath(position, target);
        if (path != null && !path.isEmpty()) {
          moveToBehaviourActive = true;
          addBehaviour(
            new MoveToBehaviour(
              CitizenAgent.this,
              (int) (Math.random() * 700 + 1200),
              target,
              onComplete,
              this
            )
          );
        } else {
          agentSays("Failed to find a path from " + position + " to " + target);
        }
      }
    }
  }

  // respondes to request of position
  private void addLocationHandlingBehaviour() {
    addBehaviour(
      new CyclicBehaviour(this) {
        public void action() {
          ACLMessage msg = receive(
            MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
          );
          if (msg != null && "Requesting location".equals(msg.getContent())) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            Point myPosition = getPosition();
            reply.setContent(myPosition.x + "," + myPosition.y);
            send(reply);
          } else {
            block();
          }
        }
      }
    );
  }

  protected void clearDestination() {
    destination = null;
    path.clear();
    destinationReached = false;
  }

  private void spawnVehicle(Point pos) {
    agentSays("Vehicle spawned at " + pos);
    cityMap.setVehiclePosition(pos);
  }

  protected void findDestination() {
    if (destination != null) {
      // agentSays("Destination already set to " + destination);
      return;
    }
    Point dest;
    do {
      int x = new Random().nextInt(cityMap.size);
      int y = new Random().nextInt(cityMap.size);
      dest = new Point(x, y);
    } while (
      !cityMap
        .getCell(dest.x, dest.y)
        .matches(
          "Sidewalk|Road|Crosswalk|Hospital|House|Police Station|Fire Station"
        )
    );

    destination = dest;
    agentSays("Destination set to " + destination);
  }

  private Point findNearestRoadOrSidewalk(Point start, boolean needsSidewalk) {
    LinkedList<Point> queue = new LinkedList<>();
    Set<Point> visited = new HashSet<>();
    queue.add(start);
    visited.add(start);

    while (!queue.isEmpty()) {
      Point current = queue.poll();
      if (current == null) {
        return null;
      }
      if (!cityMap.withinBounds(current)) {
        agentSays("Out of bounds at " + current);
        continue;
      }
      String cellType = cityMap.getCell(current.x, current.y);

      if (cellType.contains("Road") && !needsSidewalk) {
        // agentSays("Nearest road found at " + current);
        return current; // Found the nearest road
      }

      if (
        "Sidewalk".equals(cellType) && needsSidewalk && hasRoadNeighbor(current)
      ) {
        // agentSays("Nearest sidewalk next to road found at " + current);
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

  protected LinkedList<Point> calculatePath(Point start, Point end) {
    Map<Point, Node> openList = new HashMap<>();
    Set<Point> closedList = new HashSet<>();
    Map<Point, Point> cameFrom = new HashMap<>();
    Map<Point, Integer> gScore = new HashMap<>();
    PriorityQueue<Node> priorityQueue = new PriorityQueue<>(
      Comparator.comparingInt(n -> n.f)
    );
    // agentSays("Calculating path from " + start + " to " + end);
    Node startNode = new Node(start, 0, manhattanDistance(start, end));

    openList.put(start, startNode);
    priorityQueue.add(startNode);
    gScore.put(start, 0);
    while (!priorityQueue.isEmpty()) {
      Node current = priorityQueue.poll();

      if (current.position.equals(end)) {
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

    if (!cityMap.withinBounds(nextPosition)) {
      // agentSays(
      //   "Move to " + nextPosition + " is out of bounds or not traversable."
      // );
      return false;
    }

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
        return (
          (dy == -1 && dx == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Down Only":
        return (
          (dy == 1 && dx == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Left Only":
        return (
          (dx == -1 && dy == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Right Only":
        return (
          (dx == 1 && dy == 0) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Straight Only":
        return (
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Straight Only or Turn Right":
        return (
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Straight Only or Turn Left":
        return (
          ((Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Up or Turn Right":
        return (
          ((dy == -1 && dx == 0) || (dy == 0 && dx == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Up or Turn Left":
        return (
          ((dy == -1 && dx == 0) || (dy == 0 && dx == -1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Down or Turn Right":
        return (
          ((dy == 1 && dx == 0) || (dy == 0 && dx == 1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Road - Go Down or Turn Left":
        return (
          ((dy == 1 && dx == 0) || (dy == 0 && dx == -1)) &&
          (nextCellType.contains("Road") || nextCellType.contains("Crosswalk"))
        );
      case "Crosswalk":
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

  public boolean getIsInjured() {
    return isInjured;
  }

  public void setInjured(boolean isInjured) {
    this.isInjured = isInjured;
  }

  public AID getHospitalNearby() {
    return hospitalNearby;
  }

  public void setHospitalNearby(AID hospitalNearby) {
    this.hospitalNearby = hospitalNearby;
  }

  public boolean getHelpRequested() {
    return helpRequested;
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

  public double getBalance() {
    return balance;
  }

  public void setBalance(double balance) {
    this.balance = balance;
  }

  public void setPreviousBalance(double previousBalance) {
    this.previousBalance = previousBalance;
  }

  public double getPreviousBalance() {
    return previousBalance;
  }

  public double getAwarenessRange() {
    return awarenessRange;
  }

  public void setAwarenessRange(double awarenessRange) {
    this.awarenessRange = awarenessRange;
  }

  public boolean getMoveToBehaviourActive() {
    return moveToBehaviourActive;
  }

  public void setMoveToBehaviourActive(boolean moveToBehaviourActive) {
    this.moveToBehaviourActive = moveToBehaviourActive;
  }

  public void setHome(Point home) {
    this.home = home;
  }

  public Point getHome() {
    return home;
  }

  public Boolean getIsHome() {
    return isHome;
  }

  public void setIsHome(Boolean isHome) {
    this.isHome = isHome;
  }

  public Object[] getArgs() {
    return args;
  }

  // -----------------
  // END GETTERS AND SETTERS
  // -----------------

  protected void agentSays(String message) {
    System.out.println(getAID().getLocalName() + ": " + message);
  }
}

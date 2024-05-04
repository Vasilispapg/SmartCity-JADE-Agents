package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Color;
import java.awt.Point;

public class CitizenAgent extends Agent {

  // Variables for the citizen
  protected boolean isInjured = false;
  protected boolean ownsCar;
  protected Vehicle vehicle; // Vehicle associated with the citizen
  Point position; // Represents the citizen's position on the map
  MapFrame mapFrame;
  private CityMap cityMap;
  Color color;

  // Variables for finding nurses
  private AID nurseNearby;
  private boolean helpRequested = false; // Flag to check if help has been requested

  // Retry parameters
  private int retryCounter = 0;
  private final int maxRetries = 5;
  private final long initialDelay = 5000; // milliseconds

  public CitizenAgent() {
    ownsCar = Math.random() < 0.7; // 70% chance the citizen owns a car
    if (ownsCar) {
      this.vehicle = VehicleFactory.createRandomVehicle(); // A method to create different types of vehicles
    }
  }

  public void setCityMap(CityMap cityMap) {
    this.cityMap = cityMap;
  }

  public void setMapFrame(MapFrame mapFrame) {
    this.mapFrame = mapFrame;
  }

  public void setPosition(Point position) {
    this.position = position;
  }

  public void setColor(Color color) {
    this.color = color;
  }

  protected void setup() {
    Object[] args = getArguments();
    System.out.println(
      "Hello! Citizen-agent " + getAID().getName() + " is ready."
    );
    if (args != null && args.length == 4) {
      this.cityMap = (CityMap) args[0];
      this.mapFrame = (MapFrame) args[1];
      this.position = (Point) args[2];
      this.color = (Color) args[3];
    }

    if (
      this.mapFrame == null ||
      this.cityMap == null ||
      this.position == null ||
      this.color == null
    ) {
      System.err.println(
        "MapFrame or CityMap or StaticColor not set before setup."
      );
      doDelete(); // Terminate agent if not properly initialized
      return;
    }

    mapFrame.updatePosition(getAID().getLocalName(), position, color);

    addBehaviour(new MovementBehaviour(this, 1000));
    addBehaviour(new DailyActivities(this, 1000));
    addBehaviour(new RequestHelp());
    addBehaviour(new ReceiveHealingConfirmation());

    if (mapFrame == null || cityMap == null) {
      System.err.println("MapFrame or CityMap not set before setup.");
      doDelete(); // Terminate agent if not properly initialized
    }
  }

  private class DailyActivities extends TickerBehaviour {

    public DailyActivities(Agent a, long period) {
      super(a, period);
    }

    public void onTick() {
      double chance = Math.random();
      if (chance < 0.2 && vehicle.getType() != "Ambulance" && !isInjured) {
        isInjured = true;
        agentSays("I am injured");
      }
    }
  }

  private class RequestHelp extends CyclicBehaviour {

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
          block(); // Stop retrying and wait for other events
          takeDown(); // Terminate the agent
        }
      }
    }
  }

  private void resetHelpRequest() {
    helpRequested = false;
    retryCounter = 0;
    nurseNearby = null;
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

  protected void takeDown() {
    agentSays("I Died.");
    if (isInjured) {
      mapFrame.updatePosition(getAID().getLocalName(), position, Color.BLACK);
    }
  }

  private class MovementBehaviour extends TickerBehaviour {

    public MovementBehaviour(Agent a, long period) {
      super(a, period);
    }

    protected void onTick() {
      if (isInjured) {
        return;
      }
      double direction = Math.random();
      if (direction < 0.25) {
        position.y = (position.y - 1 + cityMap.size) % cityMap.size;
      } else if (direction < 0.5) {
        position.x = (position.x - 1 + cityMap.size) % cityMap.size;
      } else if (direction < 0.75) {
        position.y = (position.y + 1) % cityMap.size;
      } else {
        position.x = (position.x + 1) % cityMap.size;
      }

      // Update the map with the new position
      mapFrame.updatePosition(getAID().getLocalName(), position, color);
    }
  }

  // -----------------
  // FINDING THE NURSES OF THE AGENTS
  // -----------------
  public void findNurses() {
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
      resetHelpRequest();
    }
  }

  // -----------------
  // END OF FINDING THE NURSES OF THE AGENTS
  // -----------------

  protected void agentSays(String message) {
    System.out.println(getAID().getLocalName() + ": " + message);
  }
  // -----------------
  // REGISTER TO SERVICE
  // -----------------
}

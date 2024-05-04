package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
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

  protected boolean isInjured = false;
  protected boolean ownsCar;
  protected Vehicle vehicle; // Vehicle associated with the citizen
  Point position; // Represents the citizen's position on the map
  MapFrame mapFrame;
  private CityMap cityMap;
  Color color;
  private AID nurseNearby;

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

    System.out.println(position + " color: " + color);
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
      if (chance < 0.2 && vehicle.getType() != "Ambulance") {
        isInjured = true;
        System.out.println(getAID().getLocalName() + " got injured.");
      }
    }
  }

  private class RequestHelp extends CyclicBehaviour {

    public void action() {
      if (isInjured) {
        if (nurseNearby == null) findNurses(); // This might need to be called less frequently
        if (nurseNearby != null) {
          System.out.println(getAID().getLocalName() + " is injured.");
          System.out.println("Requesting medical assistance at " + position);
          ACLMessage helpRequest = new ACLMessage(ACLMessage.REQUEST);
          helpRequest.addReceiver(nurseNearby);
          helpRequest.setContent(
            "Need medical assistance at position: " +
            position.x +
            "," +
            position.y
          );
          myAgent.send(helpRequest);
          System.out.println(
            "Sent help request to " + nurseNearby.getLocalName()
          );
        }
        block(5000); // Don't spam the network, retry after some time if needed
      }
    }
  }

  protected void takeDown() {
    System.out.println("Citizen " + getAID().getName() + " terminating.");
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
  // FINDING THE ROLES OF THE AGENTS
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
            System.out.println("Found nurse: " + nurseAID.getName());
            nurseNearby = nurseAID;
            break;
          }
        }
      }
      if (nurseNearby == null) {
        System.out.println("No available nurse found or all are self.");
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
      System.out.println(
        getAID().getLocalName() +
        " has been healed by " +
        msg.getSender().getLocalName()
      );
    }
  }
}

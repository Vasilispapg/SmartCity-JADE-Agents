package examples.smartCity;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;

public class CitizenAgent extends Agent {

  protected double trustworthiness; // Trust level of the citizen
  protected boolean knowsThief = false; // Whether the citizen can recognize the thief
  protected boolean isInjured = false;
  protected boolean isVictimOfTheft = false;
  protected boolean ownsCar;
  protected Vehicle vehicle; // Vehicle associated with the citizen
  protected String destination;
  private Point position; // Represents the citizen's position on the map
  private MapFrame mapFrame;
  private CityMap cityMap;

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

  protected void setup() {
    Object[] args = getArguments();
    if (args != null && args.length == 3) {
      this.cityMap = (CityMap) args[0];
      this.mapFrame = (MapFrame) args[1];
      this.position = (Point) args[2];
    }
    if (
      this.mapFrame == null || this.cityMap == null || this.position == null
    ) {
      System.err.println("MapFrame or CityMap not set before setup.");
      doDelete(); // Terminate agent if not properly initialized
      return;
    }

    mapFrame.updatePosition(getLocalName(), position); // Initial position update

    addBehaviour(new DailyActivities(this, 1000));
    addBehaviour(new ReportCrime());
    addBehaviour(new HandleEmergency());
    addBehaviour(new RequestAssistance());

    if (this instanceof Drivable && ownsCar) {
      addBehaviour(new CommuteBehaviour(this, 5000));
    } else {
      addBehaviour(new MovementBehaviour(this, 1000));
    }

    if (mapFrame == null || cityMap == null) {
      System.err.println("MapFrame or CityMap not set before setup.");
      doDelete(); // Terminate agent if not properly initialized
    }
  }

  private class CommuteBehaviour extends TickerBehaviour {

    public CommuteBehaviour(Agent a, long period) {
      super(a, period);
    }

    public void onTick() {
      if (vehicle != null) {
        System.out.println(
          getAID().getLocalName() +
          " is commuting in their " +
          vehicle.getType()
        );
        destination = "work"; // Example destination
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        // msg.addReceiver(/* traffic manager identifier */);
        msg.setContent("commuting to " + destination);
        send(msg);
      }
    }
  }

  private class DailyActivities extends TickerBehaviour {

    public DailyActivities(Agent a, long period) {
      super(a, period);
    }

    public void onTick() {
      double chance = Math.random();
      if (chance < 0.1) {
        isInjured = true;
        System.out.println(getAID().getLocalName() + " got injured.");
      } else if (chance >= 0.1 && chance < 0.2) {
        isVictimOfTheft = true;
        System.out.println(
          getAID().getLocalName() + " has been a victim of theft."
        );
      }
      if (Math.random() < 0.05) { // Small chance to spot a thief
        knowsThief = true;
        System.out.println(getAID().getLocalName() + " spotted a thief.");
      }
    }
  }

  private class ReportCrime extends CyclicBehaviour {

    public void action() {
      if (knowsThief) {
        ACLMessage report = new ACLMessage(ACLMessage.INFORM);
        // report.addReceiver(/* police agent identifier */);
        if (Math.random() < trustworthiness) {
          report.setContent("thief_spotted");
        } else {
          report.setContent("false_alarm"); // Sometimes lies, based on trustworthiness
        }
        send(report);
        knowsThief = false; // Reset after reporting
      }
      block(1000); // Report at most every second
    }
  }

  private class MovementBehaviour extends TickerBehaviour {

    public MovementBehaviour(Agent a, long period) {
      super(a, period);
    }

    protected void onTick() {
      // Update the agent's position
      // This is simplified; you'd have more complex logic to follow roads and avoid obstacles
      // random movement north, east, south, or west

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
      mapFrame.updatePosition(getAID().getLocalName(), position);
    }
  }

  private class RequestAssistance extends CyclicBehaviour {

    public void action() {
      MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
      ACLMessage msg = myAgent.receive(mt);
      if (msg != null) {
        System.out.println(
          getAID().getLocalName() + " received help: " + msg.getContent()
        );
      } else {
        block();
      }
    }
  }

  private class HandleEmergency extends CyclicBehaviour {

    public void action() {
      if (isInjured) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        // msg.addReceiver(/* insert nurse/ambulance agent name */);
        msg.setContent("need_medical_assistance");
        send(msg);
        isInjured = false; // Reset status after calling for help
      }

      if (isVictimOfTheft) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        // msg.addReceiver(/* insert police agent name */);
        msg.setContent("need_police_assistance");
        send(msg);
        isVictimOfTheft = false; // Reset status after calling for help
      }
      block(500); // Check every half second
    }
  }

  protected void takeDown() {
    System.out.println("Citizen " + getAID().getName() + " terminating.");
  }
}

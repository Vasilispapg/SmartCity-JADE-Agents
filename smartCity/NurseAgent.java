package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;
import java.util.LinkedList;

public class NurseAgent extends CitizenAgent {

  private DFAgentDescription dfd = new DFAgentDescription();
  private ServiceDescription sd = new ServiceDescription();
  private boolean isBusy = false;

  protected void setup() {
    super.setup();

    registerService();

    agentSays("I am a Nurse Agent. I am ready to serve.");
    if (ownsCar()) {
      setVehicle(new Vehicle("Ambulance"));
      agentSays(
        "ready, driving an " +
        getVehicle().getType() +
        " with speed " +
        getVehicle().getSpeed() +
        " km/h"
      );
    }

    addBehaviour(new HandleRequests());
    getLatch().countDown();
  }

  private class HandleRequests extends CyclicBehaviour {

    public void action() {
      try {
        ACLMessage msg = myAgent.receive(
          MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
        );
        if (msg != null) {
          if (!isBusy) {
            isBusy = true;
            agentSays(
              "received a message from " + msg.getSender().getLocalName()
            );

            myAgent.removeBehaviour(getMovementBehaviour());
            String content = msg.getContent();
            Point targetPosition = parsePosition(content);
            try {
              if (DFService.search(myAgent, dfd) != null) DFService.deregister(
                myAgent
              ); // Nurse is busy
            } catch (FIPAException e) {
              e.printStackTrace();
            }
            addBehaviour(
              new MoveToInjuredBehaviour(
                myAgent,
                targetPosition,
                msg.getSender()
              )
            );
          } else {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent("I'm busy right now");
            send(reply);
            agentSays(
              "I'm busy right now dear" + msg.getSender().getLocalName()
            );
          }
        } else {
          block();
        }
      } catch (Exception e) {
        System.err.println("Exception in message handling: " + e.getMessage());
        e.printStackTrace();
      }
    }

    private Point parsePosition(String content) {
      String[] parts = content.split(": ")[1].split(",");
      int x = Integer.parseInt(parts[0]);
      int y = Integer.parseInt(parts[1]);
      return new Point(x, y);
    }
  }

  private class MoveToInjuredBehaviour extends TickerBehaviour {

    private Point targetPosition;
    private AID targetAgent;

    public MoveToInjuredBehaviour(
      Agent a,
      Point targetPosition,
      AID targetAgent
    ) {
      super(a, 1000);
      this.targetPosition = targetPosition;
      this.targetAgent = targetAgent;
      setPath(new LinkedList<>()); // Reset the moving path to empty
    }

    protected void onTick() {
      // Horizontal movement
      Point position = getPosition();
      if (position.x < targetPosition.x) {
        position.x++;
      } else if (position.x > targetPosition.x) {
        position.x--;
      }

      // Vertical movement
      if (position.y < targetPosition.y) {
        position.y++;
      } else if (position.y > targetPosition.y) {
        position.y--;
      }

      //   agentSays(
      //     "position: " + position + ", target position: " + targetPosition
      //   );

      // Update the position on the map after moving

      getMapFrame()
        .updatePosition(getAID().getLocalName(), position, getColor());
      setPosition(position);
      //   agentSays("moved to " + position.x + "," + position.y);

      // Check if reached the target position
      if (targetPosition.equals(getPosition())) { // If no move was made, we're at the target position
        performHealing();
        agentSays(
          "Moved to the target position which is " +
          targetPosition +
          "i am at " +
          getPosition()
        );
        stop();
      }
    }

    private void performHealing() {
      agentSays("healing " + targetAgent.getLocalName());
      // send message to the injured agent
      ACLMessage healConfirm = new ACLMessage(ACLMessage.INFORM);
      healConfirm.addReceiver(targetAgent);
      healConfirm.setContent("Healed at " + getPosition());
      send(healConfirm);

      agentSays(
        "healed " + targetAgent.getLocalName() + " at " + getPosition()
      );
      // getPosition().setLocation(new Point(2, 2)); // Reset the position

      backToWork(myAgent);
    }
  }

  private void backToWork(Agent a) {
    a.addBehaviour(getMovementBehaviour()); // Add the regular movement behaviour
    isBusy = false;
    registerService();

    agentSays("back to work.");
    agentSays("ready to serve again my current position is " + getPosition());
  }

  @Override
  protected void takeDown() {
    try {
      if (DFService.search(this, dfd) != null) {
        DFService.deregister(this);
      }
    } catch (FIPAException fe) {
      System.err.println("Failed during takedown: " + fe.getMessage());
      fe.printStackTrace();
    }
    super.takeDown();
  }

  private void registerService() {
    dfd.setName(getAID());
    sd.setType("nurse");
    sd.setName("healthcare");
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    } catch (FIPAException fe) {
      System.err.println("Failed to register service: " + fe.getMessage());
      fe.printStackTrace();
    }
    agentSays("registered as a Nurse Agent.");
    isBusy = false;
  }
}

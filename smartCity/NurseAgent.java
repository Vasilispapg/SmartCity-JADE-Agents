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

public class NurseAgent extends CitizenAgent {

  private DFAgentDescription dfd = new DFAgentDescription();
  private ServiceDescription sd = new ServiceDescription();
  private boolean isBusy = false;

  protected void setup() {
    super.setup();
    System.out.println(getLocalName() + " started as a Nurse Agent.");
    vehicle = new Vehicle("Ambulance");
    System.out.println(
      "Nurse is driving an " +
      vehicle.getType() +
      " with speed " +
      vehicle.getSpeed() +
      " km/h"
    );
    registerService();

    addBehaviour(new HandleRequests());
  }

  private class HandleRequests extends CyclicBehaviour {

    public void action() {
      ACLMessage msg = myAgent.receive(
        MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
      );
      if (msg != null) {
        if (!isBusy) {
          isBusy = true;
          agentSays(
            "received a message from " + msg.getSender().getLocalName()
          );

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
            new MoveToInjuredBehaviour(myAgent, targetPosition, msg.getSender())
          );
        } else {
          ACLMessage reply = msg.createReply();
          reply.setPerformative(ACLMessage.REFUSE);
          reply.setContent("I'm busy right now");
          send(reply);
          agentSays("I'm busy right now dear" + msg.getSender().getLocalName());
        }
      } else {
        block();
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
    }

    protected void onTick() {
      // Horizontal movement
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
      mapFrame.updatePosition(getAID().getLocalName(), position, color);
      //   agentSays("moved to " + position.x + "," + position.y);

      // Check if reached the target position
      if (targetPosition.equals(position)) { // If no move was made, we're at the target position
        performHealing();
        registerService(); // Nurse is available again
        stop();
      }
    }

    private void performHealing() {
      agentSays("healing " + targetAgent.getLocalName());
      ACLMessage healConfirm = new ACLMessage(ACLMessage.INFORM);
      healConfirm.addReceiver(targetAgent);
      healConfirm.setContent("Healed at " + position);
      send(healConfirm);
    }
  }

  @Override
  protected void takeDown() {
    // Deregister from the yellow pages
    try {
      if (DFService.search(this, dfd) != null) DFService.deregister(this);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
    agentSays("terminating.");
  }

  private void registerService() {
    dfd.setName(getAID());
    sd.setType("nurse");
    sd.setName("healthcare");
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
    isBusy = false;
  }
}

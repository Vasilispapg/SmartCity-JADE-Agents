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

  private boolean isAvailable = true;

  protected void setup() {
    super.setup();
    System.out.println(getLocalName() + " started as a Nurse Agent.");
    vehicle = new Vehicle("Ambulance");
    System.out.println(
      "Nurse agent ready, driving an " +
      vehicle.getType() +
      " with speed " +
      vehicle.getSpeed() +
      " km/h"
    );
    registerService();

    addBehaviour(new HandleRequests());
  }

  private void registerService() {
    DFAgentDescription dfd = new DFAgentDescription();
    dfd.setName(getAID());
    ServiceDescription sd = new ServiceDescription();
    sd.setType("nurse");
    sd.setName("healthcare");
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  private class HandleRequests extends CyclicBehaviour {

    public void action() {
      ACLMessage msg = myAgent.receive(
        MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
      );
      if (msg != null) {
        System.out.println(
          "Received help request from " + msg.getSender().getLocalName()
        );
        String content = msg.getContent();
        Point targetPosition = parsePosition(content);
        addBehaviour(
          new MoveToInjuredBehaviour(myAgent, targetPosition, msg.getSender())
        );
        isAvailable = false; // Nurse is now busy
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
      if (!position.equals(targetPosition)) {
        // Simple movement logic: move one step towards the target
        if (position.x < targetPosition.x) position.x++; else if (
          position.x > targetPosition.x
        ) position.x--;

        if (position.y < targetPosition.y) position.y++; else if (
          position.y > targetPosition.y
        ) position.y--;

        // Update position on the map
        mapFrame.updatePosition(getAID().getLocalName(), position, color);
        System.out.println(getAID().getLocalName() + " moving to " + position);
      } else {
        // Once at the location, perform healing
        performHealing();
        stop();
      }
    }

    private void performHealing() {
      System.out.println(
        getAID().getLocalName() +
        " has healed " +
        targetAgent.getLocalName() +
        " at " +
        position
      );
      ACLMessage healConfirm = new ACLMessage(ACLMessage.INFORM);
      healConfirm.addReceiver(targetAgent);
      healConfirm.setContent("Healed at " + position);
      send(healConfirm);
      isAvailable = true; // Nurse becomes available again
    }
  }

  @Override
  protected void takeDown() {
    // Deregister from the yellow pages
    try {
      DFService.deregister(this);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
    System.out.println(getAID().getName() + " terminating.");
  }
}

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

    registerService();

    agentSays("I am a Nurse Agent. I am ready to serve.");
    setOwnsCar(true);
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
            setDestination(null);
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
      // Set the global destination to the injured position
      setDestination(targetPosition);

      // add the new behavior to the agent
      if (getMovementBehaviour() != null) removeBehaviour(
        getMovementBehaviour()
      );
      MovementBehaviour movementBehaviour = new MovementBehaviour(
        myAgent,
        1000
      );
      setMovementBehaviour(movementBehaviour);
      addBehaviour(movementBehaviour);
      // add the new behavior to the agent
      agentSays(
        "Moving to " +
        targetPosition +
        " to heal " +
        targetAgent.getLocalName() +
        "My destination is " +
        getDestination()
      );
    }

    protected void onTick() {
      if (getPosition().equals(targetPosition)) {
        performHealing();
        // Reset or clear the destination after healing
        setDestination(null);
        setDestinationReached(true);
        // Optionally set a new destination here or leave it to other behaviors
        findDestination();
        stop();
      }
    }

    private void performHealing() {
      // Healing logic here
      agentSays(
        "Healing " + targetAgent.getLocalName() + " at " + getPosition()
      );
      // Send healing confirmation to the injured agent
      ACLMessage healConfirm = new ACLMessage(ACLMessage.INFORM);
      healConfirm.addReceiver(targetAgent);
      healConfirm.setContent("Healed at " + getPosition());
      send(healConfirm);

      // Post-healing routines or cleanup
      backToWork();
    }

    private void backToWork() {
      // Reset busy state and re-enable normal movement behaviors
      isBusy = false;
      registerService();
      // addBehaviour(new MovementBehaviour(myAgent, 1000));
      agentSays("I'm available for service");
    }
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

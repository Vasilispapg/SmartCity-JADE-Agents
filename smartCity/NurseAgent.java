package examples.smartCity;

import examples.smartCity.CitizenAgent.MovementBehaviour;
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
  private AID injuredAID;

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
    addBehaviour(new RequestTasksFromHospital(this, 1000)); // Check for tasks every 5 seconds
    addBehaviour(new HandleTaskResponses());
    getLatch().countDown();
  }

  private class RequestTasksFromHospital extends TickerBehaviour {

    public RequestTasksFromHospital(Agent a, long period) {
      super(a, period);
    }

    protected void onTick() {
      if (isBusy) return;
      if (null == getHospitalNearby()) {
        findHospital();
      }

      if (null == getHospitalNearby()) return;

      agentSays("Requesting task from " + getHospitalNearby().getLocalName());

      ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
      request.addReceiver(getHospitalNearby());
      request.setContent("Requesting task");
      request.setConversationId("task-request-" + System.currentTimeMillis());
      send(request);
    }
  }

  private class HandleTaskResponses extends CyclicBehaviour {

    public void action() {
      if (isBusy) return;
      ACLMessage msg = myAgent.receive();
      if (msg != null && msg.getPerformative() == ACLMessage.PROPOSE) {
        System.out.println(
          getLocalName() + " received a task proposal: " + msg.getContent()
        );
        ACLMessage response = msg.createReply();

        if (!isBusy) {
          setDestination(null);

          response.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
          myAgent.send(response);
          agentSays("I am accepting task");
          isBusy = true;

          injuredAID =
            new AID(
              msg.getContent().split("message")[0].split(":")[1].trim(),
              true
            );

          // getting the injured agent's position
          Citizen injuredAgent = (Citizen) AgentRegistry.getAgent(
            injuredAID.getLocalName()
          );

          Point injuredPosition = injuredAgent.getPosition();

          addBehaviour(
            new MoveToInjuredBehaviour(myAgent, injuredPosition, injuredAID)
          );
        } else {
          injuredAID =
            new AID(
              msg.getContent().split("message")[0].split(":")[1].trim(),
              true
            );
          response.setPerformative(ACLMessage.REJECT_PROPOSAL);
          response.setContent(
            "I cannot accept the task to heal " + injuredAID.getLocalName()
          );
          myAgent.send(response);
          agentSays("I am rejecting task");
        }
      } else {
        block();
      }
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
        targetPosition.x +
        "," +
        targetPosition.y +
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
      setDestination(null);
      setHospitalNearby(null);
      addBehaviour(new MovementBehaviour(myAgent, 1000));
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
    sd.setType("nurse");
    sd.setName("healthcare");
    dfd.setName(getAID());
    dfd.addServices(sd);

    try {
      // Check if the agent is already registered
      DFAgentDescription[] result = DFService.search(this, dfd);
      if (result.length > 0) {
        agentSays("Already registered.");
      } else {
        DFService.register(this, dfd);
        agentSays("Registered as a Nurse Agent.");
      }
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  protected Boolean getIsBusy() {
    return isBusy;
  }
}

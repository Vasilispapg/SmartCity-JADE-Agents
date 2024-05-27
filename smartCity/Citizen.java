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
import jade.wrapper.AgentController;
import java.awt.Point;
import java.util.LinkedList;

public class Citizen extends CitizenAgent {

  private double fear = Math.random() * 0.7 + 0.1; // Random fear between 0.1 and 0.8

  private AID closestPoliceStation;
  private Point thiefSeenPosition;

  protected void setup() {
    super.setup();
    agentSays("I am a Citizen Agent. I am ready to serve.");
    if (ownsCar()) {
      setVehicle(new Vehicle("Bicycle"));
    }

    registerAsCitizen();

    addBehaviour(new balanceCheck(this, (int) (Math.random() * 700 + 1200))); //check your pockets every 10 seconds
    // addBehaviour(new beThief()); // TODO

    getLatch().countDown();
  }

  protected void takeDown() {
    agentSays("I am done serving.");
    deregisterAsCitizen();
    super.takeDown();
  }

  private void registerAsCitizen() {
    try {
      DFAgentDescription dfd = new DFAgentDescription();
      dfd.setName(getAID());
      ServiceDescription sd = new ServiceDescription();
      sd.setType("citizen");
      sd.setName(getLocalName());
      dfd.addServices(sd);
      DFService.register(this, dfd);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  private void deregisterAsCitizen() {
    try {
      DFService.deregister(this);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  private class balanceCheck extends TickerBehaviour {

    public balanceCheck(Agent a, long period) {
      super(a, period); // Repeat every 2 seconds
    }

    public void onTick() {
      if (getBalance() < getPreviousBalance()) {
        setPreviousBalance(getBalance());
        try {
          DFAgentDescription template = new DFAgentDescription();
          ServiceDescription sd = new ServiceDescription();
          sd.setType("thief");
          template.addServices(sd);
          DFAgentDescription[] results = DFService.search(myAgent, template);
          findClosestThief(results);

          template = new DFAgentDescription();
          sd = new ServiceDescription();
          sd.setType("PoliceStation");
          template.addServices(sd);
          results = DFService.search(myAgent, template);
          findClosestPoliceStation(results, 35);
          if (getClosestPoliceStation() != null) {
            findClosestPoliceStation(results, 45);
          }
          callPolice();
        } catch (FIPAException fe) {
          fe.printStackTrace();
        }
      }
    }
  }

  private void findClosestPoliceStation(
    DFAgentDescription[] results,
    double range
  ) {
    for (DFAgentDescription dfd : results) {
      AID policeStation = dfd.getName();
      double distance = getPosition()
        .distance(
          ((PoliceStation) AgentRegistry.getAgent(policeStation)).getPosition()
        );
      if (distance < range) {
        range = distance;
        setClosestPoliceStation(policeStation);
      }
    }
  }

  private AID findClosestThief(DFAgentDescription[] results) {
    double minDistance = 4;
    AID closestThief = null;
    for (DFAgentDescription dfd : results) {
      AID thief = dfd.getName();
      double distance = getPosition()
        .distance(((ThiefAgent) AgentRegistry.getAgent(thief)).getPosition());
      if (distance < minDistance) {
        minDistance = distance;
        closestThief = thief;
        thiefSeenPosition =
          ((ThiefAgent) AgentRegistry.getAgent(thief)).getPosition();
      }
    }
    return closestThief;
  }

  private void callPolice() {
    if (getClosestPoliceStation() != null) {
      if (fear < Math.random() * 0.44 + 0.2) {
        agentSays(
          "I am being robbed but I am not scared enough to call the police"
        );
      }
      agentSays("I am being robbed. Calling the police station.");
      ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
      msg.addReceiver(getClosestPoliceStation());
      msg.setContent(
        "I am being robbed at " + getPosition().x + "," + getPosition().y
      );
      msg.setConversationId("cased-report-" + System.currentTimeMillis());
      send(msg);

      // Wait for police to arrive\
      // STOP MOVEMENT
      setDestination(null);
      setPath(new LinkedList<>());
      removeBehaviour(getMovementBehaviour());

      addBehaviour(
        new CyclicBehaviour() {
          public void action() {
            MessageTemplate mtPrefix = new MessageTemplate(
              new MessageTemplate.MatchExpression() {
                @Override
                public boolean match(ACLMessage message) {
                  String conversationId = message.getConversationId();
                  return (
                    conversationId != null &&
                    conversationId.startsWith("help-citizen-")
                  );
                }
              }
            );

            // Combine the custom template with the MatchPerformative template
            MessageTemplate mt = MessageTemplate.and(
              mtPrefix,
              MessageTemplate.MatchPerformative(ACLMessage.INFORM)
            );
            ACLMessage msg = receive(mt);
            if (msg != null) {
              agentSays("Police arrived at the scene.");
              sendInfoToPolice(msg.getSender());
              removeBehaviour(this);
            } else {
              block();
            }
          }
        }
      );
    } else {
      agentSays("I am being robbed but there is no police station nearby");
    }
  }

  private void sendInfoToPolice(AID policeStation) {
    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
    msg.addReceiver(policeStation);
    if (thiefSeenPosition != null) {
      msg.setContent(
        "I saw a thief at " +
        thiefSeenPosition.x +
        " , " +
        thiefSeenPosition.y +
        " ."
      );
    } else {
      msg.setContent("I was robbed but I didn't see the thief.");
    }
    msg.setConversationId("citizen-report-" + System.currentTimeMillis());
    send(msg);
  }

  public double getFear() {
    return fear;
  }

  public void setFear(double fear) {
    this.fear = fear;
  }

  public AID getClosestPoliceStation() {
    return closestPoliceStation;
  }

  public void setClosestPoliceStation(AID closestPoliceStation) {
    this.closestPoliceStation = closestPoliceStation;
  }

  public Point getThiefSeenPosition() {
    return thiefSeenPosition;
  }

  public void setThiefSeenPosition(Point thiefSeenPosition) {
    this.thiefSeenPosition = thiefSeenPosition;
  }

  public class beThief extends CyclicBehaviour {

    public void action() {
      if (getBalance() == 0) {
        agentSays("I have no money, i have to steal some.");
        takeDown();

        try {
          AgentRegistry.deregisterAgent(myAgent);
          int counter = 0;

          DFAgentDescription template = new DFAgentDescription();
          ServiceDescription sd = new ServiceDescription();
          sd.setType("thief");
          template.addServices(sd);
          DFAgentDescription[] results = DFService.search(myAgent, template);
          for (DFAgentDescription dfd : results) {
            if (dfd.getName().getLocalName().contains("Thief")) {
              counter++;
            }
          }

          AgentController controller = getContainerController()
            .createNewAgent(
              "ThiefAgent" + counter++,
              "examples.smartCity.ThiefAgent",
              getArgs()
            );
          controller.start();

          return;
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else {
        block();
      }
    }
  }
}

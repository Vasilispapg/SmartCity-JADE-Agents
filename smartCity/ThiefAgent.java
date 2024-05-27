package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class ThiefAgent extends CitizenAgent {

  private double stealProbability = Math.random() * 0.4 + 0.1; // Random probability between 0.1 and 0.5
  private double power = Math.random() * 0.7 + 0.1; // Random power between 0.1 and 0.8
  private double stealRange = 2.0;

  protected void setup() {
    super.setup();
    agentSays("I am a Thief Agent. I am ready to steal.");
    addBehaviour(new StealMoneyBehaviour());
    addBehaviour(
      new LocateNearbyCitizens(this, (int) (Math.random() * 700 + 4000))
    );
    addBehaviour(new hearingCopVoices());
    registerAsThief();
    addBehaviour(
      new iAmHomeBehaviour(this, (int) (Math.random() * 700 + 5000))
    );
    getLatch().countDown();
  }

  public class iAmHomeBehaviour extends TickerBehaviour {

    public iAmHomeBehaviour(Agent a, long period) {
      super(a, period);
    }

    public void onTick() {
      if (getPosition().equals(getHome())) {
        setIsHome(true);
      }
    }
  }

  private class hearingCopVoices extends CyclicBehaviour {

    public void action() {
      MessageTemplate mtPrefix = new MessageTemplate(
        new MessageTemplate.MatchExpression() {
          @Override
          public boolean match(ACLMessage message) {
            String conversationId = message.getConversationId();
            return (
              conversationId != null &&
              conversationId.startsWith("catch-thief-")
            );
          }
        }
      );
      MessageTemplate mt = MessageTemplate.and(
        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
        mtPrefix
      );
      ACLMessage msg = myAgent.receive(mt);
      if (msg != null) {
        PoliceAgent cop = (PoliceAgent) AgentRegistry.getAgent(
          msg.getSender().getLocalName()
        );
        Point copPosition = cop.getPosition();
        Point myPosition = getPosition();
        if (
          myPosition.distance(copPosition) <= 3 && Math.random() * 0.6 > power
        ) {
          agentSays("I was caught by " + cop.getLocalName());
          takeDown();
        } else {
          // Move away from the cop
          setDestination(getHome());
        }
      } else {
        block();
      }
    }
  }

  // Behaviour to periodically locate nearby citizens
  private class LocateNearbyCitizens extends TickerBehaviour {

    public LocateNearbyCitizens(Agent a, long period) {
      super(a, period); // Repeat every 2 seconds
    }

    public void onTick() {
      DFAgentDescription template = new DFAgentDescription();
      ServiceDescription sd = new ServiceDescription();
      sd.setType("citizen");
      template.addServices(sd);
      try {
        DFAgentDescription[] results = DFService.search(myAgent, template);
        for (DFAgentDescription dfd : results) {
          AID citizen = dfd.getName();
          if (!citizen.equals(getAID())) { // Avoid including itself
            requestLocation(citizen); // Request location from each citizen found
          }
        }
      } catch (FIPAException fe) {
        fe.printStackTrace();
      }
    }

    private void requestLocation(AID citizen) {
      ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
      req.addReceiver(citizen);
      req.setContent("Requesting location");
      req.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
      myAgent.send(req);
    }
  }

  private void registerAsThief() {
    try {
      DFAgentDescription dfd = new DFAgentDescription();
      dfd.setName(getAID());
      ServiceDescription sd = new ServiceDescription();
      sd.setType("thief");
      sd.setName(getLocalName());
      dfd.addServices(sd);
      DFService.register(this, dfd);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  private class StealMoneyBehaviour extends CyclicBehaviour {

    private List<AID> nearbyCitizens = new ArrayList<>();

    public void action() {
      ACLMessage msg = myAgent.receive();
      if (msg != null && msg.getPerformative() == ACLMessage.INFORM) {
        Point otherPosition = parsePosition(msg.getContent());
        Point myPosition = getPosition();
        if (myPosition.distance(otherPosition) <= stealRange) {
          nearbyCitizens.add(msg.getSender());
        }
      }

      if (Math.random() < stealProbability && !nearbyCitizens.isEmpty()) {
        // Randomly pick one nearby citizen to attempt to steal from
        int index = (int) (Math.random() * nearbyCitizens.size());
        AID target = nearbyCitizens.get(index);
        nearbyCitizens.remove(index);

        double stolenAmount = steal(target);
        if (stolenAmount > 0) {
          agentSays(
            " stole $" + stolenAmount + " from " + target.getLocalName()
          );
          // Assuming there's a method to update balances
          updateBalances(target, stolenAmount);
        }
      }
      block(); // Wait for new messages
    }

    private void updateBalances(AID target, double stolenAmount) {
      CitizenAgent targetAgent = (CitizenAgent) AgentRegistry.getAgent(target);
      if (targetAgent != null) {
        targetAgent.setBalance(targetAgent.getBalance() - stolenAmount);
        setBalance(getBalance() + stolenAmount);
      }
    }

    private double steal(AID target) {
      // Steal a random amount of money from the target
      CitizenAgent targetAgent = (CitizenAgent) AgentRegistry.getAgent(target);
      if (targetAgent == null) {
        return 0.0;
      }
      double stolenAmount = Math.round(
        Math.sqrt(Math.random() * targetAgent.getBalance() - 1)
      );
      if (stolenAmount <= 0) {
        return 0.0;
      }
      return stolenAmount;
    }

    // Utility method to parse positions from messages
    private Point parsePosition(String content) {
      String[] parts = content.split(",");
      int x = Integer.parseInt(parts[0].trim());
      int y = Integer.parseInt(parts[1].trim());
      return new Point(x, y);
    }
  }
}

package examples.smartCity;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class ThiefAgent extends CitizenAgent {

  private double stealProbability = Math.random() * 0.4; // Random probability between 0 and 0.4
  private double stealRange = 2.0;

  protected void setup() {
    super.setup();
    agentSays("I am a Thief Agent. I am ready to steal.");
    addBehaviour(new StealMoneyBehaviour());
    addBehaviour(new LocateNearbyCitizens());
  }

  // Behaviour to periodically locate nearby citizens
  private class LocateNearbyCitizens extends CyclicBehaviour {

    public void action() {
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
      block(1000); // Repeat every 2 seconds
    }

    private void requestLocation(AID citizen) {
      ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
      req.addReceiver(citizen);
      req.setContent("Requesting location");
      req.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
      myAgent.send(req);
    }
  }

  private class StealMoneyBehaviour extends CyclicBehaviour {

    private List<AID> nearbyCitizens = new ArrayList<>();

    public void action() {
      ACLMessage msg = myAgent.receive();
      if (msg != null && msg.getPerformative() == ACLMessage.INFORM) {
        Point otherPosition = parsePosition(msg.getContent());
        Point myPosition = getPosition();
        agentSays(
          "Received location from " +
          msg.getSender().getLocalName() +
          ": " +
          myPosition.distance(otherPosition)
        );
        myPosition = getPosition();
        agentSays(
          "My position: " +
          myPosition.toString() +
          " " +
          "Other position: " +
          otherPosition.toString()
        );
        if (myPosition.distance(otherPosition) <= stealRange) {
          nearbyCitizens.add(msg.getSender());
        }
      }

      if (Math.random() < stealProbability && !nearbyCitizens.isEmpty()) {
        // Randomly pick one nearby citizen to attempt to steal from
        AID target = nearbyCitizens.get(
          (int) (Math.random() * nearbyCitizens.size())
        );
        double stolenAmount = steal(target);
        if (stolenAmount > 0) {
          System.out.println(
            getAID().getLocalName() +
            " stole $" +
            stolenAmount +
            " from " +
            target.getLocalName()
          );
          // Assuming there's a method to update balances
          updateBalances(target, stolenAmount);
        }
      }
      block(10000); // Check for messages and possibly steal every 5 seconds
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

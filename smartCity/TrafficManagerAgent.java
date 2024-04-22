package examples.smartCity;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class TrafficManagerAgent extends Agent {

  protected void setup() {
    addBehaviour(new ManageTraffic());
  }

  private class ManageTraffic extends CyclicBehaviour {

    public void action() {
      MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
      ACLMessage msg = myAgent.receive(mt);
      if (msg != null) {
        System.out.println("Traffic Manager received: " + msg.getContent());
        // Implement traffic optimization logic here
        optimizeTraffic(msg.getContent());
      } else {
        block();
      }
    }

    private void optimizeTraffic(String trafficData) {
      // Example: Adjust traffic lights, change routes, send traffic updates
      System.out.println("Optimizing traffic based on data: " + trafficData);
    }
  }
}

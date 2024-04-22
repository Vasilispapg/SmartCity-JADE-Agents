package examples.smartCity;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;

public class TrafficLightAgent extends Agent {

  private String currentSignal = "green";

  protected void setup() {
    addBehaviour(new AdjustTrafficSignal(this, 1000));
  }

  private class AdjustTrafficSignal extends TickerBehaviour {

    public AdjustTrafficSignal(Agent a, long period) {
      super(a, 10000); // Check for new signals every 10 seconds
    }

    public void onTick() {
      // Simulate receiving new signal instructions from Traffic Manager
      currentSignal = (Math.random() < 0.5) ? "green" : "red";
      System.out.println(
        getAID().getLocalName() + " traffic signal set to " + currentSignal
      );
    }
  }
}

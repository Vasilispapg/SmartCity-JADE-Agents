package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class PoliceAgent extends CitizenAgent {

  protected void setup() {
    super.setup();
    System.out.println(getLocalName() + " started as a Police Agent.");
    vehicle = new Vehicle("PoliceCar");
    System.out.println(
      "Police agent ready, driving a " +
      vehicle.getType() +
      " with speed " +
      vehicle.getSpeed() +
      " km/h"
    );
    addBehaviour(new PatrolBehavior());
    addBehaviour(new InvestigateCrimeBehavior());
    addBehaviour(new RespondToEmergencyBehavior());
    addBehaviour(new ArrestThiefBehavior());
  }

  protected void performDailyActivities() {
    // Police-specific activities
    double chance = Math.random();
    if (chance < 0.1) {
      System.out.println(
        getAID().getLocalName() + " is patrolling the streets."
      );
    }
    if (knowsThief) {
      handleThiefSpotted();
    }
  }

  private void handleThiefSpotted() {
    System.out.println(
      getAID().getLocalName() + " has spotted a thief and is responding."
    );
    // Additional logic for arresting a thief
    knowsThief = false; // Reset the flag
  }

  private class PatrolBehavior extends CyclicBehaviour {

    public void action() {
      // Example behavior
      System.out.println(getLocalName() + " is patrolling.");
      block(1000); // Blocks the behaviour for 1 second
    }
  }

  private class InvestigateCrimeBehavior extends CyclicBehaviour {

    public void action() {
      // Logic for investigating crimes
      ACLMessage crimeReport = myAgent.receive(
        MessageTemplate.MatchContent("report_crime")
      );
      if (crimeReport != null) {
        // Extract details of the crime from the message
        // For simplicity, we assume the message content is the location of the crime
        String crimeLocation = crimeReport.getContent();
        System.out.println(
          getAID().getLocalName() +
          " is investigating a crime at " +
          crimeLocation
        );
        // Perform investigation logic here
      }
      block(2000); // wait for 2 seconds before checking for new reports
    }
  }

  private class RespondToEmergencyBehavior extends CyclicBehaviour {

    public void action() {
      // Logic for responding to emergencies
      ACLMessage emergencyCall = myAgent.receive(
        MessageTemplate.MatchContent("emergency")
      );
      if (emergencyCall != null) {
        // Extract the location and type of the emergency from the message
        System.out.println(
          getAID().getLocalName() + " is responding to an emergency."
        );
        // Perform response logic here
      }
      block(3000); // wait for 3 seconds before checking for new emergencies
    }
  }

  private class ArrestThiefBehavior extends CyclicBehaviour {

    public void action() {
      if (knowsThief) {
        // Logic to arrest thief
        System.out.println(getAID().getLocalName() + " is arresting a thief.");
        // Reset the flag and perform arrest logic here
        knowsThief = false;
      }
      block(1000); // wait for 1 second before checking for thieves
    }
  }

  private class PatrolAreaBehavior extends TickerBehaviour {

    public PatrolAreaBehavior(Agent a, long period) {
      super(a, period);
    }

    protected void onTick() {
      // Logic for patrolling a designated area
      System.out.println(getAID().getLocalName() + " is patrolling the area.");
      // Perform patrolling logic here, like moving to different locations
    }
  }

  private class ReportBackToStationBehavior extends TickerBehaviour {

    public ReportBackToStationBehavior(Agent a, long period) {
      super(a, period);
    }

    protected void onTick() {
      // Logic for reporting back to the police station
      System.out.println(
        getAID().getLocalName() + " is reporting back to the station."
      );
      // Send status report to the station
      ACLMessage report = new ACLMessage(ACLMessage.INFORM);
      report.addReceiver(new AID("PoliceStationAgent", AID.ISLOCALNAME));
      report.setContent("status_report");
      // this.send(report);
    }
  }
  // ... (rest of the PoliceAgent class)

}

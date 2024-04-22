package examples.smartCity;

public class NurseAgent extends CitizenAgent {

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
  }

  protected void performDailyActivities() {
    if (isInjured) {
      System.out.println(getAID().getLocalName() + " is treating injuries.");
      isInjured = false; // Reset the flag
    }
  }
}

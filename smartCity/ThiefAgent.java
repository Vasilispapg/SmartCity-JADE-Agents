package examples.smartCity;

public class ThiefAgent extends CitizenAgent {

  protected void setup() {
    super.setup();
    System.out.println(getLocalName() + " started as a Thief Agent.");
    vehicle = new Vehicle("Bike");
    System.out.println(
      "Thief agent ready, riding a " +
      vehicle.getType() +
      " with speed " +
      vehicle.getSpeed() +
      " km/h"
    );
  }

  protected void performDailyActivities() {
    double chance = Math.random();
    if (chance < 0.1) {
      System.out.println(getAID().getLocalName() + " is attempting a theft.");
      isVictimOfTheft = true; // Simulate the action of stealing
    }
  }
}

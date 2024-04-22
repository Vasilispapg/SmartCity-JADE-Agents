package examples.smartCity;

public class Citizen extends CitizenAgent {

  protected void setup() {
    super.setup();
    System.out.println(getLocalName() + " started as a Simple Citizen Agent.");
    vehicle = VehicleFactory.createRandomVehicle();
    System.out.println(
      "Simple Citizen agent ready, riding a " +
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

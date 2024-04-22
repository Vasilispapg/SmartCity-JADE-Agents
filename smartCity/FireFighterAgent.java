package examples.smartCity;

public class FireFighterAgent extends CitizenAgent {

  protected void setup() {
    super.setup();
    System.out.println(getLocalName() + " started as a Firefighter Agent.");
    vehicle = new Vehicle("FireTruck");
    System.out.println(
      "Firefighter agent ready, driving a " +
      vehicle.getType() +
      " with speed " +
      vehicle.getSpeed() +
      " km/h"
    );
  }

  protected void performDailyActivities() {
    double chance = Math.random();
    if (chance < 0.1) {
      System.out.println(getAID().getLocalName() + " is extinguishing a fire.");
    }
  }
}

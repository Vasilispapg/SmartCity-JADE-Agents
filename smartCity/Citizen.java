package examples.smartCity;

public class Citizen extends CitizenAgent {

  protected void setup() {
    super.setup();
    System.out.println(getLocalName() + " started as a Simple Citizen Agent.");
    if (ownsCar()) {
      setVehicle(new Vehicle("Bicycle"));
    }

    getLatch().countDown();
  }
}

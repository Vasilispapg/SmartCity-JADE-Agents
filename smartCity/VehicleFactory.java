package examples.smartCity;

public class VehicleFactory {

  public static Vehicle createRandomVehicle() {
    String[] types = { "Car", "PoliceCar", "Ambulance", "Firetruck", "Bike" };
    int idx = (int) (Math.random() * types.length);
    return new Vehicle(types[idx]);
  }
}

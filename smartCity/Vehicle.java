package examples.smartCity;

public class Vehicle {

  private String type;
  private double speed;

  public Vehicle(String type) {
    this.type = type;
    this.speed = 0;
    changeSpeedByType(type);
  }

  private void changeSpeedByType(String type) {
    if (type.equals("PoliceCar")) {
      this.speed = Math.random() * (70) + 30;
    } else if (type.equals("Ambulance")) {
      this.speed = Math.random() * (100) + 50;
    } else if (type.equals("FireTruck")) {
      this.speed = Math.random() * (80) + 40;
    } else {
      this.speed = Math.random() * (1) + 20;
    }
  }

  protected void changeSpeed(double speed) {
    this.speed = speed;
  }

  public String getType() {
    return type;
  }

  public double getSpeed() {
    return speed;
  }
}

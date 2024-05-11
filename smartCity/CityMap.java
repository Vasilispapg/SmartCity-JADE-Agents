package examples.smartCity;

import java.awt.Point;
import java.util.Random;

public class CityMap {

  protected String[][] grid;
  private boolean[][] trafficMap; // Traffic map to track vehicle positions

  protected int size;
  protected int blockSize = 16; // Smaller blocks within the 64x64 grid

  public CityMap(int size) {
    this.size = size;
    this.grid = new String[size][size];
    this.trafficMap = new boolean[size][size]; // Initialize traffic map
    initializeMap();
  }

  public boolean[][] getTrafficMap() {
    return trafficMap;
  }

  public boolean isTrafficBlocked(Point position) {
    if (withinBounds(position)) {
      return trafficMap[position.x][position.y];
    }
    return false;
  }

  public void clearVehiclePosition(Point position) {
    if (withinBounds(position)) {
      trafficMap[position.x][position.y] = false;
    }
  }

  public void setVehiclePosition(Point position) {
    if (withinBounds(position) && canPlaceVehicle(position)) {
      trafficMap[position.x][position.y] = true;
    }
  }

  public boolean canPlaceVehicle(Point position) {
    return withinBounds(position) && !trafficMap[position.x][position.y];
  }

  private boolean withinBounds(Point position) {
    System.out.println("Checking bounds for position: " + position.toString());

    return (
      position.x >= 0 &&
      position.x < size &&
      position.y >= 0 &&
      position.y < size
    );
  }

  private void initializeMap() {
    // Clear the map first
    clearMap();

    // Set up blocks
    for (int i = 0; i < size; i += blockSize) {
      for (int j = 0; j < size; j += blockSize) {
        setupBlock(i, j);
      }
    }
    System.out.println("Blocks" + size * size);

    System.out.println("Map initialized");
  }

  private void clearMap() {
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        grid[i][j] = "Empty";
      }
    }
  }

  private void setupBlock(int x, int y) {
    defineRoadsAndSidewalks(x, y);
    placeBuildingsAndCrosswalks(x, y);
  }

  private void defineRoadsAndSidewalks(int x, int y) {
    for (int i = 0; i < blockSize; i++) {
      for (int j = 0; j < blockSize; j++) {
        if (i == 0 || i == blockSize - 1 || j == 0 || j == blockSize - 1) {
          grid[x + i][y + j] = "Road - Straight Only";
        } else {
          grid[x + i][y + j] = "Sidewalk";
        }
      }
    }

    // Roads for all possible movements
    for (int i = 1; i < blockSize - 1; i++) {
      grid[x][y + i] = "Road - Go Right Only"; //Right
      grid[x + blockSize - 1][y + i] = "Road - Go Left Only"; // Left
      grid[x + i][y] = "Road - Go Up Only";
      grid[x + i][y + blockSize - 1] = "Road - Go Down Only";
    }
  }

  private void placeBuildingsAndCrosswalks(int x, int y) {
    Random rand = new Random();
    // Assign buildings in the center of the block
    for (int i = 1; i < blockSize - 1; i++) {
      for (int j = 1; j < blockSize - 1; j++) {
        if (i % (blockSize / 4) == 0 && j % (blockSize / 4) == 0) {
          String building = selectBuilding(rand);
          grid[x + i][y + j] = building;
        }
      }
    }

    // Add crosswalks at intersections
    for (int i = 0; i < blockSize; i++) {
      if (i == blockSize / 2 - 1 || i == blockSize / 2) {
        grid[x + i][y] = "Crosswalk";
        grid[x + i][y + blockSize - 1] = "Crosswalk";
        grid[x][y + i] = "Crosswalk";
        grid[x + blockSize - 1][y + i] = "Crosswalk";
      }
    }
  }

  private String selectBuilding(Random rand) {
    double choice = rand.nextDouble();
    if (choice < 0.25) {
      return "Hospital";
    } else if (choice < 0.50) {
      return "Police Station";
    } else if (choice < 0.75) {
      return "Fire Station";
    } else {
      return "House";
    }
  }

  public String getCell(int x, int y) {
    return grid[x][y];
  }

  public void printMap() {
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        System.out.print(grid[i][j] + " ");
      }
      System.out.println();
    }
  }
}

package examples.smartCity;

import java.util.Random;

public class CityMap {

  protected String[][] grid;
  protected int size;
  protected int blockSize = 8; // Smaller blocks within the 64x64 grid

  public CityMap(int size) {
    this.size = size;
    this.grid = new String[size][size];
    initializeMap();
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

    fillEmptyWithBuildings();
  }

  private void fillEmptyWithBuildings() {
    Random rand = new Random();
    for (int k = 0; k < size; k++) {
      for (int l = 0; l < size; l++) {
        if (grid[k][l].equals("Empty")) {
          double chance = rand.nextDouble(); // Random chance between 0 and 1

          if (chance < 0.15) {
            grid[k][l] = "Fire Station"; // 15% for fire station
          } else if (chance < 0.15 + 0.20) {
            grid[k][l] = "Police Station"; // 20% for police station
          } else if (chance < 0.15 + 0.20 + 0.25) {
            grid[k][l] = "Hospital"; // 25% for hospital
          } else {
            grid[k][l] = "House"; // The rest for houses, which is 40%
          }
        }
      }
    }
  }

  private void clearMap() {
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        grid[i][j] = "Empty";
      }
    }
  }

  private void setupBlock(int x, int y) {
    // Define the inner part of the block
    for (int i = 0; i < blockSize; i++) {
      for (int j = 0; j < blockSize; j++) {
        if (i == 0 || i == blockSize - 1 || j == 0 || j == blockSize - 1) {
          grid[x + i][y + j] = "Road";
        } else if (i == blockSize / 2 || j == blockSize / 2) {
          // continue;
        } else if (i == blockSize / 2 - 1 || j == blockSize / 2 - 1) {
          grid[x + i][y + j] = "Sidewalk";
        } else if (i == blockSize / 2 + 1 || j == blockSize / 2 + 1) {
          grid[x + i][y + j] = "Sidewalk";
        } else {
          grid[x + i][y + j] = "Sidewalk";
        }
      }
    }
  }

  private void placeBuildings(String entity, int count) {
    Random rand = new Random();
    for (int i = 0; i < count; i++) {
      int x, y;
      do {
        x = rand.nextInt(size - 2) + 1; // Avoid the outermost road
        y = rand.nextInt(size - 2) + 1; // Avoid the outermost road
      } while (!grid[x][y].equals("Empty") || grid[x][y].equals("Sidewalk"));
      grid[x][y] = entity;
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

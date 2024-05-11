package examples.smartCity;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;

public class MapFrame extends JFrame {

  private CityMap cityMap;
  private int tileSize = 25; // Size of each tile in pixels
  private Map<String, Point> agentPositions; // Tracks the position and color of each agent
  private Map<String, Color> agentColors = new HashMap<>();
  private List<Point> path; // List to hold the path points

  public MapFrame(CityMap cityMap) {
    this.cityMap = cityMap;

    this.agentPositions = new HashMap<>(); // Initialize the map
    setupUI();
  }

  public void setPath(List<Point> path) {
    this.path = path;
  }

  @SuppressWarnings("unused")
  private void drawPath(Graphics g) {
    if (path != null) {
      g.setColor(Color.GREEN); // Color for the path
      for (Point point : path) {
        g.fillRect(point.x * tileSize, point.y * tileSize, tileSize, tileSize);
      }
    }
  }

  private void drawArrows(Graphics g, String roadType, int x, int y) {
    int centerX = x + tileSize / 2;
    int centerY = y + tileSize / 2;
    int arrowSize = tileSize / 4;

    g.setColor(Color.WHITE); // Set arrow color

    switch (roadType) {
      case "Road - Go Up Only":
        drawArrowUp(g, centerX, centerY, arrowSize);
        break;
      case "Road - Go Down Only":
        drawArrowDown(g, centerX, centerY, arrowSize);
        break;
      case "Road - Go Left Only":
        drawArrowLeft(g, centerX, centerY, arrowSize);
        break;
      case "Road - Go Right Only":
        drawArrowRight(g, centerX, centerY, arrowSize);
        break;
      case "Road - Straight or Turn Right":
        drawArrowStraight(g, centerX, centerY, arrowSize);
        drawArrowRight(g, centerX, centerY, arrowSize);
        break;
      case "Road - Straight or Turn Left":
        drawArrowStraight(g, centerX, centerY, arrowSize);
        drawArrowLeft(g, centerX, centerY, arrowSize);
        break;
      // Add more cases if there are other types of roads
    }
  }

  private void drawArrowStraight(Graphics g, int x, int y, int arrowSize) {
    g.fillPolygon(
      new int[] { x, x, x },
      new int[] { y - arrowSize, y + arrowSize, y },
      3
    );
  }

  private void drawArrowUp(Graphics g, int x, int y, int size) {
    g.fillPolygon(
      new int[] { x, x - size, x + size },
      new int[] { y - size, y + size, y + size },
      3
    );
  }

  private void drawArrowDown(Graphics g, int x, int y, int size) {
    g.fillPolygon(
      new int[] { x, x - size, x + size },
      new int[] { y + size, y - size, y - size },
      3
    );
  }

  private void drawArrowLeft(Graphics g, int x, int y, int size) {
    g.fillPolygon(
      new int[] { x - size, x + size, x + size },
      new int[] { y, y - size, y + size },
      3
    );
  }

  private void drawArrowRight(Graphics g, int x, int y, int size) {
    g.fillPolygon(
      new int[] { x + size, x - size, x - size },
      new int[] { y, y - size, y + size },
      3
    );
  }

  private void setupUI() {
    int bufferHeight = 38;

    // The total height now includes an extra buffer.
    setSize(cityMap.size * tileSize, (cityMap.size * tileSize) + bufferHeight);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
    setVisible(true);
  }

  public void updatePosition(String agentName, Point position, Color color) {
    if (agentPositions != null && color != null) {
      agentPositions.put(agentName, position);
      agentColors.put(agentName, color);
      SwingUtilities.invokeLater(this::repaint);
    } else {
      System.err.println("Agent positions or colors map is not initialized.");
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    int startY = getInsets().top;

    for (int i = 0; i < cityMap.size; i++) {
      for (int j = 0; j < cityMap.size; j++) {
        drawTile(g, i, j, startY);

        // Draw coordinates
        g.setColor(Color.BLACK);
        // g.drawString(
        //   j + "," + i, // Correct the order to show column, row format and start from 0
        //   j * tileSize + 5,
        //   i * tileSize + startY + 15 // Adjust position to make sure it's visible within each tile
        // );
      }
    }
    // drawPath(g); // Draw the path
    for (Map.Entry<String, Point> entry : agentPositions.entrySet()) {
      Point p = entry.getValue();
      g.setColor(agentColors.get(entry.getKey()));
      int ovalX = p.x * tileSize + tileSize / 4;
      int ovalY = p.y * tileSize + tileSize / 4;
      int ovalWidth = tileSize / 2;
      g.fillOval(ovalX, ovalY, ovalWidth, ovalWidth);

      // Set color for text, optionally use a contrasting color for better visibility

      // Adjust the position of the text as needed, here it's centered on the oval
      FontMetrics fm = g.getFontMetrics();
      int stringWidth = fm.stringWidth(entry.getKey());
      int stringX = ovalX + (ovalWidth - stringWidth) / 2;
      int stringY =
        ovalY + ovalWidth / 2 + fm.getAscent() / 2 - fm.getDescent() / 2;
      g.setColor(Color.WHITE);
      String nameID = entry.getKey().replaceAll("[^A-Z0-9]", "");
      g.drawString(nameID, stringX, stringY);
    }
  }

  private void drawTile(Graphics g, int i, int j, int startY) {
    String cellType = cityMap.getCell(i, j);
    g.setColor(getColorForCell(cellType));
    int x = j * tileSize;
    int y = i * tileSize + startY;
    g.fillRect(x, y, tileSize, tileSize);

    // Draw arrows on roads
    if (cellType.startsWith("Road")) {
      drawArrows(g, cellType, x, y);
    }
  }

  private Color getColorForCell(String cellType) {
    StaticColors colors = new StaticColors();
    switch (cellType) {
      case "Road":
        return colors.getColor("Road");
      case "Sidewalk":
        return colors.getColor("Sidewalk");
      case "Crosswalk":
        return colors.getColor("Crosswalk");
      case "House":
        return colors.getColor("House");
      case "Hospital":
        return colors.getColor("Hospital");
      case "Police Station":
        return colors.getColor("Police Station");
      case "Fire Station":
        return colors.getColor("Fire Station");
      default:
        return colors.getColor("default");
    }
  }
}

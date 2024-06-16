package examples.smartCity;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;

public class MapFrame extends JFrame {

  private CityMap cityMap;
  private int tileSize = 25;
  private Map<String, Point> agentPositions;
  private Map<String, Color> agentColors = new HashMap<>();
  private List<Point> path;

  public MapFrame(CityMap cityMap) {
    this.cityMap = cityMap;
    this.agentPositions = new HashMap<>();
    setupUI();
  }

  public void setPath(List<Point> path) {
    this.path = path;
  }

  private void drawPath(Graphics g) {
    if (path != null) {
      g.setColor(Color.GREEN);
      for (Point point : path) {
        g.fillRect(
          point.x * tileSize,
          point.y * tileSize + getInsets().top,
          tileSize,
          tileSize
        ); // Adjust for top inset
      }
    }
  }

  private void drawArrows(Graphics g, String roadType, int x, int y) {
    int centerX = x + tileSize / 2;
    int centerY = y + tileSize / 2;
    int arrowSize = tileSize / 4;

    g.setColor(Color.WHITE);

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
      case "Road - Straight Only or Turn Right":
        drawArrowStraight(g, centerX, centerY, arrowSize);
        drawArrowRight(g, centerX, centerY, arrowSize);
        break;
      case "Road - Straight Only or Turn Left":
        drawArrowStraight(g, centerX, centerY, arrowSize);
        drawArrowLeft(g, centerX, centerY, arrowSize);
        break;
      case "Road - Go Up or Turn Right":
        drawArrowUp(g, centerX, centerY, arrowSize);
        drawArrowRight(g, centerX, centerY, arrowSize);
        break;
      case "Road - Go Up or Turn Left":
        drawArrowUp(g, centerX, centerY, arrowSize);
        drawArrowLeft(g, centerX, centerY, arrowSize);
        break;
      case "Road - Go Down or Turn Right":
        drawArrowDown(g, centerX, centerY, arrowSize);
        drawArrowRight(g, centerX, centerY, arrowSize);
        break;
      case "Road - Go Down or Turn Left":
        drawArrowDown(g, centerX, centerY, arrowSize);
        drawArrowLeft(g, centerX, centerY, arrowSize);
        break;
      case "Road - Straight Only":
        drawArrowStraight(g, centerX, centerY, arrowSize);
        break;
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
    int startY = getInsets().top; // Adjust for top inset

    for (int i = 0; i < cityMap.size; i++) {
      for (int j = 0; j < cityMap.size; j++) {
        drawTile(g, i, j, startY);
        // Draw coordinates
        g.setColor(Color.BLACK);
        // reduce the font size to fit the coordinates
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.drawString(j + "," + i, j * tileSize + 5, i * tileSize + startY + 15);
      }
    }
    // drawPath(g); // Draw the path
    for (Map.Entry<String, Point> entry : agentPositions.entrySet()) {
      Point p = entry.getValue();
      g.setColor(agentColors.get(entry.getKey()));
      int ovalX = p.x * tileSize + tileSize / 4;
      int ovalY = p.y * tileSize + tileSize / 4 + startY; // Adjust for top inset
      int ovalWidth = tileSize / 2;
      g.fillOval(ovalX, ovalY, ovalWidth, ovalWidth);

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
    String cellType = cityMap.getCell(j, i);
    g.setColor(getColorForCell(cellType));
    int x = j * tileSize;
    int y = i * tileSize + startY;
    g.fillRect(x, y, tileSize, tileSize);

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

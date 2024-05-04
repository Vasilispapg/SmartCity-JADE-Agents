package examples.smartCity;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

public class MapFrame extends JFrame {

  private CityMap cityMap;
  private int tileSize = 30; // Size of each tile in pixels
  private Map<String, Point> agentPositions; // Tracks the position and color of each agent
  private Map<String, Color> agentColors = new HashMap<>();

  public MapFrame(CityMap cityMap) {
    this.cityMap = cityMap;
    this.agentPositions = new HashMap<>(); // Initialize the map
    setupUI();
  }

  private void setupUI() {
    setTitle("City Map");
    setSize(cityMap.size * tileSize, cityMap.size * tileSize);
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
    for (int i = 0; i < cityMap.size; i++) {
      for (int j = 0; j < cityMap.size; j++) {
        drawTile(g, i, j);
      }
    }
    for (Map.Entry<String, Point> entry : agentPositions.entrySet()) {
      Point p = entry.getValue();
      g.setColor(agentColors.get(entry.getKey()));
      g.fillOval(
        p.x * tileSize + tileSize / 4,
        p.y * tileSize + tileSize / 4,
        tileSize / 2,
        tileSize / 2
      );
    }
  }

  private void drawTile(Graphics g, int i, int j) {
    StaticColors colors = new StaticColors();

    switch (cityMap.getCell(i, j)) {
      case "Road":
        g.setColor(colors.getColor("Road"));
        break;
      case "Sidewalk":
        g.setColor(colors.getColor("Sidewalk"));
        break;
      case "House":
        g.setColor(colors.getColor("House"));
        break;
      case "Hospital":
        g.setColor(colors.getColor("Hospital"));
        break;
      case "Police Station":
        g.setColor(colors.getColor("Police Station"));
        break;
      case "Fire Station":
        g.setColor(colors.getColor("Fire Station"));
        break;
      default:
        g.setColor(colors.getColor("default"));
        break;
    }
    g.fillRect(j * tileSize, i * tileSize, tileSize, tileSize);
  }
}

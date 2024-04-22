package examples.smartCity;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

public class MapFrame extends JFrame {

  private CityMap cityMap;
  private int tileSize = 30; // Size of each tile in pixels
  private Map<String, Point> agentPositions; // Tracks the position of each agent

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

  public void updatePosition(String agentName, Point position) {
    agentPositions.put(agentName, new Point(position));
    SwingUtilities.invokeLater(this::repaint); // Ensure GUI updates happen on the EDT
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
      // Adjust the drawing position based on tileSize
      g.fillOval(
        p.x * tileSize + tileSize / 4,
        p.y * tileSize + tileSize / 4,
        tileSize / 2,
        tileSize / 2
      );
    }
  }

  private void drawTile(Graphics g, int i, int j) {
    switch (cityMap.getCell(i, j)) {
      case "Road":
        g.setColor(Color.DARK_GRAY);
        break;
      case "Sidewalk":
        g.setColor(Color.LIGHT_GRAY);
        break;
      case "House":
        g.setColor(Color.decode("#793690"));
        break;
      case "Hospital":
        g.setColor(Color.WHITE);
        break;
      case "Police Station":
        g.setColor(Color.BLUE);
        break;
      case "Fire Station":
        g.setColor(Color.ORANGE);
        break;
      default:
        g.setColor(Color.BLACK);
        break;
    }
    g.fillRect(j * tileSize, i * tileSize, tileSize, tileSize);
  }
}

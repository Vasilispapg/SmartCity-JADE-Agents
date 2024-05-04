package examples.smartCity;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class StaticColors {

  private Map<String, Color> colorMap;

  public StaticColors() {
    colorMap = new HashMap<>();
    colorMap.put("Road", Color.DARK_GRAY);
    colorMap.put("Sidewalk", Color.LIGHT_GRAY);
    colorMap.put("House", Color.decode("#793690"));
    colorMap.put("Citizen", Color.decode("#793690"));
    colorMap.put("NurseAgent", Color.WHITE);
    colorMap.put("Hospital", Color.WHITE);
    colorMap.put("Police Station", Color.BLUE);
    colorMap.put("Fire Station", Color.ORANGE);
    colorMap.put("default", Color.BLACK);
  }

  public Color getColor(String key) {
    return colorMap.getOrDefault(key, colorMap.get("default"));
  }
}

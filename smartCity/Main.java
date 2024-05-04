package examples.smartCity;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import java.awt.Point;
import javax.swing.SwingUtilities;

public class Main {

  public static void main(String[] args) {
    // Start the GUI in the Event Dispatch Thread
    // 8 = 1 neighbor, 16 = 4 neighbors, 32 = 16 neighbors, etc.
    CityMap cityMap = new CityMap(16); // Move this outside of the EDT to share with agents
    MapFrame mapFrame = new MapFrame(cityMap);
    SwingUtilities.invokeLater(() -> {
      mapFrame.setVisible(true); // Ensure the map window is visible
    });
    StaticColors colorHandler = new StaticColors();

    // Start JADE runtime and setup agents
    Runtime rt = Runtime.instance();
    Profile profile = new ProfileImpl();
    profile.setParameter(Profile.MAIN_HOST, "localhost");
    profile.setParameter(Profile.GUI, "true");
    AgentContainer container = rt.createMainContainer(profile); // Main JADE container

    try {
      int identifier = 0;
      for (int i = 0; i < cityMap.size; i++) {
        for (int j = 0; j < cityMap.size; j++) {
          if (cityMap.getCell(i, j).equals("House") && identifier < 2) {
            generateAgents(
              container,
              "Citizen",
              identifier++,
              cityMap,
              mapFrame,
              new Point(i, j),
              colorHandler
            ); // Assuming each cell directly maps to a visual position
          }
          if (identifier == 2 && cityMap.getCell(i, j).equals("Hospital")) {
            generateAgents(
              container,
              "NurseAgent",
              identifier++,
              cityMap,
              mapFrame,
              new Point(i, j),
              colorHandler
            );
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void generateAgents(
    AgentContainer container,
    String agentType,
    int identifier,
    CityMap cityMap,
    MapFrame mapFrame,
    Point position,
    StaticColors colorHandler
  ) {
    try {
      Object[] args = new Object[] {
        cityMap,
        mapFrame,
        position,
        colorHandler.getColor(agentType), // Use the color for the House type
      };
      AgentController ac = container.createNewAgent(
        agentType + identifier,
        "examples.smartCity." + agentType,
        args
      );
      ac.start();
    } catch (Exception e) {
      System.err.println("Exception starting agent: " + e.toString());
    }
  }
}

package examples.smartCity;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import java.awt.Point;
import java.util.Random;
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

    // Start JADE runtime and setup agents
    Runtime rt = Runtime.instance();
    Profile profile = new ProfileImpl();
    profile.setParameter(Profile.MAIN_HOST, "localhost");
    profile.setParameter(Profile.GUI, "true");
    AgentContainer container = rt.createMainContainer(profile); // Main JADE container

    try {
      // Initialize agents
      // createRandomAgents(container, "PoliceAgent", 1, 1);
      // createRandomAgents(container, "NurseAgent", 1, 1);
      // createRandomAgents(container, "FireFighterAgent", 1, 1);
      // createRandomAgents(container, "ThiefAgent", 1, 1);
      int identifier = 0;
      for (int i = 0; i < cityMap.size; i++) {
        for (int j = 0; j < cityMap.size; j++) {
          if (cityMap.getCell(i, j).equals("House")) {
            generateAgents(
              container,
              "CitizenAgent",
              identifier++,
              cityMap,
              mapFrame,
              new Point(i, j)
            ); // Assuming each cell directly maps to a visual position
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void createRandomAgents(
    AgentContainer container,
    String agentType,
    int min,
    int max,
    CityMap cityMap,
    Point position,
    MapFrame mapFrame
  ) {
    Random rand = new Random();
    int count = rand.nextInt(max - min + 1) + min;
    for (int i = 0; i < count; i++) {
      try {
        Object[] args = new Object[] { cityMap, mapFrame, position };
        AgentController ac = container.createNewAgent(
          agentType + i,
          "examples.smartCity." + agentType,
          args
        );
        ac.start();
      } catch (Exception e) {
        System.err.println("Exception starting agent: " + e.toString());
      }
    }
  }

  private static void generateAgents(
    AgentContainer container,
    String agentType,
    int identifier,
    CityMap cityMap,
    MapFrame mapFrame,
    Point position
  ) {
    try {
      Object[] args = new Object[] { cityMap, mapFrame, position };
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

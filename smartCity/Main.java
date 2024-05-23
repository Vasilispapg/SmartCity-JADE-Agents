package examples.smartCity;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;

public class Main {

  private static List<CitizenAgent> agents = new ArrayList<>();

  public static void main(String[] args) {
    // 8 = 1 neighbor, 16 = 4 neighbors, 32 = 16 neighbors, etc.
    CityMap cityMap = new CityMap(32);
    MapFrame mapFrame = new MapFrame(cityMap);
    SwingUtilities.invokeLater(() -> {
      mapFrame.setVisible(true);
      StaticColors colorHandler = new StaticColors();

      int totalAgents = 1; // Update this based on how many agents you are starting
      CountDownLatch latch = new CountDownLatch(totalAgents);

      // Start JADE runtime and setup agents
      AgentContainer container = null;
      do {
        container = generateMainContainer(new ProfileImpl());
        if (container == null) {
          System.err.println("MAIN SYSTEM: Error creating the container");
          return;
        }
      } while (container == null);

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
                colorHandler,
                latch
              );
            }
            if (
              identifier < 3 && cityMap.getCell(i, j).equals("PoliceStation")
            ) {
              generateAgents(
                container,
                "PoliceAgent",
                identifier++,
                cityMap,
                mapFrame,
                new Point(i, j),
                colorHandler,
                latch
              );
            }
            if (identifier < 2 && cityMap.getCell(i, j).equals("Hospital")) {
              generateAgents(
                container,
                "ThiefAgent",
                identifier++,
                cityMap,
                mapFrame,
                new Point(i, j),
                colorHandler,
                latch
              );
            }
          }
        }
        // Wait for all agents to initialize
        latch.await();
        System.out.println("MAIN SYSTEM: All agents have been initialized.");
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private static AgentContainer generateMainContainer(Profile profile) {
    Runtime rt = Runtime.instance();
    Integer randomPort = (int) (Math.random() * 10000 + 10000);

    profile.setParameter(Profile.MAIN_HOST, "localhost");
    profile.setParameter(Profile.MAIN_PORT, randomPort.toString());
    profile.setParameter(Profile.GUI, "true");
    return rt.createMainContainer(profile);
  }

  private static void generateAgents(
    AgentContainer container,
    String agentType,
    int identifier,
    CityMap cityMap,
    MapFrame mapFrame,
    Point position,
    StaticColors colorHandler,
    CountDownLatch latch
  ) {
    try {
      Object[] args = new Object[] {
        cityMap,
        mapFrame,
        position,
        colorHandler.getColor(agentType),
        latch,
      };
      AgentController ac = container.createNewAgent(
        agentType + identifier,
        "examples.smartCity." + agentType,
        args
      );
      ac.start();
      // TODO : H LISTA EINAI ADEIA TSEKARE AYTO ME TO AID POY TOYS KANEIS EGGRAFIS NA DOYME TI PAIZEI
    } catch (Exception e) {
      System.err.println(
        "MAIN SYSTEM: Exception starting agent: " + e.toString()
      );
    }
  }
}

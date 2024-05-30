package examples.smartCity;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;

public class Main {

  public static void main(String[] args) {
    // 8 = 1 neighbor, 16 = 4 neighbors, 32 = 16 neighbors, etc.
    CityMap cityMap = new CityMap(32);
    MapFrame mapFrame = new MapFrame(cityMap);
    SwingUtilities.invokeLater(() -> {
      mapFrame.setVisible(true);
      StaticColors colorHandler = new StaticColors();

      int totalAgents = 15; // Update this based on how many agents you are starting
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

      // Start Sniffer Agent
      AgentController sniffer;
      try {
        sniffer =
          container.createNewAgent(
            "sniffer",
            "jade.tools.sniffer.Sniffer",
            null
          );
        sniffer.start();
      } catch (StaleProxyException e) {
        System.err.println("MAIN SYSTEM: Error starting sniffer agent");
        e.printStackTrace();
      }

      try {
        int identifierCitizen = 0;
        int identifierHospital = 0;
        int identifierNurse = 0;
        int identifierPoliceStation = 0;
        int identifierPoliceAgent = 0;
        int identifierThiefAgent = 0;
        do {
          for (int i = 0; i < cityMap.size; i++) {
            for (int j = 0; j < cityMap.size; j++) {
              identifierCitizen =
                generateAgentsModa(
                  "Citizen",
                  "House",
                  identifierCitizen,
                  cityMap,
                  mapFrame,
                  colorHandler,
                  latch,
                  container,
                  i,
                  j
                );
              if (
                cityMap.getNumOf("Hospital") >= identifierHospital + 1
              ) identifierHospital =
                generateAgentsModa(
                  "Hospital",
                  "Hospital",
                  identifierHospital,
                  cityMap,
                  mapFrame,
                  colorHandler,
                  latch,
                  container,
                  i,
                  j
                );
              identifierNurse =
                generateAgentsModa(
                  "NurseAgent",
                  "Hospital",
                  identifierNurse,
                  cityMap,
                  mapFrame,
                  colorHandler,
                  latch,
                  container,
                  i,
                  j
                );

              if (
                cityMap.getNumOf("PoliceStation") >= identifierPoliceStation + 1
              ) identifierPoliceStation =
                generateAgentsModa(
                  "PoliceStation",
                  "PoliceStation",
                  identifierPoliceStation,
                  cityMap,
                  mapFrame,
                  colorHandler,
                  latch,
                  container,
                  i,
                  j
                );
              identifierPoliceAgent =
                generateAgentsModa(
                  "PoliceAgent",
                  "PoliceStation",
                  identifierPoliceAgent,
                  cityMap,
                  mapFrame,
                  colorHandler,
                  latch,
                  container,
                  i,
                  j
                );
              identifierThiefAgent =
                generateAgentsModa(
                  "ThiefAgent",
                  "House",
                  identifierThiefAgent,
                  cityMap,
                  mapFrame,
                  colorHandler,
                  latch,
                  container,
                  i,
                  j
                );
              if (
                totalAgents <=
                (
                  identifierCitizen +
                  identifierHospital +
                  identifierNurse +
                  identifierPoliceStation +
                  identifierPoliceAgent +
                  identifierThiefAgent
                )
              ) {
                break;
              }
            }
          }
        } while (
          identifierCitizen +
          identifierHospital +
          identifierNurse +
          identifierPoliceStation +
          identifierPoliceAgent +
          identifierThiefAgent <
          totalAgents
        );

        // Wait for all agents to initialize
        latch.await();
        System.out.println("MAIN SYSTEM: All agents have been initialized.");
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private static int generateAgentsModa(
    String type,
    String typeOfDisplay,
    int identifier,
    CityMap cityMap,
    MapFrame mapFrame,
    StaticColors colorHandler,
    CountDownLatch latch,
    AgentContainer container,
    int i,
    int j
  ) {
    if (cityMap.getCell(i, j).equals(typeOfDisplay)) {
      generateAgents(
        container,
        type,
        identifier,
        cityMap,
        mapFrame,
        new Point(i, j),
        colorHandler,
        latch
      );
      return identifier + 1;
    }
    return identifier;
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
    } catch (Exception e) {
      System.err.println(
        "MAIN SYSTEM: Exception starting agent: " + e.toString()
      );
    }
  }
}

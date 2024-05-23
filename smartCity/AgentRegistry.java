package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import java.util.HashMap;
import java.util.Map;

public class AgentRegistry {

  private static Map<AID, Agent> agentMap = new HashMap<>();

  public static void registerAgent(Agent agent) {
    agentMap.put(agent.getAID(), agent);
  }

  public static Agent getAgent(AID aid) {
    return agentMap.get(aid);
  }

  public static void deregisterAgent(Agent agent) {
    agentMap.remove(agent.getAID());
  }
}

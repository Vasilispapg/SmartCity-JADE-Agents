package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import java.util.HashMap;
import java.util.Map;

public class AgentRegistry {

  private static Map<AID, Agent> agentMap = new HashMap<>();
  public static Map<String, Agent> stringAgentMap = new HashMap<>();

  public static void registerAgent(Agent agent, String name) {
    agentMap.put(agent.getAID(), agent);
    stringAgentMap.put(name, agent);
  }

  public static Agent getAgent(AID aid) {
    return agentMap.get(aid);
  }

  public static AID getAID(String name) {
    Agent agent = stringAgentMap.get(name);
    if (agent == null) return null;
    return agent.getAID();
  }

  public static void deregisterAgent(Agent agent) {
    agentMap.remove(agent.getAID());
    stringAgentMap.remove(agent.getLocalName());
  }

  public static Agent getAgent(String name) {
    return stringAgentMap.get(name);
  }

  public static void deregisterAgent(AID aid, String name) {
    agentMap.remove(aid);
    stringAgentMap.remove(name);
  }

  public static void printAgents() {
    System.out.println("Agents in registry:");
    for (Map.Entry<AID, Agent> entry : agentMap.entrySet()) {
      System.out.println(entry.getKey() + " : " + entry.getValue());
    }
  }
}

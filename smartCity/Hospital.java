package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Color;
import java.awt.Point;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;

public class Hospital extends Agent {

  private CityMap cityMap;
  private MapFrame mapFrame;
  private Point position;
  private Color color;
  private CountDownLatch latch;

  private Queue<ACLMessage> taskQueue = new LinkedList<>();
  private Map<AID, ACLMessage> taskQueueTrack = new HashMap<>();
  private Map<ACLMessage, AID> senderTrack = new HashMap<>();

  private Map<AID, String> activeTasks = new HashMap<>(); // Track which police is handling which task

  protected void setup() {
    Object[] args = getArguments();
    if (args != null && args.length > 0) {
      agentSays("I am a Hospital Agent. I am ready to serve.");

      if (args != null && args.length == 5) {
        this.cityMap = (CityMap) args[0];
        this.mapFrame = (MapFrame) args[1];
        this.position = (Point) args[2];
        this.color = (Color) args[3];
        this.latch = (CountDownLatch) args[4];
      } else {
        agentSays("Error initializing CitizenAgent. Arguments are invalid.");
        takeDown();
        return;
      }

      if (
        this.mapFrame == null ||
        this.cityMap == null ||
        this.position == null ||
        this.color == null ||
        this.latch == null
      ) {
        agentSays("Error initializing CitizenAgent. Arguments are null.");
        takeDown(); // Terminate agent if not properly initialized
        return;
      }

      mapFrame.updatePosition(getAID().getLocalName(), position, color);

      // Register the hospital in the DF
      registerHospitalInDF();
      // Add the injury report listener
      addBehaviour(new InjuryReportListener());
      addBehaviour(new sendTasksToNurses());
      addBehaviour(new ManageTaskResponses());

      // Count down the latch to signal that the agent is ready
      latch.countDown();
    } else {
      takeDown();
    }
  }

  protected void takeDown() {
    try {
      DFService.deregister(this);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
    agentSays("Hospital agent " + getAID().getName() + " terminating.");
  }

  private void registerHospitalInDF() {
    AgentRegistry.registerAgent(this, getAID().getLocalName());

    DFAgentDescription dfd = new DFAgentDescription();
    dfd.setName(getAID());
    ServiceDescription sd = new ServiceDescription();
    sd.setType("hospital");
    sd.setName("JADE-Hospital");
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  private class InjuryReportListener extends CyclicBehaviour {

    public void action() {
      // Custom MessageTemplate to match conversation IDs that start with "injury-report-"
      MessageTemplate mtPrefix = new MessageTemplate(
        new MessageTemplate.MatchExpression() {
          @Override
          public boolean match(ACLMessage message) {
            String conversationId = message.getConversationId();
            return (
              conversationId != null &&
              conversationId.startsWith("injury-report-")
            );
          }
        }
      );

      // Combine the custom template with the MatchPerformative template
      MessageTemplate mt = MessageTemplate.and(
        mtPrefix,
        MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
      );

      ACLMessage msg = receive(mt);
      if (msg != null) {
        agentSays(
          "Received help request from " +
          msg.getSender().getLocalName() +
          " at position: " +
          msg.getContent()
        );

        // Add the message to the task queue
        taskQueue.offer(msg);
        taskQueueTrack.put(msg.getSender(), msg);
        senderTrack.put(msg, msg.getSender());
      } else {
        block();
      }
    }
  }

  private class sendTasksToNurses extends CyclicBehaviour {

    double calculateTaskBalance(double distance, double severity) {
      return distance * severity;
    }

    public void action() {
      // Custom MessageTemplate to match conversation IDs that start with "injury-report-"
      MessageTemplate mtPrefix = new MessageTemplate(
        new MessageTemplate.MatchExpression() {
          @Override
          public boolean match(ACLMessage message) {
            String conversationId = message.getConversationId();
            return (
              conversationId != null &&
              conversationId.startsWith("task-request-")
            );
          }
        }
      );

      // Combine the custom template with the MatchPerformative template
      MessageTemplate mt = MessageTemplate.and(
        mtPrefix,
        MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
      );

      ACLMessage msg = myAgent.receive(mt);
      if (msg != null) {
        ACLMessage reply = msg.createReply();
        if (!taskQueue.isEmpty()) {
          ACLMessage task = taskQueue.poll();
          AID injuredCitizen = senderTrack.get(task);
          double balance = calculateTaskBalance(
            cityMap.getDistance(
              position,
              (
                (CitizenAgent) AgentRegistry.getAgent(
                  injuredCitizen.getLocalName()
                )
              ).getPosition()
            ),
            2
          );
          balance = Math.round(balance);
          reply.setPerformative(ACLMessage.PROPOSE);
          reply.setContent(
            "Injured Citizen: " +
            injuredCitizen.getLocalName() +
            " | Message: " +
            task.getContent() +
            " | Balance: " +
            balance
          );
          activeTasks.put(msg.getSender(), task.getConversationId());
        } else {
          reply.setPerformative(ACLMessage.REFUSE);
          reply.setContent("No tasks available");
        }
        myAgent.send(reply);
      } else {
        block();
      }
    }
  }

  private class ManageTaskResponses extends CyclicBehaviour {

    public void action() {
      ACLMessage response = receive(
        MessageTemplate.or(
          MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
          MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)
        )
      );
      if (response != null) {
        switch (response.getPerformative()) {
          case ACLMessage.ACCEPT_PROPOSAL:
            agentSays(
              response.getSender().getLocalName() + " accepted the task."
            );
            activeTasks.put(response.getSender(), response.getContent());
            taskQueueTrack.remove(response);
            senderTrack.remove(response);
            activeTasks.put(response.getSender(), response.getContent());

            break;
          case ACLMessage.REJECT_PROPOSAL:
            agentSays(
              "The nurse " +
              response.getSender().getLocalName() +
              " rejected the task. Task: " +
              response.getContent()
            );
            AID injuredAgent = new AID(
              response.getContent().split("heal")[1].trim(),
              true
            );

            taskQueue.offer(
              taskQueueTrack.get(
                AgentRegistry.getAID(injuredAgent.getLocalName())
              )
            );

            // Add the task back to the queue
            break;
        }
        // activeTasks.remove(response.getSender());
      } else {
        block();
      }
    }
  }

  private void agentSays(String message) {
    System.out.println(getLocalName() + ": " + message);
  }
}

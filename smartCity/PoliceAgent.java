package examples.smartCity;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.awt.Point;
import java.util.LinkedList;
import java.util.Queue;

public class PoliceAgent extends CitizenAgent {

  private DFAgentDescription dfd = new DFAgentDescription();
  private ServiceDescription sd = new ServiceDescription();
  private boolean isBusy = false;
  private AID policeStationNearby;
  private AID citizenAID;

  protected void setup() {
    super.setup();

    registerService();

    agentSays("I am a Police Agent. I am ready to serve.");
    setOwnsCar(true);
    if (ownsCar()) {
      setVehicle(new Vehicle("PoliceCar"));
      agentSays(
        "Ready, driving a " +
        getVehicle().getType() +
        " with speed " +
        getVehicle().getSpeed() +
        " km/h"
      );
    }

    addBehaviour(new RequestTasksFromStation(this, 1000));
    addBehaviour(new HandleTaskResponses());
    getLatch().countDown();
  }

  private void registerService() {
    sd.setType("policeagent");
    sd.setName(getLocalName());
    dfd.setName(getAID());
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    } catch (FIPAException e) {
      e.printStackTrace();
    }
  }

  private void findClosestPoliceStation(DFAgentDescription[] results) {
    double minDistance = 25;
    for (DFAgentDescription dfd : results) {
      AID policeStation = dfd.getName();
      double distance = getPosition()
        .distance(
          ((PoliceStation) AgentRegistry.getAgent(policeStation)).getPosition()
        );
      agentSays(
        "Distance to police station " +
        policeStation.getLocalName() +
        " is " +
        distance +
        " from " +
        (distance < minDistance)
      );
      if (distance < minDistance) {
        minDistance = distance;
        setPoliceStationNearby(policeStation);
      }
    }
  }

  protected void findPoliceStation() {
    DFAgentDescription template = new DFAgentDescription();
    ServiceDescription sd = new ServiceDescription();
    sd.setType("PoliceStation");
    template.addServices(sd);

    try {
      DFAgentDescription[] results = DFService.search(this, template);
      if (results.length > 0) {
        findClosestPoliceStation(results);
        agentSays(
          "Found PoliceStation: " + getPoliceStationNearby().getLocalName()
        );
      } else {
        agentSays("No available police stations right now.");
        setPoliceStationNearby(null);
      }
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }

  protected boolean getIsBusy() {
    return isBusy;
  }

  private class RequestTasksFromStation extends TickerBehaviour {

    public RequestTasksFromStation(Agent a, long period) {
      super(a, period);
    }

    protected void onTick() {
      if (isBusy) return;
      if (null == policeStationNearby) {
        findPoliceStation();
      }

      if (null == policeStationNearby) {
        agentSays("No police station nearby.");
      } else if (policeStationNearby != null) {
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(policeStationNearby);
        request.setContent("Requesting task");
        request.setConversationId("task-request-" + System.currentTimeMillis());
        myAgent.send(request);
        agentSays("Requested tasks from " + policeStationNearby.getLocalName());
      }
    }
  }

  private class HandleTaskResponses extends CyclicBehaviour {

    public void action() {
      if (isBusy) return;
      ACLMessage msg = myAgent.receive();
      if (msg != null && msg.getPerformative() == ACLMessage.PROPOSE) {
        System.out.println(
          getLocalName() + " received a task proposal: " + msg.getContent()
        );
        ACLMessage response = msg.createReply();

        if (!isBusy) {
          setDestination(null);

          response.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
          send(response);
          agentSays("I am accepting task");
          isBusy = true;

          citizenAID =
            new AID(
              msg.getContent().split("message")[0].split(":")[1].trim(),
              true
            );

          // getting the citizen agent's position
          Citizen citizenAgent = (Citizen) AgentRegistry.getAgent(
            citizenAID.getLocalName()
          );

          Point citizenPosition = citizenAgent.getPosition();

          addBehaviour(
            new MoveToCitizenBehaviour(myAgent, citizenPosition, citizenAID)
          );
        } else {
          citizenAID =
            new AID(
              msg.getContent().split("message")[0].split(":")[1].trim(),
              true
            );
          response.setPerformative(ACLMessage.REJECT_PROPOSAL);
          response.setContent(
            "I cannot accept the task to heal " + citizenAID.getLocalName()
          );
          send(response);
          agentSays("I am rejecting task");
        }
      } else {
        block();
      }
    }
  }

  private class MoveToCitizenBehaviour extends TickerBehaviour {

    private AID citizenAID;

    public MoveToCitizenBehaviour(
      Agent a,
      Point citizenPosition,
      AID citizenAID
    ) {
      super(a, 1000);
      this.citizenAID = citizenAID;
      setDestination(citizenPosition);
    }

    protected void onTick() {
      if (null == getDestination()) {
        return;
      }

      if (getPosition().equals(getDestination())) {
        agentSays("I have reached the citizen.");
        // send a message to the citizen
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(citizenAID);
        msg.setContent("I am here to help you.");
        msg.setConversationId("help-citizen-" + System.currentTimeMillis());
        send(msg);
        addBehaviour(new PatrolNeighborhood(myAgent, 1000));
        return;
      }
    }
  }

  private class PatrolNeighborhood extends TickerBehaviour {

    private int patrolRange = 2;
    private Queue<Point> patrolPoints;
    private Point currentTarget;

    public PatrolNeighborhood(Agent a, long period) {
      super(a, period);
      patrolPoints = new LinkedList<>();
      initializePatrolPoints();
    }

    private void initializePatrolPoints() {
      int x = getPosition().x;
      int y = getPosition().y;
      int xStart = Math.max(x - patrolRange, 0);
      int xEnd = Math.min(x + patrolRange, getCityMap().size - 1);
      int yStart = Math.max(y - patrolRange, 0);
      int yEnd = Math.min(y + patrolRange, getCityMap().size - 1);

      for (int i = xStart; i <= xEnd; i++) {
        for (int j = yStart; j <= yEnd; j++) {
          if (!getCityMap().getCell(i, j).contains("Road")) {
            patrolPoints.add(new Point(i, j));
          }
        }
      }
    }

    protected void onTick() {
      if (currentTarget == null || getPosition().equals(currentTarget)) {
        if (patrolPoints.isEmpty()) {
          initializePatrolPoints(); // Reset patrol points if all points have been visited
        }
        currentTarget = patrolPoints.poll();
        if (currentTarget != null) {
          transitionToMoveToBehaviour(
            currentTarget,
            this::onDestinationReached
          );
        }
      }
    }

    private void onDestinationReached() {
      agentSays("Patrolling to " + currentTarget.x + "," + currentTarget.y);
      findCitizensOrThiefs();
    }

    private void transitionToMoveToBehaviour(
      Point target,
      Runnable onComplete
    ) {
      if (!getMoveToBehaviourActive()) { // Ensure only one MoveToBehaviour runs at a time
        setPath(calculatePath(getPosition(), target));
        if (getPath() != null && !getPath().isEmpty()) {
          setMoveToBehaviourActive(true);
          addBehaviour(
            new MoveToBehaviour(
              myAgent,
              (int) (Math.random() * 700 + 1200),
              target,
              onComplete,
              getMovementBehaviour()
            )
          );
        } else {
          agentSays(
            "Failed to find a path from " + getPosition() + " to " + target
          );
        }
      }
    }

    private void findCitizensOrThiefs() {
      DFAgentDescription citizenTemplate = new DFAgentDescription();
      ServiceDescription citizenSD = new ServiceDescription();
      citizenSD.setType("citizen");
      citizenTemplate.addServices(citizenSD);

      try {
        DFAgentDescription[] citizens = DFService.search(
          myAgent,
          citizenTemplate
        );

        if (citizens.length > 0) {
          AID closestCitizen = findClosestCitizen(citizens);
          if (closestCitizen != null) agentSays(
            "Found Citizen: " + closestCitizen.getLocalName()
          );

          if (closestCitizen != null) {
            agentSays("Sending message to " + closestCitizen.getLocalName());
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(closestCitizen);
            msg.setContent("message: I am here to help you.");
            msg.setConversationId("help-citizen-" + System.currentTimeMillis());
            send(msg);
            // Wait for citizen to respond
            addBehaviour(
              new CyclicBehaviour() {
                public void action() {
                  MessageTemplate mtPrefix = new MessageTemplate(
                    new MessageTemplate.MatchExpression() {
                      @Override
                      public boolean match(ACLMessage message) {
                        String conversationId = message.getConversationId();
                        return (
                          conversationId != null &&
                          conversationId.startsWith("citizen-report-")
                        );
                      }
                    }
                  );

                  // Combine the custom template with the MatchPerformative template
                  MessageTemplate mt = MessageTemplate.and(
                    mtPrefix,
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                  );
                  ACLMessage msg = receive(mt);
                  if (msg != null) {
                    agentSays("Citizen reported a thief.");
                    getInfoFromCitizen(msg.getContent());
                    removeBehaviour(this);
                  } else {
                    block();
                  }
                }
              }
            );
          }
        }
      } catch (FIPAException fe) {
        fe.printStackTrace();
      }
    }

    private void getInfoFromCitizen(String content) {
      if (content.contains("saw a thief")) {
        String[] parts = content.split(" ");
        int x = Integer.parseInt(parts[5]);
        int y = Integer.parseInt(parts[7]);
        Point thiefPosition = new Point(x, y);
        agentSays("Citizen saw a thief at " + thiefPosition);
        transitionToMoveToBehaviour(
          thiefPosition,
          () -> {
            agentSays("I have reached the thief.");
            addBehaviour(new PatrolNeighborhood(myAgent, 1000));

            // if spotted thief, kill thief
            DFAgentDescription thiefTemplate = new DFAgentDescription();
            ServiceDescription thiefSD = new ServiceDescription();
            thiefSD.setType("thiefagent");
            thiefTemplate.addServices(thiefSD);

            try {
              DFAgentDescription[] thiefs = DFService.search(
                myAgent,
                thiefTemplate
              );
              AID closestThief = findClosestThief(thiefs);
              if (closestThief != null) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(closestThief);
                msg.setContent("I am here to catch you.");
                msg.setConversationId(
                  "catch-thief-" + System.currentTimeMillis()
                );
                send(msg);
                removeBehaviour(getMovementBehaviour());
                setMovementBehaviour(
                  new MovementBehaviour(
                    myAgent,
                    (int) (Math.random() * 700 + 1200)
                  )
                );
                addBehaviour(getMovementBehaviour());
                setDestination(
                  (
                    (ThiefAgent) AgentRegistry.getAgent(
                      closestThief.getLocalName()
                    )
                  ).getPosition()
                );
                // catch the thief
                addBehaviour(
                  new TickerBehaviour(myAgent, 800) {
                    protected void onTick() {
                      ThiefAgent thief = (ThiefAgent) AgentRegistry.getAgent(
                        closestThief.getLocalName()
                      );
                      setDestination(thief.getPosition());
                      if (
                        getPosition().equals(getDestination()) &&
                        !thief.getIsHome()
                      ) {
                        agentSays("I have caught the thief.");
                        removeBehaviour(this);
                        addBehaviour(new PatrolNeighborhood(myAgent, 1000));
                        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                        msg.addReceiver(closestThief);
                        msg.setContent("Got you.");
                        msg.setConversationId(
                          "catch-thief-" + System.currentTimeMillis()
                        );
                        send(msg);
                      } else {
                        agentSays("The thief has escaped.");
                        removeBehaviour(this);
                        removeBehaviour(getMovementBehaviour());
                        setMovementBehaviour(
                          new MovementBehaviour(
                            myAgent,
                            (int) (Math.random() * 700 + 1200)
                          )
                        );
                        addBehaviour(getMovementBehaviour());
                      }
                    }
                  }
                );
              }
            } catch (FIPAException fe) {
              fe.printStackTrace();
            }
          }
        );
      } else {
        agentSays("Citizen was robbed but didn't see the thief.");
        addBehaviour(new PatrolNeighborhood(myAgent, 1000));
      }
    }

    private AID findClosestCitizen(DFAgentDescription[] results) {
      double minDistance = 7;
      AID dude = null;
      for (DFAgentDescription dfd : results) {
        AID citizen = dfd.getName();
        double distance = getPosition()
          .distance(((Citizen) AgentRegistry.getAgent(citizen)).getPosition());
        if (distance < minDistance) {
          minDistance = distance;
          dude = citizen;
        }
      }
      return dude;
    }
  }

  private AID findClosestThief(DFAgentDescription[] results) {
    double minDistance = 7;
    AID dude = null;
    for (DFAgentDescription dfd : results) {
      AID thief = dfd.getName();
      agentSays("Thief: " + thief.getLocalName());
      if ((ThiefAgent) AgentRegistry.getAgent(thief) != null) {
        double distance = getPosition()
          .distance(((ThiefAgent) AgentRegistry.getAgent(thief)).getPosition());
        if (distance < minDistance) {
          minDistance = distance;
          dude = thief;
        }
      }
    }
    return dude;
  }

  protected void setBusy(boolean busy) {
    isBusy = busy;
  }

  protected void setPoliceStationNearby(AID policeStation) {
    policeStationNearby = policeStation;
  }

  protected AID getPoliceStationNearby() {
    return policeStationNearby;
  }
}

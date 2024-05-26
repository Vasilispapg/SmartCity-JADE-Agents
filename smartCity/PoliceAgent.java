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

public class PoliceAgent extends CitizenAgent {

  private DFAgentDescription dfd = new DFAgentDescription();
  private ServiceDescription sd = new ServiceDescription();
  private boolean isBusy = false;
  private AID policeStationNearby;

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
    addBehaviour(new HandleTasks());
    getLatch().countDown();
  }

  private void registerService() {
    sd.setType("police");
    sd.setName(getLocalName());
    dfd.setName(getAID());
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    } catch (FIPAException e) {
      e.printStackTrace();
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
        policeStationNearby = results[0].getName();
        agentSays("Found PoliceStation: " + policeStationNearby.getLocalName());
      } else {
        agentSays("No available police stations right now.");
        policeStationNearby = null;
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
      if (policeStationNearby != null) {
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(policeStationNearby);
        request.setContent("Requesting task");
        myAgent.send(request);
        agentSays("Requested tasks from " + policeStationNearby.getLocalName());
      }
    }
  }

  private class HandleTasks extends CyclicBehaviour {

    public void action() {
      ACLMessage msg = myAgent.receive(
        MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)
      );
      if (msg != null) {
        System.out.println(
          getLocalName() + " received a task proposal: " + msg.getContent()
        );
        ACLMessage response = msg.createReply();

        if (!isBusy) {
          response.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
          myAgent.send(response);
          agentSays("i accept the task");
          // Set the agent's state to busy
          isBusy = true;
        } else {
          response.setPerformative(ACLMessage.REJECT_PROPOSAL);
          myAgent.send(response);
          agentSays("I am busy right now.");
        }
      } else {
        block();
      }
    }
  }
}

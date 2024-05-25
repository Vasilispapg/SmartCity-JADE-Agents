package examples.smartCity;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

public class Citizen extends CitizenAgent {

  protected void setup() {
    super.setup();
    agentSays("I am a Citizen Agent. I am ready to serve.");
    if (ownsCar()) {
      setVehicle(new Vehicle("Bicycle"));
    }

    registerAsCitizen();

    getLatch().countDown();
  }

  protected void takeDown() {
    agentSays("I am a Citizen Agent. I am done serving.");
    super.takeDown();
  }

  private void registerAsCitizen() {
    try {
      DFAgentDescription dfd = new DFAgentDescription();
      dfd.setName(getAID());
      ServiceDescription sd = new ServiceDescription();
      sd.setType("citizen");
      sd.setName(getLocalName());
      dfd.addServices(sd);
      DFService.register(this, dfd);
    } catch (FIPAException fe) {
      fe.printStackTrace();
    }
  }
}

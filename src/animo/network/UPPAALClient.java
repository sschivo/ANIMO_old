package animo.network;


import java.rmi.Naming;

import animo.analyser.LevelResult;
import animo.analyser.SMCResult;
import animo.model.Model;

/**
 * The class used to access the remote server.
 */
public class UPPAALClient {
	private iUPPAALServer server = null;
	
	public UPPAALClient(String serverHost, Integer serverPort) throws Exception {
		System.setSecurityManager(new java.rmi.RMISecurityManager());
		server = (iUPPAALServer) Naming.lookup("rmi://" + serverHost + ":" + serverPort + "/UPPAALServer");
	}
	
	public LevelResult analyze(Model m, int timeTo, int nSimulationRuns, boolean computeStdDev) throws Exception {
		return server.analyze(m, timeTo, nSimulationRuns, computeStdDev);
	}
	
	public SMCResult analyzeSMC(Model m, String smcQuery) throws Exception {
		return server.analyze(m, smcQuery);
	}
}

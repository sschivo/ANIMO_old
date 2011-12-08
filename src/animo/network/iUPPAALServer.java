package animo.network;

import java.rmi.Remote;

import animo.analyser.LevelResult;
import animo.analyser.SMCResult;
import animo.model.Model;

/**
 * Remotely accessible features: simulation run or SMC analysis
 */
public interface iUPPAALServer extends Remote {
	
	public LevelResult analyze(Model m, int timeTo, int nSimulationRuns, boolean computeStdDev) throws Exception;
	
	public SMCResult analyze(Model m, String smcQuery) throws Exception;
}

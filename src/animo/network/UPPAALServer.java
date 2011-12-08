package animo.network;


import java.io.File;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import animo.ANIMOBackend;
import animo.analyser.LevelResult;
import animo.analyser.SMCResult;
import animo.analyser.uppaal.ResultAverager;
import animo.analyser.uppaal.UppaalModelAnalyserFasterConcrete;
import animo.model.Model;

/**
 * The remote server. Implements the methods for simulation run analysis and
 * SMC analysis. Listens for remote connections on the given port.
 */
public class UPPAALServer extends UnicastRemoteObject implements iUPPAALServer {
	private static final long serialVersionUID = 5030971508567718530L;
	private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
	private static final int DEFAULT_PORT = 1234;

	protected UPPAALServer(int port) throws RemoteException {
		super();
		try {
			LocateRegistry.createRegistry(port);
			Naming.bind("rmi://localhost:" + port + "/UPPAALServer", this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public LevelResult analyze(Model m, int timeTo, int nSimulationRuns, boolean computeStdDev) throws Exception {
		System.out.println(df.format(new Date(System.currentTimeMillis())) + " Analysing \"normal\" model with simulation up to " + timeTo);
		LevelResult result;
		if (nSimulationRuns > 1) {
			result = new ResultAverager(null, null).analyzeAverage(m, timeTo, nSimulationRuns, computeStdDev);
		} else {
			result = new UppaalModelAnalyserFasterConcrete(null, null).analyze(m, timeTo);
		}
		System.out.println(df.format(new Date(System.currentTimeMillis())) + " Done.");
		System.out.println();
		return result;
	}

	@Override
	public SMCResult analyze(Model m, String smcQuery) throws Exception {
		System.out.println(df.format(new Date(System.currentTimeMillis())) + " Analysing \"SMC\" model with query " + smcQuery);
		SMCResult result = new UppaalModelAnalyserFasterConcrete(null, null).analyzeSMC(m, smcQuery);
		System.out.println(df.format(new Date(System.currentTimeMillis())) + " Done.");
		System.out.println();
		return result;
	}
	
	public static void main(String[] args) {
		try {
			int port = DEFAULT_PORT;
			if (args.length < 1) {
				System.err.println("No port given: default to " + port);
			} else {
				try {
					port = Integer.parseInt(args[0]);
				} catch (NumberFormatException ex) {
					throw new Exception("Invalid port number", ex);
				}
			}
			ANIMOBackend.initialise(new File("ANIMO-configuration.xml"));

			@SuppressWarnings("unused")
			UPPAALServer server = new UPPAALServer(port);
			System.out.println(df.format(new Date(System.currentTimeMillis())) + " Server started and listening on port " + port + ".");
		} catch (Exception ex) {
			System.err.println("Problems in starting server!");
			ex.printStackTrace();
		}
	}

}

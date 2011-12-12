package animo.cytoscape;

import giny.model.Edge;
import giny.model.Node;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import animo.analyser.LevelResult;
import animo.analyser.SMCResult;
import animo.analyser.uppaal.ResultAverager;
import animo.analyser.uppaal.UppaalModelAnalyserFasterConcrete;
import animo.analyser.uppaal.VariablesModel;
import animo.exceptions.ANIMOException;
import animo.model.Model;
import animo.model.Reactant;
import animo.model.ReactantParameter;
import animo.model.Reaction;
import animo.model.Scenario;
import animo.model.ScenarioMono;
import animo.model.UserFormula;
import animo.network.UPPAALClient;
import animo.util.Table;

import cern.jet.stat.quantile.Quantile1Test;

import cytoscape.CyNetwork;
import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;
import cytoscape.task.Task;
import cytoscape.task.TaskMonitor;
import cytoscape.task.ui.JTask;
import cytoscape.task.ui.JTaskConfig;
import cytoscape.task.util.TaskManager;
import cytoscape.util.CytoscapeAction;
import cytoscape.view.CyNetworkView;
import cytoscape.view.cytopanels.CytoPanel;
import cytoscape.view.cytopanels.CytoPanelImp;
import cytoscape.view.cytopanels.CytoPanelState;

/**
 * The run action runs the network through the ANIMO analyser.
 * 
 * @author Brend Wanders
 * 
 */
public class RunAction extends CytoscapeAction {
	private static final long serialVersionUID = -5018057013811632477L;
	private static final String NUMBER_OF_LEVELS = Model.Properties.NUMBER_OF_LEVELS, //The total number of levels for a node (=reactant), or for the whole network (the name of the property is the same)
								SECONDS_PER_POINT = Model.Properties.SECONDS_PER_POINT, //The number of real-life seconds represented by a single UPPAAL time unit
								SECS_POINT_SCALE_FACTOR = Model.Properties.SECS_POINT_SCALE_FACTOR, //The scale factor for the UPPAAL time settings, allowing to keep the same scenario parameters, while varying the "density" of simulation sample points
								LEVELS_SCALE_FACTOR = Model.Properties.LEVELS_SCALE_FACTOR, //The scale factor used by each reaction to counterbalance the change in number of levels for the reactants.
								INCREMENT = Model.Properties.INCREMENT, //The increment in activity caused by a reaction on its downstream reactant
								INFLUENCING_REACTANTS = Model.Properties.INFLUENCING_REACTANTS, //The reactants determining the speed of a reaction
								INFLUENCED_REACTANTS = Model.Properties.INFLUENCED_REACTANTS, //The reactants influenced by a reaction
								INFLUENCE_VALUES = Model.Properties.INFLUENCE_VALUES, //By how much the influenced reactants are changed by a reaction (usually, +1 or -1)
								BI_REACTION = Model.Properties.BI_REACTION, //Identifies a reaction having two reatants
								MONO_REACTION = Model.Properties.MONO_REACTION, //Identifies a reaction having only one reactant
								USER_DEFINED_FORMULA = Model.Properties.USER_DEFINED_FORMULAE, //Used to tell us whether the scenario for a reaction comes from a user-defined formula or is one of the default ones
								REACTANT = Model.Properties.REACTANT, //The name of the reactant taking part to the reaction
								CATALYST = Model.Properties.CATALYST, //The name of the catalyst enabling the reaction
								SCENARIO = Model.Properties.SCENARIO, //The id of the scenario used to set the parameters for an edge (=reaction)
								CYTOSCAPE_ID = Model.Properties.CYTOSCAPE_ID, //The id assigned to the node/edge by Cytoscape
								CANONICAL_NAME = Model.Properties.CANONICAL_NAME, //The name of a reactant displayed to the user
								INITIAL_QUANTITY = Model.Properties.INITIAL_QUANTITY, //The initial quantity (concentration) of a node (=reactant)
								INITIAL_LEVEL = Model.Properties.INITIAL_LEVEL, //The starting activity level of a reactant
								UNCERTAINTY = Model.Properties.UNCERTAINTY, //The uncertainty about the parameters setting for an edge(=reaction)
								ENABLED = Model.Properties.ENABLED, //Whether the node/edge is enabled. Influences the display of that node/edge thanks to the discrete Visual Mapping defined by AugmentAction
								PLOTTED = Model.Properties.PLOTTED, //Whether the node is plotted in the graph. Default: yes
								GROUP = Model.Properties.GROUP; //Could possibly be never used. All nodes(=reactants) belonging to the same group represent alternative (in the sense of exclusive or) phosphorylation sites of the same protein.
	private static final int VERY_LARGE_TIME_VALUE = 1073741822;
	private int timeTo = 1200; //The default number of UPPAAL time units until which a simulation will run
	private double scale = 0.2; //The time scale representing the number of real-life minutes represented by a single UPPAAL time unit
	private JRadioButton remoteUppaal, smcUppaal; //The RadioButtons telling us whether we use a local or a remote engine, and whether we use the Statistical Model Checking or the "normal" engine
	private JCheckBox computeStdDev; //Whether to compute the standard deviation when computing the average of a series of runs (if average of N runs is requested)
	private JFormattedTextField timeToFormula, nSimulationRuns; //Up to which point in time (real-life minutes) the simulation(s) will run, and the number of simulations (if average of N runs is requested)
	private JTextField serverName, serverPort, smcFormula; //The name of the server, and the corresponding port, in the case we use a remote engine. The text inserted by the user for the SMC formula. Notice that this formula will need to be changed so that it will be compliant with the UPPAAL time scale, and reactant names
	private boolean needToStop; //Whether the user has pressed the Cancel button on the TaskMonitor while we were running an analysis process
	private RunAction meStesso; //Myself
	
	/**
	 * Constructor.
	 * 
	 * @param plugin the plugin we should use
	 */
	public RunAction(ANIMOPlugin plugin, JRadioButton remoteUppaal, JTextField serverName, JTextField serverPort, JRadioButton smcUppaal, JFormattedTextField timeToFormula, JFormattedTextField nSimulationRuns, JCheckBox computeStdDev, JTextField smcFormula) {
		super("Analyse network");
		this.remoteUppaal = remoteUppaal;
		this.serverName = serverName;
		this.serverPort = serverPort;
		this.smcUppaal = smcUppaal;
		this.timeToFormula = timeToFormula;
		this.nSimulationRuns = nSimulationRuns;
		this.computeStdDev = computeStdDev;
		this.smcFormula = smcFormula;
		this.meStesso = this;
	}
	
	public static String timeDifferenceFormat(long startTime, long endTime) {
		long diffInSeconds = (endTime - startTime) / 1000;
	    long diff[] = new long[] { 0, 0, 0, 0 };
	    /* sec */diff[3] = (diffInSeconds >= 60 ? diffInSeconds % 60 : diffInSeconds);
	    /* min */diff[2] = (diffInSeconds = (diffInSeconds / 60)) >= 60 ? diffInSeconds % 60 : diffInSeconds;
	    /* hours */diff[1] = (diffInSeconds = (diffInSeconds / 60)) >= 24 ? diffInSeconds % 24 : diffInSeconds;
	    /* days */diff[0] = (diffInSeconds = (diffInSeconds / 24));
	    
	    return String.format(
		        "%d day%s, %d hour%s, %d minute%s, %d second%s",
		        diff[0],
		        diff[0] != 1 ? "s" : "",
		        diff[1],
		        diff[1] != 1 ? "s" : "",
		        diff[2],
		        diff[2] != 1 ? "s" : "",
		        diff[3],
		        diff[3] != 1 ? "s" : "");
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		RunTask task = new RunTask();

		// Configure JTask Dialog Pop-Up Box
		JTaskConfig jTaskConfig = new JTaskConfig();
		jTaskConfig.setOwner(Cytoscape.getDesktop());
		// jTaskConfig.displayCloseButton(true);
		// jTaskConfig.displayCancelButton(true);

		jTaskConfig.displayStatus(true);
		jTaskConfig.setAutoDispose(true);
		jTaskConfig.displayCancelButton(true);
		jTaskConfig.displayTimeElapsed(true);
		jTaskConfig.setModal(true);
		
		long startTime = System.currentTimeMillis();
		Date now = new Date(startTime);
		File logFile = null;
		PrintStream logStream = null;
		PrintStream oldErr = System.err;
		try {
			if (UppaalModelAnalyserFasterConcrete.areWeUnderWindows()) {
				logFile = File.createTempFile("run", ".log"); //windows doesn't like long file names..
			} else {
				logFile = File.createTempFile("Cytoscape run " + now.toString(), ".log");
			}
			logFile.deleteOnExit();
			logStream = new PrintStream(new FileOutputStream(logFile));
			System.setErr(logStream);
		} catch (Exception ex) {
			//We have no log file, bad luck: we will have to use System.err.
		}
		
		// Execute Task in New Thread; pops open JTask Dialog Box.
		TaskManager.executeTask(task, jTaskConfig);
		
		long endTime = System.currentTimeMillis();
		
		try {
			System.err.println("Time taken: " + timeDifferenceFormat(startTime, endTime));
			System.err.flush();
			System.setErr(oldErr);
			if (logStream != null) {
				logStream.close();
			}
		} catch (Exception ex) {
			
		}
	}
	
	public boolean needToStop() {
		return this.needToStop;
	}

	private class RunTask implements Task {

		private static final String TIMES_U = Model.Properties.TIMES_UPPER;
		private static final String TIMES_L = Model.Properties.TIMES_LOWER;
		private static final String DIMENSIONS = Model.Properties.DIMENSIONS;
		private static final String REACTION_TYPE = Model.Properties.REACTION_TYPE;
		private static final String REACTANT_NAME = Model.Properties.REACTANT_NAME;
		private static final String REACTANT_ALIAS = Model.Properties.ALIAS;
		private TaskMonitor monitor;

		@Override
		public String getTitle() {
			return "ANIMO analysis";
		}

		@Override
		public void halt() {
			needToStop = true;
		}

		@Override
		public void run() {
			try {
				needToStop = false;
				
				this.monitor.setStatus("Creating model representation");
				this.monitor.setPercentCompleted(0);
				
				final Model model = this.getANIMOModel();
				
				if (smcUppaal.isSelected()) {
					performSMCAnalysis(model);
				} else {
					performNormalAnalysis(model);
				}
				
			} catch (InterruptedException e) {
				this.monitor.setException(e, "Analysis cancelled by the user.");
			} catch (Exception e) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				JOptionPane.showMessageDialog(Cytoscape.getDesktop(), "Unexpected error: please report it to developers.\n" + sw.toString());
				this.monitor.setException(e, "An error occurred while analysing the network.");
			}
		}
		
		/**
		 * Translate the SMC formula into UPPAAL time units, reactant names
		 * and give it to the analyser. Show the result in a message window.
		 * @param model
		 * @throws Exception
		 */
		private void performSMCAnalysis(final Model model) throws Exception {
			//TODO: "understand" the formula and correctly change time values and reagent names
			String probabilisticFormula = smcFormula.getText();
			for (Reactant r : model.getReactants()) {
				String name = r.get(REACTANT_ALIAS).as(String.class);
				if (probabilisticFormula.contains(name)) {
					probabilisticFormula = probabilisticFormula.replace(name, r.getId());
				}
			}
			if (probabilisticFormula.contains("Pr[<")) {
	            String[] parts = probabilisticFormula.split("Pr\\[<");
	            StringBuilder sb = new StringBuilder();
	            for (String p : parts) {
	               if (p.length() < 1) continue;
	               String timeS;
	               if (p.startsWith("=")) {
	                  timeS = p.substring(1, p.indexOf("]"));
	               } else {
	                  timeS = p.substring(0, p.indexOf("]"));
	               }
	               int time;
	               try {
	                  time = Integer.parseInt(timeS);
	               } catch (Exception ex) {
	                  throw new Exception("Problems with the identification of time string \"" + timeS + "\"");
	               }
	               time = (int)(time * 60.0 / model.getProperties().get(SECONDS_PER_POINT).as(Double.class));
	               sb.append("Pr[<");
	               if (p.startsWith("=")) {
	                  sb.append("=");
	               }
	               sb.append(time);
	               sb.append(p.substring(p.indexOf("]")));
	            }
	            probabilisticFormula = sb.toString();
	         }

			
			this.monitor.setStatus("Analysing model with UPPAAL");
			this.monitor.setPercentCompleted(-1);

			// analyse model
			final SMCResult result;
			
			if (remoteUppaal.isSelected()) {
				UPPAALClient client = new UPPAALClient(serverName.getText(), Integer.parseInt(serverPort.getText()));
				result = client.analyzeSMC(model, probabilisticFormula);
			} else {
				result = new UppaalModelAnalyserFasterConcrete(monitor, meStesso).analyzeSMC(model, probabilisticFormula);
			}
			
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					JOptionPane.showMessageDialog(Cytoscape.getDesktop(), result.toString(), "Result", JOptionPane.INFORMATION_MESSAGE);
				}
			});
			
		}
		
		/**
		 * Perform a simulation analysis. Translate the user-set number of real-life minutes
		 * for the length of the simulation, and obtain all input data for the model engine,
		 * based on the control the user has set (average, N simulation, StdDev, etc).
		 * When the analysis is done, display the obtained SimpleLevelResult on a ResultPanel
		 * @param model
		 * @throws Exception
		 */
		private void performNormalAnalysis(final Model model) throws Exception {

			int nMinutesToSimulate = 0;
			try {
				nMinutesToSimulate = Integer.parseInt(timeToFormula.getValue().toString());
			} catch (Exception ex) {
				throw new Exception("Unable to understand the number of minutes requested for the simulation.");
			}
				/*(int)(timeTo * model.getProperties().get(SECONDS_PER_POINT).as(Double.class) / 60);
			String inputTime = JOptionPane.showInputDialog(Cytoscape.getDesktop(), "Up to which time (in real-life MINUTES)?", nMinutesToSimulate);
			if (inputTime != null) {
				try {
					nMinutesToSimulate = Integer.parseInt(inputTime);
				} catch (Exception ex) {
					//the default value is still there, so nothing to change
				}
			} else {
				return;
			}*/
			
			timeTo = (int)(nMinutesToSimulate * 60.0 / model.getProperties().get(SECONDS_PER_POINT).as(Double.class));
			scale = (double)nMinutesToSimulate / timeTo;
			//System.err.println("Scale = " + scale);
			
			//this.monitor.setStatus("Analysing model with UPPAAL");
			this.monitor.setPercentCompleted(-1);

			// composite the analyser (this should be done from
			// configuration)
			//ModelAnalyser<LevelResult> analyzer = new UppaalModelAnalyser(new VariablesInterpreter(), new VariablesModel());

			// analyse model
			final LevelResult result;
			
			if (remoteUppaal.isSelected()) {
				UPPAALClient client = new UPPAALClient(serverName.getText(), Integer.parseInt(serverPort.getText()));
				int nSims = 1;
				if (nSimulationRuns.isEnabled()) {
					try {
						nSims = Integer.parseInt(nSimulationRuns.getText());
					} catch (Exception e) {
						throw new Exception("Unable to understand the number of requested simulations.");
					}
				} else {
					nSims = 1;
				}
				monitor.setStatus("Forwarding the request to the server " + serverName.getText() + ":" + serverPort.getText());
				result = client.analyze(model, timeTo, nSims, computeStdDev.isSelected());
			} else {
				//ModelAnalyser<LevelResult> analyzer = new UppaalModelAnalyser(new VariablesInterpreter(), new VariablesModel());
				//result = analyzer.analyze(model, timeTo);
				if (nSimulationRuns.isEnabled()) {
					int nSims = 0;
					try {
						nSims = Integer.parseInt(nSimulationRuns.getText());
					} catch (Exception e) {
						throw new Exception("Unable to understand the number of requested simulations.");
					}
					result = new ResultAverager(monitor, meStesso).analyzeAverage(model, timeTo, nSims, computeStdDev.isSelected());
				} else {
					result = new UppaalModelAnalyserFasterConcrete(monitor, meStesso).analyze(model, timeTo);
				}
			}
			
			/*CsvWriter csvWriter = new CsvWriter();
			csvWriter.writeCsv("/tmp/test.csv", model, result);*/
			
			if (result.getReactantIds().isEmpty()) {
				throw new Exception("No reactants selected for plot, or no reactants present in the result");
			} else {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						final CytoPanel p = Cytoscape.getDesktop().getCytoPanel(SwingConstants.EAST);
	
						// JFrame frame = new JFrame("ANIMO result viewer");
						// frame.setLayout(new BorderLayout());
						ANIMOResultPanel resultViewer = new ANIMOResultPanel(model, result, scale);
						// frame.add(resultViewer, BorderLayout.CENTER);
						// frame.setLocationRelativeTo(Cytoscape.getDesktop());
						// frame.pack();
						// frame.setSize(new Dimension(800, 600));
						// frame.setVisible(true);
	
						final JPanel container = new JPanel(new BorderLayout(2, 2));
						container.add(resultViewer, BorderLayout.CENTER);
						JPanel buttons = new JPanel(new GridLayout(1, 4, 2, 2));
	
						JButton close = new JButton(new AbstractAction("Close") {
							private static final long serialVersionUID = 4327349309742276633L;
	
							@Override
							public void actionPerformed(ActionEvent e) {
								p.remove(container);
							}
						});
	
						buttons.add(close);
						container.add(buttons, BorderLayout.NORTH);
	
						p.add("ANIMO Results", container);
	
						if (p.getState().equals(CytoPanelState.HIDE)) {
							CytoPanelImp p1 = (CytoPanelImp)Cytoscape.getDesktop().getCytoPanel(SwingConstants.WEST);
							CyNetworkView p2 = Cytoscape.getCurrentNetworkView();
							CytoPanelImp p3 = (CytoPanelImp)Cytoscape.getDesktop().getCytoPanel(SwingConstants.SOUTH);
							Dimension d = Cytoscape.getDesktop().getSize();
							if (!p1.getState().equals(CytoPanelState.HIDE)) {
								d.width -= p1.getWidth();
							}
							if (p2 != null) {
								d.width -= Cytoscape.getDesktop().getNetworkViewManager().getInternalFrame(p2).getWidth();
							}
							if (!p3.getState().equals(CytoPanelState.HIDE)) {
								d.height -= p3.getHeight();
							}
							((CytoPanelImp)p).setPreferredSize(d);
							((CytoPanelImp)p).setMaximumSize(d);
							((CytoPanelImp)p).setSize(d);
							p.setState(CytoPanelState.DOCK);
						}
						
						p.setSelectedIndex(p.getCytoPanelComponentCount() - 1);
					}
				});
			}
		}

		@Override
		public void setTaskMonitor(TaskMonitor monitor) throws IllegalThreadStateException {
			this.monitor = monitor;
		}

		/**
		 * Translate the Cytoscape network in the internal ANIMO model representation.
		 * This intermediate model will then translated as needed into the proper UPPAAL
		 * model by the analysers. All properties needed from the Cytoscape network are
		 * copied in the resulting model, checking that all are set ok.
		 * @return The intermediate ANIMO model
		 * @throws ANIMOException
		 */
		@SuppressWarnings("unchecked")
		private Model getANIMOModel() throws ANIMOException {
			checkParameters();
			
			long startTime = System.currentTimeMillis();
			
			Map<String, String> nodeNameToId = new HashMap<String, String>();
			Map<String, String> edgeNameToId = new HashMap<String, String>();
			
			Model model = new Model();
			
			CyNetwork network = Cytoscape.getCurrentNetwork();
			
			final int totalWork = network.getNodeCount() + network.getEdgeCount();
			int doneWork = 0;
			
			CyAttributes networkAttributes = Cytoscape.getNetworkAttributes();
			
			model.getProperties().let(NUMBER_OF_LEVELS).be(networkAttributes.getAttribute(network.getIdentifier(), NUMBER_OF_LEVELS));
			model.getProperties().let(SECONDS_PER_POINT).be(networkAttributes.getAttribute(network.getIdentifier(), SECONDS_PER_POINT));
			double secStepFactor = networkAttributes.getDoubleAttribute(network.getIdentifier(), SECS_POINT_SCALE_FACTOR);
			model.getProperties().let(SECS_POINT_SCALE_FACTOR).be(secStepFactor);
			
			final Integer MaxNLevels = networkAttributes.getIntegerAttribute(network.getIdentifier(), NUMBER_OF_LEVELS);
			final Double nSecondsPerPoint = networkAttributes.getDoubleAttribute(network.getIdentifier(), SECONDS_PER_POINT);
			
			model.getProperties().let(NUMBER_OF_LEVELS).be(MaxNLevels);
			model.getProperties().let(SECONDS_PER_POINT).be(nSecondsPerPoint);
			
			CyAttributes nodeAttributes = Cytoscape.getNodeAttributes();
			CyAttributes edgeAttributes = Cytoscape.getEdgeAttributes();
			
			// do nodes first
			final Iterator<Node> nodes = (Iterator<Node>) network.nodesIterator();
			for (int i = 0; nodes.hasNext(); i++) {
				this.monitor.setPercentCompleted((100 * doneWork++) / totalWork);
				Node node = nodes.next();
				
				final String reactantId = "reactant" + i;
				Reactant r = new Reactant(reactantId);
				nodeNameToId.put(node.getIdentifier(), reactantId);
				
				r.let(CYTOSCAPE_ID).be(node.getIdentifier());
				r.let(REACTANT_NAME).be(node.getIdentifier());
				r.let(REACTANT_ALIAS).be(nodeAttributes.getAttribute(node.getIdentifier(), CANONICAL_NAME));
				r.let(NUMBER_OF_LEVELS).be(nodeAttributes.getIntegerAttribute(node.getIdentifier(), NUMBER_OF_LEVELS));
				r.let(GROUP).be(nodeAttributes.getAttribute(node.getIdentifier(), GROUP));
				r.let(ENABLED).be(nodeAttributes.getAttribute(node.getIdentifier(), ENABLED));
				r.let(PLOTTED).be(nodeAttributes.getAttribute(node.getIdentifier(), PLOTTED));
				r.let(INITIAL_QUANTITY).be(nodeAttributes.getIntegerAttribute(node.getIdentifier(), INITIAL_QUANTITY));
				r.let(INITIAL_LEVEL).be(nodeAttributes.getIntegerAttribute(node.getIdentifier(), INITIAL_LEVEL));
				
				//If the quantity of this reactant is not influenced by any reaction, the maximum growth factor for its quantity is 1, otherwise it is 10 (i.e., the quantity can grow up to 10 times its initial value).
				Iterator<Edge> edges = (Iterator<Edge>) network.edgesIterator();
				int factor = 0;
				for (int j=0;edges.hasNext();j++) {
					Edge edge = edges.next();
					if (!edgeAttributes.hasAttribute(edge.getIdentifier(), ENABLED) || edgeAttributes.getBooleanAttribute(edge.getIdentifier(), ENABLED)) { //of course, we are interested only in enabled edges
						List<String> influencedReactants = edgeAttributes.getListAttribute(edge.getIdentifier(), INFLUENCED_REACTANTS);
						for (String s : influencedReactants) {
							ReactantParameter par = new ReactantParameter(s);
							if (par.getReactantIdentifier().equals(node.getIdentifier()) && par.getPropertyName().equals(Model.Properties.QUANTITY)) {
								factor = 10;
								break;
							}
						}
					}
					if (factor != 0) {
						break;
					}
				}
				if (factor == 0) {
					factor = 1;
				}
				nodeAttributes.setAttribute(node.getIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH, factor);
				r.let(Model.Properties.MAXIMUM_QUANTITY_GROWTH).be(factor);
				
				model.add(r);
			}
			
			
			// do edges next
			final Iterator<Edge> edges = (Iterator<Edge>) network.edgesIterator();
			for (int i = 0; edges.hasNext(); i++) {
				this.monitor.setPercentCompleted((100 * doneWork++) / totalWork);
				Edge edge = edges.next();
				
				Double levelsScaleFactor = nodeAttributes.getDoubleAttribute(edge.getSource().getIdentifier(), LEVELS_SCALE_FACTOR) / nodeAttributes.getDoubleAttribute(edge.getTarget().getIdentifier(), LEVELS_SCALE_FACTOR);
										//edgeAttributes.getDoubleAttribute(edge.getIdentifier(), LEVELS_SCALE_FACTOR); //The scale factor due to the nodes' number of levels is now a property of the nodes themselves, not of the reactions (we take care of retrocompatibility by transferring and deleting attributes found in reactions to their nodes instead in the checkParameters method)
				
				String reactionId = "reaction" + i;
				Reaction r = new Reaction(reactionId);
				edgeNameToId.put(edge.getIdentifier(), reactionId);
				
				r.let(ENABLED).be(edgeAttributes.getAttribute(edge.getIdentifier(), ENABLED));
				r.let(INCREMENT).be(edgeAttributes.getAttribute(edge.getIdentifier(), INCREMENT));
				r.let(INFLUENCED_REACTANTS).be(edgeAttributes.getListAttribute(edge.getIdentifier(), INFLUENCED_REACTANTS));
				r.let(INFLUENCE_VALUES).be(edgeAttributes.getListAttribute(edge.getIdentifier(), INFLUENCE_VALUES));
				
				if (!r.get(ENABLED).as(Boolean.class)) continue;
				
				if (edge.getSource() == edge.getTarget()) {
					r.let(REACTION_TYPE).be(MONO_REACTION);
					r.let(USER_DEFINED_FORMULA).be(false);

					final String reactant = nodeNameToId.get(edge.getTarget().getIdentifier());
					r.let(REACTANT).be(reactant);
					
					int nLevels;
					
					if (!model.getReactant(reactant).get(NUMBER_OF_LEVELS).isNull()) {
						nLevels = model.getReactant(reactant).get(NUMBER_OF_LEVELS).as(Integer.class);
					} else {
						nLevels = model.getProperties().get(NUMBER_OF_LEVELS).as(Integer.class);
					}
					
					ScenarioMono scenario = new ScenarioMono();
					
					String[] parameters = scenario.listVariableParameters();
					for (int j = 0;j < parameters.length;j++) {
						Double parVal = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), parameters[j]);
						if (parVal != null) {
							scenario.setParameter(parameters[j], parVal);
						} else {
							//this should never happen, because the parameter should at least have its default value (see checkParameters)
						}
					}
					
					List<ReactantParameter> influencingReactants = new Vector<ReactantParameter>();
					influencingReactants.add(new ReactantParameter(edge.getTarget().getIdentifier(), Model.Properties.ACTIVITY_LEVEL));
					r.let(INFLUENCING_REACTANTS).be(influencingReactants);
					
					List<Integer> dimensions = new Vector<Integer>();
					dimensions.add(nLevels+1);
					r.let(DIMENSIONS).be(dimensions);
					
					double uncertainty = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), UNCERTAINTY);
					
					List<Double> times = scenario.generateTimes(1 + nLevels);
					Table timesLTable = new Table(nLevels + 1, 1);
					Table timesUTable = new Table(nLevels + 1, 1);
					
					for (int j = 0; j < nLevels + 1; j++) {
						Double t = times.get(j);
						if (Double.isInfinite(t)) {
							timesLTable.set(j, 0, VariablesModel.INFINITE_TIME);
							timesUTable.set(j, 0, VariablesModel.INFINITE_TIME);
						} else if (uncertainty == 0) {
							timesLTable.set(j, 0, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t)));
							timesUTable.set(j, 0, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t)));
						} else {
							//timesLTable.set(j, 0, Math.max(1, (int)Math.round(secStepFactor * t * (100.0 - uncertainty) / 100.0))); //we use Math.max because we do not want to put 0 as a time
							//timesUTable.set(j, 0, Math.max(1, (int)Math.round(secStepFactor * t * (100.0 + uncertainty) / 100.0)));
							timesLTable.set(j, 0, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t * (1 - uncertainty / 100.0)))); //we use Math.max because we do not want to put 0 as a time
							timesUTable.set(j, 0, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t * (1 + uncertainty / 100.0))));
						}
					}
					r.let(TIMES_L).be(timesLTable);
					r.let(TIMES_U).be(timesUTable);

				} else {
					r.let(REACTION_TYPE).be(BI_REACTION);
					
					final String reactant = nodeNameToId.get(edge.getTarget().getIdentifier());
					r.let(REACTANT).be(reactant);
					
					final String catalyst = nodeNameToId.get(edge.getSource().getIdentifier());
					r.let(CATALYST).be(catalyst);
					
					Integer scenarioIdx;
					/*if (edgeAttributes.hasAttribute(edge.getIdentifier(), SCENARIO)) {
						scenarioIdx = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), SCENARIO);
					} else {
						//we do this thing in checkParameters
						scenarioIdx = 0;
					}*/
					Scenario[] scenarios = Scenario.availableScenarios;
					scenarioIdx = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), SCENARIO);
					Scenario scenario = scenarios[scenarioIdx];
					
					double uncertainty = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), UNCERTAINTY);
					
					if (scenario instanceof UserFormula) {
						r.let(USER_DEFINED_FORMULA).be(true);
						
						List<Integer> dimensions = new Vector<Integer>();
						UserFormula userFormula = (UserFormula)scenario;
						String[] parameters = userFormula.listVariableParameters();
						for (int j = 0;j < parameters.length;j++) {
							Double parVal = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), parameters[j]);
							if (parVal != null) {
								userFormula.setParameter(parameters[j], parVal);
							} else {
								//checkParameters should make sure that each parameter is present, at least with its default value
							}
						}
						String[] linkedParameters = userFormula.listLinkedVariables();
						List<ReactantParameter> influencingReactants = new Vector<ReactantParameter>();
						for (int j=0;j<linkedParameters.length;j++) {
							String parVal = edgeAttributes.getStringAttribute(edge.getIdentifier(), linkedParameters[j]);
							if (parVal != null) {
								ReactantParameter par = new ReactantParameter(parVal);
								userFormula.setLinkedVariable(linkedParameters[j], par);
								influencingReactants.add(par);
							} else {
								//checkParameters saves us
							}
						}
						r.let(INFLUENCING_REACTANTS).be(influencingReactants);
						
						List<Double> timesList = (userFormula).generateTimes(dimensions);
						List<Integer> timesL = new Vector<Integer>(timesList.size()),
									  timesU = new Vector<Integer>(timesList.size());
						for (Double t : timesList) {
							if (Double.isInfinite(t)) {
								timesL.add(VariablesModel.INFINITE_TIME);
								timesU.add(VariablesModel.INFINITE_TIME);
							} else if (uncertainty == 0) {
								timesL.add(Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t)));
								timesU.add(Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t)));
							} else {
								timesL.add(Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t * (1 - uncertainty / 100.0))));
								timesU.add(Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t * (1 + uncertainty / 100.0))));
							}
						}
						r.let(TIMES_L).be(timesL);
						r.let(TIMES_U).be(timesU);
						r.let(DIMENSIONS).be(dimensions);
					} else {
						r.let(USER_DEFINED_FORMULA).be(false);
						
						List<ReactantParameter> influencingReactants = new Vector<ReactantParameter>();
						influencingReactants.add(new ReactantParameter(edge.getSource().getIdentifier(), Model.Properties.ACTIVITY_LEVEL));
						influencingReactants.add(new ReactantParameter(edge.getTarget().getIdentifier(), Model.Properties.ACTIVITY_LEVEL));
						r.let(INFLUENCING_REACTANTS).be(influencingReactants);
						
						int nLevelsR1,
							nLevelsR2;
						
						if (!model.getReactant(catalyst).get(NUMBER_OF_LEVELS).isNull()) {
							nLevelsR1 = model.getReactant(catalyst).get(NUMBER_OF_LEVELS).as(Integer.class);
						} else {
							nLevelsR1 = model.getProperties().get(NUMBER_OF_LEVELS).as(Integer.class);
						}
						if (!model.getReactant(reactant).get(NUMBER_OF_LEVELS).isNull()) {
							nLevelsR2 = model.getReactant(reactant).get(NUMBER_OF_LEVELS).as(Integer.class);
						} else {
							nLevelsR2 = model.getProperties().get(NUMBER_OF_LEVELS).as(Integer.class);
						}
						
						int maxQuantityGrowthR1 = 1,
							maxQuantityGrowthR2 = 1;
						if (nodeAttributes.hasAttribute(edge.getSource().getIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH)) {
							maxQuantityGrowthR1 = nodeAttributes.getIntegerAttribute(edge.getSource().getIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH);
						}
						if (nodeAttributes.hasAttribute(edge.getTarget().getIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH)) {
							maxQuantityGrowthR2 = nodeAttributes.getIntegerAttribute(edge.getTarget().getIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH);
						}
						nLevelsR1 = nLevelsR1 * maxQuantityGrowthR1 + 1;
						nLevelsR2 = nLevelsR2 * maxQuantityGrowthR2 + 1;
						
						List<Integer> dimensions = new Vector<Integer>();
						dimensions.add(nLevelsR1);
						dimensions.add(nLevelsR2);
						r.let(DIMENSIONS).be(dimensions);
						
						String[] parameters = scenario.listVariableParameters();
						for (int j = 0;j < parameters.length;j++) {
							Double parVal = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), parameters[j]);
							if (parVal != null) {
								scenario.setParameter(parameters[j], parVal);
							} else {
								//checkParameters should make sure that each parameter is present, at least with its default value
							}
						}
						
						
						boolean activatingReaction = true;
						if (edgeAttributes.getIntegerAttribute(edge.getIdentifier(), INCREMENT) > 0) {
							activatingReaction = true;
						} else {
							activatingReaction = false;
						}
						//Reactant catalicammello = model.getReactant(catalyst), rettile = model.getReactant(reactant);
						//System.out.println("Inizio a generare i tempi per " + catalicammello.get(REACTANT_ALIAS).as(String.class) + " (" + catalicammello.get(NUMBER_OF_LEVELS).as(Integer.class) + " livelli) --> " + rettile.get(REACTANT_ALIAS).as(String.class) + " (" + rettile.get(NUMBER_OF_LEVELS).as(Integer.class) + " livelli)");
						List<Double> times = scenario.generateTimes(nLevelsR1, nLevelsR2, activatingReaction);
						//System.out.println("Finito di generare i tempi per " + catalicammello.get(REACTANT_ALIAS).as(String.class) + " (" + catalicammello.get(NUMBER_OF_LEVELS).as(Integer.class) + " livelli) --> " + rettile.get(REACTANT_ALIAS).as(String.class) + " (" + rettile.get(NUMBER_OF_LEVELS).as(Integer.class) + " livelli): sono " + times.size() + " valori.");
						Table timesLTable = new Table(nLevelsR2, nLevelsR1);
						Table timesUTable = new Table(nLevelsR2, nLevelsR1);
						
						for (int j = 0; j < nLevelsR2; j++) {
							for (int k = 0; k < nLevelsR1; k++) {
								Double t = times.get(j * nLevelsR1 + k);
								if (Double.isInfinite(t)) {
									timesLTable.set(j, k, VariablesModel.INFINITE_TIME);
									timesUTable.set(j, k, VariablesModel.INFINITE_TIME);
								} else if (uncertainty == 0) {
									timesLTable.set(j, k, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t)));
									timesUTable.set(j, k, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t)));
								} else {
									timesLTable.set(j, k, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t * (1 - uncertainty / 100.0))));
									timesUTable.set(j, k, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t * (1 + uncertainty / 100.0))));
								}
							}
						}
						//System.out.println("\nRiempite anche le tabelle per " + catalicammello.get(REACTANT_ALIAS).as(String.class) + " (" + catalicammello.get(NUMBER_OF_LEVELS).as(Integer.class) + " livelli) --> " + rettile.get(REACTANT_ALIAS).as(String.class) + " (" + rettile.get(NUMBER_OF_LEVELS).as(Integer.class) + " livelli).");
						/*List<Double> times = scenario.generateTimes(1 + nLevelsR1, 1 + nLevelsR2, activatingReaction);
						Table timesLTable = new Table(nLevelsR2 + 1, nLevelsR1 + 1);
						Table timesUTable = new Table(nLevelsR2 + 1, nLevelsR1 + 1);
						
						for (int j = 0; j < nLevelsR2 + 1; j++) {
							for (int k = 0; k < nLevelsR1 + 1; k++) {
								Double t = times.get(j * (nLevelsR1 + 1) + k);
								if (Double.isInfinite(t)) {
									timesLTable.set(j, k, VariablesModel.INFINITE_TIME);
									timesUTable.set(j, k, VariablesModel.INFINITE_TIME);
								} else if (uncertainty == 0) {
									timesLTable.set(j, k, (int)Math.round(secStepFactor * levelsScaleFactor * t));
									timesUTable.set(j, k, (int)Math.round(secStepFactor * levelsScaleFactor * t));
								} else {
									timesLTable.set(j, k, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t * (1 - uncertainty / 100.0))));
									timesUTable.set(j, k, Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * t * (1 + uncertainty / 100.0))));
								}
							}
						}*/
						r.let(TIMES_L).be(timesLTable);
						r.let(TIMES_U).be(timesUTable);
					}
				}

				model.add(r);
			}
			
			/*This should not be necessary any more, as we do that in checkParameters()
			//check that the number of levels is present in each reactant
			Integer defNumberOfLevels = model.getProperties().get(NUMBER_OF_LEVELS).as(Integer.class);
			for (Reactant r : model.getReactants()) {
				Integer nLvl = r.get(NUMBER_OF_LEVELS).as(Integer.class);
				if (nLvl == null) {
					Property nameO = r.get(REACTANT_ALIAS);
					String name;
					if (nameO == null) {
						name = r.getId();
					} else {
						name = nameO.as(String.class);
					}
					String inputLevels = JOptionPane.showInputDialog("Missing number of levels for reactant \"" + name + "\" (" + r.getId() + ").\nPlease insert the max number of levels for \"" + name + "\"", defNumberOfLevels);
					if (inputLevels != null) {
						try {
							nLvl = new Integer(inputLevels);
						} catch (Exception ex) {
							nLvl = defNumberOfLevels;
						}
					} else {
						nLvl = defNumberOfLevels;
					}
					r.let(NUMBER_OF_LEVELS).be(nLvl);
					//System.err.println("Numbero di livelli di " + r.get("cytoscape id").as(String.class) + " = " + nLvl);
					nodeAttributes.setAttribute(r.get(CYTOSCAPE_ID).as(String.class), NUMBER_OF_LEVELS, nLvl);
				}
			}*/
			
			System.err.println("\tModel generation took " + timeDifferenceFormat(startTime, System.currentTimeMillis()));
			
			return model;
		}

		
		/**
		 * Check that all parameters are ok. If possible, ask the user to
		 * input parameters on the fly. If this is not possible, throw an
		 * exception specifying what parameters are missing.
		 */
		@SuppressWarnings("unchecked")
		private void checkParameters() throws ANIMOException {
			
			CyNetwork network = Cytoscape.getCurrentNetwork();
			CyAttributes networkAttributes = Cytoscape.getNetworkAttributes();
			CyAttributes nodeAttributes = Cytoscape.getNodeAttributes();
			CyAttributes edgeAttributes = Cytoscape.getEdgeAttributes();
			
			
			//============================== FIRST PART: CHECK THAT ALL PROPERTIES ARE SET =====================================
			//TODO: we could collect the list of all things that were set automatically and show them before continuing with the
			//generation of the model. Alternatively, we could throw exceptions like bullets for any slight misbehavior =)
			//Another alternative is to collect the list of what we want to change, and actually make the changes only after the
			//user has approved them. Otherwise, interrupt the analysis by throwing exception.
			
			if (!networkAttributes.hasAttribute(network.getIdentifier(), NUMBER_OF_LEVELS)) {
				//throw new ANIMOException("Network attribute '" + NUMBER_OF_LEVELS + "' is missing.");
				int defaultNLevels = 15;
				String inputLevels = JOptionPane.showInputDialog((JTask)this.monitor, "Missing number of levels for the network. Please insert the max number of levels", defaultNLevels);
				Integer nLvl;
				if (inputLevels != null) {
					try {
						nLvl = new Integer(inputLevels);
					} catch (Exception ex) {
						nLvl = defaultNLevels;
					}
				} else {
					nLvl = defaultNLevels;
				}
				networkAttributes.setAttribute(network.getIdentifier(), NUMBER_OF_LEVELS, nLvl);
			}
			
			if (!networkAttributes.hasAttribute(network.getIdentifier(), SECONDS_PER_POINT)) {
				//throw new ANIMOException("Network attribute '" + SECONDS_PER_POINT + "' is missing.");
				double defaultSecondsPerPoint = 12;
				String inputSecs = JOptionPane.showInputDialog((JTask)this.monitor, "Missing number of seconds per point for the network.\nPlease insert the number of real-life seconds a simulation point will represent", defaultSecondsPerPoint);
				Double nSecPerPoint;
				if (inputSecs != null) {
					try {
						nSecPerPoint = new Double(inputSecs);
					} catch (Exception ex) {
						nSecPerPoint = defaultSecondsPerPoint;
					}
				} else {
					nSecPerPoint = defaultSecondsPerPoint;
				}
				networkAttributes.setAttribute(network.getIdentifier(), SECONDS_PER_POINT, nSecPerPoint);
			}
			
			double secStepFactor;
			if (networkAttributes.hasAttribute(network.getIdentifier(), SECS_POINT_SCALE_FACTOR)) {
				secStepFactor = networkAttributes.getDoubleAttribute(network.getIdentifier(), SECS_POINT_SCALE_FACTOR);
			} else {
				secStepFactor = 1.0;
				networkAttributes.setAttribute(network.getIdentifier(), SECS_POINT_SCALE_FACTOR, secStepFactor);
			}
			
			
			Iterator<Edge> edges = (Iterator<Edge>) network.edgesIterator();
			for (int i = 0; edges.hasNext(); i++) {
				Edge edge = edges.next();
				if (!edgeAttributes.hasAttribute(edge.getIdentifier(), ENABLED)) {
					edgeAttributes.setAttribute(edge.getIdentifier(), ENABLED, true);
				}
				if (!edgeAttributes.getBooleanAttribute(edge.getIdentifier(), ENABLED)) continue;
				
				//Check that the edge has a selected scenario
				if (!edgeAttributes.hasAttribute(edge.getIdentifier(), SCENARIO)) {
					edgeAttributes.setAttribute(edge.getIdentifier(), SCENARIO, 0);
				}
				//Check that the edge has the definition of all parameters requested by the selected scenario
				//otherwise set the parameters to their default values
				Scenario scenario;
				if (edge.getSource().equals(edge.getTarget())) {
					scenario = new ScenarioMono();
				} else {
					scenario = Scenario.availableScenarios[edgeAttributes.getIntegerAttribute(edge.getIdentifier(), SCENARIO)];
				}
				String[] paramNames = scenario.listVariableParameters();
				for (String param : paramNames) {
					if (!edgeAttributes.hasAttribute(edge.getIdentifier(), param)) {
						edgeAttributes.setAttribute(edge.getIdentifier(), param, scenario.getDefaultParameterValue(param));
					}
				}
				
				if (!edgeAttributes.hasAttribute(edge.getIdentifier(), UNCERTAINTY)) {
					edgeAttributes.setAttribute(edge.getIdentifier(), UNCERTAINTY, 0);
				}
				
				if (!edgeAttributes.hasAttribute(edge.getIdentifier(), INCREMENT)) {
					edgeAttributes.setAttribute(edge.getIdentifier(), INCREMENT, 1);
				}
				
				if (!edgeAttributes.hasAttribute(edge.getIdentifier(), INFLUENCED_REACTANTS)
						|| !edgeAttributes.hasAttribute(edge.getIdentifier(), INFLUENCE_VALUES)) {
					Vector<String> downstreamOnly = new Vector<String>();
					downstreamOnly.add(new ReactantParameter(edge.getTarget().getIdentifier(), Model.Properties.ACTIVITY_LEVEL).toString());
					edgeAttributes.setListAttribute(edge.getIdentifier(), INFLUENCED_REACTANTS, downstreamOnly);
					Vector<Integer> downstreamInfluence = new Vector<Integer>();
					downstreamInfluence.add(edgeAttributes.getIntegerAttribute(edge.getIdentifier(), INCREMENT));
					edgeAttributes.setListAttribute(edge.getIdentifier(), INFLUENCE_VALUES, downstreamInfluence);
				}
				

				if (edgeAttributes.hasAttribute(edge.getIdentifier(), LEVELS_SCALE_FACTOR)) { //Some old models have this property set as a property of the reaction instead of a property of the reactants: we collect it all in the upstream reactant, leaving 1.0 as scale for the downstream, and (important!) remove the property from the reaction
					Double scale = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), LEVELS_SCALE_FACTOR);
					//nodeAttributes.setAttribute(edge.getSource().getIdentifier(), LEVELS_SCALE_FACTOR, scale / nodeAttributes.getDoubleAttribute(edge.getSource().getIdentifier(), LEVELS_SCALE_FACTOR));
					Double scaleUpstream = nodeAttributes.getIntegerAttribute(edge.getSource().getIdentifier(), NUMBER_OF_LEVELS) / 15.0,
						   scaleDownstream = nodeAttributes.getIntegerAttribute(edge.getTarget().getIdentifier(), NUMBER_OF_LEVELS) / 15.0;
					//String nomeReazione = nodeAttributes.getStringAttribute(edge.getSource().getIdentifier(), CANONICAL_NAME) + " (" + nodeAttributes.getIntegerAttribute(edge.getSource().getIdentifier(), NUMBER_OF_LEVELS) + ") " + ((edgeAttributes.getIntegerAttribute(edge.getIdentifier(), INCREMENT) > 0) ? " --> " : " --| ") + nodeAttributes.getStringAttribute(edge.getTarget().getIdentifier(), CANONICAL_NAME) + " (" + nodeAttributes.getIntegerAttribute(edge.getTarget().getIdentifier(), NUMBER_OF_LEVELS) + ")";
					if (Math.abs(scaleUpstream / scaleDownstream - scale) > 1e-6) { //If the components were scaled before the reaction was introduced, then we need to modify the parameters of the reaction in order to keep things working
						//JOptionPane.showMessageDialog(null, "Errore, la scala upstream è " + scaleUpstream + ", la scala downstream è " + scaleDownstream + ",\nil / viene " + (scaleUpstream / scaleDownstream) + ",\nil * viene " + (scaleUpstream * scaleDownstream) + ",\nma la scala attuale della reazione è " + scale, nomeReazione, JOptionPane.WARNING_MESSAGE);
						
						//Counterbalance the scale introduced by the two scales
						double factor = scale * scaleDownstream / scaleUpstream;
						Integer scenarioIdx = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), Model.Properties.SCENARIO);
						if (scenarioIdx == 0) { //Scenario 1-2-3-4
							Double parameter = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_ONLY_PARAMETER);
							parameter /= factor;
							edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_ONLY_PARAMETER, parameter);
						} else if (scenarioIdx == 1) { //Scenario 5
							Double k2km = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_K2_KM);
							k2km /= factor;
							edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_K2_KM, k2km);
						} else if (scenarioIdx == 2) { //Scenario 6
							Double k2 = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_K2);
							k2 /= factor;
							edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_K2, k2);
						}
					}
					/*} else {
						JOptionPane.showMessageDialog(null, "Tutto ok! La scala upstream è " + scaleUpstream + ", la scala downstream è " + scaleDownstream + ",\nil / viene " + (scaleUpstream / scaleDownstream) + ",\nil * viene " + (scaleUpstream * scaleDownstream) + ",\nma la scala attuale della reazione è " + scale, nomeReazione, JOptionPane.INFORMATION_MESSAGE);
					}*/
					nodeAttributes.setAttribute(edge.getSource().getIdentifier(), LEVELS_SCALE_FACTOR, scaleUpstream);
					nodeAttributes.setAttribute(edge.getTarget().getIdentifier(), LEVELS_SCALE_FACTOR, scaleDownstream);
					edgeAttributes.deleteAttribute(edge.getIdentifier(), LEVELS_SCALE_FACTOR);
				}
				/*if (!edgeAttributes.hasAttribute(edge.getIdentifier(), LEVELS_SCALE_FACTOR)) {  //This is commented because edges should not have this property anymore
					edgeAttributes.setAttribute(edge.getIdentifier(), LEVELS_SCALE_FACTOR, 1.0);
				}*/
			}
			
			//Now that we have set all properties nice and well, check that there is at least one reactant selected for plotting that actually makes sense to plot (it needs to be involved in an enabled reaction)
			boolean noReactantsPlotted = true;
			Vector<String> reactantsActuallyInvolvedInReactions = new Vector<String>();
			Iterator<Edge> edgess = (Iterator<Edge>) network.edgesIterator();
			for (int i = 0; edgess.hasNext(); i++) {
				Edge edge = edgess.next();
				if (!edgeAttributes.getBooleanAttribute(edge.getIdentifier(), Model.Properties.ENABLED)) {
					continue;
				}
				List<String> influencedReactants = edgeAttributes.getListAttribute(edge.getIdentifier(), Model.Properties.INFLUENCED_REACTANTS);
				for (String s : influencedReactants) {
					ReactantParameter rp = new ReactantParameter(s);
					reactantsActuallyInvolvedInReactions.add(rp.getReactantIdentifier());
				}
				Scenario[] scenarios = Scenario.availableScenarios;
				int scenarioIdx = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), Model.Properties.SCENARIO);
				Scenario reactionScenario = scenarios[scenarioIdx];
				if (reactionScenario instanceof UserFormula) {
					UserFormula formula = (UserFormula)reactionScenario;
					for (ReactantParameter rp : formula.getLinkedVariables().values()) {
						reactantsActuallyInvolvedInReactions.add(rp.getReactantIdentifier());
					}
				}
			}
			Iterator<Node> nodes = (Iterator<Node>) network.nodesIterator();
			for (int i = 0; nodes.hasNext(); i++) {
				Node node = nodes.next();
				boolean enabled = false;
				if (!nodeAttributes.hasAttribute(node.getIdentifier(), ENABLED)) {
					nodeAttributes.setAttribute(node.getIdentifier(), ENABLED, true);
					enabled = true;
				} else {
					enabled = nodeAttributes.getBooleanAttribute(node.getIdentifier(), ENABLED);
				}
				
				if (!nodeAttributes.hasAttribute(node.getIdentifier(), PLOTTED)) {
					nodeAttributes.setAttribute(node.getIdentifier(), PLOTTED, true);
					if (enabled) {
						if (reactantsActuallyInvolvedInReactions.contains(node.getIdentifier())) {
							noReactantsPlotted = false;
						}
					}
				} else if (enabled && nodeAttributes.getBooleanAttribute(node.getIdentifier(), PLOTTED)) {
					if (reactantsActuallyInvolvedInReactions.contains(node.getIdentifier())) {
						noReactantsPlotted = false;
					}
				}
				
				if (!nodeAttributes.hasAttribute(node.getIdentifier(), NUMBER_OF_LEVELS)) {
					nodeAttributes.setAttribute(node.getIdentifier(), NUMBER_OF_LEVELS, networkAttributes.getIntegerAttribute(network.getIdentifier(), NUMBER_OF_LEVELS));
				}
				
				if (!nodeAttributes.hasAttribute(node.getIdentifier(), INITIAL_QUANTITY)) {
					nodeAttributes.setAttribute(node.getIdentifier(), INITIAL_QUANTITY, nodeAttributes.getIntegerAttribute(node.getIdentifier(), NUMBER_OF_LEVELS));
				}
				
				if (!nodeAttributes.hasAttribute(node.getIdentifier(), INITIAL_LEVEL)) {
					//throw new ANIMOException("Node attribute 'initialConcentration' is missing on '" + node.getIdentifier() + "'");
					nodeAttributes.setAttribute(node.getIdentifier(), INITIAL_LEVEL, 0);
				}
				
				if (!nodeAttributes.hasAttribute(node.getIdentifier(), LEVELS_SCALE_FACTOR)) {
					nodeAttributes.setAttribute(node.getIdentifier(), LEVELS_SCALE_FACTOR, 1.0);
				}
			}
			
			if (noReactantsPlotted && !smcUppaal.isSelected()) {
				JOptionPane.showMessageDialog((JTask)this.monitor, "No reactants are selected for plot, or none of the selected ones is affected by any of the enabled reactions.\nPlease select at least one reactant to be plotted in the graph.", "Error", JOptionPane.ERROR_MESSAGE); 
				throw new ANIMOException("No reactants are selected for plot, or none of the selected ones is affected by any of the enabled reactions.\nPlease select at least one reactant to be plotted in the graph.");
			}
			
			Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);
			
			if (true) {
				return;
			}
			//============ SECOND PART: MAKE SURE THAT REACTION PARAMETERS IN COMBINATION WITH TIME POINTS DENSITY (SECONDS/POINT) DON'T GENERATE BAD PARAMETERS FOR UPPAAL =============
			
			double minSecStep = Double.NEGATIVE_INFINITY, maxSecStep = Double.POSITIVE_INFINITY, //The lower bound of the "valid" interval for secs/step (minSecStep) is the maximum of the lower bounds we find for it, while the upper bound (maxSecStep) is the minimum of all upper bounds. This is why we compute them in this apparently strange way
				   secPerStep = networkAttributes.getDoubleAttribute(network.getIdentifier(), SECONDS_PER_POINT);
			
			
			edges = (Iterator<Edge>) network.edgesIterator();
			for (int i = 0; edges.hasNext(); i++) {
				Edge edge = edges.next();
				double levelsScaleFactor = nodeAttributes.getDoubleAttribute(edge.getSource().getIdentifier(), LEVELS_SCALE_FACTOR) / nodeAttributes.getDoubleAttribute(edge.getTarget().getIdentifier(), LEVELS_SCALE_FACTOR);
											//edgeAttributes.getDoubleAttribute(edge.getIdentifier(), LEVELS_SCALE_FACTOR); //Now the scale factor due to the nodes' scale is a property of each node
				if (edge.getSource() == edge.getTarget()) {
					String rId = edge.getSource().getIdentifier();
					
					int nLevels;
					if (nodeAttributes.hasAttribute(rId, NUMBER_OF_LEVELS)) {
						nLevels = nodeAttributes.getIntegerAttribute(rId, NUMBER_OF_LEVELS);
					} else {
						nLevels = networkAttributes.getIntegerAttribute(network.getIdentifier(), NUMBER_OF_LEVELS);
					}
					
					ScenarioMono scenario = new ScenarioMono();
					String[] parameters = scenario.listVariableParameters();
					for (int j = 0;j < parameters.length;j++) {
						Double parVal = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), parameters[j]);
						if (parVal != null) {
							scenario.setParameter(parameters[j], parVal);
						} else {
							//TODO: show the editing window
						}
					}
					
					double uncertainty;
					if (edgeAttributes.hasAttribute(edge.getIdentifier(), UNCERTAINTY)) {
						uncertainty = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), UNCERTAINTY);
					} else {
						uncertainty = 0;
					}
					
					Double massimo = scenario.computeFormula(1),
						minimo = scenario.computeFormula(nLevels);
					int massimoUB,
						minimoLB;
					if (!Double.isInfinite(massimo)) {
						massimoUB = Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * massimo * (1 + uncertainty / 100.0)));
					} else {
						massimoUB = VariablesModel.INFINITE_TIME;
					}
					if (!Double.isInfinite(minimo)) {
						minimoLB = Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * minimo * (1 - uncertainty / 100.0)));
					} else {
						minimoLB = VariablesModel.INFINITE_TIME;
					}
					if (massimoUB > VERY_LARGE_TIME_VALUE) {
						//System.err.println("La reazione " + nodeAttributes.getAttribute(rId, Model.Properties.CANONICAL_NAME) + " --| " + nodeAttributes.getAttribute(rId, Model.Properties.CANONICAL_NAME) + " ha un numero troppo alto in angolo alto-sx!! (1)");
						double rate = scenario.computeRate(1);
						double proposedSecStep = secPerStep / (VERY_LARGE_TIME_VALUE * rate / (secStepFactor * levelsScaleFactor * (1 + uncertainty / 100.0))); //Math.ceil(secPerStep / (VERY_LARGE_TIME_VALUE * rate / ((100.0 + uncertainty) / 100.0)));
						if (proposedSecStep > minSecStep) {
							minSecStep = proposedSecStep;
						}
					}
					if (minimoLB == 1) {
						//System.err.println("La reazione " + nodeAttributes.getAttribute(rId, Model.Properties.CANONICAL_NAME) + " --| " + nodeAttributes.getAttribute(rId, Model.Properties.CANONICAL_NAME) + " ha un uno in angolo basso-dx!! (" + nLevels + ")");
						double rate = scenario.computeRate(nLevels);
						double proposedSecStep = secPerStep / (1.5 * rate / (secStepFactor * levelsScaleFactor * (1 - uncertainty / 100.0))); //Math.floor(secPerStep / (1.5 * rate / ((100.0 - uncertainty) / 100.0)));
						if (proposedSecStep < maxSecStep) {
							maxSecStep = proposedSecStep;
						}
					}
				} else {
					String r1Id = edge.getSource().getIdentifier(),
						   r2Id = edge.getTarget().getIdentifier();
				
					int nLevelsR1, nLevelsR2;
					if (nodeAttributes.hasAttribute(r1Id, NUMBER_OF_LEVELS)) {
						nLevelsR1 = nodeAttributes.getIntegerAttribute(r1Id, NUMBER_OF_LEVELS);
					} else {
						//TODO: il controllo per la presenza dei livelli non l'ho ancora fatto a questo punto!!
						//suggerisco di fare una funzione apposta per fare tutta la serie di controlli che facciamo all'inizio di getModel
						//a cui aggiungiamo in coda questo controllo sugli uni (e numeri troppo grandi)!
						nLevelsR1 = networkAttributes.getIntegerAttribute(network.getIdentifier(), NUMBER_OF_LEVELS);
					}
					if (nodeAttributes.hasAttribute(r2Id, NUMBER_OF_LEVELS)) {
						nLevelsR2 = nodeAttributes.getIntegerAttribute(r2Id, NUMBER_OF_LEVELS);
					} else {
						nLevelsR2 = networkAttributes.getIntegerAttribute(network.getIdentifier(), NUMBER_OF_LEVELS);
					}
					
					Scenario[] scenarios = Scenario.availableScenarios;
					Integer scenarioIdx = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), SCENARIO);
					if (scenarioIdx == null) {
						//TODO: show the editing window
						scenarioIdx = 0;
					}
					Scenario scenario = scenarios[scenarioIdx];
					
					String[] parameters = scenario.listVariableParameters();
					for (int j = 0;j < parameters.length;j++) {
						Double parVal = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), parameters[j]);
						if (parVal != null) {
							scenario.setParameter(parameters[j], parVal);
						} else {
							//TODO: show the editing window
						}
					}
					
					double uncertainty;
					if (edgeAttributes.hasAttribute(edge.getIdentifier(), UNCERTAINTY)) {
						uncertainty = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), UNCERTAINTY);
					} else {
						uncertainty = 0;
					}
					
					boolean activatingReaction = true;
					if (edgeAttributes.hasAttribute(edge.getIdentifier(), INCREMENT) && edgeAttributes.getIntegerAttribute(edge.getIdentifier(), INCREMENT) > 0) {
						activatingReaction = true;
					} else {
						activatingReaction = false;
					}
					
					if (activatingReaction) {
						//System.err.println("Controllo la reazione " + nodeAttributes.getAttribute(r1Id, Model.Properties.CANONICAL_NAME) + " --> " + nodeAttributes.getAttribute(r2Id, Model.Properties.CANONICAL_NAME) + "..."); 
						Double angoloAltoDx = scenario.computeFormula(nLevelsR1, nLevelsR1, 0, nLevelsR2, activatingReaction),
							angoloBassoSx = scenario.computeFormula(1, nLevelsR1, nLevelsR2 - 1, nLevelsR2, activatingReaction);
						int angoloAltoDxLB,
							angoloBassoSxUB;
						if (!Double.isInfinite(angoloAltoDx)) {
							angoloAltoDxLB = Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * angoloAltoDx * (1 - uncertainty / 100.0)));
						} else {
							angoloAltoDxLB = VariablesModel.INFINITE_TIME;
						}
						if (!Double.isInfinite(angoloBassoSx)) {
							angoloBassoSxUB = Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * angoloBassoSx * (1 + uncertainty / 100.0)));
						} else {
							angoloBassoSxUB = VariablesModel.INFINITE_TIME;
						}
						if (angoloAltoDxLB == 1) {
							//System.err.println("La reazione " + nodeAttributes.getAttribute(r1Id, Model.Properties.CANONICAL_NAME) + " --> " + nodeAttributes.getAttribute(r2Id, Model.Properties.CANONICAL_NAME) + " ha un uno in angolo alto-dx!! (" + (nLevelsR1) + ", " + 0 + ")");
							double rate = scenario.computeRate(nLevelsR1, nLevelsR1, 0, nLevelsR2,  activatingReaction);
							/*if (rate > 1) {
								System.err.println("\tIl rate (" + rate + ") è infatti > 1");
							} else {
								System.err.println("\tIl rate (" + rate + ") però NON è > 1! Il reciproco viene " + (int)Math.round(1 / rate) + ", ma l'uno ce l'abbiamo perché facciamo -" + uncertainty + "%, che viene appunto " + ((int)((int)Math.round(1 / rate) * (100.0 - uncertainty) / 100.0)));
							}*/
							//System.err.println("\tQuindi consiglio di DIVIDERE sec/step almeno per " + (1.5 * rate / ((100.0 - uncertainty) / 100.0)) + ", ottenendo quindi non più di " + (secPerStep / (1.5 * rate / (secStepFactor * (100.0 - uncertainty) / 100.0))));
							double proposedSecStep = secPerStep / (1.5 * rate / (secStepFactor * levelsScaleFactor * (1 - uncertainty / 100.0))); //Math.floor(secPerStep / (1.5 * rate / ((100.0 - uncertainty) / 100.0)));
							if (proposedSecStep < maxSecStep) {
								maxSecStep = proposedSecStep;
							}
						}
						if (angoloBassoSxUB > VERY_LARGE_TIME_VALUE) {
							//System.err.println("La reazione " + nodeAttributes.getAttribute(r1Id, Model.Properties.CANONICAL_NAME) + " --> " + nodeAttributes.getAttribute(r2Id, Model.Properties.CANONICAL_NAME) + " ha un numero troppo alto in angolo basso-sx!! (" + 1 + ", " + (nLevelsR2 - 1) + ")");
							double rate = scenario.computeRate(1, nLevelsR1, nLevelsR2 - 1, nLevelsR2, activatingReaction);
							//In questo caso si consiglia di dividere sec/step per un fattore < (VERY_LARGE_TIME_VALUE * rate / ((100.0 + uncertainty) / 100.0))
							double proposedSecStep = secPerStep / (VERY_LARGE_TIME_VALUE * rate / (secStepFactor * levelsScaleFactor * (1 + uncertainty / 100.0))); //Math.ceil(secPerStep / (VERY_LARGE_TIME_VALUE * rate / ((100.0 + uncertainty) / 100.0)));
							if (proposedSecStep > minSecStep) {
								minSecStep = proposedSecStep;
							}
						}
					} else {
						//System.err.println("Controllo la reazione " + nodeAttributes.getAttribute(r1Id, Model.Properties.CANONICAL_NAME) + " --| " + nodeAttributes.getAttribute(r2Id, Model.Properties.CANONICAL_NAME) + "...");
						Double angoloBassoDx = scenario.computeFormula(nLevelsR1, nLevelsR1, nLevelsR2, nLevelsR2, activatingReaction),
							angoloAltoSx = scenario.computeFormula(1, nLevelsR1, 1, nLevelsR2, activatingReaction);
						int angoloBassoDxLB,
							angoloAltoSxUB;
						if (!Double.isInfinite(angoloBassoDx)) {
							angoloBassoDxLB = Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * angoloBassoDx * (1 - uncertainty / 100.0)));
						} else {
							angoloBassoDxLB = VariablesModel.INFINITE_TIME;
						}
						if (!Double.isInfinite(angoloAltoSx)) {
							angoloAltoSxUB = Math.max(1, (int)Math.round(secStepFactor * levelsScaleFactor * angoloAltoSx * (1 + uncertainty / 100.0)));
						} else {
							angoloAltoSxUB = VariablesModel.INFINITE_TIME;
						}
						if (angoloBassoDxLB == 1) {
							//System.err.println("La reazione " + nodeAttributes.getAttribute(r1Id, Model.Properties.CANONICAL_NAME) + " --| " + nodeAttributes.getAttribute(r2Id, Model.Properties.CANONICAL_NAME) + " ha un uno in angolo basso-dx!! (" + (nLevelsR1) + ", " + (nLevelsR2) + ")");
							double rate = scenario.computeRate(nLevelsR1, nLevelsR1, nLevelsR2, nLevelsR2, activatingReaction);
							/*if (rate > 1) {
								System.err.println("\tIl rate (" + rate + ") è infatti > 1");
							} else {
								System.err.println("\tIl rate (" + rate + ") però NON è > 1! Il reciproco viene " + (int)Math.round(1 / rate) + ", ma l'uno ce l'abbiamo perché facciamo -" + uncertainty + "%, che viene appunto " + ((int)((int)Math.round(1 / rate) * (100.0 - uncertainty) / 100.0)));
							}*/
							//System.err.println("\tQuindi consiglio di DIVIDERE sec/step almeno per " + (1.5 * rate / ((100.0 - uncertainty) / 100.0)) + ", ottenendo quindi non più di " + (secPerStep / (1.5 * rate / (secStepFactor * (100.0 - uncertainty) / 100.0))));
							double proposedSecStep = secPerStep / (1.5 * rate / (secStepFactor * levelsScaleFactor * (1 - uncertainty / 100.0))); //Math.floor(secPerStep / (1.5 * rate / ((100.0 - uncertainty) / 100.0)));
							if (proposedSecStep < maxSecStep) {
								maxSecStep = proposedSecStep;
							}
						}
						if (angoloAltoSxUB > VERY_LARGE_TIME_VALUE) {
							//System.err.println("La reazione " + nodeAttributes.getAttribute(r1Id, Model.Properties.CANONICAL_NAME) + " --| " + nodeAttributes.getAttribute(r2Id, Model.Properties.CANONICAL_NAME) + " ha un numero troppo alto in angolo alto-sx!! (1, 1)");
							double rate = scenario.computeRate(1, nLevelsR1, 1, nLevelsR2, activatingReaction);
							//In questo caso si consiglia di dividere sec/step per un fattore < (VERY_LARGE_TIME_VALUE * rate / ((100.0 + uncertainty) / 100.0))
							double proposedSecStep = secPerStep / (VERY_LARGE_TIME_VALUE * rate / (secStepFactor * levelsScaleFactor * (1 + uncertainty / 100.0))); //Math.ceil(secPerStep / (VERY_LARGE_TIME_VALUE * rate / ((100.0 + uncertainty) / 100.0)));
							if (proposedSecStep > minSecStep) {
								minSecStep = proposedSecStep;
							}
						}
					}
				}
			}
			if (!Double.isInfinite(minSecStep) || !Double.isInfinite(maxSecStep)) {
				System.err.println("As far as I see from the computations, a valid interval for secs/point is [" + minSecStep + ", " + maxSecStep + "]");
			}
			if (!Double.isInfinite(maxSecStep) && secPerStep > maxSecStep) {
				System.err.println("\tThe current setting is over the top: " + secPerStep + " > " + maxSecStep + ", so take " + maxSecStep);
				secPerStep = maxSecStep;
				networkAttributes.setAttribute(network.getIdentifier(), SECONDS_PER_POINT, secPerStep);
			} else {
				//System.err.println("\tNon vado sopra il massimo: " + secPerStep + " <= " + maxSecStep);
			}
			if (!Double.isInfinite(minSecStep) && secPerStep < minSecStep) { //Notice that this check is made last because it is the most important: if we set seconds/point to a value less than the computed minimum, the time values will be so large that UPPAAL will not be able to understand them, thus producing no result
				System.err.println("\tThe current seetting is under the bottom: " + secPerStep + " < " + minSecStep + ", so take " + minSecStep);
				secPerStep = minSecStep;
				networkAttributes.setAttribute(network.getIdentifier(), SECONDS_PER_POINT, secPerStep);
			} else {
				//System.err.println("\tNon vado neanche sotto il minimo: " + secPerStep + " >= " + minSecStep);
			}
			
			Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);

		}
	}
}

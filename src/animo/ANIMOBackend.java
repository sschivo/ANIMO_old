/**
 * 
 */
package animo;

import giny.model.Node;
import giny.view.EdgeView;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import animo.exceptions.ANIMOException;
import animo.model.Model;
import animo.model.Scenario;
import animo.util.XmlConfiguration;
import animo.util.XmlEnvironment;

import cytoscape.CyEdge;
import cytoscape.CyNetwork;
import cytoscape.CyNode;
import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;
import cytoscape.data.attr.MultiHashMapListener;
import cytoscape.view.CyNetworkView;

/**
 * The ANIMO backend singleton is used to initialise the ANIMO backend, and to
 * retrieve configuration.
 * 
 * @author B. Wanders
 */
public class ANIMOBackend {
	/**
	 * The singleton instance.
	 */
	private static ANIMOBackend instance;

	/**
	 * The configuration properties.
	 */
	private XmlConfiguration configuration = null;

	private static final String NUMBER_OF_LEVELS = Model.Properties.NUMBER_OF_LEVELS, //Property that can belong to a node or to a network. If related to a single node, it represents the maximum number of levels for that single reactant. If related to a complete network, it is the maximum value of the NUMBER_OF_LEVELS property among all nodes in the network. Expressed as integer number in [0, 100] (chosen by the user).
								INITIAL_LEVEL = Model.Properties.INITIAL_LEVEL, //Property belonging to a node. The initial activity level for a node. Expressed as an integer number in [0, NUMBER_OF_LEVELS for that node]
								SHOWN_LEVEL = Model.Properties.SHOWN_LEVEL, //Property belonging to a node. The current activity level of a node. Expressed as a relative number representing INITIAL_LEVEL / NUMBER_OF_LEVELS, so it is a double number in [0, 1]
								SECONDS_PER_POINT = Model.Properties.SECONDS_PER_POINT, //Property belonging to a network. The number of real-life seconds represented by a single UPPAAL time unit.
								ENABLED = Model.Properties.ENABLED; //Whether the node is enabled (included in the exported UPPAAL model)
								//SCENARIO = Model.Properties.SCENARIO; //Property belonging to an edge. The id of the scenario on which the reaction corresponding to the edge computes its time tables.
	
	/**
	 * Constructor.
	 * 
	 * @param configuration the configuration file location
	 * @throws ANIMOException if the ANIMO backend could not be initialised
	 */
	private ANIMOBackend(File configuration) throws ANIMOException {

		// read configuration file
		try {
			// initialise the XML environment
			XmlEnvironment.getInstance();

			try {
				// read config from file
				this.configuration = new XmlConfiguration(XmlEnvironment.parse(configuration));
			} catch (SAXException ex) {
				// create default configuration
				this.configuration = new XmlConfiguration(configuration);
			}
			
			
			
			//register variable listener
			Cytoscape.getNodeAttributes().getMultiHashMap().addDataListener(new MultiHashMapListener() {

				@Override
				public void allAttributeValuesRemoved(String arg0, String arg1) {
					
				}

				/**
				 * Perform useful updates when a property is changed.
				 * If the maximum number of levels of a reactant (NUMBER_OF_LEVELS) was changed, we update
				 * all reactions in which the reactant is involved, changing the right parameter(s) depending
				 * on which scenario the reaction is based. We also update the NUMBER_OF_LEVELS property of the
				 * whole network, making sure that it is always set to the maximum of that property for all
				 * nodes.
				 * If the initial activity level was changed, we update the activity ratio (SHOWN_LEVEL) to
				 * reflect the change also in the graphics of the node (see the visual mapping based on that
				 * property in AugmentAction.actionPerformed).
				 */
				@SuppressWarnings("unchecked")
				@Override
				public void attributeValueAssigned(String objectKey, String attributeName,
						Object[] keyIntoValue, Object oldAttributeValue, Object newAttributeValue) {
					
					
					if (attributeName.equals(NUMBER_OF_LEVELS)) { //we are here to listen for #levels changes in order to update the parameters of reactions in which the affected reactant is involved
						
						if (oldAttributeValue == null) { //We need to compute the scale factor also when this is the first time we edit the parameter, otherwise we will not be consistent with scale factors
							oldAttributeValue = 15;
						}
						
						double newLevel = 0, oldLevel = 0, factor = 0;
						newLevel = Double.parseDouble(newAttributeValue.toString());
						oldLevel = Double.parseDouble(oldAttributeValue.toString());
						factor = newLevel / oldLevel;
						
						//CyNetwork network = Cytoscape.getCurrentNetwork();
						CyAttributes nodeAttributes = Cytoscape.getNodeAttributes();
						Double scale;
						if (nodeAttributes.hasAttribute(objectKey, Model.Properties.LEVELS_SCALE_FACTOR)) {
							scale = nodeAttributes.getDoubleAttribute(objectKey, Model.Properties.LEVELS_SCALE_FACTOR);
						} else {
							scale = 1.0;
						}
						scale *= factor;
						nodeAttributes.setAttribute(objectKey, Model.Properties.LEVELS_SCALE_FACTOR, scale);
						/*CyAttributes edgeAttributes = Cytoscape.getEdgeAttributes();
						final Iterator<Edge> edges = (Iterator<Edge>) network.edgesIterator();
						for (int i = 0; edges.hasNext(); i++) {
							Edge edge = edges.next();
							Double scale;
							if (edgeAttributes.hasAttribute(edge.getIdentifier(), Model.Properties.LEVELS_SCALE_FACTOR)) {
								scale = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.LEVELS_SCALE_FACTOR);
							} else {
								scale = 1.0;
							}
							
							if (!edge.getSource().equals(edge.getTarget())) {
								if (edge.getSource().getIdentifier().equals(objectKey)) {
									scale *= factor;
								} else if (edge.getTarget().getIdentifier().equals(objectKey)) {
									scale /= factor;
								}
							}
							edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.LEVELS_SCALE_FACTOR, scale);*/
							
							//The following is not required anymore because it was substituted by the change in Model.Properties.LEVELS_SCALE_FACTOR here above.
							/*if (edge.getSource().getIdentifier().equals(objectKey) || edge.getTarget().getIdentifier().equals(objectKey)) {
								//update the parameters for the reaction
								if (edge.getSource().equals(edge.getTarget())) {
									//We don't need to change the parameter for mono reactions.
									//Double parameter = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), "parameter");
									//parameter /= factor;
									//edgeAttributes.setAttribute(edge.getIdentifier(), "parameter", parameter);
								} else {
									Integer scenarioIdx = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), Model.Properties.SCENARIO);
									if (scenarioIdx == 0) { //Scenario 1-2-3-4
										Double parameter = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_ONLY_PARAMETER);
										if (edge.getSource().getIdentifier().equals(objectKey)) { //We do something only if the changed reactant was the upstream one
											parameter /= factor;
										} else {
											parameter *= factor;
										}
										edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_ONLY_PARAMETER, parameter);
									} else if (scenarioIdx == 1) { //Scenario 5
										Double stot = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_STOT),
											   k2km = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_K2_KM);
										if (edge.getSource().getIdentifier().equals(objectKey)) { //If the changed reactant is the upstream one, we change only k2/km
											k2km /= factor;
										} else { //If the changed reactant is the downstream one, we change only Stot
											stot *= factor;
										}
										edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_STOT, stot);
										edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_K2_KM, k2km);
									} else if (scenarioIdx == 2) { //Scenario 6
										Double stot = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_STOT),
												 km = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_KM),
												 k2 = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_K2);
										if (edge.getSource().getIdentifier().equals(objectKey)) { //If the changed reactant is the upstream one, we change only k2
											k2 /= factor;
										} else { //If the changed reactant is the downstream one, we change all three
											stot *= factor;
											km *= factor;
											k2 *= factor;
										}
										edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_STOT, stot);
										edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_KM, km);
										edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_PARAMETER_K2, k2);
									}
								}
							}*/
						/*}*/
						//Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null); //!!! If you don't advertise the change of property, Cytoscape will never notice it ?!?
						
						//Update the value of "levels" in the network with the maximum of all levels
						int maxLevels = 0;
						java.util.Iterator<giny.model.Node> iter = Cytoscape.getCurrentNetwork().nodesIterator();
						while (iter.hasNext()) {
							giny.model.Node node = iter.next();
							int levels;
							if (Cytoscape.getNodeAttributes().hasAttribute(node.getIdentifier(), NUMBER_OF_LEVELS)) {
								Object val = Cytoscape.getNodeAttributes().getAttribute(node.getIdentifier(), NUMBER_OF_LEVELS);
								levels = Integer.parseInt(val.toString());
							} else {
								levels = 0;
							}
							if (levels > maxLevels) {
								maxLevels = levels;
							}
						}
						Cytoscape.getNetworkAttributes().setAttribute(Cytoscape.getCurrentNetwork().getIdentifier(), NUMBER_OF_LEVELS, maxLevels);
					} else if (attributeName.equals(INITIAL_LEVEL)) {
						CyAttributes nodeAttr = Cytoscape.getNodeAttributes();
						int currentLevel = 0, totalLevels = 0;
						try {
							currentLevel = Integer.parseInt(newAttributeValue.toString());
							totalLevels = nodeAttr.getIntegerAttribute(objectKey, NUMBER_OF_LEVELS);
						} catch (Exception ex) {
							currentLevel = 0;
							totalLevels = 1;
						}
						double activityRatio = (double)currentLevel / totalLevels;
						nodeAttr.setAttribute(objectKey, SHOWN_LEVEL, activityRatio);
						Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);
					} else if (attributeName.equals(ENABLED)) {
						if (oldAttributeValue == null) {
							oldAttributeValue = true;
						}
						CyAttributes nodeAttr = Cytoscape.getNodeAttributes(),
									 edgeAttr = Cytoscape.getEdgeAttributes();
						CyNetwork network = Cytoscape.getCurrentNetwork();
						CyNetworkView view = Cytoscape.getCurrentNetworkView();
						if (view == null) return;
						String nodeId = objectKey;
						boolean status = nodeAttr.getBooleanAttribute(nodeId, Model.Properties.ENABLED);
						//nodeAttr.setAttribute(nodeId, Model.Properties.ENABLED, status);
						Node node = null;
						Iterator nodeIter = network.nodesIterator();
						while (nodeIter.hasNext()) {
							Node n = (Node)(nodeIter.next());
							if (n == null) continue;
							if (n.getIdentifier().equals(nodeId)) {
								node = n;
								break;
							}
						}
						if (node != null) {
							int [] adjacentEdges = network.getAdjacentEdgeIndicesArray(node.getRootGraphIndex(), true, true, true);
							for (int edgeIdx : adjacentEdges) {
								//TODO: questo � giusto per ricordarsi quanto schifo faccia sta roba.
								//non � possibile che non si possa distinguere il caso in cui si sta caricando la rete (e quindi la visualizzazione non esiste)
								//dal caso normale
								EdgeView ziocane = view.getEdgeView(edgeIdx);
								if (ziocane == null) return;
								CyEdge edge = (CyEdge)ziocane.getEdge();
								edgeAttr.setAttribute(edge.getIdentifier(), Model.Properties.ENABLED, status);
							}
							for (int i : network.getEdgeIndicesArray()) {
								CyEdge edge = (CyEdge)view.getEdgeView(i).getEdge();
								CyNode source = (CyNode)edge.getSource(),
									   target = (CyNode)edge.getTarget();
								if ((nodeAttr.hasAttribute(source.getIdentifier(), Model.Properties.ENABLED) && !nodeAttr.getBooleanAttribute(source.getIdentifier(), Model.Properties.ENABLED))
									|| (nodeAttr.hasAttribute(target.getIdentifier(), Model.Properties.ENABLED) && !nodeAttr.getBooleanAttribute(target.getIdentifier(), Model.Properties.ENABLED))) {
									edgeAttr.setAttribute(edge.getIdentifier(), Model.Properties.ENABLED, false);
								}
							}
							Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);
						}
					}
				}

				@Override
				public void attributeValueRemoved(String arg0, String arg1,
						Object[] arg2, Object arg3) {
					
				}
				
			});
			
			
			
			Cytoscape.getNetworkAttributes().getMultiHashMap().addDataListener(new MultiHashMapListener() {

				@Override
				public void allAttributeValuesRemoved(String arg0, String arg1) {
					
				}

				/**
				 * When the user changes the SECONDS_PER_POINT property, we need to update all reaction
				 * parameters, causing all reactions to speed up or slow down, depending whether the time
				 * interval was shortened or stretched.
				 */
				@Override
				public void attributeValueAssigned(String objectKey, String attributeName,
						Object[] keyIntoValue, Object oldAttributeValue, Object newAttributeValue) {
					
					
					if (attributeName.equals(SECONDS_PER_POINT)) {
						//System.err.println("Setto il sec/point a " + newAttributeValue + ", mentre il vecchio era " + oldAttributeValue);
						if (oldAttributeValue == null) {
							//System.err.println("Quindi non faccio nulla");
							return; //If there was no old value, we can do very little
						} else {
							//System.err.println("Quindi aggiorno il fattore di scala");
						}
						
						CyNetwork network = Cytoscape.getCurrentNetwork();
						CyAttributes networkAttributes = Cytoscape.getNetworkAttributes();

						//System.err.println("Detto questo, esamino l'esistente fattore di scala: " + networkAttributes.getDoubleAttribute(network.getIdentifier(), Model.Properties.SECS_POINT_SCALE_FACTOR));
						
						double newSecondsPerPoint = 0, oldSecondsPerPoint = 0, factor = 0;
						newSecondsPerPoint = Double.parseDouble(newAttributeValue.toString());
						oldSecondsPerPoint = Double.parseDouble(oldAttributeValue.toString());
						factor = oldSecondsPerPoint / newSecondsPerPoint;
						
						//System.err.println("Sgamato! Hai cambiato il valore di sec/step di un fattore " + factor + "!");
						
						if (networkAttributes.hasAttribute(network.getIdentifier(), Model.Properties.SECS_POINT_SCALE_FACTOR)) {
							double val = networkAttributes.getDoubleAttribute(network.getIdentifier(), Model.Properties.SECS_POINT_SCALE_FACTOR);
							//System.err.println("MOLTIPLICO IL VECCHIO FATTORE DI SCALA " + val + " PER " + factor);
							val *= factor;
							networkAttributes.setAttribute(network.getIdentifier(), Model.Properties.SECS_POINT_SCALE_FACTOR, val);
						} else {
							//when we don't find a value for the SECS_POINT_SCALE_FACTOR, we assume it to be 1, so the new value will be simply the value of the factor.
							//System.err.println("NON AVENDO UN VECCHIO FATTORE DI SCALA, SETTO SEMPLICEMENTE AL NUOVO VALORE " + factor);
							networkAttributes.setAttribute(network.getIdentifier(), Model.Properties.SECS_POINT_SCALE_FACTOR, factor);
						}
						
						/*CyAttributes edgeAttributes = Cytoscape.getEdgeAttributes();
						final Iterator<Edge> edges = (Iterator<Edge>) network.edgesIterator();
						for (int i = 0; edges.hasNext(); i++) {
							Edge edge = edges.next();
							
							if (edge.getSource().equals(edge.getTarget())) {
								Double parameter = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_ONLY_PARAMETER);
								parameter /= factor;
								edgeAttributes.setAttribute(edge.getIdentifier(), Model.Properties.SCENARIO_ONLY_PARAMETER, parameter);
							} else {
								Integer scenarioIdx = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), SCENARIO);
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
						}*/
					} else if (attributeName.equals(Model.Properties.USER_DEFINED_FORMULAE)) {
						//if (oldAttributeValue == null) return; //If there was no old value, we can do very little
						Scenario.loadScenarios();
					}
				}

				@Override
				public void attributeValueRemoved(String arg0, String arg1,
						Object[] arg2, Object arg3) {
					
				}
			});
			
		//} catch (SAXException e) {
		//	throw new ANIMOException("Could not parse configuration file '" + configuration + "'", e);
		} catch (IOException e) {
			throw new ANIMOException("Could not read configuration file '" + configuration + "'", e);
		} catch (ParserConfigurationException e) {
			throw new ANIMOException("Could not write configuration file '" + configuration + "': parser configuration error", e);
		} catch (TransformerConfigurationException e) {
			throw new ANIMOException("Could not write configuration file '" + configuration + "': transformer (?!?) configuration error", e);
		} catch (TransformerException e) {
			throw new ANIMOException("Could not write configuration file '" + configuration + "': transformer write error", e);
		}
	}

	/**
	 * Initialises the ANIMO backend with the given configuration.
	 * 
	 * @param configuration the location of the configuration file
	 * @throws ANIMOException if the backend could not be initialised
	 */
	public static void initialise(File configuration) throws ANIMOException {
		assert !isInitialised() : "Can not re-initialise ANIMO backend.";

		ANIMOBackend.instance = new ANIMOBackend(configuration);
	}

	/**
	 * Returns the singleton instance.
	 * 
	 * @return the single instance
	 */
	public static ANIMOBackend get() {
		assert isInitialised() : "ANIMO Backend not yet initialised.";
		return instance;
	}

	/**
	 * Returns the configuration properties.
	 * 
	 * @return the configuration
	 */
	public XmlConfiguration configuration() {
		return this.configuration;
	}

	/**
	 * Returns whether the ANIMO backend is initialised.
	 * 
	 * @return whether the backend is initialised
	 */
	public static boolean isInitialised() {
		return instance != null;
	}
}

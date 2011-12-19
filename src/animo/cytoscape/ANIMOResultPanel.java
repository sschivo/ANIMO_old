package animo.cytoscape;


import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import animo.analyser.LevelResult;
import animo.analyser.uppaal.ResultAverager;
import animo.analyser.uppaal.VariablesModel;
import animo.graph.Graph;
import animo.model.Model;
import animo.model.ReactantParameter;
import animo.model.Reaction;
import animo.util.Pair;
import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;

/**
 * The ANIMO result panel.
 * 
 * @author Brend Wanders
 */
public class ANIMOResultPanel extends JPanel implements ChangeListener {

	private static final long serialVersionUID = -163756255393221954L;
	private final Model model; //The model from which the results were obtained
	private final LevelResult result; //Contains the results to be shown in this panel
	private JSlider slider; //The slider to allow the user to choose a moment in the simulation time, which will be reflected on the network window as node colors, indicating the corresponding reactant activity level.

	/**
	 * The panel constructor.
	 * 
	 * @param model the model this panel uses
	 * @param result the results object this panel uses
	 */
	@SuppressWarnings("unchecked")
	public ANIMOResultPanel(Model model, LevelResult result, double scale) {
		super(new BorderLayout(), true);
		this.model = model;
		this.result = result;

		JPanel sliderPanel = new JPanel(new BorderLayout());
		this.slider = new JSlider();
		this.slider.setOrientation(JSlider.HORIZONTAL);
		this.slider.setMinimum(0);
		this.slider.setMaximum((int)(double)(result.getTimeIndices().get(result.getTimeIndices().size() - 1)));
		this.slider.setValue(0);
		this.slider.getModel().addChangeListener(this);

		sliderPanel.add(this.slider, BorderLayout.CENTER);

		this.add(sliderPanel, BorderLayout.SOUTH);

		Graph g = new Graph();
		//We map reactant IDs to their corresponding aliases (canonical names, i.e., the names displayed to the user in the network window), so that
		//we will be able to use graph series names consistent with what the user has chosen.
		Map<String, String> seriesNameMapping = new HashMap<String, String>();
		Vector<String> filteredSeriesNames = new Vector<String>(), //profit from the cycle for the series mapping to create a filter for the series to be actually plotted
					   secondBlockSeriesNames = new Vector<String>();
		for (String r : result.getReactantIds()) {
			String name = null;
			String originalR = r;
			String rLower = r.toLowerCase();
			if (model.getReactant(r) != null) { //we can also refer to a name not present in the reactant collection
				name = model.getReactant(r).get(Model.Properties.ALIAS).as(String.class); //if an alias is set, we prefer it
				if (name == null) {
					name = model.getReactant(r).get(Model.Properties.REACTANT_NAME).as(String.class);
				}
			} else if (rLower.contains(VariablesModel.ACTIVITY_SUFFIX)
					|| rLower.contains(VariablesModel.QUANTITY_SUFFIX)
					|| rLower.contains(VariablesModel.MAX_QUANTITY_SUFFIX)
					|| rLower.contains(VariablesModel.PERCENTAGE_SUFFIX)) {
				if (rLower.contains(VariablesModel.ACTIVITY_SUFFIX)) {
					r = r.substring(0, r.lastIndexOf(VariablesModel.ACTIVITY_SUFFIX)) + r.substring(r.lastIndexOf(VariablesModel.ACTIVITY_SUFFIX) + VariablesModel.ACTIVITY_SUFFIX.length());
				} else if (rLower.contains(VariablesModel.MAX_QUANTITY_SUFFIX)) {
					r = r.substring(0, r.lastIndexOf(VariablesModel.MAX_QUANTITY_SUFFIX)) + r.substring(r.lastIndexOf(VariablesModel.MAX_QUANTITY_SUFFIX) + VariablesModel.MAX_QUANTITY_SUFFIX.length());
				} else if (rLower.contains(VariablesModel.QUANTITY_SUFFIX)) {
					r = r.substring(0, r.lastIndexOf(VariablesModel.QUANTITY_SUFFIX)) + r.substring(r.lastIndexOf(VariablesModel.QUANTITY_SUFFIX) + VariablesModel.QUANTITY_SUFFIX.length());
				} else if (rLower.contains(VariablesModel.PERCENTAGE_SUFFIX)) {
					r = r.substring(0, r.lastIndexOf(VariablesModel.PERCENTAGE_SUFFIX)) + r.substring(r.lastIndexOf(VariablesModel.PERCENTAGE_SUFFIX) + VariablesModel.PERCENTAGE_SUFFIX.length());
				}
				if (rLower.contains(ResultAverager.STD_DEV)) {
					r = r.substring(0, r.lastIndexOf(ResultAverager.STD_DEV)) + r.substring(r.lastIndexOf(ResultAverager.STD_DEV) + ResultAverager.STD_DEV.length());
				}
				name = model.getReactant(r).get(Model.Properties.ALIAS).as(String.class);
				boolean quantityInvolved = false, activityInvolved = false;
				Map<String, String> fromCytoscapeIdToModelId = model.getProperties().get(Model.Properties.CYTOSCAPE_TO_MODEL_NAMES_MAP).as(Map.class);
				for (Reaction reaction : model.getReactions()) {
					if (!reaction.get(Model.Properties.ENABLED).as(Boolean.class)) continue;
					List<String> influencedReactants = reaction.get(Model.Properties.INFLUENCED_REACTANTS).as(List.class);
					for (String reac : influencedReactants) {
						ReactantParameter infReac = new ReactantParameter(reac);
						if (fromCytoscapeIdToModelId.get(infReac.getReactantIdentifier()).equals(r)) {
							if (infReac.getPropertyName().equals(Model.Properties.QUANTITY)) {
								quantityInvolved = true;
							}
							if (infReac.getPropertyName().equals(Model.Properties.ACTIVITY_LEVEL)) {
								activityInvolved = true;
							}
						}
					}
					List<ReactantParameter> influencingReactants = reaction.get(Model.Properties.INFLUENCING_REACTANTS).as(List.class);
					for (ReactantParameter infReac : influencingReactants) {
						if (fromCytoscapeIdToModelId.get(infReac.getReactantIdentifier()).equals(r)) {
							if (infReac.getPropertyName().equals(Model.Properties.QUANTITY)) {
								quantityInvolved = true;
							}
							if (infReac.getPropertyName().equals(Model.Properties.ACTIVITY_LEVEL)) {
								activityInvolved = true;
							}
						}
					}
				}
				boolean plotted = model.getReactant(r).get(Model.Properties.PLOTTED).as(Boolean.class);
				if (plotted) {
					if (rLower.contains(VariablesModel.PERCENTAGE_SUFFIX) && activityInvolved) { //We use the % value of activity level instead of its "discrete levels" value (ACTIVITY_LEVEL)
						name += " activity";
						filteredSeriesNames.add(originalR);
						secondBlockSeriesNames.add(originalR); //Represent the % with a separate scale
					} else if (rLower.contains(VariablesModel.QUANTITY_SUFFIX) && quantityInvolved) {
						name += " concentration";
						filteredSeriesNames.add(originalR);
					}
				}
				if (rLower.contains(ResultAverager.STD_DEV)) {
					name += ResultAverager.STD_DEV;
				}
			} else if (rLower.contains(ResultAverager.STD_DEV)) {
				//TODO: the problem here is that we assume that ResultAverager.STD_DEV starts with a "_". If it does not, we are not able to work correctly here.
				//We also assume that ResultAverager.STD_DEV is a lowercase string.
				if (model.getReactant(r.substring(0, r.lastIndexOf("_"))).get(Model.Properties.ALIAS).as(String.class) != null) {
					name = model.getReactant(r.substring(0, r.lastIndexOf("_"))).get(Model.Properties.ALIAS).as(String.class) + ResultAverager.STD_DEV;
				} else {
					name = r; //in this case, I simply don't know what we are talking about =)
				}
			}
			if ((!rLower.contains(VariablesModel.ACTIVITY_SUFFIX) && !rLower.contains(VariablesModel.QUANTITY_SUFFIX)
					&& !rLower.contains(VariablesModel.MAX_QUANTITY_SUFFIX) && !rLower.contains(VariablesModel.PERCENTAGE_SUFFIX) && !rLower.contains(VariablesModel.SEMAPHORE_SUFFIX)) //These have already been dealt with
				&& ((!rLower.contains(ResultAverager.STD_DEV) && model.getReactant(r).get(Model.Properties.PLOTTED).as(Boolean.class))
					|| rLower.contains(ResultAverager.STD_DEV))) {
				filteredSeriesNames.add(originalR);
			}
			seriesNameMapping.put(originalR, name);
		}
		if (filteredSeriesNames.isEmpty()) {
			JOptionPane.showMessageDialog(Cytoscape.getDesktop(), "This should never happen: none of the reactants selected for plotting are influenced by enabled reactions.");
		}
		Pair<LevelResult, LevelResult> blocks = result.filter(filteredSeriesNames).split(secondBlockSeriesNames);
		LevelResult firstBlock = blocks.first,
					secondBlock = blocks.second;
		if (!firstBlock.isEmpty()) {
			g.parseLevelResult(firstBlock, seriesNameMapping, scale); //Add all series to the graph, using the mapping we built here to "translate" the names into the user-defined ones.
			g.setMainYLabel("mM");
		}
		if (!firstBlock.isEmpty() && !secondBlock.isEmpty()) {
			g.parseLevelResult(secondBlock, seriesNameMapping, scale, true); //Add all series to the graph, using the mapping we built here to "translate" the names into the user-defined ones.
			g.setSecondaryYLabel("%");
		} else if (!secondBlock.isEmpty()) {
			g.parseLevelResult(secondBlock, seriesNameMapping, scale); //we go as first, if we are alone
			g.setMainYLabel("%");
		}
		g.setXSeriesName("Time (min)");
		
		if (!model.getProperties().get(Model.Properties.NUMBER_OF_LEVELS).isNull()) { //if we find a maximum value for activity levels, we declare it to the graph, so that other added graphs (such as experimental data) will be automatically rescaled to match us
			double maxY = 0;
			if (!firstBlock.isEmpty()) {
				maxY = Math.max(maxY, firstBlock.getMaximumValue());
			}
			if (!secondBlock.isEmpty()) {
				maxY = Math.max(maxY, 100/*secondBlock.getMaximumValue()*/); //We may like more to represent % always on a 0-100 scale by default?
			}
			//int nLevels = model.getProperties().get(Model.Properties.NUMBER_OF_LEVELS).as(Integer.class);
			g.declareMaxYValue(maxY);
			double maxTime = scale * result.getTimeIndices().get(result.getTimeIndices().size()-1);
			g.setDrawArea(0, (int)maxTime, 0, (int)Math.round(maxY)); //This is done because the graph automatically computes the area to be shown based on minimum and maximum values for X and Y, including StdDev. So, if the StdDev of a particular series (which represents an average) in a particular point is larger that the value of that series in that point, the minimum y value would be negative. As this is not very nice to see, I decided that we will recenter the graph to more strict bounds instead.
		}
		this.add(g, BorderLayout.CENTER);
	}
	
	/**
	 * When the user moves the time slider, we update the activity ratio (SHOWN_LEVEL) of
	 * all nodes in the network window, so that, thanks to the continuous Visual Mapping
	 * defined when the interface is augmented (see AugmentAction), different colors will
	 * show different activity levels. 
	 */
	@Override
	public void stateChanged(ChangeEvent e) {
		final int t = this.slider.getValue();
		CyAttributes nodeAttributes = Cytoscape.getNodeAttributes();
		/*final int levels = this.model.getProperties().get(Model.Properties.NUMBER_OF_LEVELS).as(Integer.class); //at this point, all levels have already been rescaled to the maximum (= the number of levels of the model), so we use it as a reference for the number of levels to show on the network nodes 
		for (String r : this.result.getReactantIds()) {
			if (this.model.getReactant(r) == null) continue;
			final String id = this.model.getReactant(r).get(Model.Properties.REACTANT_NAME).as(String.class);
			final double level = this.result.getConcentration(r, t);
			nodeAttributes.setAttribute(id, Model.Properties.SHOWN_LEVEL, level / levels);
		}*/
		for (String r : result.getReactantIds()) {
			String realR = removeSuffixes(r);
			if (model.getReactant(realR) == null) continue;
			String id = model.getReactant(realR).get(Model.Properties.REACTANT_NAME).as(String.class);
			double qty = result.getConcentration(realR + VariablesModel.QUANTITY_SUFFIX, t),
				   act = result.getConcentration(realR + VariablesModel.ACTIVITY_SUFFIX, t);
			nodeAttributes.setAttribute(id, Model.Properties.SHOWN_LEVEL, act / qty);
			/*double perc = result.getConcentration(realR + VariablesModel.PERCENTAGE_SUFFIX, t); //TODO: It would be more correct to directly use the percentage value, but at the moment it is scaled to the "maximum level" in the model, which makes very little sense
			nodeAttributes.setAttribute(id, Model.Properties.SHOWN_LEVEL, perc / 100.0);*/
		}

		Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);

		Cytoscape.getCurrentNetworkView().updateView();
	}
	
	private String removeSuffixes(String r) {
		if (r.contains(VariablesModel.ACTIVITY_SUFFIX)) {
			r = r.substring(0, r.lastIndexOf(VariablesModel.ACTIVITY_SUFFIX)) + r.substring(r.lastIndexOf(VariablesModel.ACTIVITY_SUFFIX) + VariablesModel.ACTIVITY_SUFFIX.length());
		} else if (r.contains(VariablesModel.MAX_QUANTITY_SUFFIX)) {
			r = r.substring(0, r.lastIndexOf(VariablesModel.MAX_QUANTITY_SUFFIX)) + r.substring(r.lastIndexOf(VariablesModel.MAX_QUANTITY_SUFFIX) + VariablesModel.MAX_QUANTITY_SUFFIX.length());
		} else if (r.contains(VariablesModel.QUANTITY_SUFFIX)) {
			r = r.substring(0, r.lastIndexOf(VariablesModel.QUANTITY_SUFFIX)) + r.substring(r.lastIndexOf(VariablesModel.QUANTITY_SUFFIX) + VariablesModel.QUANTITY_SUFFIX.length());
		}
		return r;
	}
}

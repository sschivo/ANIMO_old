package animo.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A model. This model keeps itself consistent, as long as both {@link Reactant}
 * and {@link Reaction} implementations keep their {@link #equals(Object)}
 * method based on identity the model is automatically consistent.
 * 
 * @author B. Wanders
 */
public class Model implements Serializable {
	public static class Properties {
		public static final String QUANTITY = "Quantity", //The amount of reactant
								   MAXIMUM_QUANTITY_GROWTH = "Maximum quantity growth", //How many times the quantity can grow with respect to its initial value (if this quantity is 10, the quantity can become 10 times larger)
								   ACTIVITY_LEVEL = "Activity level", //The discrete level of activity of the reactant
								   INACTIVITY_LEVEL = "Inactive reactant", //The discrete level of INactivity of the reactant (it should always be quantity - activity level)
								   NUMBER_OF_LEVELS = "levels", //Property that can belong to a node or to a network. If related to a single node, it represents the maximum number of levels for that single reactant. If related to a complete network, it is the maximum value of the NUMBER_OF_LEVELS property among all nodes in the network. Expressed as integer number in [0, 100] (chosen by the user).
								   INITIAL_QUANTITY = "initialTotalConcentration", //The initial quantity (concentration) of the molecular species represented by a node (this is the total, thus including both "active" and "inactive" molecules). This number can initially be in the range [0, NUMBER_OF_LEVELS], and can increase up to 10 * NUMBER_OF_LEVELS
								   INITIAL_LEVEL = "initialConcentration", //Property belonging to a node. The initial activity level for a node. Expressed as an integer number in [0, INITIAL_QUANTITY for that node]
								   SHOWN_LEVEL = "activityRatio", //Property belonging to a node. The current activity level of a node. Expressed as a relative number representing INITIAL_LEVEL / INITIAL_QUANTITY, so it is a double number in [0, 1]
								   SECONDS_PER_POINT = "seconds per point", //Property belonging to a network. The number of real-life seconds represented by a single UPPAAL time unit.
								   SECS_POINT_SCALE_FACTOR = "time scale factor", //This value is multiplied to the time bounds as a counterbalance to any change in seconds per point. This allows us to avoid having to directly modify the parameters of scenarios.
								   LEVELS_SCALE_FACTOR = "levels scale factor", //Also this value is multiplied to the time bounds, and it counterbalances the changes in the number of levels for the reactants. It is specific for every reaction.
								   SCENARIO = "scenario", //Property belonging to an edge. The id of the scenario on which the reaction corresponding to the edge computes its time tables.
								   ALIAS = "alias", //The property used to indicate the user-chosen name of a node
								   CANONICAL_NAME = "canonicalName", //The same, but in the Cytoscape model instead of the Model
								   ENABLED = "enabled", //Tells us whether a node/edge is enabled
								   PLOTTED = "plotted", //Tells us whether to plot a node or not
								   GROUP = "group", //A group of nodes identifies alternative phosphorylation sites (can be useless)
								   TIMES_UPPER = "timesU", //Upper time bound
								   TIMES = "times", //Time bound (no upper nor lower: it is possible that it is never be used in practice)
								   TIMES_LOWER = "timesL", //Lower time bound
								   DIMENSIONS = "dimensions", //The number of elements in each dimension of a multi-dimensional time matrix
								   INCREMENT = "increment", //Increment in substrate as effect of the reaction (+1, -1, etc)
								   BI_REACTION = "reaction2", //Reaction between two reactants (substrate/reactant and catalyst)
								   MONO_REACTION = "reaction1", //Reaction with only one reactant
								   UNCERTAINTY = "uncertainty", //The percentage of uncertainty about the reaction parameter settings
								   REACTANT = "reactant", //The reactant for a mono-reaction or the substrate for a bi-reaction
								   CYTOSCAPE_ID = "cytoscape id", //The ID assigned to the node/edge by Cytoscape
								   MOLECULE_TYPE = "moleculeType", //The type of the reactant (kinase, phosphatase, receptor, cytokine, ...)
								   TYPE_CYTOKINE = "Cytokine", //The following TYPE_* keys are the possible values for the property MOLECULE_TYPE
								   TYPE_RECEPTOR = "Receptor",
								   TYPE_KINASE = "Kinase",
								   TYPE_PHOSPHATASE = "Phosphatase",
								   TYPE_TRANSCRIPTION_FACTOR = "Transcription factor",
								   TYPE_COMPLEX = "Complex",
								   TYPE_OTHER = "Other",
								   REACTANT_NAME = "name", //The name of the reactant (possibly outdated property name)
								   REACTION_TYPE = "type", //Type of reaction (mono, bi)
								   CATALYST = "catalyst", //Catalyst in a bi-reaction
								   REACTANT_INDEX = "index", //The index of the reactant (sometimes we need to assign them an index)
								   SCENARIO_PARAMETER_KM = "km", //The following are all scenario parameters
								   SCENARIO_PARAMETER_K2 = "k2",
								   SCENARIO_PARAMETER_STOT = "Stot",
								   SCENARIO_PARAMETER_K2_KM = "k2/km",
								   SCENARIO_ONLY_PARAMETER = "parameter",
								   INFLUENCING_REACTANTS = "Influencing reactants", //The reactants (in the form identifier.quantity/activity/inactivity) influencing a reaction. This Edge property is represented as a list of Strings
								   INFLUENCED_REACTANTS = "Influenced reactants", //The reactants (in the form identifier.quantity/activity) influenced by a reaction. This Edge property is represented as a list of Strings
								   INFLUENCE_VALUES = "Influence values", //The influence values (usually, +1 or -1) for each reactant influenced by a reaction. This Edge property is represented as a list of Integers, and needs to be in the same order (and same lenght) as the one for INFLUENCED_REACTANTS
								   USER_DEFINED_FORMULAE = "User-defined formulae",
								   CYTOSCAPE_TO_MODEL_NAMES_MAP = "Cytoscape to model names"; //The map that allows us to understand to which reactant name inside the model a cytoscape node id corresponds
	}

	
	private static final long serialVersionUID = 9078409933212069999L;
	/**
	 * The vertices in the model.
	 */
	private final Map<String, Reactant> reactants;
	/**
	 * The edges in the model.
	 */
	private final Map<String, Reaction> reactions;

	/**
	 * The global properties on the model.
	 */
	private final PropertyBag properties;

	/**
	 * Constructor.
	 */
	public Model() {
		this.reactants = new HashMap<String, Reactant>();
		this.reactions = new HashMap<String, Reaction>();
		this.properties = new PropertyBag();
	}

	/**
	 * Puts (adds or replaces) a vertex into the model.
	 * 
	 * @param v the vertex to add
	 */
	public void add(Reactant v) {
		assert v.getModel() == null : "Can't add a reactant that is already part of a model.";

		this.reactants.put(v.getId(), v);
		v.setModel(this);
	}

	/**
	 * Puts (adds or replaces) an edge into the model.
	 * 
	 * @param e the edge to remove
	 */
	public void add(Reaction e) {
		assert e.getModel() == null : "Can't add a reaction that is already part of a model.";

		this.reactions.put(e.getId(), e);
		e.setModel(this);
	}

	/**
	 * Removes an edge.
	 * 
	 * @param e the edge to remove
	 */
	public void remove(Reaction e) {
		assert e.getModel() == this : "Can't remove a reaction that is not part of this model.";
		this.reactions.remove(e.getId());
		e.setModel(null);
	}

	/**
	 * Removes a vertex, this method also cleans all edges connecting to this
	 * vertex.
	 * 
	 * @param v the vertex to remove
	 */
	public void remove(Reactant v) {
		assert v.getModel() == this : "Can't remove a reactant that is not part of this model.";
		this.reactants.remove(v.getId());
		v.setModel(null);
	}

	/**
	 * Returns the edge with the given identifier, or {@code null}.
	 * 
	 * @param id the identifier we are looking for
	 * @return the found {@link Reaction}, or {@code null}
	 */
	public Reaction getReaction(String id) {
		return this.reactions.get(id);
	}

	/**
	 * Returns the vertex with the given identifier, or {@code null}.
	 * 
	 * @param id the identifier we are looking for
	 * @return the found {@link Reactant}, or {@code null}
	 */
	public Reactant getReactant(String id) {
		return this.reactants.get(id);
	}

	/**
	 * Returns the properties for this model.
	 * 
	 * @return the properties of this model
	 */
	public PropertyBag getProperties() {
		return this.properties;
	}

	/**
	 * Returns an unmodifiable view of all vertices in this model.
	 * 
	 * @return all vertices
	 */
	public Collection<Reactant> getReactants() {
		return Collections.unmodifiableCollection(this.reactants.values());
	}

	/**
	 * returns an unmodifiable view of all edges in this model.
	 * 
	 * @return all edges
	 */
	public Collection<Reaction> getReactions() {
		return Collections.unmodifiableCollection(this.reactions.values());
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();

		result.append("Model[\n");
		for (Reactant v : this.reactants.values()) {
			result.append("  " + v + "\n");
		}
		for (Reaction e : this.reactions.values()) {
			result.append("  " + e + "\n");
		}
		result.append("]");

		return result.toString();
	}
}

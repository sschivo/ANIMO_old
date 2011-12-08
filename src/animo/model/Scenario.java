package animo.model;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import javax.swing.JLabel;

import animo.exceptions.ANIMOException;

import cytoscape.CyNetwork;
import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;

/**
 * Represents a scenario for a reaction in the model.
 * There are 3 predefined scenarios, each one with its set of parameters.
 */
public class Scenario {
	private static final String SCENARIO_PARAMETER_KM = Model.Properties.SCENARIO_PARAMETER_KM,
								SCENARIO_PARAMETER_K2 = Model.Properties.SCENARIO_PARAMETER_K2,
								SCENARIO_PARAMETER_STOT = Model.Properties.SCENARIO_PARAMETER_STOT,
								SCENARIO_PARAMETER_K2_KM = Model.Properties.SCENARIO_PARAMETER_K2_KM,
								SCENARIO_ONLY_PARAMETER = Model.Properties.SCENARIO_ONLY_PARAMETER;
	protected HashMap<String, Double> parameters = new HashMap<String, Double>(); //Parameter name -> value
	protected HashMap<String, Double> defaultParameterValues = new HashMap<String, Double>(); //The defaul values of parameters
	protected HashMap<String, ReactantParameter> linkedVariables = new HashMap<String, ReactantParameter>(); //Variable name -> <reactant, property> to which the variable will be linked
	public static final int INFINITE_TIME = -1; //The constant to mean that the reaction will not happen
	
	private static Scenario[] defaultScenarios = new Scenario[3]; //The default predefined scenarios
	public static Scenario[] availableScenarios = defaultScenarios; 
	
	public Scenario() {
	}
	
	/**
	 * Add the model-specific scenarios to the bottom of the list of available scenarios
	 */
	@SuppressWarnings("unchecked")
	public static void loadScenarios() {
		//TODO: First step: load local scenarios from the configuration file
		Scenario[] currentlyAvailableScenarios = defaultScenarios;
		//TODO: l'importante è che currentlyAvailableScenarios contenga poi gli scenari di default + quelli locali.
		
		//Next step: load scenarios from the current network properties
		CyNetwork network = Cytoscape.getCurrentNetwork();
		CyAttributes networkAttributes = Cytoscape.getNetworkAttributes(); 
		if (networkAttributes.hasAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE)) {
			List<String> formulaList = networkAttributes.getListAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE);
			if (formulaList.isEmpty() || !formulaList.get(formulaList.size() - 1).equals(UserFormula.FORMULA_LIST_END)) { //As the smart Cytoscape adds the pieces of a list ONE AT THE TIME (!!), we wait until the list is complete (i.e. its last element is the one indicating the end) before actually reading it
				availableScenarios = currentlyAvailableScenarios;
			} else {
				Scenario[] userDefinedScenarios = UserFormula.readFormulae(formulaList),
						   newAvailableScenarios = new Scenario[currentlyAvailableScenarios.length + userDefinedScenarios.length];
				int i = 0;
				for (Scenario s : currentlyAvailableScenarios) {
					newAvailableScenarios[i++] = s;
				}
				for (Scenario s : userDefinedScenarios) {
					newAvailableScenarios[i++] = s;
				}
				availableScenarios = newAvailableScenarios;
			}
		} else {
			availableScenarios = currentlyAvailableScenarios;
		}
	}
	
	/**
	 * Add a new formula to the list of formulae.
	 * The list can be empty (or non-existent, if the property was never set): in that case, we create a new list
	 * @param formula The formula to be added
	 */
	@SuppressWarnings("unchecked")
	public static void addNewUserFormula(UserFormula formula) {
		CyNetwork network = Cytoscape.getCurrentNetwork();
		CyAttributes networkAttributes = Cytoscape.getNetworkAttributes();
		List<String> formulaList;
		if (networkAttributes.hasAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE)) {
			formulaList = networkAttributes.getListAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE);
		} else {
			formulaList = null;
		}
		formulaList = UserFormula.addNewUserFormula(formulaList, formula);
		networkAttributes.setListAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE, formulaList);
		Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);
	}
	
	/**
	 * Update the data of the given formula in the list. The formula needs to exist in the list,
	 * otherwise ANIMOException is thrown.
	 * @param formula The formula to be updated in the list
	 * @throws ANIMOException Thrown when the given formula was not in the list
	 */
	@SuppressWarnings("unchecked")
	public static void updateUserFormula(UserFormula oldFormula, UserFormula editedFormula) throws ANIMOException {
		CyNetwork network = Cytoscape.getCurrentNetwork();
		CyAttributes networkAttributes = Cytoscape.getNetworkAttributes();
		List<String> formulaList;
		if (networkAttributes.hasAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE)) {
			formulaList = networkAttributes.getListAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE);
		} else {
			throw new ANIMOException("There are no formulae in the current network: how could you ask me to update one?");
		}
		formulaList = UserFormula.updateUserFormula(formulaList, oldFormula, editedFormula);
		if (formulaList == null) {
			throw new ANIMOException("There was no formula called \"" + oldFormula.getName() + "\": where did you find it?");
		} else {
			networkAttributes.setListAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE, formulaList);
			Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);
		}
	}
	
	/**
	 * Delete a formula from the list of user-defined formulae
	 * @param condemnedFormula The formula to delete
	 * @throws ANIMOException If the formula was not found
	 */
	@SuppressWarnings("unchecked")
	public static void deleteUserFormula(UserFormula condemnedFormula) throws ANIMOException {
		CyNetwork network = Cytoscape.getCurrentNetwork();
		CyAttributes networkAttributes = Cytoscape.getNetworkAttributes();
		List<String> formulaList;
		if (networkAttributes.hasAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE)) {
			formulaList = networkAttributes.getListAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE);
		} else {
			throw new ANIMOException("There are no user-defined formulae in the current network: how could you ask me to delete one?");
		}
		formulaList = UserFormula.deleteUserFormula(formulaList, condemnedFormula);
		if (formulaList == null) {
			throw new ANIMOException("There was no formula called \"" + condemnedFormula.getName() + "\": where did you find it?");
		} else {
			networkAttributes.setListAttribute(network.getIdentifier(), Model.Properties.USER_DEFINED_FORMULAE, formulaList);
			Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);
		}
	}
	
	static {
		defaultScenarios[0] = new Scenario() {
			@Override
			public double computeRate(int r1Level, int nLevelsR1, int r2Level, int nLevelsR2, boolean activatingReaction) {
				double par = parameters.get(SCENARIO_ONLY_PARAMETER),
					   E = r1Level;
				double rate = par * E;
				return rate;
			}
			
			@Override
			public String[] listVariableParameters() {
				return new String[]{SCENARIO_ONLY_PARAMETER};
			}
			
			@Override
			public String[] listLinkedVariables() {
				return new String[]{};
			}
			
			@Override
			public JLabel getFormulaLabel() {
				return new JLabel(SCENARIO_ONLY_PARAMETER + " * E");
			}
			
			@Override
			public String toString() {
				return "Scenario 1-2-3-4";
			}
		};
		//defaultScenarios[0].setParameter(SCENARIO_ONLY_PARAMETER, 0.01);
		defaultScenarios[0].setDefaultParameterValue(SCENARIO_ONLY_PARAMETER, 0.01);
		
		defaultScenarios[1] = new Scenario() {
			@Override
			public double computeRate(int r1Level, int nLevelsR1, int r2Level, int nLevelsR2, boolean activatingReaction) {
				double par1 = parameters.get(SCENARIO_PARAMETER_K2_KM),
					   //Stot = parameters.get(SCENARIO_PARAMETER_STOT), TODO: THIS IS STRANGE. Where do we put Stot in the formula???
					   S,
					   E = (double)r1Level;
				/*r2Level = (int)Math.round(r2Level * Stot / nLevelsR2);
				if (activatingReaction) {
					S = Stot - r2Level;
				} else {
					//S = Stot - r2Level;
					S = r2Level;
				}*/
				//if (!activatingReaction) {
					S = r2Level;
				//} else {
				//	S = nLevelsR2 - r2Level;
				//}
				double rate = par1 * E * S;
				return rate;
			}
			
			@Override
			public String[] listVariableParameters() {
				return new String[]{SCENARIO_PARAMETER_K2_KM, SCENARIO_PARAMETER_STOT};
			}
			
			@Override
			public String[] listLinkedVariables() {
				return new String[]{};
			}
			
			@Override
			public JLabel getFormulaLabel() {
				return new JLabel(SCENARIO_PARAMETER_K2_KM + " * E * (" + SCENARIO_PARAMETER_STOT + " - S)");
			}
			
			@Override
			public String toString() {
				return "Scenario 5";
			}
		};
		//defaultScenarios[1].setParameter(SCENARIO_PARAMETER_K2_KM, 0.001);
		//defaultScenarios[1].setParameter(SCENARIO_PARAMETER_STOT, 15.0);
		defaultScenarios[1].setDefaultParameterValue(SCENARIO_PARAMETER_K2_KM, 0.001);
		defaultScenarios[1].setDefaultParameterValue(SCENARIO_PARAMETER_STOT, 15.0);
		
		defaultScenarios[2] = new Scenario() {
			@Override
			public double computeRate(int r1Level, int nLevelsR1, int r2Level, int nLevelsR2, boolean activatingReaction) {
				double k2 = parameters.get(SCENARIO_PARAMETER_K2),
					   E = (double)r1Level,
					   //Stot = parameters.get(SCENARIO_PARAMETER_STOT), TODO: THIS IS STRANGE. Where do we put Stot in the formula???
					   S,
					   km = parameters.get(SCENARIO_PARAMETER_KM);
				/*r2Level = (int)Math.round(r2Level * Stot / nLevelsR2);
				if (activatingReaction) {
					S = Stot - r2Level;
				} else {
					//S = Stot - r2Level;
					S = r2Level;
				}*/
				//if (!activatingReaction) {
					S = r2Level;
				//} else {
				//	S = nLevelsR2 - r2Level;
				//}
				double rate = k2 * E * S / (km + S);
				return rate;
			}
			
			@Override
			public String[] listVariableParameters() {
				return new String[]{SCENARIO_PARAMETER_K2, SCENARIO_PARAMETER_KM, SCENARIO_PARAMETER_STOT};
			}
			
			@Override
			public String[] listLinkedVariables() {
				return new String[]{};
			}
			
			@Override
			public JLabel getFormulaLabel() {
				return new JLabel(SCENARIO_PARAMETER_K2 + " * E * (" + SCENARIO_PARAMETER_STOT + " - S) / (" + SCENARIO_PARAMETER_KM + " + " + SCENARIO_PARAMETER_STOT + " - S)");
			}
			
			@Override
			public String toString() {
				return "Scenario 6";
			}
		};
		//defaultScenarios[2].setParameter(SCENARIO_PARAMETER_K2, 0.01);
		//defaultScenarios[2].setParameter(SCENARIO_PARAMETER_KM, 10.0);
		//defaultScenarios[2].setParameter(SCENARIO_PARAMETER_STOT, 15.0);
		defaultScenarios[2].setDefaultParameterValue(SCENARIO_PARAMETER_K2, 0.01);
		defaultScenarios[2].setDefaultParameterValue(SCENARIO_PARAMETER_KM, 10.0);
		defaultScenarios[2].setDefaultParameterValue(SCENARIO_PARAMETER_STOT, 15.0);
	}
	
	
	@SuppressWarnings("unchecked")
	public Scenario(Scenario source) {
		this.parameters = (HashMap<String, Double>)source.parameters.clone();
	}
	
	public void setParameter(String name, Double value) {
		parameters.put(name, value);
	}
	
	public Double getParameter(String name) {
		return parameters.get(name);
	}
	
	public void setLinkedVariable(String name, ReactantParameter linkedVariable) {
		linkedVariables.put(name, linkedVariable);
	}
	
	public ReactantParameter getLinkedVariable(String name) {
		return linkedVariables.get(name);
	}
	
	
	@SuppressWarnings("unchecked")
	public HashMap<String, Double> getParameters() {
		return (HashMap<String, Double>)parameters.clone();
	}
	
	@SuppressWarnings("unchecked")
	public HashMap<String, ReactantParameter> getLinkedVariables() {
		return (HashMap<String, ReactantParameter>)linkedVariables.clone();
	}
	
	public void setDefaultParameterValue(String name, Double value) {
		defaultParameterValues.put(name, value);
		parameters.put(name, value);
	}
	
	public Double getDefaultParameterValue(String name) {
		return defaultParameterValues.get(name);
	}
	
	@SuppressWarnings("unchecked")
	public HashMap<String, Double> getDefaultParameterValues() {
		return (HashMap<String, Double>)defaultParameterValues.clone();
	}
	
	public double computeRate(int r1Level, int nLevelsR1, int r2Level, int nLevelsR2, boolean activatingReaction) throws ANIMOException {
		double k2 = parameters.get(SCENARIO_PARAMETER_K2),
		   E = r1Level,
		   km = parameters.get(SCENARIO_PARAMETER_KM),
		   Stot = parameters.get(SCENARIO_PARAMETER_STOT),
		   S;
		r2Level = (int)Math.round(r2Level * Stot / nLevelsR2);
		//if (activatingReaction) { //The quantity of unreacted substrate is different depending on the point of view of the reaction: if we are activating, the unreacted substrate is inactive. But if we are inhibiting the unreacted substrate is ACTIVE.
			S = Stot - r2Level; //The above comment is not valid anymore: now we use different indexing inside the uppaal template instead of generating different time tables
		//} else {
		//	S = r2Level;
		//}
		double rate = k2 * E * S / (km + S);
		return rate;
	}
	
	public Double computeFormula(int r1Level, int nLevelsR1, int r2Level, int nLevelsR2, boolean activatingReaction) throws ANIMOException {
		double rate = computeRate(r1Level, nLevelsR1, r2Level, nLevelsR2, activatingReaction);
		if (rate > 1e-8) {
			//return Math.max(1, (int)Math.round(1 / rate)); //We need to put at least 1 because otherwise the reaction will keep happening forever (it is not very nice not to let time pass..)
			return 1.0 / rate;
		} else {
			//return INFINITE_TIME;
			return Double.POSITIVE_INFINITY;
		}
	}

	/**
	 * Generate the times table based on the scenario formula and parameters.
	 * @param nLevelsReactant1 The total number of levels of reactant1 (the enzyme or catalyst)
	 * @param nLevelsReactant2 The total number of levels of reactant2 (the substrate)
	 * @param activatingReaction True if the reaction has activating effect, false if it is inhibiting
	 * @return Output is a list for no particular reason anymore. It used to be
	 * set as a Cytoscape property, but these tables can become clumsy to be stored
	 * inside the model, slowing down the loading/saving processes in the Cytoscape
	 * interface. We now compute the tables on the fly and output them directly as
	 * reference constants in the UPPPAAL models, saving also memory.
	 */
	public List<Double> generateTimes(int nLevelsReactant1, int nLevelsReactant2, boolean activatingReaction) throws ANIMOException {
		List<Double> times = new Vector<Double>(nLevelsReactant1 * nLevelsReactant2);
		int i, limit;
		for (int j=0;j<nLevelsReactant1;j++) {
			times.add(Double.POSITIVE_INFINITY); //all reactant2 already reacted (i.e. no unreacted reactant2) = no reaction. We have to take care of the fact that "unreacted" may mean "active" or "inactive" (depending on the effect of the reaction on Reactant2) directly inside the computeRate
		}
		i = 1; //the first line was already done before, with all infinites
		limit = nLevelsReactant2;
		for (;i<limit;i++) {
			times.add(Double.POSITIVE_INFINITY); //no reactant1 = no reaction
			for (int j=1;j<nLevelsReactant1;j++) {
				times.add(computeFormula(j, nLevelsReactant1, i, nLevelsReactant2, activatingReaction));
			}
		}
		return times;
	}
	
	public String[] listVariableParameters() {
		return new String[]{};
	}
	
	public String[] listLinkedVariables() {
		return new String[]{};
	}
	
	public JLabel getFormulaLabel() {
		return new JLabel("k2 * E * S / (km + S)");
	}
	
}

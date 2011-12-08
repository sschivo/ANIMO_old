package animo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.JLabel;

import animo.exceptions.ANIMOException;

import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;

public class UserFormula extends Scenario {
	public static final String FORMULA_VARIABLES_SEPARATOR = ";",
								FORMULA_LIST_END = "#";

	public static final String UPSTREAM_REACTANT_ACTIVITY = "Upstream reactant activity",
							   DOWNSTREAM_REACTANT_ACTIVITY = "Downstream reactant activity";
	
	private String name,
				   javaScriptFormula = null;
	private Vector<FormulaVariable> variables = null;
	
	public UserFormula() {
		this.name = "(Formula name)";
		this.javaScriptFormula = "";
		this.variables = new Vector<FormulaVariable>();
	}
	
	public UserFormula(String name, String javaScriptFormula, Vector<FormulaVariable> variables) {
		this.name = name;
		this.javaScriptFormula = javaScriptFormula;
		this.variables = variables;
		/*for (FormulaVariable v : variables) {
			if (v.isParameter()) {
				defaultParameterValues.put(v.getName(), v.getDefaultParameterValue());
			}
		}*/
	}
	
	/**
	 * Read the user-defined formulae from the current network.
	 * Format for the user-defined formulae (string list): (name, formula, parameterVariables[], FORMULA_VARIABLES_SEPARATOR, linkedVariables[], FORMULA_VARIABLES_SEPARATOR, )+ FORMULA_LIST_END
	 * @param formulaList The parameter taken from the network property Model.Properties.USER_DEFINED_FORMULAE
	 * @return A vector containing the user-defined formulae read from the list
	 */
	public static Scenario[] readFormulae(List<String> formulaList) {
		Vector<UserFormula> result = new Vector<UserFormula>();
		ListIterator<String> it = formulaList.listIterator();
		String s = it.next();
		while (it.hasNext() && !s.equals(FORMULA_LIST_END)) {
			String name = s;
			String formula = it.next();
			Vector<FormulaVariable> vars = new Vector<FormulaVariable>();
			s = it.next();
			while (it.hasNext() && !s.equals(FORMULA_VARIABLES_SEPARATOR)) {
				vars.add(new FormulaVariable(s)); //This constructor is done so that it reads the correct format
				s = it.next();
			}
			result.add(new UserFormula(name, formula, vars));
			s = it.next();
		}
		return result.toArray(new Scenario[] {});
	}
	
	/**
	 * Insert a new UserFormula into the given list. For the format, see readFormulae
	 * @param formulaList The list of user-defined formulae stored in the network property Model.Properties.USER_DEFINED_FORMULAE
	 * @param formula The formula to be inserted into the list
	 */
	public static List<String> addNewUserFormula(List<String> formulaList, UserFormula formula) {
		ListIterator<String> it;
		if (formulaList == null) {
			formulaList = new ArrayList<String>();
			formulaList.add(FORMULA_LIST_END);
			it = formulaList.listIterator();
			it.next(); //so that in every case we have already read the end
		} else {
			it = formulaList.listIterator();
			String s = it.next();
			while (it.hasNext() && !s.equals(FORMULA_LIST_END)) {
				s = it.next();
			}
		}
		it.previous(); //go before the end
		it.add(formula.getName());
		it.add(formula.getFormula());
		for (FormulaVariable v : formula.getVariables()) {
			it.add(v.toString()); //The toString method is done so that it outputs the correct format
		}
		it.add(FORMULA_VARIABLES_SEPARATOR);
		return formulaList;
	}
	
	/**
	 * Update an existing UserFormula in the given list. For the format, see readFormulae
	 * @param formulaList The list of user-defined formulae stored in the network property Model.Properties.USER_DEFINED_FORMULAE
	 * @param formula The formula to be updated
	 */
	public static List<String> updateUserFormula(List<String> formulaList, UserFormula oldFormula, UserFormula editedFormula) {
		ListIterator<String> it = formulaList.listIterator();
		String s = it.next();
		while (it.hasNext() && !s.equals(FORMULA_LIST_END)) {
			if (s.equals(oldFormula.getName())) {
				while (it.hasNext() && !s.equals(FORMULA_VARIABLES_SEPARATOR)) {
					it.remove();
					s = it.next();
				}
				it.previous();
				it.add(editedFormula.getName());
				it.add(editedFormula.getFormula());
				for (FormulaVariable v : editedFormula.getVariables()) {
					it.add(v.toString()); //The toString method is done so that it outputs the correct format
				}
				return formulaList;
			}
			s = it.next();
		}
		//If we arrive here we did not find the formula: report error
		return null;
	}
	
	/**
	 * Remove a selected formula from the list
	 * @param formulaList The list from which to remove the formula (for the format, check readFormulae)
	 * @param condemnedFormula The formula to be deleted (the name will be used to find the target)
	 * @return The changed list
	 */
	public static List<String> deleteUserFormula(List<String> formulaList, UserFormula condemnedFormula) {
		ListIterator<String> it = formulaList.listIterator();
		String s = it.next();
		boolean notFound = true;
		while (it.hasNext() && !s.equals(FORMULA_LIST_END)) {
			if (s.equals(condemnedFormula.getName())) {
				notFound = false;
				while (it.hasNext() && !s.equals(FORMULA_VARIABLES_SEPARATOR)) {
					it.remove();
					s = it.next();
				}
				it.remove(); //to remove the ";"
				break;
			}
			s = it.next();
		}
		if (notFound) {
			return null;
		} else {
			return formulaList;
		}
	}
	
	/**
	 * Prepare a JavaScript script which includes the user-input formula, and which we use
	 * to compute all the values of a time table. The resulting table is returned as a List.
	 * @param dimensions The number of elements for each variable parameter of the formula
	 * @return The list of times computed with the JavaScript engine
	 * @throws ANIMOException
	 */
	//Slightly better than the "other" one from the performance point of view, but not so
	//great if you consider that you MUST use one-line formulae, while the other allows you
	//to use any JavaScript expression which has a double value (you can even define functions
	//etc as long as the evaluation of the last expression you execute returns a number).
	//Still, if you have a JavaScript interpreter that compiles just-in-time, this version
	//should definitely go faster (we are anyway speaking about 1-2 seconds in total for
	//model generation in both cases).
	public List<Double> generateTimes(List<Integer> dimensions) throws ANIMOException {
		int nSpaces = 1, nVariables = 0;
		CyAttributes nodeAttr = Cytoscape.getNodeAttributes();
		List<FormulaVariable> inputReactantsList = new Vector<FormulaVariable>();
		ScriptEngineManager factory = new ScriptEngineManager();
		ScriptEngine engine = factory.getEngineByName("JavaScript");
		Bindings variableValues = engine.createBindings();
		StringBuilder formula = new StringBuilder(),
					  allParametersNamesBuilder = new StringBuilder("");
		String newLine = System.getProperty("line.separator");
		for (FormulaVariable v : variables) {
			if (allParametersNamesBuilder.length() >= 1) {
				allParametersNamesBuilder.append(", ");
			}
			allParametersNamesBuilder.append(v.getName());
			if (v.isParameter()) {
				formula.append("var " + v.getName() + " = " + parameters.get(v.getName()) + ";" + newLine);
			} else {
				formula.append("var " + v.getName() + ";" + newLine);
				nVariables++;
				ReactantParameter r = v.getLinkedValue();
				if (r == null) {
					throw new ANIMOException("In user-defined formula \"" + getName() + "\", the value linked to the variable \"" + v.getName() + "\" is invalid (null).");
				}
				inputReactantsList.add(v);
				String property = r.getPropertyName();
				if (property.equals(Model.Properties.QUANTITY)
					|| property.equals(Model.Properties.ACTIVITY_LEVEL)
					|| property.equals(Model.Properties.INACTIVITY_LEVEL)) {
				
					//TODO: if the quantity of this parameter is not influenced by anybody, the number of values does not need the * 10!!!!!!!
					int maximumGrowthFactor = 1;
					if (nodeAttr.hasAttribute(r.getReactantIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH)) {
						maximumGrowthFactor = nodeAttr.getIntegerAttribute(r.getReactantIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH);
					}
					int size = 1 + maximumGrowthFactor * nodeAttr.getIntegerAttribute(r.getReactantIdentifier(), Model.Properties.NUMBER_OF_LEVELS); //The values of all parameters we consider (quantity, activity, inactivity) range from 0 to 10*granularity
					nSpaces = nSpaces * size;
					dimensions.add(size);
				} else {
					//NOTE: Here we should never pass!
					throw new ANIMOException("The parameter " + v.getName() + " is used with its property " + property + ", which was not considered!");
				}
			}
		}
		String allParametersNames = allParametersNamesBuilder.toString();
		List<Double> allTimes = new Vector<Double>(nSpaces);
		variableValues.put("allTimes", allTimes);
		formula.append("function compute(" + allParametersNames + ") {" + newLine + "\treturn " + javaScriptFormula + ";" + newLine + "}" + newLine + newLine);
		formula.append("function generateTimes() {" + newLine);
		for (int i=0;i<inputReactantsList.size();i++) {
			String v = inputReactantsList.get(i).getName();
			for (int j=0;j<i;j++) {
				formula.append("\t");
			}
			formula.append("\tfor (" + v + "=0;" + v + "<" + dimensions.get(i) + ";" + v + "++) {" + newLine);
		}
		StringBuilder tabsBuilder = new StringBuilder();
		for (int j=0;j<inputReactantsList.size();j++) {
			tabsBuilder.append("\t");
		}
		String tabs = tabsBuilder.toString();
		formula.append(newLine
				+ tabs + "\trate = compute(" + allParametersNames + ");" + newLine 
				+ tabs + "\tif (rate < 1.0E-8) {" + newLine 
				+ tabs + "\t\tallTimes.add(java.lang.Double.POSITIVE_INFINITY);" + newLine
				+ tabs + "\t} else {" + newLine
				+ tabs + "\t\tallTimes.add(1.0 / rate);" + newLine
				+ tabs + "\t}" + newLine);
		for (int i=inputReactantsList.size();i>0;i--) {
			for (int j=0;j<i;j++) {
				formula.append("\t");
			}
			formula.append("}" + newLine);
		}
		formula.append("}" + newLine + newLine + "generateTimes();");
		try {
			engine.eval(formula.toString(), variableValues);
		} catch (Exception ex) {
			throw new ANIMOException("Error while evaluating formula \"" + getName() + "\".", ex);
		}
		return allTimes;
	}
	
	//This may not be extremely good when looking at the performances, mainly because we call a lot of times the evaulation.
	//The currently other one makes only one call, and the performances may be better if you use a JIT engine for JavaScript.
	//This can is better when we take into account that basically ANY JavaScript code would work here,
	//while the other only accepts one-line formulae (it adds a "return " in front of the formula and packs
	//it into a function)
	public List<Double> generateTimesOld(List<Integer> dimensions) throws ANIMOException {
		int nSpaces = 1, nVariables = 0;
		CyAttributes nodeAttr = Cytoscape.getNodeAttributes();
		List<FormulaVariable> inputReactantsList = new Vector<FormulaVariable>();
		ScriptEngineManager factory = new ScriptEngineManager();
		ScriptEngine engine = factory.getEngineByName("JavaScript");
		Bindings variableValues = engine.createBindings();
		for (FormulaVariable v : variables) {
			if (!v.isParameter()) {
				nVariables++;
				ReactantParameter r = v.getLinkedValue();
				if (r == null) {
					throw new ANIMOException("In user-defined formula \"" + getName() + "\", the value linked to the variable \"" + v.getName() + "\" is invalid (null).");
				}
				inputReactantsList.add(v);
				String property = r.getPropertyName();
				if (property.equals(Model.Properties.QUANTITY)
					|| property.equals(Model.Properties.ACTIVITY_LEVEL)
					|| property.equals(Model.Properties.INACTIVITY_LEVEL)) {
				
					//TODO: if the quantity of this parameter is not influenced by anybody, the number of values does not need the * 10!!!!!!!
					int maximumGrowthFactor = 1;
					if (nodeAttr.hasAttribute(r.getReactantIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH)) {
						maximumGrowthFactor = nodeAttr.getIntegerAttribute(r.getReactantIdentifier(), Model.Properties.MAXIMUM_QUANTITY_GROWTH);
					}
					int size = 1 + maximumGrowthFactor * nodeAttr.getIntegerAttribute(r.getReactantIdentifier(), Model.Properties.NUMBER_OF_LEVELS); //The values of all parameters we consider (quantity, activity, inactivity) range from 0 to 10*granularity
					nSpaces = nSpaces * size;
					dimensions.add(size);
				} else {
					
				}
			} else {
				variableValues.put(v.getName(), parameters.get(v.getName())); //Of course, don't forget to instantiate all constant user-defined constants (parameters)
			}
		}
		List<Double> allTimes = new Vector<Double>(nSpaces);
		generateTimes(allTimes, engine, variableValues, inputReactantsList, dimensions, 0);
		return allTimes;
	}
	
	public void generateTimes(List<Double> allTimes, ScriptEngine engine, Bindings variableValues, List<FormulaVariable> inputReactants, List<Integer> dimensions, int fromIndex) throws ANIMOException {
		if (fromIndex > inputReactants.size() - 1) {
			Object result;
			double rate, value;
			try {
				result = engine.eval(javaScriptFormula, variableValues);
				rate = Double.parseDouble(result.toString());
				if (rate > 1e-8) {
					value = 1.0 / rate;
				} else {
					value = Double.POSITIVE_INFINITY;
				}
			} catch (Exception ex) {
				throw new ANIMOException("Error while evaluating formula \"" + getName() + "\".", ex);
			}
			allTimes.add(value);
			return;
		}
		
		FormulaVariable variable = inputReactants.get(fromIndex);
		int min = 0;
		int max = dimensions.get(fromIndex);
		for (int i=min;i<max;i++) {
			variableValues.put(variable.getName(), i);
			generateTimes(allTimes, engine, variableValues, inputReactants, dimensions, fromIndex + 1);
		}
	}
	
	@Override
	public double computeRate(int r1Level, int nLevelsR1, int r2Level, int nLevelsR2, boolean activatingReaction) throws ANIMOException {
		ScriptEngineManager factory = new ScriptEngineManager();
		ScriptEngine engine = factory.getEngineByName("JavaScript");
		Bindings bindi = engine.createBindings();
		for (FormulaVariable v : variables) {
			if (v.isParameter()) {
				bindi.put(v.getName(), parameters.get(v.getName()));
			} else {
				if (v.getLinkedValue() == null) {
					throw new ANIMOException("In user-defined formula \"" + getName() + "\", the value linked to the variable \"" + v.getName() + "\" is invalid (null).");
				} else if (v.getLinkedValue().equals(UPSTREAM_REACTANT_ACTIVITY)) {
					bindi.put(v.getName(), r1Level);
				} else if (v.getLinkedValue().equals(DOWNSTREAM_REACTANT_ACTIVITY)) {
					bindi.put(v.getName(), r2Level);
				} else {
					//UNKNOWN PARAMETER
					System.err.println("Unknown linked variable name: " + v.getName());
				}
			}
		}
		Object result;
		try {
			result = engine.eval(javaScriptFormula, bindi);
		} catch (Exception ex) {
			throw new ANIMOException("Error while evaluating formula \"" + getName() + "\".", ex);
		}
		return Double.parseDouble(result.toString());
	}
	
	@Override
	public String[] listVariableParameters() {
		Vector<String> variableParameters = new Vector<String>();
		for (FormulaVariable v : variables) {
			if (v.isParameter()) {
				variableParameters.add(v.getName());
			}
		}
		return variableParameters.toArray(new String[]{});
	}
	
	@Override
	public String[] listLinkedVariables() {
		Vector<String> linkedVariables = new Vector<String>();
		for (FormulaVariable v : variables) {
			if (!v.isParameter()) {
				linkedVariables.add(v.getName());
			}
		}
		return linkedVariables.toArray(new String[]{});
	}
	
	@Override
	public void setLinkedVariable(String name, ReactantParameter linkedVariable) {
		for (FormulaVariable v : variables) {
			if (name.equals(v.getName())) {
				v.setLinkedValue(linkedVariable);
			}
		}
	}
	
	@Override
	public ReactantParameter getLinkedVariable(String name) {
		for (FormulaVariable v : variables) {
			if (name.equals(v.getName())) {
				return v.getLinkedValue();
			}
		}
		return null;
	}
	
	@Override
	public JLabel getFormulaLabel() {
		return new JLabel(javaScriptFormula);
	}
	
	@Override
	public Double getDefaultParameterValue(String name) {
		Double value = 1.0;
		for (FormulaVariable v : variables) {
			if (v.getName().equals(name)) {
				value = v.getDefaultParameterValue();
				break;
			}
		}
		return value;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public String getName() {
		return name;
	}
	
	public String getFormula() {
		return javaScriptFormula;
	}
	
	public Vector<FormulaVariable> getVariables() {
		return variables;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setFormula(String javaScriptFormula) {
		this.javaScriptFormula = javaScriptFormula;
	}
	
	public void setVariables(Vector<FormulaVariable> variables) {
		this.variables = variables;
	}
	
}

package animo.model;
import java.util.*;

/**
 * A single-reactant version of Scenario.
 * It has only one scenario, with one parameter.
 */
public class ScenarioMono extends Scenario {
	private static final String SCENARIO_ONLY_PARAMETER = Model.Properties.SCENARIO_ONLY_PARAMETER;
	//protected HashMap<String, Double> parameters = new HashMap<String, Double>();
	
	public ScenarioMono() {
		setDefaultParameterValue(SCENARIO_ONLY_PARAMETER, 0.1);
	}
	
	/*@Override
	public void setParameter(String name, Double value) {
		parameters.put(name, value);
	}
	
	@Override
	public Double getParameter(String name) {
		return parameters.get(name);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public HashMap<String, Double> getParameters() {
		return (HashMap<String, Double>)parameters.clone();
	}*/
	
	public double computeRate(int reactantLevel) {
		double param = parameters.get(SCENARIO_ONLY_PARAMETER);
		double rate = param * reactantLevel;
		return rate;
	}
	
	public double computeFormula(int reactantLevel) {
		double rate = computeRate(reactantLevel);
		if (rate > 1e-8) {
			//return Math.max(1, (int)Math.round(1 / rate)); //We need to put at least 1 because otherwise the reaction will keep happening forever (it is not very nice not to let time pass..)
			return 1.0 / rate;
		} else {
			//return Scenario.INFINITE_TIME;
			return Double.POSITIVE_INFINITY;
		}
	}

	public List<Double> generateTimes(int nLevels) {
		List<Double> times = new LinkedList<Double>();
		times.add(Double.POSITIVE_INFINITY); //if reactant is already completely inactive, no reaction
		for (int i=1;i<nLevels;i++) {
			times.add(computeFormula(i));
		}
		return times;
	}
	
	@Override
	public String[] listVariableParameters() {
		return new String[]{SCENARIO_ONLY_PARAMETER};
	}
	
	@Override
	public String toString() {
		return "Scenario";
	}
}

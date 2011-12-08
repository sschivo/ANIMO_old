package animo.model;

public class FormulaVariable {
	public static final String LINK_SYMBOL = " -> ",
							   DEF_VAL_SYMBOL = " = ";
	
	private String name;
	private boolean parameter;
	private ReactantParameter linkedValue;
	private double defaultParameterValue;
	
	public FormulaVariable(String parsethis) { //Read from the list format in the network property
		if (parsethis.contains(LINK_SYMBOL)) {
			name = parsethis.substring(0, parsethis.indexOf(LINK_SYMBOL));
			parameter = false;
			linkedValue = new ReactantParameter(parsethis.substring(parsethis.indexOf(LINK_SYMBOL) + LINK_SYMBOL.length()));
		} else if (parsethis.contains(DEF_VAL_SYMBOL)) {
			name = parsethis.substring(0, parsethis.indexOf(DEF_VAL_SYMBOL));
			parameter = true;
			linkedValue = null;
			defaultParameterValue = Double.parseDouble(parsethis.substring(parsethis.indexOf(DEF_VAL_SYMBOL) + DEF_VAL_SYMBOL.length()));
		} else {
			name = parsethis;
			parameter = true;
			linkedValue = null;
			defaultParameterValue = 1.0;
		}
	}
	
	public String toString() { //Write into the list format in the network property
		String res = name;
		if (!parameter) {
			res = res + LINK_SYMBOL + linkedValue; 
		} else {
			res = res + DEF_VAL_SYMBOL + defaultParameterValue;
		}
		return res;
	}
	
	public FormulaVariable(String name, boolean parameter, ReactantParameter linkedValue, double defaultParameterValue) {
		this.name = name;
		this.parameter = parameter;
		this.linkedValue = linkedValue;
		this.defaultParameterValue = defaultParameterValue;
	}
	
	public FormulaVariable(FormulaVariable other) {
		this.name = other.name;
		this.parameter = other.parameter;
		this.linkedValue = other.linkedValue;
		this.defaultParameterValue = other.defaultParameterValue;
	}
	
	public String getName() {
		return name;
	}
	
	public boolean isParameter() {
		return parameter;
	}
	
	public ReactantParameter getLinkedValue() {
		return linkedValue;
	}
	
	public double getDefaultParameterValue() {
		return defaultParameterValue;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setParameter(boolean parameter) {
		this.parameter = parameter;
	}
	
	public void setLinkedValue(ReactantParameter linkedValue) {
		this.linkedValue = linkedValue;
	}
	
	public void setDefaultParameterValue(double defaultParameterValue) {
		this.defaultParameterValue = defaultParameterValue;
	}
}

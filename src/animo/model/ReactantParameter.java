package animo.model;


/**
 * This class represents a particular property of a reactant, and is intended to be used
 * as a parameter for a reaction
 */
public class ReactantParameter {
	
	public static final String REACTANT_SEPARATOR_SYMBOL = ".";
	
	private String reactantIdentifier,
				   propertyName; //We should limit ourselves to properties like quantity and activity level
	
	public ReactantParameter(String parsethis) {
		if (parsethis.contains(REACTANT_SEPARATOR_SYMBOL)) {
			this.reactantIdentifier = parsethis.substring(0, parsethis.indexOf(REACTANT_SEPARATOR_SYMBOL));
			this.propertyName = parsethis.substring(parsethis.indexOf(REACTANT_SEPARATOR_SYMBOL) + REACTANT_SEPARATOR_SYMBOL.length());
		} else {
			this.reactantIdentifier = parsethis;
			this.propertyName = "";
		}
	}
	
	public ReactantParameter(String reactantIdentifier, String propertyName) {
		this.reactantIdentifier = reactantIdentifier;
		this.propertyName = propertyName;
	}
	
	public String toString() {
		return this.reactantIdentifier + REACTANT_SEPARATOR_SYMBOL + this.propertyName;
	}
	
	public String getReactantIdentifier() {
		return this.reactantIdentifier;
	}
	
	public String getPropertyName() {
		return this.propertyName;
	}
	
	/*
	 *TODO: We should not use this: the property names are (for the moment) different from the actual "useful" values.
	 * For example, Model.Properties.ACTIVITY_LEVEL is not the same as Model.Properties.INITIAL_LEVEL (even if they should be the same..)
	 * So I cannot ask the system to give me the value of ACTIVITY_LEVEL because it will probably not exist
	public Object getPropertyValue() {
		return Cytoscape.getNodeAttributes().getAttribute(reactantIdentifier, propertyName);
	}
	*/
	
	public void setReactantIdentifier(String reactantIdentifier) {
		this.reactantIdentifier = reactantIdentifier;
	}
	
	public void setPropertyName(String propertyName) {
		this.propertyName = propertyName;
	}
}

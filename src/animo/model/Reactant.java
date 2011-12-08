/**
 * 
 */
package animo.model;

import java.io.Serializable;

/**
 * A vertex is a component in the network.
 * 
 * @author B. Wanders
 */
public class Reactant extends Entity implements Serializable {
	private static final long serialVersionUID = -8610211944385660028L;

	/**
	 * Constructor.
	 * 
	 * @param id the vertex id
	 */
	public Reactant(String id) {
		super(id);
	}

	@Override
	public String toString() {
		return "Reactant '" + this.getId() + "'";
	}
}

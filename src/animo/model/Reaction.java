/**
 * 
 */
package animo.model;

import java.io.Serializable;

/**
 * A connection between two components in the network.
 * 
 * @author B. Wanders
 */
public class Reaction extends Entity implements Serializable {
	private static final long serialVersionUID = 6737480973051526963L;

	/**
	 * Constructor.
	 * 
	 * @param id the identifier for this edge
	 */
	public Reaction(String id) {
		super(id);
	}

	@Override
	public String toString() {
		return "Reaction '" + this.getId() + "'";
	}
}

/**
 * 
 */
package animo.serializer;


import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import animo.exceptions.SerializationException;
import animo.util.AXPathExpression;
import animo.util.XmlEnvironment;

/**
 * The {@link Boolean} serializer.
 * 
 * @author B. Wanders
 */
public class BooleanSerializer implements TypeSerializer<Boolean> {
	/**
	 * Value pattern.
	 */
	private final AXPathExpression expression = XmlEnvironment.hardcodedXPath(".");

	@Override
	public Boolean deserialize(Node root) throws SerializationException {
		try {
			return new Boolean(this.expression.getString(root));
		} catch (XPathExpressionException e) {
			throw new SerializationException("Could not deserialize, expression " + this.expression.toString()
					+ " did not match.", e);
		}
	}

	@Override
	public Node serialize(Document doc, Object value) {
		return doc.createTextNode(value.toString());
	}

}

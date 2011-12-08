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
 * The {@link Integer} serializer.
 * 
 * @author B. Wanders
 */
public class IntegerSerializer implements TypeSerializer<Integer> {
	/**
	 * Value pattern.
	 */
	private final AXPathExpression expression = XmlEnvironment.hardcodedXPath(".");

	@Override
	public Integer deserialize(Node root) throws SerializationException {
		String value = null;
		try {
			value = this.expression.getString(root);
			return new Integer(value);
		} catch (XPathExpressionException e) {
			throw new SerializationException("Could not deserialize, expression " + this.expression.toString()
					+ " did not match.", e);
		} catch (NumberFormatException e) {
			throw new SerializationException("Could not interpret value '" + value + "' as an integer.", e);
		}
	}

	@Override
	public Node serialize(Document doc, Object value) {
		return doc.createTextNode(value.toString());
	}

}

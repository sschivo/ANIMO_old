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
 * The {@link Float} serializer.
 * 
 * @author B. Wanders
 */
public class FloatSerializer implements TypeSerializer<Float> {
	/**
	 * Value pattern.
	 */
	private final AXPathExpression expression = XmlEnvironment.hardcodedXPath(".");

	@Override
	public Float deserialize(Node root) throws SerializationException {
		String value = null;
		try {
			value = this.expression.getString(root);
			return new Float(value);
		} catch (XPathExpressionException e) {
			throw new SerializationException("Could not deserialize, expression " + this.expression.toString()
					+ " did not match.", e);
		} catch (NumberFormatException e) {
			throw new SerializationException("Could not interpret value '" + value + "' as an float.", e);
		}
	}

	@Override
	public Node serialize(Document doc, Object value) {
		return doc.createTextNode(value.toString());
	}

}

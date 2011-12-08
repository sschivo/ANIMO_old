package animo.analyser.uppaal;


import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import animo.exceptions.ANIMOException;
import animo.model.Model;
import animo.model.Property;
import animo.model.Reactant;
import animo.model.ReactantParameter;
import animo.model.Reaction;
import animo.util.Table;

/**
 * Produces an UPPAAL model to be used with the UPPAAL SMC engine.
 * For comments on what the different functions do, refer to the VariablesModel class.
 */
public class VariablesModelSMC extends VariablesModel {

	private static final String REACTANT_INDEX = Model.Properties.REACTANT_INDEX,
								CYTOSCAPE_ID = Model.Properties.CYTOSCAPE_ID,
								USER_DEFINED_FORMULA = Model.Properties.USER_DEFINED_FORMULAE;
	private Map<String, String> fromCytoscapeIdtoModelId = new HashMap<String, String>();
	
	@Override
	protected void appendModel(StringBuilder out, Model m) {
		try {
			out.append("<?xml version='1.0' encoding='utf-8'?>");
			out.append(newLine);
			out.append("<!DOCTYPE nta PUBLIC '-//Uppaal Team//DTD Flat System 1.1//EN' 'http://www.it.uu.se/research/group/darts/uppaal/flat-1_1.dtd'>");
			out.append(newLine);
			out.append("<nta>");
			out.append(newLine);
			out.append("<declaration>");
			out.append(newLine);
			
			// output global declarations
			out.append("// Place global declarations here.");
			out.append(newLine);
			out.append("clock globalTime;");
			out.append(newLine);
			out.append("const int INFINITE_TIME = " + INFINITE_TIME + ";");
			out.append(newLine);
			int countReactants = 0;
			for (Reactant r : m.getReactants()) {
				if (r.get(ENABLED).as(Boolean.class)) {
					countReactants++;
				}
			}
			out.append("const int N_REACTANTS = " + countReactants + ";");
			out.append(newLine);
			out.append("broadcast chan reaction_happening[N_REACTANTS];");
			out.append(newLine);
			out.append(newLine);
			
			int reactantIndex = 0;
			for (Reactant r : m.getReactants()) {
				if (!r.get(ENABLED).as(Boolean.class)) continue;
				r.let(REACTANT_INDEX).be(reactantIndex);
				fromCytoscapeIdtoModelId.put(r.get(CYTOSCAPE_ID).as(String.class), r.getId());
				reactantIndex++; 
				this.appendReactantVariables(out, r);
			}
			m.getProperties().let(Model.Properties.CYTOSCAPE_TO_MODEL_NAMES_MAP).be(fromCytoscapeIdtoModelId);
			
			out.append(newLine);
			out.append("//Round the number a to integer (a is on a scale 10 times larger, to include 1 decimal digit. Thus, to represent 1.5 a contains 15)"
					+ "\nint round(int a) {"
					+ "\n\tint res = a / 10;"
					+ "\n\tint m = a % 10;"
					+ "\n\tif (m > 4) {"
					+ "\n\t\tres = res + 1;"
					+ "\n\t}"
					+ "\n\treturn res;"
					+ "\n}\n\n");
			out.append("//What percentage is a with respect to b?"
					+ "\nint percentage(int a, int b) {"
					+ "\n\tif (b == 0) return 0;"
					+ "\n\telse return 10 * round(a * 100 * 10 / b);"
					+ "\n}\n");
			out.append("</declaration>");
			
			out.append(newLine);
			out.append(newLine);
			// output templates
			this.appendTemplates(out, m);
			
			out.append(newLine);
			out.append("<system>");
			out.append(newLine);
			
			int reactionIndex = 0;
			for (Reaction r : m.getReactions()) {
				if (!r.get(ENABLED).as(Boolean.class)) continue;
				this.appendReactionProcesses(out, m, r, reactionIndex);
				reactionIndex++;
			}
			out.append(newLine);
			out.append(newLine);
	
			//out.append("Crono = crono();");
			out.append(newLine);
			out.append(newLine);
			
			// compose the system
			out.append("system ");
			Iterator<Reaction> iter = m.getReactions().iterator();
			boolean first = true;
			while (iter.hasNext()) {
				Reaction r = iter.next();
				if (!r.get(ENABLED).as(Boolean.class)) continue;
				if (!first) {
					out.append(", ");
				}
				out.append(getReactionName(r));
				first = false;
			}
			//out.append(", Crono;");
			out.append(";");
			
			out.append(newLine);
			out.append(newLine);
			out.append("</system>");
			out.append(newLine);
			out.append("</nta>");
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			System.err.println(out.toString());
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void appendReactionProcesses(StringBuilder out, Model m, Reaction r, int index) {
		//NOTICE THAT index IS NOT USED HERE!!
		//We used it in the VariablesModel class, and just to maintain the same form, we still take it here, even if it is never used.
		index = -1;
		
		if (r.get(REACTION_TYPE).as(String.class).equals(MONO_REACTION)) {
			String reactantId = r.get(REACTANT).as(String.class);
			out.append("//Mono-reaction on " + reactantId + " (" + m.getReactant(reactantId).get(ALIAS).as(String.class) + ")");
			out.append(newLine);
			
			Table timesL, timesU;
			Property property = r.get(TIMES_LOWER);
			if (property != null) {
				timesL = property.as(Table.class);
			} else {
				timesL = r.get(TIMES).as(Table.class);
			}
			property = r.get(TIMES_UPPER);
			if (property != null) {
				timesU = property.as(Table.class);
			} else {
				timesU = r.get(TIMES).as(Table.class);
			}
			assert timesL.getColumnCount() == 1 : "Table LowerBound is (larger than one)-dimensional.";
			assert timesU.getColumnCount() == 1 : "Table UpperBound is (larger than one)-dimensional.";
			assert timesL.getRowCount() == m.getReactant(reactantId).get(NUMBER_OF_LEVELS).as(Integer.class) + 1 : "Incorrect number of rows in 'timesLower' table of '" + r + "'";
			assert timesU.getRowCount() == m.getReactant(reactantId).get(NUMBER_OF_LEVELS).as(Integer.class) + 1 : "Incorrect number of rows in 'timesUpper' table of '" + r + "'";
			
			// output times table constants for this reaction (lower bound)
			out.append("const int " + reactantId + "_tLower[" + m.getReactant(reactantId).get(NUMBER_OF_LEVELS).as(Integer.class) + "+1] := {");
			for (int i = 0; i < timesL.getRowCount() - 1; i++) {
				out.append(formatTime(timesL.get(i, 0)) + ", ");
			}
			out.append(formatTime(timesL.get(timesL.getRowCount() - 1, 0)) + "};");
			out.append(newLine);
			
			// output times table constants for this reaction (upper bound)
			out.append("const int " + reactantId + "_tUpper[" + m.getReactant(reactantId).get(NUMBER_OF_LEVELS).as(Integer.class) + "+1] := {");
			for (int i = 0; i < timesU.getRowCount() - 1; i++) {
				out.append(formatTime(timesU.get(i, 0)) + ", ");
			}
			out.append(formatTime(timesU.get(timesU.getRowCount() - 1, 0)) + "};");
			out.append(newLine);

			// output reaction instantiation
			final String name = getReactionName(r);
			out.append(name + " = Reaction_" + reactantId + "(" + reactantId + ACTIVITY_SUFFIX + ", " + reactantId + "_tLower, "
					+ reactantId + "_tUpper, " + r.get(INCREMENT).as(Integer.class) + ", reaction_happening[" + m.getReactant(reactantId).get(REACTANT_INDEX).as(Integer.class) + "]);");
			out.append(newLine);
			out.append(newLine);

		} else if (r.get(REACTION_TYPE).as(String.class).equals(BI_REACTION)) {
			if (r.get(USER_DEFINED_FORMULA).as(Boolean.class)) {
				List<Integer> dimensions = r.get(DIMENSIONS).as(List.class), //The matrix of time constants is multi-dimensional: we need to output it with care, or all reality will collapse!
							  timesL = r.get(TIMES_LOWER).as(List.class),
							  timesU = r.get(TIMES_UPPER).as(List.class);
				List<String> influencedReactantsRead = r.get(INFLUENCED_REACTANTS).as(List.class);
				List<ReactantParameter> influencedReactants = new Vector<ReactantParameter>();
				for (String s : influencedReactantsRead) {
					influencedReactants.add(new ReactantParameter(s));
				}
				List<Integer> influenceValues = r.get(INFLUENCE_VALUES).as(List.class);
				List<ReactantParameter> influencingReactants = r.get(INFLUENCING_REACTANTS).as(List.class);
				int nInput = dimensions.size(),
					nOutput = influencedReactants.size();
				
				String r1Id = r.get(CATALYST).as(String.class);
				String r2Id = r.get(REACTANT).as(String.class);
				out.append("//Reaction " + r1Id + " (" + m.getReactant(r1Id).get(ALIAS).as(String.class) + ") " + (r.get(INCREMENT).as(Integer.class)>0?"-->":"--|") + " " + r2Id + " (" + m.getReactant(r2Id).get(ALIAS).as(String.class) + ")");
				out.append(newLine);
				
				out.append("const int " + r1Id + "_" + r2Id + "_r_tLower[");
				for (int i=0;i<dimensions.size() - 1;i++) {
					int d = dimensions.get(i);
					out.append("" + d + "][");
				}
				out.append("" + dimensions.get(dimensions.size() - 1) + "] := ");
				printMatrix(out, timesL, dimensions, 0, 0);
				out.append(";");
				out.append(newLine);
				
				out.append("const int " + r1Id + "_" + r2Id + "_r_tUpper[");
				for (int i=0;i<dimensions.size() - 1;i++) {
					int d = dimensions.get(i);
					out.append("" + d + "][");
				}
				out.append("" + dimensions.get(dimensions.size() - 1) + "] := ");
				printMatrix(out, timesU, dimensions, 0, 0);
				out.append(";");
				out.append(newLine);
				out.append(newLine);
				
				// output process instantiation
				final String name = getReactionName(r);
				/*out.append(name + " = Reaction2_" + r1Id + "_" + r2Id + "(" + r1Id + ", " + r2Id + ", " + r1Id + "_" + r2Id
						+ "_r_tLower, " + r1Id + "_" + r2Id + "_r_tUpper, " + r.get(INCREMENT).as(Integer.class)
						+ ", reaction_happening[" + m.getReactant(r1Id).get(REACTANT_INDEX).as(Integer.class) + "], reaction_happening[" + m.getReactant(r2Id).get(REACTANT_INDEX).as(Integer.class) + "]);");*/
				out.append(name + " = Reaction2_" + r1Id + "_" + r2Id + "(");
				for (int i=0;i<nInput;i++) {
					if (i > 0) {
						out.append(", ");
					}
					String id = fromCytoscapeIdtoModelId.get(influencingReactants.get(i).getReactantIdentifier());
					out.append(id + QUANTITY_SUFFIX + ", " + id + ACTIVITY_SUFFIX);
				}
				for (int i=0;i<nOutput;i++) {
					String id = fromCytoscapeIdtoModelId.get(influencedReactants.get(i).getReactantIdentifier());
					out.append(", " + id + QUANTITY_SUFFIX + ", " + id + ACTIVITY_SUFFIX + ", " + id + MAX_QUANTITY_SUFFIX + ", " + id + PERCENTAGE_SUFFIX);
				}
				out.append(", " + r1Id + "_" + r2Id + "_r_tLower, " + r1Id + "_" + r2Id + "_r_tUpper");
				for (int i=0;i<nOutput;i++) {
					out.append(", " + influenceValues.get(i));
				}
				for (int i=0;i<nInput;i++) {
					out.append(", reaction_happening[" + m.getReactant(fromCytoscapeIdtoModelId.get(influencingReactants.get(i).getReactantIdentifier())).get(REACTANT_INDEX).as(Integer.class) + "]");
				}
				for (int i=0;i<nOutput;i++) {
					out.append(", reaction_happening[" + m.getReactant(fromCytoscapeIdtoModelId.get(influencedReactants.get(i).getReactantIdentifier())).get(REACTANT_INDEX).as(Integer.class) + "]");
				}
				out.append(");");
				out.append(newLine);
				out.append(newLine);
			} else {
				String r1Id = r.get(CATALYST).as(String.class);
				String r2Id = r.get(REACTANT).as(String.class);
				out.append("//Reaction " + r1Id + " (" + m.getReactant(r1Id).get(ALIAS).as(String.class) + ") " + (r.get(INCREMENT).as(Integer.class)>0?"-->":"--|") + " " + r2Id + " (" + m.getReactant(r2Id).get(ALIAS).as(String.class) + ")");
				out.append(newLine);
				
				Table timesL, timesU;
				Property property = r.get(TIMES_LOWER);
				if (property != null) {
					timesL = property.as(Table.class);
				} else {
					timesL = r.get(TIMES).as(Table.class);
				}
				property = r.get(TIMES_UPPER);
				if (property != null) {
					timesU = property.as(Table.class);
				} else {
					timesU = r.get(TIMES).as(Table.class);
				}
	
				assert timesL.getRowCount() == m.getReactant(r2Id).get(NUMBER_OF_LEVELS).as(Integer.class) + 1 : "Incorrect number of rows in 'times lower' table of '" + r + "'.";
				assert timesU.getRowCount() == m.getReactant(r2Id).get(NUMBER_OF_LEVELS).as(Integer.class) + 1 : "Incorrect number of rows in 'times upper' table of '" + r + "'.";
				assert timesL.getColumnCount() == m.getReactant(r1Id).get(NUMBER_OF_LEVELS).as(Integer.class) + 1 : "Incorrect number of columns in 'times lower' table of '" + r + "'.";
				assert timesU.getColumnCount() == m.getReactant(r1Id).get(NUMBER_OF_LEVELS).as(Integer.class) + 1 : "Incorrect number of columns in 'times upper' table of '" + r + "'.";
				
				List<Integer> dimensions = r.get(DIMENSIONS).as(List.class);
				if (dimensions.size() < 2) {
					System.err.println("ERROR! The number of dimensions for the reaction " + r.get(CATALYST).as(String.class) + ((r.get(INCREMENT).as(Integer.class) > 0)?" --> ":" --| ") + r.get(REACTANT).as(String.class) + " is not 2 as expected.");
				}
				int nLevelsR1 = dimensions.get(0),
					nLevelsR2 = dimensions.get(1);
				
				// output times table constant for this reaction
				out.append("const int " + r1Id + "_" + r2Id + "_r_tLower[" + nLevelsR2 + "][" + nLevelsR1 + "] := {");
				out.append(newLine);
				
				// for each row
				for (int row = 0; row < timesL.getRowCount(); row++) {
					out.append("\t\t{");
					
					// for each column
					for (int col = 0; col < timesL.getColumnCount(); col++) {
						out.append(formatTime(timesL.get(row, col)));
						
						// seperate value with a comma if it is not the last one
						if (col < timesL.getColumnCount() - 1) {
							out.append(", ");
						}
					}
					out.append("}");
	
					// end row line with a comma if it is not the last one
					if (row < timesL.getRowCount() - 1) {
						out.append(",");
					}
					out.append(newLine);
				}
	
				out.append("};");
				out.append(newLine);
				
				// output times table constant for this reaction
				out.append("const int " + r1Id + "_" + r2Id + "_r_tUpper[" + nLevelsR2 + "][" + nLevelsR1 + "] := {");
				out.append(newLine);
	
				// for each row
				for (int row = 0; row < timesU.getRowCount(); row++) {
					out.append("\t\t{");
	
					// for each column
					for (int col = 0; col < timesU.getColumnCount(); col++) {
						out.append(formatTime(timesU.get(row, col)));
	
						// seperate value with a comma if it is not the last one
						if (col < timesU.getColumnCount() - 1) {
							out.append(", ");
						}
					}
					out.append("}");
	
					// end row line with a comma if it is not the last one
					if (row < timesU.getRowCount() - 1) {
						out.append(",");
					}
					out.append(newLine);
				}
	
				out.append("};");
				out.append(newLine);
				out.append(newLine);
	
				// output process instantiation
				final String name = getReactionName(r);
				out.append(name + " = Reaction2_" + r1Id + "_" + r2Id + "(" + r1Id + ACTIVITY_SUFFIX + ", " + r2Id + ACTIVITY_SUFFIX + ", " + r2Id + QUANTITY_SUFFIX + ", " + r1Id + "_" + r2Id
						+ "_r_tLower, " + r1Id + "_" + r2Id + "_r_tUpper, " + r.get(INCREMENT).as(Integer.class)
						+ ", reaction_happening[" + m.getReactant(r1Id).get(REACTANT_INDEX).as(Integer.class) + "], reaction_happening[" + m.getReactant(r2Id).get(REACTANT_INDEX).as(Integer.class) + "]);");
				out.append(newLine);
				out.append(newLine);
			}
		}
	}


	/**
	 * Print the contents of the n-dimensional linearized matrix times
	 * @param out Where to print the matrix (will be output as UPPAAL file)
	 * @param times The n-dimensional matrix containing all the reaction times for all the possible values of the reactant properties on which the considered reaction depends
	 * @param dimensions For each of the n dimensions of the matrix, tells us how many elements that dimension has
	 * @param timesIndex Starting point inside the linearized matrix: we print elements from this point on
	 * @param dimIndex Index of the dimension we are currently printing
	 * @return The number of elements that where printed
	 */
	private int printMatrix(StringBuilder out, List<Integer> times, List<Integer> dimensions, int timesIndex, int dimIndex) {
		if (dimIndex > dimensions.size() - 1) { //If we have to print a single element, print it
			int value = times.get(timesIndex);
			out.append(formatTime(value));
			return 1;
		}
		//Otherwise, we have to print a number of elements corresponding to the number of elements in the current dimension (at index dimIndex) of the matrix
		int min = 0,
			max = dimensions.get(dimIndex),
			nPrinted = 0;
		out.append("{");
		for (int i=min;i<max;i++) {
			nPrinted += printMatrix(out, times, dimensions, timesIndex + nPrinted, dimIndex + 1); //Count the already printed elements, so that we always print new elements
			if (i < max - 1) { //The last element of the current dimension will end with a "}", thus it does not need the "," after it
				out.append(", ");
			}
			if (dimIndex + 1 < dimensions.size()) { //If the element we have printed was not a single one (see the base case of the function), start a new line for the next element
				out.append(newLine);
			}
		}
		out.append("}");
		return nPrinted;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void appendTemplates(StringBuilder out, Model m) {
		try {
			StringWriter outString;
			DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document document;
			Transformer tra = TransformerFactory.newInstance().newTransformer();
			tra.setOutputProperty(OutputKeys.INDENT, "yes");
			tra.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			tra.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			for (Reaction r : m.getReactions()) {
				if (!r.get(ENABLED).as(Boolean.class)) continue;
				outString = new StringWriter();
				if (r.get(REACTION_TYPE).as(String.class).equals(BI_REACTION)) {
					if (r.get(USER_DEFINED_FORMULA).as(Boolean.class)) {
						int nInput, nOutput;
						List<Integer> dimensions = r.get(DIMENSIONS).as(List.class);
						List<String> influencedReactants = r.get(INFLUENCED_REACTANTS).as(List.class);
						List<ReactantParameter> influencingReactants = r.get(INFLUENCING_REACTANTS).as(List.class);
						nInput = dimensions.size();
						nOutput = influencedReactants.size();
						StringBuilder reactionTemplate = new StringBuilder("<template><name x=\"5\" y=\"5\">Reaction2_" + r.get(CATALYST).as(String.class) + "_" + r.get(REACTANT).as(String.class) + "</name><parameter>");
						StringBuilder matrixDimensionsListBuilder = new StringBuilder("["),
									  matrixIndicesListBuilder = new StringBuilder("["),
									  matrixIndicesLocalListBuilder = new StringBuilder("[");
						for (int i=0;i<dimensions.size() - 1;i++) {
							matrixDimensionsListBuilder.append("" + dimensions.get(i) + "][");
							matrixIndicesListBuilder.append("input_reactant" + (i + 1));
							matrixIndicesLocalListBuilder.append("input_reactant_o" + (i + 1));
							String property = influencingReactants.get(i).getPropertyName();
							if (property.equals(Model.Properties.ACTIVITY_LEVEL)) {
								matrixIndicesListBuilder.append(ACTIVITY_SUFFIX);
								matrixIndicesLocalListBuilder.append(ACTIVITY_SUFFIX);
							} else if (property.equals(Model.Properties.QUANTITY)) {
								matrixIndicesListBuilder.append(QUANTITY_SUFFIX);
								matrixIndicesLocalListBuilder.append(QUANTITY_SUFFIX);
							} else if (property.equals(Model.Properties.INACTIVITY_LEVEL)) {
								matrixIndicesListBuilder.append(QUANTITY_SUFFIX + " - input_reactant" + (i + 1) + ACTIVITY_SUFFIX);
								matrixIndicesLocalListBuilder.append(QUANTITY_SUFFIX + " - input_reactant_o" + (i + 1) + ACTIVITY_SUFFIX);
							}
							matrixIndicesListBuilder.append("][");
							matrixIndicesLocalListBuilder.append("][");
						}
						matrixDimensionsListBuilder.append("" + dimensions.get(dimensions.size() - 1));
						matrixIndicesListBuilder.append("input_reactant" + dimensions.size());
						matrixIndicesLocalListBuilder.append("input_reactant_o" + dimensions.size());
						String property = influencingReactants.get(dimensions.size() - 1).getPropertyName();
						if (property.equals(Model.Properties.ACTIVITY_LEVEL)) {
							matrixIndicesListBuilder.append(ACTIVITY_SUFFIX);
							matrixIndicesLocalListBuilder.append(ACTIVITY_SUFFIX);
						} else if (property.equals(Model.Properties.QUANTITY)) {
							matrixIndicesListBuilder.append(QUANTITY_SUFFIX);
							matrixIndicesLocalListBuilder.append(QUANTITY_SUFFIX);
						} else if (property.equals(Model.Properties.INACTIVITY_LEVEL)) {
							matrixIndicesListBuilder.append(QUANTITY_SUFFIX + " - input_reactant" + dimensions.size() + ACTIVITY_SUFFIX);
							matrixIndicesLocalListBuilder.append(QUANTITY_SUFFIX + " - input_reactant_o" + dimensions.size() + ACTIVITY_SUFFIX);
						}
						matrixDimensionsListBuilder.append("]");
						matrixIndicesListBuilder.append("]");
						matrixIndicesLocalListBuilder.append("]");
						String matrixDimensionsList = matrixDimensionsListBuilder.toString(),
							   matrixIndicesList = matrixIndicesListBuilder.toString(),
							   matrixIndicesLocalList = matrixIndicesLocalListBuilder.toString();
						for (int i=0;i<nInput;i++) {
							if (i > 0) {
								reactionTemplate.append(", ");
							}
							reactionTemplate.append("int &amp;input_reactant" + (i+1) + QUANTITY_SUFFIX + ", int &amp;input_reactant" + (i+1) + ACTIVITY_SUFFIX);
						}
						for (int i=0;i<nOutput;i++) {
							reactionTemplate.append(", int &amp;output_reactant" + (i+1) + QUANTITY_SUFFIX + ", int &amp;output_reactant" + (i+1) + ACTIVITY_SUFFIX + ", int &amp;output_reactant" + (i+1) + MAX_QUANTITY_SUFFIX + ", int &amp;output_reactant" + (i + 1) + PERCENTAGE_SUFFIX);
						}
						reactionTemplate.append(", const int timeL" + matrixDimensionsList);
						reactionTemplate.append(", const int timeU" + matrixDimensionsList);
						for (int i=0;i<nOutput;i++) {
							reactionTemplate.append(", const int delta" + (i + 1));
						}
						for (int i=0;i<nInput;i++) {
							reactionTemplate.append(", broadcast chan &amp;input" + (i + 1) + "_reacting");
						}
						for (int i=0;i<nOutput;i++) {
							reactionTemplate.append(", broadcast chan &amp;output" + (i + 1) + "_reacting");
						}
						reactionTemplate.append("</parameter><declaration>// Place local declarations here.\nclock c;\nint ");
						for (int i=0;i<nInput;i++) {
							if (i > 0) {
								reactionTemplate.append(", ");
							}
							reactionTemplate.append("input_reactant_o" + (i+1) + ACTIVITY_SUFFIX);
							reactionTemplate.append(", input_reactant_o" + (i+1) + QUANTITY_SUFFIX);
						}
						reactionTemplate.append(";\n\n");
						for (int i=0;i<nOutput;i++) { //We don't need a special function to respect the limits of the output reactants, as these checks are done elsewhere by the areWeAtTheLimits function. We need instead to make sure that the percentage is ok
							property = new ReactantParameter(influencedReactants.get(i)).getPropertyName();
							if (property.equals(Model.Properties.ACTIVITY_LEVEL)) {
								reactionTemplate.append("void updateReactant" + (i + 1) + "() {"
										+ "\n\toutput_reactant" + (i + 1) + ACTIVITY_SUFFIX + " := output_reactant" + (i + 1) + ACTIVITY_SUFFIX + " + delta" + (i + 1) + ";"
										+ "\n\toutput_reactant" + (i + 1) + PERCENTAGE_SUFFIX + " := percentage(output_reactant" + (i + 1) + ACTIVITY_SUFFIX + ", output_reactant" + (i + 1) + QUANTITY_SUFFIX + ");"
										+ "\n}\n\n");
							} else if (property.equals(Model.Properties.QUANTITY)) {
								reactionTemplate.append("void updateReactant" + (i + 1) + "() {"
										+ "\n\toutput_reactant" + (i + 1) + QUANTITY_SUFFIX + " := output_reactant" + (i + 1) + QUANTITY_SUFFIX + " + delta" + (i + 1) + ";"
										+ "\n\tif (delta" + (i + 1) + " &lt; 0) {"
										+ "\n\t\toutput_reactant" + (i + 1) + ACTIVITY_SUFFIX + " := round(output_reactant" + (i + 1) + QUANTITY_SUFFIX + " * output_reactant" + (i + 1) + PERCENTAGE_SUFFIX + " / 100);"
										+ "\n\t}"
										+ "\n\toutput_reactant" + (i + 1) + PERCENTAGE_SUFFIX + " := percentage(output_reactant" + (i + 1) + ACTIVITY_SUFFIX + ", output_reactant" + (i + 1) + QUANTITY_SUFFIX + ");"
										+ "\n}\n\n");
							}
						}
						reactionTemplate.append("bool areWeAtTheLimits() {"
											  + "\n\tint res;\n");
						for (int i=0;i<nOutput;i++) {
							property = new ReactantParameter(influencedReactants.get(i)).getPropertyName();
							if (property.equals(Model.Properties.ACTIVITY_LEVEL)) {
								reactionTemplate.append(  "\n\tres := output_reactant" + (i + 1) + ACTIVITY_SUFFIX + " + delta" + (i + 1) + ";"
														+ "\n\tif (res &lt; 0 || res &gt; output_reactant" + (i + 1) + QUANTITY_SUFFIX + ") {"
														+ "\n\t\treturn true;"
														+ "\n\t}");
							} else if (property.equals(Model.Properties.QUANTITY)) {
								reactionTemplate.append(  "\n\tres := output_reactant" + (i + 1) + QUANTITY_SUFFIX + " + delta" + (i + 1) + ";"
														+ "\n\tif (res &lt; 0 || res &gt; output_reactant" + (i + 1) + MAX_QUANTITY_SUFFIX + ") {"
														+ "\n\t\treturn true;"
														+ "\n\t}");
							}
						}
						reactionTemplate.append("\n\treturn false;"
											  + "\n}\n\n");
						//s1 = start, s2 = not_reacting, s3 = reacting, s4 = resetting
						reactionTemplate.append("</declaration><location id=\"id0\" x=\"-1328\" y=\"-952\"><name x=\"-1338\" y=\"-982\">not_reacting</name></location><location id=\"id1\" x=\"-1064\" y=\"-800\"><name x=\"-1074\" y=\"-830\">resetting</name><urgent/></location>");
						reactionTemplate.append("<location id=\"id2\" x=\"-1328\" y=\"-696\"><name x=\"-1338\" y=\"-726\">reacting</name><label kind=\"invariant\" x=\"-1568\" y=\"-720\">timeU" + matrixIndicesLocalList);
						reactionTemplate.append(" == INFINITE_TIME\n|| c&lt;=timeU" + matrixIndicesLocalList);
						reactionTemplate.append("</label></location><location id=\"id3\" x=\"-1328\" y=\"-840\"><name x=\"-1352\" y=\"-864\">start</name><urgent/></location>");
						if (nOutput > 1) {
							int x = -1328,
								incrementX = (1328 - 1064) / nOutput;
							for (int i=0;i<nOutput - 1;i++) {
								x += incrementX;
								reactionTemplate.append("<location id=\"upd" + (i + 1) + "\" x=\"" + x + "\" y=\"-560\"><urgent/></location>");
							}
						}
						reactionTemplate.append("<init ref=\"id3\"/>");
						//Transitions s2->s4 to exit from the "non-reacting" state and check whether we can perform the reaction
						for (int i=0;i<nInput;i++) {
							reactionTemplate.append("<transition><source ref=\"id0\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1522\" y=\"-" + (1000 + i * 20) + "\">input" + (i+1) + "_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-" + (1000 + i * 20) + "\">c:=0</label><nail x=\"-1360\" y=\"-" + (984 + i * 20) + "\"/><nail x=\"-" + (1616 + i * 10) + "\" y=\"-" + (984 + i * 20) + "\"/><nail x=\"-" + (1616 + i * 10) + "\" y=\"-" + (512 - i * 10) + "\"/><nail x=\"-" + (784 - i * 10) + "\" y=\"-" + (512 - i * 10) + "\"/><nail x=\"-" + (784 - i * 10) + "\" y=\"-" + (736 + i * 10) + "\"/></transition>");
						}
						for (int j=0;j<nOutput;j++) {
							int i = j + nInput;
							reactionTemplate.append("<transition><source ref=\"id0\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1522\" y=\"-" + (1000 + i * 20) + "\">output" + (j+1) + "_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-" + (1000 + i * 20) + "\">c:=0</label><nail x=\"-1360\" y=\"-" + (984 + i * 20) + "\"/><nail x=\"-" + (1616 + i * 10) + "\" y=\"-" + (984 + i * 20) + "\"/><nail x=\"-" + (1616 + i * 10) + "\" y=\"-" + (512 - i * 10) + "\"/><nail x=\"-" + (784 - i * 10) + "\" y=\"-" + (512 - i * 10) + "\"/><nail x=\"-" + (784 - i * 10) + "\" y=\"-" + (736 + i * 10) + "\"/></transition>");
						}
						//Transitions s3->s4: someone has reacted before us, so we check whether we can continue or our job would be useless
						for (int i=0;i<nInput;i++) {
							reactionTemplate.append("<transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1224\" y=\"-768\">input" + (i + 1) + "_reacting?</label><nail x=\"-1248\" y=\"-752\"/><nail x=\"-1088\" y=\"-752\"/></transition>");
						}
						for (int i=0;i<nOutput;i++) {
							reactionTemplate.append("<transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1224\" y=\"-768\">output" + (i + 1) + "_reacting?</label><nail x=\"-1248\" y=\"-752\"/><nail x=\"-1088\" y=\"-752\"/></transition>");
						}
						//Transition s1->s3: when starting, check that we can react and then go to the "reacting" state
						reactionTemplate.append("<transition><source ref=\"id3\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1528\" y=\"-824\">!areWeAtTheLimits()\n&amp;&amp; timeL" + matrixIndicesList + " \n  != INFINITE_TIME</label><label kind=\"assignment\" x=\"-1528\" y=\"-782\">");
						for (int i=0;i<nInput;i++) {
							reactionTemplate.append("input_reactant_o" + (i + 1) + ACTIVITY_SUFFIX + " := input_reactant" + (i + 1) + ACTIVITY_SUFFIX + ",\n");
							reactionTemplate.append("input_reactant_o" + (i + 1) + QUANTITY_SUFFIX + " := input_reactant" + (i + 1) + QUANTITY_SUFFIX + ",\n");
						}
						reactionTemplate.append("c:=0</label></transition>");
						//Transition s1->s2: when starting, we found that we cannot react and so we go to the "non-reacting" state
						reactionTemplate.append("<transition><source ref=\"id3\"/><target ref=\"id0\"/><label kind=\"guard\" x=\"-1480\" y=\"-912\">areWeAtTheLimits()\n|| timeL" + matrixIndicesList + " == INFINITE_TIME</label></transition>");
						//Transition s4->s2: after a reaction happened, we find that we cannot react anymore, so we go to the "non-reacting" state
						reactionTemplate.append("<transition><source ref=\"id1\"/><target ref=\"id0\"/><label kind=\"guard\" x=\"-1272\" y=\"-968\">areWeAtTheLimits()\n|| timeL" + matrixIndicesList + " == INFINITE_TIME</label><nail x=\"-952\" y=\"-800\"/><nail x=\"-952\" y=\"-952\"/></transition>");
						if (nOutput > 1) { //If we have more than one output, we make multiple update steps, changing only one at the time (it is particularly important for the synchronizations)
							String prev = "id2", next;
							int x = -1328,
								incrementX = (1328 - 1064) / nOutput;
							for (int i=0;i<nOutput - 1;i++) {
								next = "upd" + (i + 1);
								x += incrementX;
								reactionTemplate.append("<transition><source ref=\"" + prev + "\"/><target ref=\"" + next + "\"/>");
								if (i == 0) { //The first of the chain needs of course to have the guard
									reactionTemplate.append("<label kind=\"guard\">c&gt;=timeL" + matrixIndicesLocalList + "</label>");
								}
								reactionTemplate.append("<label kind=\"synchronisation\">output" + (i + 1) + "_reacting!</label><label kind=\"assignment\">updateReactant" + (i + 1) + "()</label></transition>");
								prev = "upd" + (i + 1);
							}
							next = "id1";
							int i = nOutput - 1;
							reactionTemplate.append("<transition><source ref=\"" + prev + "\"/><target ref=\"" + next + "\"/><label kind=\"synchronisation\">output" + (i + 1) + "_reacting!</label><label kind=\"assignment\">c := 0,\nupdateReactant" + (i + 1) + "()</label></transition>");
						} else {
							reactionTemplate.append("<transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1096\" y=\"-640\">c&gt;=timeL" + matrixIndicesLocalList + "</label><label kind=\"synchronisation\" x=\"-1096\" y=\"-608\">output1_reacting!");
							reactionTemplate.append("</label><label kind=\"assignment\" x=\"-1096\" y=\"-592\">updateReactant1(),\nc:=0</label><nail x=\"-1280\" y=\"-656\"/><nail x=\"-1280\" y=\"-560\"/><nail x=\"-1104\" y=\"-560\"/><nail x=\"-848\" y=\"-560\"/><nail x=\"-848\" y=\"-704\"/></transition>");
						}
						//Transition s4->s3: we "shorten the fuse" for the reaction because the upper limit has been lowered in the meantime
						reactionTemplate.append("<transition><source ref=\"id1\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1296\" y=\"-840\">!areWeAtTheLimits()\n&amp;&amp; (timeU" + matrixIndicesList + " != INFINITE_TIME\n&amp;&amp; c&gt;timeU" + matrixIndicesList + ")</label><label kind=\"assignment\" x=\"-1296\" y=\"-816\">c:=timeU" + matrixIndicesList);
						for (int i=0;i<nInput;i++) {
							reactionTemplate.append(",\ninput_reactant_o" + (i + 1) + ACTIVITY_SUFFIX + " := input_reactant" + (i + 1) + ACTIVITY_SUFFIX);
							reactionTemplate.append(",\ninput_reactant_o" + (i + 1) + QUANTITY_SUFFIX + " := input_reactant" + (i + 1) + QUANTITY_SUFFIX);
						}
						reactionTemplate.append("</label><nail x=\"-1248\" y=\"-800\"/></transition>");
						//Transition s4->s3: all good, we can continue with the reaction from where we left it
						reactionTemplate.append("<transition><source ref=\"id1\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1272\" y=\"-752\">!areWeAtTheLimits()\n&amp;&amp; ((timeU" + matrixIndicesList + " == INFINITE_TIME\n&amp;&amp; timeL" + matrixIndicesList + " != INFINITE_TIME)\n|| (timeU" + matrixIndicesList + " != INFINITE_TIME\n&amp;&amp; c&lt;=timeU" + matrixIndicesList + "))</label><label kind=\"assignment\" x=\"-1272\" y=\"-704\">");
						for (int i=0;i<nInput;i++) {
							if (i > 0) {
								reactionTemplate.append(",\n ");
							}
							reactionTemplate.append("input_reactant_o" + (i + 1) + ACTIVITY_SUFFIX + " := input_reactant" + (i + 1) + ACTIVITY_SUFFIX + ",\n");
							reactionTemplate.append("input_reactant_o" + (i + 1) + QUANTITY_SUFFIX + " := input_reactant" + (i + 1) + QUANTITY_SUFFIX);
						}
						reactionTemplate.append("</label><nail x=\"-1064\" y=\"-696\"/></transition>");
						reactionTemplate.append("</template>");
						document = documentBuilder.parse(new ByteArrayInputStream(reactionTemplate.toString().getBytes()));
					} else {
						//document = documentBuilder.parse(new ByteArrayInputStream(("<template><name x=\"5\" y=\"5\">Reaction2_" + r.get(CATALYST).as(String.class) + "_" + r.get(REACTANT).as(String.class) + "</name><parameter>int &amp;reactant1, int &amp;reactant2, const int timeL[" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "+1][" + m.getReactant(r.get(CATALYST).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "+1], const int timeU[" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "+1][" + m.getReactant(r.get(CATALYST).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "+1], const int delta, broadcast chan &amp;r1_reacting, broadcast chan &amp;r2_reacting</parameter><declaration>// Place local declarations here.\nclock c;\nint r1, r2;</declaration><location id=\"id0\" x=\"-1328\" y=\"-952\"><name x=\"-1338\" y=\"-982\">s2</name></location><location id=\"id1\" x=\"-1064\" y=\"-800\"><name x=\"-1074\" y=\"-830\">s4</name><urgent/></location><location id=\"id2\" x=\"-1328\" y=\"-696\"><name x=\"-1338\" y=\"-726\">s3</name><label kind=\"invariant\" x=\"-1568\" y=\"-720\">timeU[r2][r1] == INFINITE_TIME\n|| c&lt;=timeU[r2][r1]</label></location><location id=\"id3\" x=\"-1328\" y=\"-840\"><name x=\"-1352\" y=\"-864\">s1</name><urgent/></location><init ref=\"id3\"/><transition><source ref=\"id0\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1592\" y=\"-1016\">r1_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-1000\">c:=0</label><nail x=\"-1360\" y=\"-984\"/><nail x=\"-1616\" y=\"-984\"/><nail x=\"-1616\" y=\"-512\"/><nail x=\"-784\" y=\"-512\"/><nail x=\"-784\" y=\"-736\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1184\" y=\"-784\">r2_reacting?</label><nail x=\"-1248\" y=\"-768\"/><nail x=\"-1096\" y=\"-768\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1096\" y=\"-640\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2+delta&gt;" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "</label><label kind=\"synchronisation\" x=\"-1096\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1096\" y=\"-592\">reactant2:=" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + ",\nc:=0</label><nail x=\"-1280\" y=\"-656\"/><nail x=\"-1104\" y=\"-656\"/><nail x=\"-1104\" y=\"-560\"/><nail x=\"-848\" y=\"-560\"/><nail x=\"-848\" y=\"-704\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1576\" y=\"-640\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2+delta&lt;0</label><label kind=\"synchronisation\" x=\"-1576\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1576\" y=\"-592\">reactant2:=0,\nc:=0</label><nail x=\"-1416\" y=\"-656\"/><nail x=\"-1584\" y=\"-656\"/><nail x=\"-1584\" y=\"-544\"/><nail x=\"-816\" y=\"-544\"/><nail x=\"-816\" y=\"-720\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1224\" y=\"-768\">r1_reacting?</label><nail x=\"-1248\" y=\"-752\"/><nail x=\"-1088\" y=\"-752\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1384\" y=\"-648\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2+delta&gt;=0\n&amp;&amp; reactant2+delta&lt;=" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "</label><label kind=\"synchronisation\" x=\"-1384\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1384\" y=\"-592\">reactant2:=reactant2+delta,\nc:=0</label><nail x=\"-1328\" y=\"-656\"/><nail x=\"-1392\" y=\"-656\"/><nail x=\"-1392\" y=\"-552\"/><nail x=\"-832\" y=\"-552\"/><nail x=\"-832\" y=\"-712\"/></transition><transition><source ref=\"id0\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1592\" y=\"-984\">r2_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-968\">c:=0</label><nail x=\"-1600\" y=\"-952\"/><nail x=\"-1600\" y=\"-528\"/><nail x=\"-800\" y=\"-528\"/><nail x=\"-800\" y=\"-728\"/></transition><transition><source ref=\"id1\"/><target ref=\"id0\"/><label kind=\"guard\" x=\"-1272\" y=\"-968\">timeL[reactant2][reactant1] == INFINITE_TIME</label><nail x=\"-952\" y=\"-800\"/><nail x=\"-952\" y=\"-952\"/></transition><transition><source ref=\"id3\"/><target ref=\"id0\"/><label kind=\"guard\" x=\"-1480\" y=\"-912\">timeL[reactant2][reactant1] == INFINITE_TIME</label></transition><transition><source ref=\"id1\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1296\" y=\"-840\">timeU[reactant2][reactant1] != INFINITE_TIME\n&amp;&amp; c&gt;timeU[reactant2][reactant1]</label><label kind=\"assignment\" x=\"-1296\" y=\"-816\">c:=timeU[reactant2][reactant1],\nr1:=reactant1,\nr2:=reactant2</label><nail x=\"-1248\" y=\"-800\"/></transition><transition><source ref=\"id1\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1272\" y=\"-752\">(timeU[reactant2][reactant1] == INFINITE_TIME\n&amp;&amp; timeL[reactant2][reactant1] != INFINITE_TIME)\n|| (timeU[reactant2][reactant1] != INFINITE_TIME\n&amp;&amp; c&lt;=timeU[reactant2][reactant1])</label><label kind=\"assignment\" x=\"-1272\" y=\"-704\">r1:=reactant1,\nr2:=reactant2</label><nail x=\"-1064\" y=\"-696\"/></transition><transition><source ref=\"id3\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1528\" y=\"-824\">timeL[reactant2][reactant1] \n  != INFINITE_TIME</label><label kind=\"assignment\" x=\"-1448\" y=\"-792\">r1 := reactant1,\nr2 := reactant2,\nc:=0</label></transition></template>").getBytes()));
						List<Integer> dimensions = r.get(DIMENSIONS).as(List.class);
						if (dimensions.size() < 2) {
							throw new ANIMOException("The number of dimensions for the reaction " + r.get(CATALYST).as(String.class) + ((r.get(INCREMENT).as(Integer.class) > 0)?" --> ":" --| ") + r.get(REACTANT).as(String.class) + " is not 2 as expected");
						}
						int nLevelsR1 = dimensions.get(0),
							nLevelsR2 = dimensions.get(1);
						if (r.get(INCREMENT).as(Integer.class) > 0) {
							document = documentBuilder.parse(new ByteArrayInputStream(("<template><name x=\"5\" y=\"5\">Reaction2_" + r.get(CATALYST).as(String.class) + "_" + r.get(REACTANT).as(String.class) + "</name><parameter>int &amp;reactant1, int &amp;reactant2" + ACTIVITY_SUFFIX + ", int &amp;reactant2" + QUANTITY_SUFFIX + ", const int timeL[" + nLevelsR2 + "][" + nLevelsR1 + "], const int timeU[" + nLevelsR2 + "][" + nLevelsR1 + "], const int delta, broadcast chan &amp;r1_reacting, broadcast chan &amp;r2_reacting</parameter><declaration>// Place local declarations here.\nclock c;\nint r1, r2;</declaration><location id=\"id0\" x=\"-1328\" y=\"-952\"><name x=\"-1338\" y=\"-982\">s2</name></location><location id=\"id1\" x=\"-1064\" y=\"-800\"><name x=\"-1074\" y=\"-830\">s4</name><urgent/></location><location id=\"id2\" x=\"-1328\" y=\"-696\"><name x=\"-1338\" y=\"-726\">s3</name><label kind=\"invariant\" x=\"-1568\" y=\"-720\">timeU[r2][r1] == INFINITE_TIME\n|| c&lt;=timeU[r2][r1]</label></location><location id=\"id3\" x=\"-1328\" y=\"-840\"><name x=\"-1352\" y=\"-864\">s1</name><urgent/></location><init ref=\"id3\"/><transition><source ref=\"id0\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1592\" y=\"-1016\">r1_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-1000\">c:=0</label><nail x=\"-1360\" y=\"-984\"/><nail x=\"-1616\" y=\"-984\"/><nail x=\"-1616\" y=\"-512\"/><nail x=\"-784\" y=\"-512\"/><nail x=\"-784\" y=\"-736\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1184\" y=\"-784\">r2_reacting?</label><nail x=\"-1248\" y=\"-768\"/><nail x=\"-1096\" y=\"-768\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1096\" y=\"-640\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2" + ACTIVITY_SUFFIX + "+delta&gt;" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "</label><label kind=\"synchronisation\" x=\"-1096\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1096\" y=\"-592\">reactant2" + ACTIVITY_SUFFIX + ":=" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + ",\nc:=0</label><nail x=\"-1280\" y=\"-656\"/><nail x=\"-1104\" y=\"-656\"/><nail x=\"-1104\" y=\"-560\"/><nail x=\"-848\" y=\"-560\"/><nail x=\"-848\" y=\"-704\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1576\" y=\"-640\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2" + ACTIVITY_SUFFIX + "+delta&lt;0</label><label kind=\"synchronisation\" x=\"-1576\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1576\" y=\"-592\">reactant2" + ACTIVITY_SUFFIX + ":=0,\nc:=0</label><nail x=\"-1416\" y=\"-656\"/><nail x=\"-1584\" y=\"-656\"/><nail x=\"-1584\" y=\"-544\"/><nail x=\"-816\" y=\"-544\"/><nail x=\"-816\" y=\"-720\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1224\" y=\"-768\">r1_reacting?</label><nail x=\"-1248\" y=\"-752\"/><nail x=\"-1088\" y=\"-752\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1384\" y=\"-648\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2" + ACTIVITY_SUFFIX + "+delta&gt;=0\n&amp;&amp; reactant2" + ACTIVITY_SUFFIX + "+delta&lt;=" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "</label><label kind=\"synchronisation\" x=\"-1384\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1384\" y=\"-592\">reactant2" + ACTIVITY_SUFFIX + ":=reactant2" + ACTIVITY_SUFFIX + "+delta,\nc:=0</label><nail x=\"-1328\" y=\"-656\"/><nail x=\"-1392\" y=\"-656\"/><nail x=\"-1392\" y=\"-552\"/><nail x=\"-832\" y=\"-552\"/><nail x=\"-832\" y=\"-712\"/></transition><transition><source ref=\"id0\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1592\" y=\"-984\">r2_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-968\">c:=0</label><nail x=\"-1600\" y=\"-952\"/><nail x=\"-1600\" y=\"-528\"/><nail x=\"-800\" y=\"-528\"/><nail x=\"-800\" y=\"-728\"/></transition><transition><source ref=\"id1\"/><target ref=\"id0\"/><label kind=\"guard\" x=\"-1272\" y=\"-968\">timeL[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1] == INFINITE_TIME</label><nail x=\"-952\" y=\"-800\"/><nail x=\"-952\" y=\"-952\"/></transition><transition><source ref=\"id3\"/><target ref=\"id0\"/><label kind=\"guard\" x=\"-1480\" y=\"-912\">timeL[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1] == INFINITE_TIME</label></transition><transition><source ref=\"id1\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1296\" y=\"-840\">timeU[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1] != INFINITE_TIME\n&amp;&amp; c&gt;timeU[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1]</label><label kind=\"assignment\" x=\"-1296\" y=\"-816\">c:=timeU[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1],\nr1:=reactant1,\nr2:=reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "</label><nail x=\"-1248\" y=\"-800\"/></transition><transition><source ref=\"id1\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1272\" y=\"-752\">(timeU[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1] == INFINITE_TIME\n&amp;&amp; timeL[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1] != INFINITE_TIME)\n|| (timeU[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1] != INFINITE_TIME\n&amp;&amp; c&lt;=timeU[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1])</label><label kind=\"assignment\" x=\"-1272\" y=\"-704\">r1:=reactant1,\nr2:=reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "</label><nail x=\"-1064\" y=\"-696\"/></transition><transition><source ref=\"id3\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1528\" y=\"-824\">timeL[reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + "][reactant1] \n  != INFINITE_TIME</label><label kind=\"assignment\" x=\"-1448\" y=\"-792\">r1 := reactant1,\nr2 := reactant2" + QUANTITY_SUFFIX + " - reactant2" + ACTIVITY_SUFFIX + ",\nc:=0</label></transition></template>").getBytes()));
						} else { //If the reaction has negative effect, the table is indexed by the activity level of the substrate instead of its "inactivity level" (quantity - activity)
							document = documentBuilder.parse(new ByteArrayInputStream(("<template><name x=\"5\" y=\"5\">Reaction2_" + r.get(CATALYST).as(String.class) + "_" + r.get(REACTANT).as(String.class) + "</name><parameter>int &amp;reactant1, int &amp;reactant2" + ACTIVITY_SUFFIX + ", int &amp;reactant2" + QUANTITY_SUFFIX + ", const int timeL[" + nLevelsR2 + "][" + nLevelsR1 + "], const int timeU[" + nLevelsR2 + "][" + nLevelsR1 + "], const int delta, broadcast chan &amp;r1_reacting, broadcast chan &amp;r2_reacting</parameter><declaration>// Place local declarations here.\nclock c;\nint r1, r2;</declaration><location id=\"id0\" x=\"-1328\" y=\"-952\"><name x=\"-1338\" y=\"-982\">s2</name></location><location id=\"id1\" x=\"-1064\" y=\"-800\"><name x=\"-1074\" y=\"-830\">s4</name><urgent/></location><location id=\"id2\" x=\"-1328\" y=\"-696\"><name x=\"-1338\" y=\"-726\">s3</name><label kind=\"invariant\" x=\"-1568\" y=\"-720\">timeU[r2][r1] == INFINITE_TIME\n|| c&lt;=timeU[r2][r1]</label></location><location id=\"id3\" x=\"-1328\" y=\"-840\"><name x=\"-1352\" y=\"-864\">s1</name><urgent/></location><init ref=\"id3\"/><transition><source ref=\"id0\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1592\" y=\"-1016\">r1_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-1000\">c:=0</label><nail x=\"-1360\" y=\"-984\"/><nail x=\"-1616\" y=\"-984\"/><nail x=\"-1616\" y=\"-512\"/><nail x=\"-784\" y=\"-512\"/><nail x=\"-784\" y=\"-736\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1184\" y=\"-784\">r2_reacting?</label><nail x=\"-1248\" y=\"-768\"/><nail x=\"-1096\" y=\"-768\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1096\" y=\"-640\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2" + ACTIVITY_SUFFIX + "+delta&gt;" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "</label><label kind=\"synchronisation\" x=\"-1096\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1096\" y=\"-592\">reactant2" + ACTIVITY_SUFFIX + ":=" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + ",\nc:=0</label><nail x=\"-1280\" y=\"-656\"/><nail x=\"-1104\" y=\"-656\"/><nail x=\"-1104\" y=\"-560\"/><nail x=\"-848\" y=\"-560\"/><nail x=\"-848\" y=\"-704\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1576\" y=\"-640\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2" + ACTIVITY_SUFFIX + "+delta&lt;0</label><label kind=\"synchronisation\" x=\"-1576\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1576\" y=\"-592\">reactant2" + ACTIVITY_SUFFIX + ":=0,\nc:=0</label><nail x=\"-1416\" y=\"-656\"/><nail x=\"-1584\" y=\"-656\"/><nail x=\"-1584\" y=\"-544\"/><nail x=\"-816\" y=\"-544\"/><nail x=\"-816\" y=\"-720\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1224\" y=\"-768\">r1_reacting?</label><nail x=\"-1248\" y=\"-752\"/><nail x=\"-1088\" y=\"-752\"/></transition><transition><source ref=\"id2\"/><target ref=\"id1\"/><label kind=\"guard\" x=\"-1384\" y=\"-648\">c&gt;=timeL[r2][r1]\n&amp;&amp; reactant2" + ACTIVITY_SUFFIX + "+delta&gt;=0\n&amp;&amp; reactant2" + ACTIVITY_SUFFIX + "+delta&lt;=" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "</label><label kind=\"synchronisation\" x=\"-1384\" y=\"-608\">r2_reacting!</label><label kind=\"assignment\" x=\"-1384\" y=\"-592\">reactant2" + ACTIVITY_SUFFIX + ":=reactant2" + ACTIVITY_SUFFIX + "+delta,\nc:=0</label><nail x=\"-1328\" y=\"-656\"/><nail x=\"-1392\" y=\"-656\"/><nail x=\"-1392\" y=\"-552\"/><nail x=\"-832\" y=\"-552\"/><nail x=\"-832\" y=\"-712\"/></transition><transition><source ref=\"id0\"/><target ref=\"id1\"/><label kind=\"synchronisation\" x=\"-1592\" y=\"-984\">r2_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-968\">c:=0</label><nail x=\"-1600\" y=\"-952\"/><nail x=\"-1600\" y=\"-528\"/><nail x=\"-800\" y=\"-528\"/><nail x=\"-800\" y=\"-728\"/></transition><transition><source ref=\"id1\"/><target ref=\"id0\"/><label kind=\"guard\" x=\"-1272\" y=\"-968\">timeL[reactant2" + ACTIVITY_SUFFIX + "][reactant1] == INFINITE_TIME</label><nail x=\"-952\" y=\"-800\"/><nail x=\"-952\" y=\"-952\"/></transition><transition><source ref=\"id3\"/><target ref=\"id0\"/><label kind=\"guard\" x=\"-1480\" y=\"-912\">timeL[reactant2" + ACTIVITY_SUFFIX + "][reactant1] == INFINITE_TIME</label></transition><transition><source ref=\"id1\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1296\" y=\"-840\">timeU[reactant2" + ACTIVITY_SUFFIX + "][reactant1] != INFINITE_TIME\n&amp;&amp; c&gt;timeU[reactant2" + ACTIVITY_SUFFIX + "][reactant1]</label><label kind=\"assignment\" x=\"-1296\" y=\"-816\">c:=timeU[reactant2" + ACTIVITY_SUFFIX + "][reactant1],\nr1:=reactant1,\nr2:=reactant2" + ACTIVITY_SUFFIX + "</label><nail x=\"-1248\" y=\"-800\"/></transition><transition><source ref=\"id1\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1272\" y=\"-752\">(timeU[reactant2" + ACTIVITY_SUFFIX + "][reactant1] == INFINITE_TIME\n&amp;&amp; timeL[reactant2" + ACTIVITY_SUFFIX + "][reactant1] != INFINITE_TIME)\n|| (timeU[reactant2" + ACTIVITY_SUFFIX + "][reactant1] != INFINITE_TIME\n&amp;&amp; c&lt;=timeU[reactant2" + ACTIVITY_SUFFIX + "][reactant1])</label><label kind=\"assignment\" x=\"-1272\" y=\"-704\">r1:=reactant1,\nr2:=reactant2" + ACTIVITY_SUFFIX + "</label><nail x=\"-1064\" y=\"-696\"/></transition><transition><source ref=\"id3\"/><target ref=\"id2\"/><label kind=\"guard\" x=\"-1528\" y=\"-824\">timeL[reactant2" + ACTIVITY_SUFFIX + "][reactant1] \n  != INFINITE_TIME</label><label kind=\"assignment\" x=\"-1448\" y=\"-792\">r1 := reactant1,\nr2 := reactant2" + ACTIVITY_SUFFIX + ",\nc:=0</label></transition></template>").getBytes()));
						}
					}
				} else {
					document = documentBuilder.parse(new ByteArrayInputStream(("<template><name x=\"5\" y=\"5\">Reaction_" + r.get(REACTANT).as(String.class) + "</name><parameter>int &amp;reactant, const int timeL[" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "+1], const int timeU[" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "+1], const int delta, broadcast chan &amp;inform_reacting</parameter><declaration>// Place local declarations here.\nclock c;\nint r;</declaration><location id=\"id4\" x=\"-1328\" y=\"-952\"><name x=\"-1338\" y=\"-982\">s2</name></location><location id=\"id5\" x=\"-1064\" y=\"-800\"><name x=\"-1074\" y=\"-830\">s4</name><urgent/></location><location id=\"id6\" x=\"-1328\" y=\"-696\"><name x=\"-1338\" y=\"-726\">s3</name><label kind=\"invariant\" x=\"-1528\" y=\"-720\">timeU[r] == INFINITE_TIME\n|| c&lt;=timeU[r]</label></location><location id=\"id7\" x=\"-1328\" y=\"-840\"><name x=\"-1352\" y=\"-864\">s1</name><urgent/></location><init ref=\"id7\"/><transition><source ref=\"id6\"/><target ref=\"id5\"/><label kind=\"guard\" x=\"-1096\" y=\"-640\">c&gt;=timeL[r]\n&amp;&amp; reactant+delta&gt;" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "</label><label kind=\"synchronisation\" x=\"-1096\" y=\"-608\">inform_reacting!</label><label kind=\"assignment\" x=\"-1096\" y=\"-592\">reactant:=" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + ",\nc:=0</label><nail x=\"-1280\" y=\"-656\"/><nail x=\"-1104\" y=\"-656\"/><nail x=\"-1104\" y=\"-560\"/><nail x=\"-848\" y=\"-560\"/><nail x=\"-848\" y=\"-704\"/></transition><transition><source ref=\"id6\"/><target ref=\"id5\"/><label kind=\"guard\" x=\"-1576\" y=\"-640\">c&gt;=timeL[r]\n&amp;&amp; reactant+delta&lt;0</label><label kind=\"synchronisation\" x=\"-1576\" y=\"-608\">inform_reacting!</label><label kind=\"assignment\" x=\"-1576\" y=\"-592\">reactant:=0,\nc:=0</label><nail x=\"-1416\" y=\"-656\"/><nail x=\"-1584\" y=\"-656\"/><nail x=\"-1584\" y=\"-544\"/><nail x=\"-816\" y=\"-544\"/><nail x=\"-816\" y=\"-720\"/></transition><transition><source ref=\"id6\"/><target ref=\"id5\"/><label kind=\"synchronisation\" x=\"-1248\" y=\"-768\">inform_reacting?</label><nail x=\"-1256\" y=\"-752\"/><nail x=\"-1120\" y=\"-752\"/></transition><transition><source ref=\"id6\"/><target ref=\"id5\"/><label kind=\"guard\" x=\"-1384\" y=\"-648\">c&gt;=timeL[r]\n&amp;&amp; reactant+delta&gt;=0\n&amp;&amp; reactant+delta&lt;=" + m.getReactant(r.get(REACTANT).as(String.class)).get(NUMBER_OF_LEVELS).as(Integer.class) + "</label><label kind=\"synchronisation\" x=\"-1384\" y=\"-608\">inform_reacting!</label><label kind=\"assignment\" x=\"-1384\" y=\"-592\">reactant:=reactant+delta,\nc:=0</label><nail x=\"-1328\" y=\"-656\"/><nail x=\"-1392\" y=\"-656\"/><nail x=\"-1392\" y=\"-552\"/><nail x=\"-832\" y=\"-552\"/><nail x=\"-832\" y=\"-712\"/></transition><transition><source ref=\"id4\"/><target ref=\"id5\"/><label kind=\"synchronisation\" x=\"-1592\" y=\"-984\">inform_reacting?</label><label kind=\"assignment\" x=\"-1592\" y=\"-968\">c:=0</label><nail x=\"-1600\" y=\"-952\"/><nail x=\"-1600\" y=\"-528\"/><nail x=\"-800\" y=\"-528\"/><nail x=\"-800\" y=\"-728\"/></transition><transition><source ref=\"id5\"/><target ref=\"id4\"/><label kind=\"guard\" x=\"-1272\" y=\"-968\">timeL[reactant] == INFINITE_TIME</label><nail x=\"-952\" y=\"-800\"/><nail x=\"-952\" y=\"-952\"/></transition><transition><source ref=\"id7\"/><target ref=\"id4\"/><label kind=\"guard\" x=\"-1432\" y=\"-912\">timeL[reactant] == INFINITE_TIME</label></transition><transition><source ref=\"id5\"/><target ref=\"id6\"/><label kind=\"guard\" x=\"-1296\" y=\"-840\">timeU[reactant] != INFINITE_TIME\n&amp;&amp; c&gt;timeU[reactant]</label><label kind=\"assignment\" x=\"-1296\" y=\"-816\">c:=timeU[reactant],\nr:=reactant</label><nail x=\"-1248\" y=\"-800\"/></transition><transition><source ref=\"id5\"/><target ref=\"id6\"/><label kind=\"guard\" x=\"-1272\" y=\"-744\">(timeU[reactant] == INFINITE_TIME\n&amp;&amp; timeL[reactant] != INFINITE_TIME)\n|| (timeU[reactant] != INFINITE_TIME\n&amp;&amp; c&lt;=timeU[reactant])</label><label kind=\"assignment\" x=\"-1272\" y=\"-696\">r:=reactant</label><nail x=\"-1064\" y=\"-680\"/><nail x=\"-1280\" y=\"-680\"/></transition><transition><source ref=\"id7\"/><target ref=\"id6\"/><label kind=\"guard\" x=\"-1456\" y=\"-824\">timeL[reactant] \n  != INFINITE_TIME</label><label kind=\"assignment\" x=\"-1456\" y=\"-800\">r := reactant,\nc:=0</label></transition></template>").getBytes()));
				}
				tra.transform(new DOMSource(document), new StreamResult(outString));
				out.append(outString.toString());
				out.append(newLine);
				out.append(newLine);
			}
		} catch (Exception e) {
			System.err.println("Error: " + e);
			e.printStackTrace();
		}
	}
	
	@Override
	protected void appendReactantVariables(StringBuilder out, Reactant r) {
		// outputs the global variables necessary for the given reactant
		out.append("//" + r.getId() + " = " + r.get(ALIAS).as(String.class));
		out.append(newLine);
		out.append("int " + r.getId() + ACTIVITY_SUFFIX + " := " + r.get(INITIAL_LEVEL).as(Integer.class) + ";");
		out.append(newLine);
		out.append("int " + r.getId() + QUANTITY_SUFFIX + " := " + r.get(INITIAL_QUANTITY).as(Integer.class) + ";");
		out.append(newLine);
		out.append("int " + r.getId() + MAX_QUANTITY_SUFFIX + " := " + (r.get(NUMBER_OF_LEVELS).as(Integer.class) * r.get(Model.Properties.MAXIMUM_QUANTITY_GROWTH).as(Integer.class)) + ";");
		out.append(newLine);
		out.append("int " + r.getId() + PERCENTAGE_SUFFIX + " := " + (int)Math.round(100.0 * 10.0 * r.get(INITIAL_LEVEL).as(Integer.class) / r.get(INITIAL_QUANTITY).as(Integer.class)) + ";");
		out.append(newLine);
		out.append(newLine);
	}

}

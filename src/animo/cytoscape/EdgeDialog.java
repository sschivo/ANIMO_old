package animo.cytoscape;

import giny.model.Edge;
import giny.model.Node;
import giny.view.NodeView;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import animo.model.Model;
import animo.model.ReactantParameter;
import animo.model.Scenario;
import animo.model.ScenarioMono;
import animo.model.UserFormula;

import cytoscape.CyNetwork;
import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;
import cytoscape.view.CyNetworkView;

/**
 * The edge dialog contains the settings of a edge.
 * 
 * @author Brend Wanders
 * 
 */
public class EdgeDialog extends JDialog {
	private static final long serialVersionUID = 6630154220142970079L;
	private static final String DECIMAL_FORMAT_STRING = "##.####",
								SAVE = "Save",
								CANCEL = "Cancel",
								SCENARIO = Model.Properties.SCENARIO,
								CANONICAL_NAME = Model.Properties.CANONICAL_NAME,
								INCREMENT = Model.Properties.INCREMENT,
								UNCERTAINTY = Model.Properties.UNCERTAINTY;
	
	private Scenario[] scenarios = Scenario.availableScenarios;
	private JLabel formulaLabel = new JLabel();
	private String reactantAliases[], reactantIdentifiers[]; //The identifiers and aliases of all reactants in the network
	private List<ReactantParameter> influencedReactants; //The list of reactants (with name and a property [quantity/activity level]) influenced by the current reaction
	private List<Integer> influenceValues; //The list of influence values (usually, +1 or -1) to be associated to each influenced reactant
	
	private int previouslySelectedScenario = 0;
	private boolean weAreEditingTheComboBoxShutUp = false;
	
	public EdgeDialog(final Edge edge) {
		this(Cytoscape.getDesktop(), edge);
	}

	/**
	 * Constructor.
	 * 
	 * @param edge the edge to display for.
	 */
	@SuppressWarnings("unchecked")
	public EdgeDialog(final Window owner, final Edge edge) {
		super(owner, "Reaction '" + edge.getIdentifier() + "'", Dialog.ModalityType.APPLICATION_MODAL);
		//super("Reaction " + Cytoscape.getNodeAttributes().getAttribute(edge.getSource().getIdentifier(), "canonicalName") + ((Integer.parseInt(Cytoscape.getEdgeAttributes().getAttribute(edge.getIdentifier(), "increment").toString()) >= 0)?" --> ":" --| ") + Cytoscape.getNodeAttributes().getAttribute(edge.getTarget().getIdentifier(), "canonicalName"));
		StringBuilder title = new StringBuilder();
		title.append("Reaction ");
		CyAttributes nodeAttrib = Cytoscape.getNodeAttributes();
		final CyAttributes edgeAttrib = Cytoscape.getEdgeAttributes();
		int increment;
		if (edgeAttrib.hasAttribute(edge.getIdentifier(), INCREMENT)) {
			increment = edgeAttrib.getIntegerAttribute(edge.getIdentifier(), INCREMENT);
		} else {
			if (edge.getSource().equals(edge.getTarget())) {
				increment = -1;
			} else {
				increment = 1;
			}
		}
		String res;
		if (nodeAttrib.hasAttribute(edge.getSource().getIdentifier(), CANONICAL_NAME)) {
			res = nodeAttrib.getStringAttribute(edge.getSource().getIdentifier(), CANONICAL_NAME);
			title.append(res);
			
			if (increment >= 0) {
				title.append(" --> ");
			} else {
				title.append(" --| ");
			}
			if (nodeAttrib.hasAttribute(edge.getTarget().getIdentifier(), CANONICAL_NAME)) {
				res = nodeAttrib.getStringAttribute(edge.getTarget().getIdentifier(), CANONICAL_NAME);
				title.append(res);
				this.setTitle(title.toString());
			}
		}
		
		//Read the list of reactant identifiers and aliases from the nodes in the current network
		CyNetwork network = Cytoscape.getCurrentNetwork();
		reactantAliases = new String[network.getNodeCount()];
		reactantIdentifiers = new String[network.getNodeCount()];
		Iterator<Node> nodes = (Iterator<Node>) network.nodesIterator();
		for (int i = 0; nodes.hasNext(); i++) {
			Node node = nodes.next();
			reactantIdentifiers[i] = node.getIdentifier();
			if (nodeAttrib.hasAttribute(node.getIdentifier(), Model.Properties.CANONICAL_NAME)) {
				reactantAliases[i] = nodeAttrib.getStringAttribute(node.getIdentifier(), Model.Properties.CANONICAL_NAME);
			} else {
				reactantAliases[i] = reactantIdentifiers[i];
			}
			if (edge.getSource().getIdentifier().equals(reactantIdentifiers[i])) { //Highlight the nodes involved in the current reaction
				reactantAliases[i] += " (the Upstream reactant)";
			} else if (edge.getTarget().getIdentifier().equals(reactantIdentifiers[i])) {
				reactantAliases[i] += " (the Downstream reactant)";
			}
		}
		

		this.setLayout(new BorderLayout(2, 2));

		JPanel values = new JPanel(new GridLayout(1, 2, 2, 2));
		final Box boxScenario = new Box(BoxLayout.X_AXIS);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		
		if (edge.getSource() == edge.getTarget()) { //if it is an auto-arc, the reaction this edge represents is a mono-reaction (one reactant only). Thus, we use the mono-reaction scenario
			final ScenarioMono scenario = new ScenarioMono();
			final Box parameterBox = new Box(BoxLayout.X_AXIS);
			final Box boxInputReactants = new Box(BoxLayout.Y_AXIS);
			updateParametersBox(edge, parameterBox, boxInputReactants, scenario);
			Box allParametersBox = new Box(BoxLayout.Y_AXIS);
			allParametersBox.add(parameterBox);
			Integer value;
			if (edgeAttrib.hasAttribute(edge.getIdentifier(), UNCERTAINTY)) {
				value = edgeAttrib.getIntegerAttribute(edge.getIdentifier(), UNCERTAINTY);
			} else {
				value = 0;
			}
			final JSlider uncertainty = new JSlider(0, 100, value);
			uncertainty.setPaintTicks(true);
			uncertainty.setMinorTickSpacing(5);
			uncertainty.setMajorTickSpacing(10);
			uncertainty.setPaintLabels(true);
			final LabelledField incertaintyField = new LabelledField("Uncertainty = " + uncertainty.getValue() + "%", uncertainty);
			uncertainty.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					incertaintyField.setTitle("Uncertainty = " + uncertainty.getValue() + "%");
					repaint();
				}
			});
			allParametersBox.add(incertaintyField);
			/*final JRadioButton positiveIncrement = new JRadioButton("Activation"),
							   negativeIncrement = new JRadioButton("Inhibition");
			ButtonGroup incrementGroup = new ButtonGroup();
			incrementGroup.add(positiveIncrement);
			incrementGroup.add(negativeIncrement);
			if (increment >= 0) {
				positiveIncrement.setSelected(true);
				negativeIncrement.setSelected(false);
			} else {
				positiveIncrement.setSelected(false);
				negativeIncrement.setSelected(true);
			}
			Box incrementBox = new Box(BoxLayout.X_AXIS);
			incrementBox.add(positiveIncrement);
			incrementBox.add(Box.createHorizontalStrut(50));
			incrementBox.add(negativeIncrement);
			allParametersBox.add(new LabelledField("Influence", incrementBox));*/
			Box incrementBox = new Box(BoxLayout.Y_AXIS);
			loadInfluencedReactants(edge);
			//updateInfluenceBox(edge, incrementBox, scenario);
			updateReactantsBox(edge, incrementBox, false, new String[]{});
			allParametersBox.add(new LabelledField("Influence", incrementBox));
			boxScenario.add(allParametersBox);
			
			controls.add(new JButton(new AbstractAction(SAVE) {
				private static final long serialVersionUID = 1435389753489L;

				@Override
				public void actionPerformed(ActionEvent e) {
					Component[] paramFields = parameterBox.getComponents();
					for (int i=0;i<paramFields.length;i++) {
						if (paramFields[i] instanceof LabelledField) {
							LabelledField paramField = (LabelledField)paramFields[i];
							String paramName = paramField.getTitle();
							Double paramValue;
							if (paramField.getField() instanceof JFormattedTextField) {
								paramValue = new Double(((JFormattedTextField)(paramField).getField()).getValue().toString());
							} else if (paramField.getField() instanceof Box) {
								paramValue = new Double(((JFormattedTextField)(((Box)paramField.getField()).getComponents()[0])).getValue().toString());
							} else {
								paramValue = scenario.getParameter(paramName);
							}
							scenario.setParameter(paramName, paramValue);
							edgeAttrib.setAttribute(edge.getIdentifier(), paramName, paramValue);
						}
					}
					int uncert = uncertainty.getValue();
					edgeAttrib.setAttribute(edge.getIdentifier(), SCENARIO, 0);
					edgeAttrib.setAttribute(edge.getIdentifier(), UNCERTAINTY, uncert);
					//edgeAttrib.setAttribute(edge.getIdentifier(), INCREMENT, ((positiveIncrement.isSelected())?1:-1));
					saveInfluencedReactants(edge);
					
					Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);

					EdgeDialog.this.dispose();
				}
			}));
		} else {
			final JButton buttonNewScenario = new JButton("New..."),
						  buttonEditScenario = new JButton("Edit..."),
						  buttonDeleteScenario = new JButton("Delete");
			final Box boxScenarioParameters = new Box(BoxLayout.X_AXIS);
			final Box boxInputReactants = new Box(BoxLayout.Y_AXIS);
			final JComboBox comboScenario = new JComboBox(scenarios);
			comboScenario.setMaximumSize(new Dimension(200, 20));
			comboScenario.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (!weAreEditingTheComboBoxShutUp) {
						selectedNewScenario(edge, boxScenarioParameters, boxInputReactants, comboScenario);
						if (comboScenario.getSelectedItem() instanceof UserFormula) {
							buttonEditScenario.setEnabled(true);
							buttonDeleteScenario.setEnabled(true);
						} else {
							buttonEditScenario.setEnabled(false);
							buttonDeleteScenario.setEnabled(false);
						}
					}
				}
			});
			
			int scenarioIdx;
			if (edgeAttrib.hasAttribute(edge.getIdentifier(), SCENARIO)) {
				scenarioIdx = edgeAttrib.getIntegerAttribute(edge.getIdentifier(), SCENARIO); 
			} else {
				scenarioIdx = 0;
			}
			
			previouslySelectedScenario = scenarioIdx;
			comboScenario.setSelectedIndex(scenarioIdx);
			Box boxComboScenario = new Box(BoxLayout.Y_AXIS);
			boxComboScenario.add(comboScenario);
			Box boxButtonsScenario = new Box(BoxLayout.X_AXIS);
			boxButtonsScenario.add(buttonNewScenario);
			boxButtonsScenario.add(Box.createGlue());
			boxButtonsScenario.add(buttonEditScenario);
			boxButtonsScenario.add(Box.createGlue());
			boxButtonsScenario.add(buttonDeleteScenario);
			boxComboScenario.add(boxButtonsScenario);
			formulaLabel.setHorizontalAlignment(SwingConstants.CENTER);
			formulaLabel.setHorizontalTextPosition(SwingConstants.CENTER);
			formulaLabel.setVerticalAlignment(SwingConstants.CENTER);
			formulaLabel.setVerticalTextPosition(SwingConstants.CENTER);
			formulaLabel.setFont(new Font("serif", Font.BOLD, 20));
			JPanel formulaPanel = new JPanel();
			formulaPanel.add(formulaLabel);
			JScrollPane formulaScroll = new JScrollPane(formulaPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			boxComboScenario.add(formulaScroll);
			buttonNewScenario.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					final UserFormula formula = new UserFormula();
					FormulaDialog dialog = new FormulaDialog(formula, true);
					dialog.addSaveListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							weAreEditingTheComboBoxShutUp = true;
							Scenario.loadScenarios();
							scenarios = Scenario.availableScenarios;
							comboScenario.removeAllItems();
							for (int i=0;i<Scenario.availableScenarios.length;i++) {
								comboScenario.addItem(Scenario.availableScenarios[i]);
							}
							EdgeDialog.this.pack();
							weAreEditingTheComboBoxShutUp = false;
							comboScenario.setSelectedIndex(comboScenario.getItemCount() - 1); //The added formula was inserted at the last position in the list
						}
					});
					dialog.pack();
					dialog.setLocationRelativeTo(Cytoscape.getDesktop());
					dialog.setVisible(true);
				}
			});
			buttonEditScenario.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					FormulaDialog dialog;
					final Scenario editingItem = (Scenario)comboScenario.getSelectedItem();
					if (editingItem instanceof UserFormula) {
						dialog = new FormulaDialog((UserFormula)comboScenario.getSelectedItem(), false);
					} else {
						return;
					}
					dialog.addSaveListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							//If we have modified a formula, we just need to "pretend" that the user has selected it, so that the list of parameters is updated
							//selectedNewScenario(edge, boxScenarioParameters, comboScenario);
							Scenario selectedItem = null;
							weAreEditingTheComboBoxShutUp = true;
							Scenario.loadScenarios();
							scenarios = Scenario.availableScenarios;
							for (int i=0;i<comboScenario.getItemCount();i++) {
								if (comboScenario.getItemAt(i) == editingItem) {
									selectedItem = Scenario.availableScenarios[i];
								}
							}
							comboScenario.removeAllItems();
							for (int i=0;i<Scenario.availableScenarios.length;i++) {
								comboScenario.addItem(Scenario.availableScenarios[i]);
							}
							EdgeDialog.this.pack();
							weAreEditingTheComboBoxShutUp = false;
							comboScenario.setSelectedItem(selectedItem);
						}
					});
					dialog.pack();
					dialog.setLocationRelativeTo(Cytoscape.getDesktop());
					dialog.setVisible(true);
				}
			});
			buttonDeleteScenario.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (JOptionPane.showConfirmDialog(Cytoscape.getDesktop(), "Do you really want to delete the formula \"" + comboScenario.getSelectedItem() + "\"?", "Confirmation required", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
						weAreEditingTheComboBoxShutUp = true;
						UserFormula condemned = (UserFormula)comboScenario.getSelectedItem();
						try {
							Scenario.deleteUserFormula(condemned);
						} catch (Exception ex) {
							StringWriter wr = new StringWriter();
							ex.printStackTrace(new PrintWriter(wr));
							JOptionPane.showMessageDialog(Cytoscape.getDesktop(), ex.getMessage() + ": " + ex + wr.toString(), "Error", JOptionPane.ERROR_MESSAGE);
						}
						comboScenario.removeItem(condemned);
						weAreEditingTheComboBoxShutUp = false;
						comboScenario.setSelectedIndex(0);
					}
				}
			});
			//boxComboScenario.add(Box.createGlue());
			boxScenario.add(new LabelledField("Scenario/formula", boxComboScenario));
			boxScenario.add(Box.createGlue());
			
			Box boxScenarioAllParameters = new Box(BoxLayout.Y_AXIS);
			boxScenarioAllParameters.add(boxScenarioParameters);
			boxScenarioAllParameters.add(boxInputReactants);
			Integer value;
			if (edgeAttrib.hasAttribute(edge.getIdentifier(), UNCERTAINTY)) {
				value = edgeAttrib.getIntegerAttribute(edge.getIdentifier(), UNCERTAINTY);
			} else {
				value = 0;
			}
			final JSlider uncertainty = new JSlider(0, 100, value);
			uncertainty.setPaintTicks(true);
			uncertainty.setMinorTickSpacing(5);
			uncertainty.setMajorTickSpacing(10);
			uncertainty.setPaintLabels(true);
			final LabelledField uncertaintyField = new LabelledField("Uncertainty = " + uncertainty.getValue() + "%", uncertainty);
			uncertainty.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					uncertaintyField.setTitle("Uncertainty = " + uncertainty.getValue() + "%");
					repaint();
				}
			});
			boxScenarioAllParameters.add(uncertaintyField);
			/*final JRadioButton positiveIncrement = new JRadioButton("Activation"),
			   				   negativeIncrement = new JRadioButton("Inhibition");
			ButtonGroup incrementGroup = new ButtonGroup();
			incrementGroup.add(positiveIncrement);
			incrementGroup.add(negativeIncrement);
			if (increment >= 0) {
				positiveIncrement.setSelected(true);
				negativeIncrement.setSelected(false);
			} else {
				positiveIncrement.setSelected(false);
				negativeIncrement.setSelected(true);
			}*/
			final Box influenceBox = new Box(BoxLayout.Y_AXIS);
			loadInfluencedReactants(edge);
			JButton addInfluencedReactant = new JButton("Add influenced reactant");
			addInfluencedReactant.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					influencedReactants.add(new ReactantParameter("", ""));
					influenceValues.add(1);
					//updateInfluenceBox(edge, influenceBox, (Scenario)comboScenario.getSelectedItem());
					updateReactantsBox(edge, influenceBox, false, new String[]{});
					EdgeDialog.this.pack();
				}
			});
			Box boxInfluencePlusButton = new Box(BoxLayout.Y_AXIS);
			Box addInfluencedReactantBox = new Box(BoxLayout.X_AXIS);
			addInfluencedReactantBox.add(Box.createGlue());
			addInfluencedReactantBox.add(addInfluencedReactant);
			addInfluencedReactantBox.add(Box.createGlue());
			boxInfluencePlusButton.add(addInfluencedReactantBox);
			boxInfluencePlusButton.add(influenceBox);
			//updateInfluenceBox(edge, influenceBox, (Scenario)comboScenario.getSelectedItem());
			updateReactantsBox(edge, influenceBox, false, new String[]{});
			//influenceBox.add(positiveIncrement);
			//influenceBox.add(Box.createHorizontalStrut(50));
			//influenceBox.add(negativeIncrement);
			boxScenarioAllParameters.add(new LabelledField("Influence", boxInfluencePlusButton));
			boxScenario.add(boxScenarioAllParameters);
			
			boxScenario.add(Box.createGlue());
			

			controls.add(new JButton(new AbstractAction(SAVE) {
				private static final long serialVersionUID = -6920908627164931058L;

				@Override
				public void actionPerformed(ActionEvent e) {
					Scenario selectedScenario = (Scenario)comboScenario.getSelectedItem();
					Component[] paramFields = boxScenarioParameters.getComponents();
					for (int i=0;i<paramFields.length;i++) {
						if (paramFields[i] instanceof LabelledField) {
							LabelledField paramField = (LabelledField)paramFields[i];
							String paramName = paramField.getTitle();
							Double paramValue;
							if (paramField.getField() instanceof JFormattedTextField) {
								paramValue = new Double(((JFormattedTextField)(paramField).getField()).getValue().toString());
							} else if (paramField.getField() instanceof Box) {
								paramValue = new Double(((JFormattedTextField)(((Box)paramField.getField()).getComponents()[0])).getValue().toString());
							} else {
								paramValue = selectedScenario.getParameter(paramName);
							}
							selectedScenario.setParameter(paramName, paramValue);
							edgeAttrib.setAttribute(edge.getIdentifier(), paramName, paramValue);
						}
					}
					Component[] inputReactantsFields = boxInputReactants.getComponents();
					for (int i=0;i<inputReactantsFields.length;i++) {
						if (inputReactantsFields[i] instanceof LabelledField) {
							LabelledField inputReactantField = (LabelledField)inputReactantsFields[i];
							String paramName = inputReactantField.getTitle();
							ReactantParameter paramValue;
							String reactantName, propertyName;
							assert(inputReactantField.getField() instanceof Box); //TODO: I'm not very proud of this: we assume that the structure is fixed as this
							reactantName = reactantIdentifiers[((JComboBox)(((Box)inputReactantField.getField()).getComponents()[0])).getSelectedIndex()];
							propertyName = ((JComboBox)(((Box)inputReactantField.getField()).getComponents()[1])).getSelectedItem().toString();							
							paramValue = new ReactantParameter(reactantName, propertyName);
							selectedScenario.setLinkedVariable(paramName, paramValue);
							edgeAttrib.setAttribute(edge.getIdentifier(), paramName, paramValue.toString());
						}
					}
					int uncert = uncertainty.getValue();
					
					edgeAttrib.setAttribute(edge.getIdentifier(), UNCERTAINTY, uncert);
					edgeAttrib.setAttribute(edge.getIdentifier(), SCENARIO, comboScenario.getSelectedIndex());
					//edgeAttrib.setAttribute(edge.getIdentifier(), INCREMENT, ((positiveIncrement.isSelected())?1:-1));
					saveInfluencedReactants(edge);
					
					Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);

					EdgeDialog.this.dispose();
				}
			}));
		}
		
		
		values.add(boxScenario);


		controls.add(new JButton(new AbstractAction(CANCEL) {
			private static final long serialVersionUID = 3103827646050457714L;

			@Override
			public void actionPerformed(ActionEvent e) {
				// discard changes
				EdgeDialog.this.dispose();
			}
		}));

		this.add(values, BorderLayout.CENTER);
		this.add(controls, BorderLayout.SOUTH);
	}
	
	/**
	 * Used to update the list of parameters when a new scenario/user-defined formula is selected from the combo box
	 * @param edge The edge representing the reaction on which we are working now
	 * @param boxScenarioParameters The box containing the controls for the formula variables intended as parameters (= whose value is given by the user)
	 * @param boxInputReactants The box containing the variables that need to be linked to reactants in the network
	 * @param comboScenario The combo box containing the list of all available scenarios/formulae
	 */
	private void selectedNewScenario(Edge edge, Box boxScenarioParameters, Box boxInputReactants, JComboBox comboScenario) {
		updateParametersBox(edge, boxScenarioParameters, boxInputReactants, (Scenario)comboScenario.getSelectedItem());
		JLabel formula = ((Scenario)comboScenario.getSelectedItem()).getFormulaLabel();
		formulaLabel.setText(formula.getText());
		formulaLabel.setIcon(formula.getIcon());
		formulaLabel.setHorizontalAlignment(SwingConstants.CENTER);
		formulaLabel.setHorizontalTextPosition(SwingConstants.CENTER);
		formulaLabel.setVerticalAlignment(SwingConstants.CENTER);
		formulaLabel.setVerticalTextPosition(SwingConstants.CENTER);
		
		EdgeDialog.this.validate();
		EdgeDialog.this.pack();
	}
	
	/**
	 * Update the list of parameters shown when a differnt scenario is chosen
	 * @param edge The edge representing the reaction of which we display the parameter
	 * @param parametersBox The box in which to put the list of parameters for the new scenario
	 * @param inputReactantsBox The box in which the variables to be linked to existing network reactants are contained
	 * @param selectedScenario The newly selected scenario
	 */
	private void updateParametersBox(Edge edge, Box parametersBox, Box inputReactantsBox, Scenario selectedScenario) {
		parametersBox.removeAll();
		
		//Look for the index of the currently selected scenario, so that we can compare it with the previously selected one, and thus correctly convert the parameters between the two
		int currentlySelectedScenario = 0;
		for (int i=0;i<scenarios.length;i++) {
			if (scenarios[i].equals(selectedScenario)) {
				currentlySelectedScenario = i;
				break;
			}
		}
		
		String[] parameters = selectedScenario.listVariableParameters();
		CyAttributes edgeAttrib = Cytoscape.getEdgeAttributes();
		CyAttributes nodeAttrib = Cytoscape.getNodeAttributes();
		for (int i=0;i<parameters.length;i++) {
			DecimalFormat format = new DecimalFormat(DECIMAL_FORMAT_STRING);
			format.setMinimumFractionDigits(8);
			final JFormattedTextField param = new JFormattedTextField(format);
			//if (currentlySelectedScenario != previouslySelectedScenario) {
			/*if (previouslySelectedScenario == 0 && currentlySelectedScenario == 1) { //1234 -> 5
				if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_STOT)) {
					if (nodeAttrib.hasAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS)) {
						param.setValue(nodeAttrib.getIntegerAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS));
					}
				} else if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_K2_KM)) {
					Double par = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_ONLY_PARAMETER);
					Double Stot;
					if (nodeAttrib.hasAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS)) {
						Stot = (double)nodeAttrib.getIntegerAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS);
					} else if (edgeAttrib.hasAttribute(edge.getIdentifier(), parameters[i])) {
						Stot = edgeAttrib.getDoubleAttribute(edge.getIdentifier(), parameters[i]);
					} else {
						Stot = selectedScenario.getParameter(parameters[i]);
					}
					Double k2km = par / Stot;
					param.setValue(k2km);
				}
			} else if (previouslySelectedScenario == 1 && currentlySelectedScenario == 0) { //5 -> 1234
				Double k2km, Stot;
				k2km = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_K2_KM);
				Stot = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_STOT);
				param.setValue(k2km * Stot);
			} else if (previouslySelectedScenario == 1 && currentlySelectedScenario == 2) { //5 -> 6
				if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_STOT)) {
					param.setValue(scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_STOT));
				} else if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_KM)) {
					Double Stot = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_STOT);
					Double k2km = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_K2_KM);
					Double k2 = (Stot * Stot - Stot) * k2km; //this allows us to have a difference of 0.001 between scenarios 5 and 6
					Double km = k2 / k2km;
					param.setValue(km);
				} else if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_K2)) {
					Double Stot = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_STOT);
					Double k2km = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_K2_KM);
					Double k2 = (Stot * Stot - Stot) * k2km; //this allows us to have a difference of 0.001 between scenarios 5 and 6
					param.setValue(k2);
				}
			} else if (previouslySelectedScenario == 2 && currentlySelectedScenario == 1) { //6 -> 5
				if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_STOT)) {
					param.setValue(scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_STOT));
				} else if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_K2_KM)) {
					Double k2 = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_K2);
					Double km = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_KM);
					Double k2km = k2 / km;
					param.setValue(k2km);
				}
			} else if (previouslySelectedScenario == 0 && currentlySelectedScenario == 2) { //1234 -> 6
				if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_STOT)) {
					if (nodeAttrib.hasAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS)) {
						param.setValue(nodeAttrib.getIntegerAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS));
					}
				} else if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_KM)) {
					Double par = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_ONLY_PARAMETER);
					Double Stot;
					if (nodeAttrib.hasAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS)) {
						Stot = (double)nodeAttrib.getIntegerAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS);
					} else if (edgeAttrib.hasAttribute(edge.getIdentifier(), parameters[i])) {
						Stot = edgeAttrib.getDoubleAttribute(edge.getIdentifier(), parameters[i]);
					} else {
						Stot = selectedScenario.getParameter(parameters[i]);
					}
					Double k2km = par / Stot;
					Double k2 = (Stot * Stot - Stot) * k2km; //this allows us to have a difference of 0.001 between scenarios 5 and 6
					Double km = k2 / k2km;
					param.setValue(km);
				} else if (parameters[i].equals(Model.Properties.SCENARIO_PARAMETER_K2)) {
					Double par = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_ONLY_PARAMETER);
					Double Stot;
					if (nodeAttrib.hasAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS)) {
						Stot = (double)nodeAttrib.getIntegerAttribute(edge.getTarget().getIdentifier(), Model.Properties.NUMBER_OF_LEVELS);
					} else if (edgeAttrib.hasAttribute(edge.getIdentifier(), parameters[i])) {
						Stot = edgeAttrib.getDoubleAttribute(edge.getIdentifier(), parameters[i]);
					} else {
						Stot = selectedScenario.getParameter(parameters[i]);
					}
					Double k2km = par / Stot;
					Double k2 = (Stot * Stot - Stot) * k2km; //this allows us to have a difference of 0.001 between scenarios 5 and 6
					param.setValue(k2);
				}
			} else if (previouslySelectedScenario == 2 && currentlySelectedScenario == 0) { //6 -> 1234
				Double Stot = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_STOT);
				Double k2 = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_K2);
				Double km = scenarios[previouslySelectedScenario].getParameter(Model.Properties.SCENARIO_PARAMETER_KM);
				Double k2km = k2 / km;
				param.setValue(Stot * k2km);
			} else*/ if (edgeAttrib.hasAttribute(edge.getIdentifier(), parameters[i])) { //Generic scenarios (possibly user-defined formulae) need to look for an already present parameter
				Double value = edgeAttrib.getDoubleAttribute(edge.getIdentifier(), parameters[i]);
				param.setValue(value);
				scenarios[currentlySelectedScenario].setParameter(parameters[i], value);
			} else { //If the reaction had no parameter defined, we load the default value
				Double defaultValue = scenarios[currentlySelectedScenario].getDefaultParameterValue(parameters[i]);
				if (defaultValue == null) {
					param.setValue(1.0);
				} else {
					param.setValue(defaultValue);
				}
			}
			//}
			Dimension prefSize = param.getPreferredSize();
			prefSize.width *= 1.5;
			param.setPreferredSize(prefSize);
			if (parameters.length == 1) { //If we have only one parameter, we show a slider for the parameter
				final JSlider parSlider = new JSlider(1, 5, 3);
				Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
				labelTable.put(new Integer(1), new JLabel("v. slow"));
				labelTable.put(new Integer(2), new JLabel("slow"));
				labelTable.put(new Integer(3), new JLabel("medium"));
				labelTable.put(new Integer(4), new JLabel("fast"));
				labelTable.put(new Integer(5), new JLabel("v. fast"));
				parSlider.setLabelTable(labelTable);
				parSlider.setPaintLabels(true);
				parSlider.setMajorTickSpacing(1);
				parSlider.setPaintTicks(true);
				parSlider.setSnapToTicks(true);
				prefSize = parSlider.getPreferredSize();
				prefSize.width *= 1.5;
				parSlider.setPreferredSize(prefSize);
				Box parSliBox = new Box(BoxLayout.Y_AXIS);
				param.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						param.setEnabled(true);
						parSlider.setEnabled(false);
					}
				});
				parSlider.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						parSlider.setEnabled(true);
						param.setEnabled(false);
						double multiplicator = (Double)(param.getValue()) / 0.004;
						double exp = Math.log(multiplicator) / Math.log(2);
						int sliderValue = (int)(exp + 3);
						if (sliderValue < 1) {
							parSlider.setValue(1);
						} else if (sliderValue > 5) {
							parSlider.setValue(5);
						} else {
							parSlider.setValue(sliderValue);
						}
					}
				});
				parSlider.addChangeListener(new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						int exp = parSlider.getValue() - 3;
						double multiplicator = Math.pow(2.0, exp);
						param.setValue(0.004 * multiplicator);
					}
				});
				parSliBox.add(param);
				parSliBox.add(parSlider);
				param.setEnabled(true);
				parSlider.setEnabled(false);
				parametersBox.add(new LabelledField(parameters[i], parSliBox));
			} else {
				parametersBox.add(new LabelledField(parameters[i], param));
			}
		}
		parametersBox.validate();
		
		//inputReactantsBox is the box that contains the links from formula variables to reactants (nodes) in the network. Of course, this box contains something as long as there are parameters declared as "linked to the network" in the user formula
		updateReactantsBox(edge, inputReactantsBox, true, selectedScenario.listLinkedVariables());
		
		//Update the index of the currently selected scenario, so that if the user changes scenario we will be able to know which we were using before, and thus correctly convert the parameters between the two
		previouslySelectedScenario = 0;
		for (int i=0;i<scenarios.length;i++) {
			if (scenarios[i].equals(selectedScenario)) {
				previouslySelectedScenario = i;
				break;
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void loadInfluencedReactants(Edge edge) {
		influencedReactants = new Vector<ReactantParameter>();
		influenceValues = new Vector<Integer>();
		CyAttributes edgeAttr = Cytoscape.getEdgeAttributes();
		if (edgeAttr.hasAttribute(edge.getIdentifier(), Model.Properties.INFLUENCED_REACTANTS)) {
			List<String> influencedReactantsRead = edgeAttr.getListAttribute(edge.getIdentifier(), Model.Properties.INFLUENCED_REACTANTS);
			influenceValues = edgeAttr.getListAttribute(edge.getIdentifier(), Model.Properties.INFLUENCE_VALUES);
			for (String s : influencedReactantsRead) {
				influencedReactants.add(new ReactantParameter(s));
			}
		} else {
			ReactantParameter downstream = new ReactantParameter(edge.getTarget().getIdentifier(), Model.Properties.ACTIVITY_LEVEL);
			influencedReactants.add(downstream);
			influenceValues.add(1);
		}
	}
	
	private void saveInfluencedReactants(Edge edge) {
		CyAttributes edgeAttr = Cytoscape.getEdgeAttributes();
		List<String> influencedReactantsToWrite = new Vector<String>();
		boolean incrementFound = false;
		for (ReactantParameter influencedReactant : influencedReactants) {
			if (influencedReactant.getReactantIdentifier().equals(edge.getTarget().getIdentifier())) { //If we have found an influence on the downstream reactant of the reaction, use that as "official" influence of the reaction (to show the -> or -| in the graphic representation of the edge)
				edgeAttr.setAttribute(edge.getIdentifier(), Model.Properties.INCREMENT, influenceValues.get(influencedReactants.indexOf(influencedReactant)));
				incrementFound = true;
			}
			influencedReactantsToWrite.add(influencedReactant.toString());
		}
		edgeAttr.setListAttribute(edge.getIdentifier(), Model.Properties.INFLUENCED_REACTANTS, influencedReactantsToWrite);
		edgeAttr.setListAttribute(edge.getIdentifier(), Model.Properties.INFLUENCE_VALUES, influenceValues);
		if (!incrementFound) {
			int increment = 1;
			if (!influenceValues.isEmpty()) { //If I have no idea of which influence to show (because the downstream reactant is not included in the list of influenced reactants (!!)), I choose the first I find.
				increment = influenceValues.get(0);
			} else {
				increment = 1;
			}
			edgeAttr.setAttribute(edge.getIdentifier(), Model.Properties.INCREMENT, increment);
		}
	}
	
	/**
	 * Update the list of parameters shown when a different scenario is chosen
	 * @param edge The edge representing the reaction of which we display the parameter
	 * @param influenceBox The box in which to put the list of influences to reactants is to be put
	 * @param selectedScenario The newly selected scenario
	 */
	/*
	private void updateInfluenceBox(Edge edge, Box influenceBox, Scenario selectedScenario) {
		updateReactantsBox(edge, influenceBox, false, new String[]{});
		if (selectedScenario != null) return;
		for (Component c : influenceBox.getComponents()) { //TODO: please pay attention that here we assume to know the structure of boxes and components created in updateReactantsBox!!
			if (c instanceof LabelledField) {
				LabelledField field = (LabelledField)c;
				String title = field.getTitle();
				final int index = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1; //TODO: È veramente demenziale! Tipo che ci fidiamo di trovare il numero sempre lì, e lo usiamo come indice!
				Component c1 = field.getField();
				if (c1 instanceof Box) {
					Box reactantBox = (Box)c1;
					Component[] content = reactantBox.getComponents();
					if (content.length == 2) { //We have 2 components: the comboboxes for the selection of reactant and property (che schifo!!)
						final JComboBox reactantComboBox = (JComboBox)content[0],
										propertyComboBox = (JComboBox)content[1];
						reactantComboBox.addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								influencedReactants.get(index).setReactantIdentifier(reactantIdentifiers[reactantComboBox.getSelectedIndex()]);
							}
						});

						propertyComboBox.addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								influencedReactants.get(index).setPropertyName(propertyComboBox.getSelectedItem().toString());
							}
						});
					}
					final JRadioButton activatingInfluence = new JRadioButton("Increases"),
									   inhibitingInfluence = new JRadioButton("Decreases");
					ButtonGroup influenceGroup = new ButtonGroup();
					influenceGroup.add(activatingInfluence);
					influenceGroup.add(inhibitingInfluence);
					if (influenceValues.get(index) >= 0) {
						activatingInfluence.setSelected(true);
						inhibitingInfluence.setSelected(false);
					} else {
						activatingInfluence.setSelected(false);
						inhibitingInfluence.setSelected(true);
					}
					activatingInfluence.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							influenceValues.set(index, 1);
						}
					});
					inhibitingInfluence.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							influenceValues.set(index, -1);
						}
					});
					Box incDecBox = new Box(BoxLayout.Y_AXIS);
					incDecBox.add(activatingInfluence);
					incDecBox.add(inhibitingInfluence);
					reactantBox.add(incDecBox);
					JButton removeInfluencedReactant = new JButton("Remove");
					reactantBox.add(removeInfluencedReactant, 0);
				}
			} else {
				//eheh boh
			}
		}
		influenceBox.validate();
	}*/
	
	@SuppressWarnings("unchecked")
	private void updateReactantsBox(final Edge edge, final Box reactantsBox, final boolean input, final String[] inputVariablesList) {
		String[] availableProperties;
		if (!input) {
			availableProperties = new String[] {Model.Properties.ACTIVITY_LEVEL, Model.Properties.QUANTITY};
		} else {
			availableProperties = new String[] {Model.Properties.ACTIVITY_LEVEL, Model.Properties.INACTIVITY_LEVEL, Model.Properties.QUANTITY};
		}
		reactantsBox.removeAll();
		final NodeView nodeViews[];
		final CyNetwork network = Cytoscape.getCurrentNetwork();
		final CyNetworkView networkView = Cytoscape.getCurrentNetworkView();
		CyAttributes edgeAttrib = Cytoscape.getEdgeAttributes();
		nodeViews = new NodeView[network.getNodeCount()];
		Iterator<Node> nodes = (Iterator<Node>) network.nodesIterator();
		for (int i = 0; nodes.hasNext(); i++) {
			Node node = nodes.next();
			nodeViews[i] = networkView.getNodeView(node);
		}
		Map<String, ReactantParameter> linkedVariables = new TreeMap<String, ReactantParameter>();
		if (input) {
			for (String name : inputVariablesList) {
				Object parameter = edgeAttrib.getAttribute(edge.getIdentifier(), name);
				if (parameter != null && parameter instanceof String) {
					linkedVariables.put(name, new ReactantParameter((String)parameter)); //we may have had a parameter with this same name, but with a different value type
				} else {
					linkedVariables.put(name, new ReactantParameter(name, name)); //TODO: we do this just to add a parameter even when we don't have it already linked in the network (maybe it's the first time we load that parameter
				}
			}
		} else {
			int index = 0;
			for (ReactantParameter reactant : influencedReactants) {
				String linkedVariableName = "Target reactant " + (index + 1);
				linkedVariables.put(linkedVariableName, reactant);
				index++;
			}
		}
		
		int idx = 0;
		for (String linkedVariableName : linkedVariables.keySet()) {
			final int index = idx;
			ReactantParameter linkedVariable = linkedVariables.get(linkedVariableName);
			final JComboBox reactantsList = new JComboBox(reactantAliases);
			int selectedIdx = -1;
			if (linkedVariable != null) {
				for (int i=0;i<reactantIdentifiers.length;i++) {
					if (reactantIdentifiers[i].equals(linkedVariable.getReactantIdentifier())) {
						selectedIdx = i;
						break;
					}
				}
				if (selectedIdx != -1) {
					reactantsList.setSelectedIndex(selectedIdx);
				} else {
					int selectedIndex = -1;
					if (input) { //The default is the upstream reactant for the input, and the downstream reactant for the output
						for (int i=0;i<reactantIdentifiers.length;i++) {
							if (reactantIdentifiers[i].equals(edge.getSource().getIdentifier())) {
								selectedIndex = i;
								break;
							}
						}
					} else {
						for (int i=0;i<reactantIdentifiers.length;i++) {
							if (reactantIdentifiers[i].equals(edge.getTarget().getIdentifier())) {
								selectedIndex = i;
								break;
							}
						}
					}
					if (selectedIndex != -1) {
						linkedVariable.setReactantIdentifier(reactantIdentifiers[selectedIndex]); //linkedVariable is actually the real influencedReactant that we want to change
						reactantsList.setSelectedIndex(selectedIndex);
					} else {
						linkedVariable.setReactantIdentifier(reactantIdentifiers[0]);
						reactantsList.setSelectedIndex(0);
					}
				}
			}
			reactantsList.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) { //Show the selected node in the network, so that the user can be sure of which it is.
					for (NodeView v : nodeViews) {
						if (v.getNode().getIdentifier().equals(reactantIdentifiers[reactantsList.getSelectedIndex()])) {
							v.select();
						} else {
							v.unselect();
						}
					}
					networkView.updateView();
					if (!input) {
						influencedReactants.get(index).setReactantIdentifier(reactantIdentifiers[reactantsList.getSelectedIndex()]); //And save the modified node in the list of outputs
					}
				}
			});
			final JComboBox properties = new JComboBox(availableProperties);
			selectedIdx = -1;
			if (linkedVariable != null) {
				for (int i=0;i<availableProperties.length;i++) {
					if (availableProperties[i].equals(linkedVariable.getPropertyName())) {
						selectedIdx = i;
						break;
					}
				}
				if (selectedIdx != -1) {
					properties.setSelectedIndex(selectedIdx);
				} else {
					linkedVariable.setPropertyName(availableProperties[0]); //linkedVariable is actually the real influencedReactant that we want to change
				}
			}
			if (!input) {
				properties.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						influencedReactants.get(index).setPropertyName(properties.getSelectedItem().toString());
					}
				});
			}
			
			Box reactantsListBox = new Box(BoxLayout.X_AXIS);
			if (!input) {
				JButton remove = new JButton("Remove");
				remove.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						influencedReactants.remove(index);
						influenceValues.remove(index);
						updateReactantsBox(edge, reactantsBox, false, inputVariablesList);
					}
				});
				if (linkedVariables.size() > 1) {
					reactantsListBox.add(remove);
				}
			}
			reactantsListBox.add(reactantsList);
			reactantsListBox.add(properties);
			if (!input) {
				final JRadioButton activatingInfluence = new JRadioButton("Increases"),
								   inhibitingInfluence = new JRadioButton("Decreases");
				ButtonGroup influenceGroup = new ButtonGroup();
				influenceGroup.add(activatingInfluence);
				influenceGroup.add(inhibitingInfluence);
				if (influenceValues.get(index) >= 0) {
					activatingInfluence.setSelected(true);
					inhibitingInfluence.setSelected(false);
				} else {
					activatingInfluence.setSelected(false);
					inhibitingInfluence.setSelected(true);
				}
				activatingInfluence.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						influenceValues.set(index, 1);
					}
				});
				inhibitingInfluence.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						influenceValues.set(index, -1);
					}
				});
				Box incDecBox = new Box(BoxLayout.Y_AXIS);
				incDecBox.add(activatingInfluence);
				incDecBox.add(inhibitingInfluence);
				reactantsListBox.add(incDecBox);
			}
			reactantsBox.add(new LabelledField(linkedVariableName, reactantsListBox));
			
			idx++;
		}
		reactantsBox.validate();
	}
}

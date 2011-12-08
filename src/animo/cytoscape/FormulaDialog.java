package animo.cytoscape;


import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import animo.model.FormulaVariable;
import animo.model.Scenario;
import animo.model.UserFormula;

import cytoscape.Cytoscape;

/**
 * The edge dialog contains the settings of a edge.
 * 
 * @author Brend Wanders
 * 
 */
public class FormulaDialog extends JDialog {
	private static final long serialVersionUID = 6630154220142970079L;
	private static final String DECIMAL_FORMAT_STRING = "##.####";
	private static final String SAVE = "Save",
								CANCEL = "Cancel";
	
	private Vector<FormulaVariable> variables = null;
	private Vector<ActionListener> saveListeners = new Vector<ActionListener>();

	/**
	 * @param formula The user-defined formula we want to edit in this dialog
	 * @param isNewFormula Tells us whether the formula is new or not
	 */
	public FormulaDialog(UserFormula formula, boolean isNewFormula) {
		this(Cytoscape.getDesktop(), formula, isNewFormula);
	}
	
	/**
	 * @param owner The window whose controls are disabled while the dialog is open
	 * @param formula The user-defined formula we want to edit in this dialog
	 * @param isNewFormula Tells us whether the formula is new or not
	 */
	public FormulaDialog(final Window owner, final UserFormula formula, final boolean isNewFormula) {
		super(owner, "Edit formula", Dialog.ModalityType.APPLICATION_MODAL);
		
		this.setLayout(new BorderLayout(2, 2));

		JPanel values = new JPanel(new GridBagLayout());
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		
		final JTextField nameField = new JTextField(formula.getName());
		values.add(new LabelledField("Name", nameField), new GridBagConstraints(0, 0, 1, 1, 0.4, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
		nameField.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				nameField.setSelectionStart(0);
				nameField.setSelectionEnd(nameField.getText().length());
			}
			
			@Override
			public void focusLost(FocusEvent e) {
				
			}
		});
		
		final JTextArea formulaArea = new JTextArea(formula.getFormula(), 20, 6);
		JScrollPane formulaScroll = new JScrollPane(formulaArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		values.add(new LabelledField("Formula", formulaScroll), new GridBagConstraints(0, 1, 1, 1, 0.4, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
		
		
		JButton addVariableButton = new JButton("Add variable");
		final Box variablesBox = new Box(BoxLayout.Y_AXIS);
		addVariableButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				variables.add(new FormulaVariable("(Variable name)", true, null, 1.0));
				updateVariablesBox(formula, variablesBox);
			}
		});
		
		variables = new Vector<FormulaVariable>();
		for (FormulaVariable v : formula.getVariables()) { //we use a local copy of the variable definitions, modifying it as we go along. If the Save button is hit, we will simply replace the vector. Otherwise, we discard the changes
			variables.add(new FormulaVariable(v));
		}
		
		updateVariablesBox(formula, variablesBox);
		values.add(addVariableButton, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
		
		JScrollPane variablesScroll = new JScrollPane(variablesBox, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		variablesScroll.getVerticalScrollBar().setUnitIncrement(16);
		values.add(new LabelledField("Variables", variablesScroll), new GridBagConstraints(1, 1, 1, 1, 0.6, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
		
		controls.add(new JButton(new AbstractAction(SAVE) {
			private static final long serialVersionUID = 6593269642268161773L;

			@Override
			public void actionPerformed(ActionEvent e) {
				//Apply changes
				UserFormula editedFormula = new UserFormula(nameField.getText(), formulaArea.getText(), variables);
				/*for (FormulaVariable v : editedFormula.getVariables()) {
					System.err.println("Il valore di default del parametro " + v.getName() + " è " + v.getDefaultParameterValue());
					if (v.isParameter() && formula.getDefaultParameterValue(v.getName()) == null) {
						editedFormula.setDefaultParameterValue(v.getName(), 1.0);
					} else if (v.isParameter() && formula.getDefaultParameterValue(v.getName()) != null) {
						editedFormula.setDefaultParameterValue(v.getName(), formula.getDefaultParameterValue(v.getName()));
					}
				}*/
				
				try {
					if (isNewFormula) {
						Scenario.addNewUserFormula(editedFormula);
					} else {
						Scenario.updateUserFormula(formula, editedFormula); //We mainly need the old formula to be sure that we find it in the list (we look for the name, which may have been modified)
					}
				} catch (Exception ex) {
					StringWriter wr = new StringWriter();
					ex.printStackTrace(new PrintWriter(wr));
					JOptionPane.showMessageDialog(Cytoscape.getDesktop(), ex.getMessage() + ": " + ex + wr.toString(), "Error", JOptionPane.ERROR_MESSAGE);
				}
				
				Cytoscape.firePropertyChange(Cytoscape.ATTRIBUTES_CHANGED, null, null);
				FormulaDialog.this.dispose();
				
				ActionEvent ev = new ActionEvent(FormulaDialog.this, 0, "Save");
				for (ActionListener l : saveListeners) { //Alert all our listeners that we have updated something
					l.actionPerformed(ev);
				}
			}
		}));
		
		controls.add(new JButton(new AbstractAction(CANCEL) {
			private static final long serialVersionUID = 7598205732684536966L;

			@Override
			public void actionPerformed(ActionEvent e) {
				// discard changes
				FormulaDialog.this.dispose();
			}
		}));

		this.add(values, BorderLayout.CENTER);
		this.add(controls, BorderLayout.SOUTH);
	}
	
	/**
	 * When the user hits the Save button, we inform all our saveListeners.
	 * It is useful if someone else has called this dialog and wants to know when it finishes the updates.
	 * @param l
	 */
	public void addSaveListener(ActionListener l) {
		this.saveListeners.add(l);
	}
	
	/**
	 * Update the list of parameters shown when a parameter is added/removed
	 */
	private void updateVariablesBox(final UserFormula formula, final Box variablesBox) {
		boolean had0components = false;
		if (variablesBox.getComponentCount() == 0) {
			had0components = true;
		}
		variablesBox.removeAll();
		for (FormulaVariable v : variables) {
			variablesBox.add(createPanelForVariable(v, formula, variablesBox));
		}
		if (had0components) {
			FormulaDialog.this.pack();
		}
		FormulaDialog.this.validate();
	}
	
	private JPanel createPanelForVariable(final FormulaVariable variable, final UserFormula formula, final Box variablesBox) {
		JPanel varPanel = new JPanel();
		varPanel.setLayout(new GridBagLayout());
		JButton removeVariable = new JButton("Remove");
		removeVariable.setToolTipText("Remove \"" + variable.getName() + "\" from the list of variables.");
		removeVariable.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				variables.remove(variable);
				updateVariablesBox(formula, variablesBox);
			}
		});
		varPanel.add(removeVariable, new GridBagConstraints(0, 0, 1, 2, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
		final JTextField nameField = new JTextField(variable.getName());
		nameField.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				nameField.setSelectionStart(0);
				nameField.setSelectionEnd(nameField.getText().length());
			}

			@Override
			public void focusLost(FocusEvent e) {
				
			}
		});
		nameField.getDocument().addDocumentListener(new DocumentListener() {
			private void changed() {
				variable.setName(nameField.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				changed();
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				changed();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				changed();
			}
		});
		varPanel.add(new LabelledField("Name", nameField), new GridBagConstraints(1, 0, 1, 2, 0.5, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
		final JRadioButton radioParam = new JRadioButton("Parameter"),
						   radioLinked = new JRadioButton("Quantity/activity of a node in the network");
		ButtonGroup varTypeGroup = new ButtonGroup();
		varTypeGroup.add(radioParam);
		varTypeGroup.add(radioLinked);
		varPanel.add(radioParam, new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
		varPanel.add(radioLinked, new GridBagConstraints(2, 1, 2, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
		
		DecimalFormat format = new DecimalFormat(DECIMAL_FORMAT_STRING);
		format.setMinimumFractionDigits(8);
		final JFormattedTextField defaultValueField = new JFormattedTextField(format);
		defaultValueField.setValue(variable.getDefaultParameterValue());
		defaultValueField.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				defaultValueField.setSelectionStart(0);
				defaultValueField.setSelectionEnd(defaultValueField.getText().length());
			}
			
			@Override
			public void focusLost(FocusEvent e) {
				
			}
		});
		defaultValueField.getDocument().addDocumentListener(new DocumentListener() {
			private void changed() {
				double val;
				try {
					val = Double.parseDouble(defaultValueField.getValue().toString());
				} catch (Exception ex) {
					val = 1.0;
				}
				variable.setDefaultParameterValue(val);
			}
			
			@Override
			public void changedUpdate(DocumentEvent e) {
				changed();
			}
			
			@Override
			public void insertUpdate(DocumentEvent e) {
				changed();
			}
			
			@Override
			public void removeUpdate(DocumentEvent e) {
				changed();
			}
		});
		varPanel.add(new LabelledField("Default value", defaultValueField), new GridBagConstraints(3, 0, 1, 1, 0.5, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
		
		/*String[] linkedOptions = new String[]{UserFormula.UPSTREAM_REACTANT_ACTIVITY, UserFormula.DOWNSTREAM_REACTANT_ACTIVITY};
		final JComboBox comboLinked = new JComboBox(linkedOptions);
		if (!variable.isParameter() && variable.getLinkedValue() != null) {
			for (String s : linkedOptions) {
				if (s.equals(variable.getLinkedValue())) {
					comboLinked.setSelectedItem(s);
				}
			}
		}
		comboLinked.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				variable.setLinkedValue(comboLinked.getSelectedItem().toString());
			}
		});
		varPanel.add(comboLinked, new GridBagConstraints(3, 1, 1, 1, 0.5, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));*/
		
		if (variable.isParameter()) {
			radioParam.setSelected(true);
			radioLinked.setSelected(false);
			//comboLinked.setEnabled(false);
			defaultValueField.setEnabled(true);
		} else {
			radioLinked.setSelected(true);
			radioParam.setSelected(false);
			//comboLinked.setEnabled(true);
			defaultValueField.setEnabled(false);
		}
		ActionListener listener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				variable.setParameter(radioParam.isSelected());
				defaultValueField.setEnabled(variable.isParameter());
				defaultValueField.setValue(variable.getDefaultParameterValue());
				/*comboLinked.setEnabled(!variable.isParameter());
				if (!radioParam.isSelected()) {
					variable.setLinkedValue(comboLinked.getSelectedItem().toString());
				}*/
			}
		};
		radioParam.addActionListener(listener);
		radioLinked.addActionListener(listener);
		
		return varPanel;
	}

}

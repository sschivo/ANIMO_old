package animo.cytoscape;


import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import animo.ANIMOBackend;
import animo.exceptions.ANIMOException;

import cytoscape.Cytoscape;
import cytoscape.CytoscapeInit;
import cytoscape.data.attr.MultiHashMapListener;
import cytoscape.plugin.CytoscapePlugin;
import cytoscape.view.CytoscapeDesktop;
import cytoscape.view.cytopanels.CytoPanel;

/**
 * The ANIMO Cytoscape plugin main class.
 * 
 * @author Brend Wanders
 */
public class ANIMOPlugin extends CytoscapePlugin {
	private final File configurationFile;
	private static final String SECONDS_PER_POINT = "seconds per point";

	/**
	 * Mandatory constructor.
	 */
	public ANIMOPlugin() {
		this.configurationFile = CytoscapeInit.getConfigFile("ANIMO-configuration.xml");

		try {
			ANIMOBackend.initialise(this.configurationFile);
			System.setProperty("java.security.policy", CytoscapeInit.getConfigFile("ANIMO-security.policy").getAbsolutePath());

			CytoPanel p = Cytoscape.getDesktop().getCytoPanel(SwingConstants.WEST);
			p.add("ANIMO", this.setupPanel(this));
			
			PropertyChangeListener pcl = new ANIMOPropertyChangeListener();
			Cytoscape.getPropertyChangeSupport().addPropertyChangeListener(Cytoscape.NETWORK_CREATED, pcl); //Add all visual mappings
			Cytoscape.getPropertyChangeSupport().addPropertyChangeListener(Cytoscape.NETWORK_LOADED, pcl); //Make arrows "smooth"
			Cytoscape.getPropertyChangeSupport().addPropertyChangeListener(CytoscapeDesktop.NETWORK_VIEW_CREATED, pcl); //Add right-click menus
			Cytoscape.getPropertyChangeSupport().addPropertyChangeListener(Cytoscape.NETWORK_MODIFIED, pcl); //Add/remove nodes
			
		} catch (ANIMOException e) {
			// show error panel
			CytoPanel p = Cytoscape.getDesktop().getCytoPanel(SwingConstants.WEST);
			JPanel errorPanel = new JPanel(new BorderLayout());
			JTextArea message = new JTextArea(e.getMessage());
			message.setEditable(false);
			JScrollPane viewport = new JScrollPane(message);
			errorPanel.add(viewport, BorderLayout.CENTER);
			p.add("ANIMO", errorPanel);
		}
	}
	
	
	/**
	 * Constructs the panel
	 * 
	 * @param plugin the plugin for which the control panel is constructed.
	 * @return
	 */
	private JPanel setupPanel(ANIMOPlugin plugin) {
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout(2, 2));

		// the button container
		JPanel buttons = new JPanel(new GridLayout(5, 1, 2, 2));
		
		//This part allows the user to choose whether to perform the computations on the local machine or on a remote machine.
		Box uppaalBox = new Box(BoxLayout.Y_AXIS);
		final JRadioButton localUppaal = new JRadioButton("Local"),
					 remoteUppaal = new JRadioButton("Remote");
		ButtonGroup uppaalGroup = new ButtonGroup();
		uppaalGroup.add(localUppaal);
		uppaalGroup.add(remoteUppaal);
		final Box serverBox = new Box(BoxLayout.Y_AXIS);
		final JTextField serverName = new JTextField("my.server.com"),
				   serverPort = new JFormattedTextField("1234");
		remoteUppaal.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				boolean sel = remoteUppaal.isSelected();
				serverName.setEnabled(sel);
				serverPort.setEnabled(sel);
			}
		});
		localUppaal.setSelected(true);
		remoteUppaal.setSelected(false);
		serverName.setEnabled(false);
		serverPort.setEnabled(false);
		//serverBox.add(serverName);
		//serverBox.add(serverPort);
		Box sBox = new Box(BoxLayout.X_AXIS);
		sBox.add(new JLabel("Server"));
		sBox.add(serverName);
		serverBox.add(Box.createVerticalStrut(10));
		serverBox.add(sBox);
		Box pBox = new Box(BoxLayout.X_AXIS);
		pBox.add(new JLabel("Port"));
		pBox.add(serverPort);
		serverBox.add(Box.createVerticalStrut(10));
		serverBox.add(pBox);
		serverBox.add(Box.createVerticalStrut(10));
		
		Box localUppaalBox = new Box(BoxLayout.X_AXIS);
		localUppaalBox.add(Box.createHorizontalStrut(5));
		localUppaalBox.add(localUppaal);
		localUppaalBox.add(Box.createGlue());
		uppaalBox.add(localUppaalBox);
		//uppaalBox.add(remoteUppaal);
		remoteUppaal.setOpaque(true);
		final ComponentTitledBorder border = new ComponentTitledBorder(remoteUppaal, serverBox, BorderFactory.createEtchedBorder());
		localUppaal.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				serverBox.repaint();
			}
		});
		serverBox.setBorder(border);
		uppaalBox.add(serverBox);
		buttons.add(uppaalBox);
		
		//This part allows the user to choose between simulation run(s) and Statistical Model Checking
		final JRadioButton normalUppaal = new JRadioButton("Simulation"),
						   smcUppaal = new JRadioButton("SMC");
		ButtonGroup modelCheckingGroup = new ButtonGroup();
		modelCheckingGroup.add(normalUppaal);
		modelCheckingGroup.add(smcUppaal);
		
		final JCheckBox computeAverage = new JCheckBox("Average of");
		final JCheckBox computeStdDev = new JCheckBox("Show standard deviation as error bars");
		computeStdDev.setToolTipText(computeStdDev.getText());
		final JFormattedTextField timeTo = new JFormattedTextField(240);
		final JFormattedTextField nSimulationRuns = new JFormattedTextField(10);
		final JTextField smcFormula = new JTextField("Pr[<=50](<> MK2 > 50)");
		timeTo.setToolTipText("Plot activity levels up to this time point (real-life MINUTES).");
		nSimulationRuns.setToolTipText("Number of simulations of which to show the average. NO statistical guarantees!");
		smcFormula.setToolTipText("Give an answer to this probabilistic query (times in real-life MINUTES).");
		normalUppaal.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (normalUppaal.isSelected()) {
					timeTo.setEnabled(true);
					computeAverage.setEnabled(true);
					nSimulationRuns.setEnabled(computeAverage.isSelected());
					computeStdDev.setEnabled(computeAverage.isSelected());
					smcFormula.setEnabled(false);
				} else {
					timeTo.setEnabled(false);
					computeAverage.setEnabled(false);
					nSimulationRuns.setEnabled(false);
					computeStdDev.setEnabled(false);
					smcFormula.setEnabled(true);
				}
			}
		});
		computeAverage.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (computeAverage.isSelected() && normalUppaal.isSelected()) {
					nSimulationRuns.setEnabled(true);
					computeStdDev.setEnabled(true);
				} else {
					nSimulationRuns.setEnabled(false);
					computeStdDev.setEnabled(false);
				}
			}
		});
		normalUppaal.setSelected(true);
		smcUppaal.setSelected(false);
		timeTo.setEnabled(true);
		computeAverage.setEnabled(true);
		computeAverage.setSelected(false);
		computeStdDev.setEnabled(false);
		computeStdDev.setSelected(false);
		nSimulationRuns.setEnabled(false);
		smcFormula.setEnabled(false);
		Box modelCheckingBox = new Box(BoxLayout.Y_AXIS);
		final Box normalBox = new Box(BoxLayout.Y_AXIS);
		Box smcBox = new Box(BoxLayout.X_AXIS);
		//Box normalUppaalBox = new Box(BoxLayout.X_AXIS);
		//normalUppaalBox.add(normalUppaal);
		//normalUppaalBox.add(Box.createGlue());
		//normalBox.add(normalUppaalBox);
		Box timeToBox = new Box(BoxLayout.X_AXIS);
		timeToBox.add(new JLabel("until"));
		timeToBox.add(timeTo);
		timeToBox.add(new JLabel("minutes"));
		normalBox.add(timeToBox);
		Box averageBox = new Box(BoxLayout.X_AXIS);
		averageBox.add(computeAverage);
		averageBox.add(nSimulationRuns);
		averageBox.add(new JLabel("runs"));
		normalBox.add(averageBox);
		Box stdDevBox = new Box(BoxLayout.X_AXIS);
		stdDevBox.add(computeStdDev);
		stdDevBox.add(Box.createGlue());
		normalBox.add(stdDevBox);
		smcBox.add(smcUppaal);
		smcBox.add(smcFormula);
		normalUppaal.setOpaque(true);
		final ComponentTitledBorder border2 = new ComponentTitledBorder(normalUppaal, normalBox, BorderFactory.createEtchedBorder());
		smcUppaal.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				normalBox.repaint();
			}
		});
		normalBox.setBorder(border2);
		modelCheckingBox.add(normalBox);
		modelCheckingBox.add(smcBox);
		buttons.add(modelCheckingBox);
		
		Box buttonsBox = new Box(BoxLayout.Y_AXIS);
		
		final JButton changeSecondsPerPointbutton = new JButton();
		changeSecondsPerPointbutton.setToolTipText("Click here to change the number of seconds to which a single simulation step corresponds");
		new ChangeSecondsAction(plugin, changeSecondsPerPointbutton); //This manages the button for changing the number of seconds per UPPAAL time unit
		//buttons.add(changeSecondsPerPointbutton);
		Box changeSecondsPerPointbuttonBox = new Box(BoxLayout.X_AXIS);
		changeSecondsPerPointbuttonBox.add(Box.createGlue());
		changeSecondsPerPointbuttonBox.add(changeSecondsPerPointbutton);
		changeSecondsPerPointbuttonBox.add(Box.createGlue());
		buttonsBox.add(changeSecondsPerPointbuttonBox);
		Cytoscape.getNetworkAttributes().getMultiHashMap().addDataListener(new MultiHashMapListener() {

			@Override
			public void allAttributeValuesRemoved(String arg0, String arg1) {
				
			}

			@Override
			public void attributeValueAssigned(String objectKey, String attributeName,
					Object[] keyIntoValue, Object oldAttributeValue, Object newAttributeValue) {
				
				if (attributeName.equals(SECONDS_PER_POINT)) {
					if (newAttributeValue != null) {
						double value = 10;
						try {
							value = Double.parseDouble(newAttributeValue.toString());
						} catch (Exception e) {
							value = 10;
						}
						DecimalFormat df = new DecimalFormat("#.########");
						changeSecondsPerPointbutton.setText(df.format(value) + " seconds/step");
					} else {
						changeSecondsPerPointbutton.setText("Choose seconds/step");
					}
				}
			}

			@Override
			public void attributeValueRemoved(String arg0, String arg1,
					Object[] arg2, Object arg3) {
				
			}
		});

		//The "Analyse network" button: perform the requested analysis on the current network with the given parameters
		JButton runButton = new JButton(new RunAction(plugin, remoteUppaal, serverName, serverPort, smcUppaal, timeTo, nSimulationRuns, computeStdDev, smcFormula));
		//buttons.add(runButton);
		Box runButtonBox = new Box(BoxLayout.X_AXIS);
		runButtonBox.add(Box.createGlue());
		runButtonBox.add(runButton);
		runButtonBox.add(Box.createGlue());
		buttonsBox.add(runButtonBox);
		
		/*JButton augmentButton = new JButton(new AugmentAction(plugin));
		//buttons.add(augmentButton);
		Box augmentButtonBox = new Box(BoxLayout.X_AXIS);
		augmentButtonBox.add(Box.createGlue());
		augmentButtonBox.add(augmentButton);
		augmentButtonBox.add(Box.createGlue());
		buttonsBox.add(augmentButtonBox);*/
		/*JButton loadScenariosButton = new JButton(new Action() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				Scenario.loadScenarios();
			}
			
			@Override
			public void setEnabled(boolean b) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void removePropertyChangeListener(PropertyChangeListener listener) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void putValue(String key, Object value) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public boolean isEnabled() {
				return true;
			}
			
			@Override
			public Object getValue(String key) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public void addPropertyChangeListener(PropertyChangeListener listener) {
				// TODO Auto-generated method stub
				
			}
		});
		loadScenariosButton.setText("Load scenarios");
		Box loadScenariosButtonBox = new Box(BoxLayout.X_AXIS);
		loadScenariosButtonBox.add(Box.createGlue());
		loadScenariosButtonBox.add(loadScenariosButton);
		loadScenariosButtonBox.add(Box.createGlue());
		buttonsBox.add(loadScenariosButtonBox);*/
		
		buttons.add(buttonsBox);
		
		panel.add(buttons, BorderLayout.NORTH);

		return panel;
	}

}

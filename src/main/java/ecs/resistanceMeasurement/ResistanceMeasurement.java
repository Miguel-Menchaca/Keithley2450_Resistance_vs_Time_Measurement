package ecs.resistanceMeasurement;

import java.util.List;
import java.util.ArrayList;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import javax.swing.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import java.util.prefs.Preferences;

public class ResistanceMeasurement extends JFrame{
	
	private final XYSeries resistanceSeries = new XYSeries("Resistance");
	private XYSeriesCollection resistanceDataset;
	private int plotCounter = 1;
	
	private JTextField voltage;
	private JTextField time;
	private JTextField complianceCurrent;
	private JTextField currentRange;
	private JTextField sampleInterval;
	private JTextField nplc;
	private JTextField folderPathField;
	private JTextField outputFilenameField;
	private JTextArea outputArea;
	
	private final Preferences prefs = Preferences.userNodeForPackage(getClass());
	
	private JPanel chartContainer;
	private JFreeChart resistanceChart;
	
	private ChartPanel resistanceChartPanel;
	private JComboBox<String> plotSelector;
	
	private JButton browseButton;

	private Process pythonProcess;
	private BufferedWriter output;
	private Thread readerThread;
	
	// Path to Python executable in virtual environment
	private static final String PYTHON_VENV_PATH = getPythonPath();
	
	
	public ResistanceMeasurement() {
		initialize();
	}
	
	/**
	 * Determines the correct Python executable path
	 * Looks for venv in the same directory as the JAR/classes
	 */
	private static String getPythonPath() {
		try {
			// Get the directory where the application is running
			String appDir = new File(ResistanceMeasurement.class
				.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.toURI()
				.getPath())
				.getParent();
						
			// Check for venv in application directory
			File venvPython = new File(appDir, "venv/Scripts/python.exe");
			if (venvPython.exists()) {
				return venvPython.getAbsolutePath();
			}
			
			// Fallback: check in current working directory
			venvPython = new File("venv/Scripts/python.exe");
			if (venvPython.exists()) {
				return venvPython.getAbsolutePath();
			}
			
			// Last resort: use system Python
			System.err.println("WARNING: Virtual environment not found. Using system Python.");
			return "python";
			
		} catch (Exception e){
			System.err.println("Error determining Python path: " + e.getMessage());
			return "python";
		}
	}
	
	/**
	 * Get the Python script path
	 */
	private String getPythonScriptPath() {
		try {
			// First, try the project root directory (for development in Eclipse)
			File scriptFile = new File("Resistance_Keithley.py");
			if (scriptFile.exists()) {
				return scriptFile.getAbsolutePath();
			}
			
			// Then try relative to the JAR location
			String appDir = new File(ResistanceMeasurement.class
					.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI()
					.getPath())
					.getParent();
			
			scriptFile = new File(appDir, "Resistance_Keithley.py");
			if (scriptFile.exists()) {
				return scriptFile.getAbsolutePath();
			}
			
			// Try scripts subfolder
			scriptFile = new File("scripts/Resistance_Keithley.py");
			if (scriptFile.exists()) {
				return scriptFile.getAbsolutePath();
			}
			
			throw new IOException("Python script not found: Resistance_Keithley.py");
			
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this,
					"Error: Python script not found!\n" + e.getMessage(),
					"Script Error",
					JOptionPane.ERROR_MESSAGE);
			return "Resistance_Keithley.py";
		}
	}
	
	
	private void initialize() {
		
		// Verify python environment on startup
		verifyPythonEnvironment();
		
		// ------------------ WINDOW SETTINGS ------------ //
		setTitle("Keithley Resistance Measurement");
		setBounds(200, 200, 1200, 900);
		//setBounds(200, 200, 800, 600);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		getContentPane().setLayout(new BorderLayout());
		setVisible(true);
		
		// --------------Buttons and output ---------------//
				JButton startButton = new JButton("Start Measurement");
				JButton stopButton = new JButton("Stop Measurement");
				stopButton.setVisible(false); // stop button initially hidden
				JButton loadCsvButton = new JButton("Load plot");
				JButton clearChartButton = new JButton("Clear chart");
				
				
				
				// ------------ Buttons actions ------------- //
				startButton.addActionListener(e -> startMeasurement(startButton, stopButton));
				stopButton.addActionListener(e -> stopMeasurement(startButton, stopButton));
				loadCsvButton.addActionListener(e -> loadCsvFile());
				clearChartButton.addActionListener(e -> clearPlots());
		
				// ------- go to main menu button ----------
				//JButton backButton = new JButton("Back to Menu");
				//backButton.addActionListener(e-> {
				//	new StartupWindow();
				//	dispose();
				//});
				

				outputArea = new JTextArea(6,30);  
				outputArea.setEditable(false);
				JScrollPane scrollPane = new JScrollPane(outputArea);
		
				// ----- bottom panel --------
				JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
				//bottomPanel.add(backButton);
				bottomPanel.add(startButton);
				bottomPanel.add(stopButton);
				bottomPanel.add(loadCsvButton);
				bottomPanel.add(clearChartButton);

		// -------------- Input Parameters Panel ----------------//
				
		JPanel inputPanel = new JPanel(new GridLayout(1, 2));  // Two columns
		inputPanel.setBorder(BorderFactory.createTitledBorder("Input Parameters"));
		

		//
		// LEFT SIDE — Chrono Parameters
		//
		JPanel leftParams = new JPanel(new GridLayout(6, 2, 4, 4));
		


		leftParams.add(new JLabel("Applied Voltage (V):"));
		voltage = new JTextField(prefs.get("Voltage", "1"));
		leftParams.add(voltage);
		
		leftParams.add(new JLabel("Application Time (s):"));
		time = new JTextField(prefs.get("Time","3"));
		leftParams.add(time);
		
		// These fill last rows visually (so both panels look balanced)
		leftParams.add(new JLabel(""));
		leftParams.add(new JLabel(""));
		leftParams.add(new JLabel(""));
		leftParams.add(new JLabel(""));



		//
		// RIGHT SIDE — Additional Parameters
		//

		JPanel rightParams = new JPanel(new GridLayout(6, 2, 4, 4));
		
		rightParams.add(new JLabel("Sample Interval (s):"));
		sampleInterval = new JTextField(prefs.get("sampleInterval", "AUTO"));
		rightParams.add(sampleInterval);

		rightParams.add(new JLabel("Compliance Current (A):"));
		complianceCurrent = new JTextField(prefs.get("complianceCurrent", "1"));
		rightParams.add(complianceCurrent);

		rightParams.add(new JLabel("Current Range (A):"));
		currentRange = new JTextField(prefs.get("currentRange", "1"));
		rightParams.add(currentRange);

		rightParams.add(new JLabel("NPLC:"));
		nplc = new JTextField(prefs.get("nplc", "1"));
		rightParams.add(nplc);
		
		// These fill last rows visually (so both panels look balanced)
		leftParams.add(new JLabel(""));
		leftParams.add(new JLabel(""));
		leftParams.add(new JLabel(""));
		leftParams.add(new JLabel(""));

		
		inputPanel.add(leftParams);
		inputPanel.add(rightParams);
		
		// ------------------ Saving Options Panel -------------//
		
		JPanel savePanel = new JPanel(new GridBagLayout());
		savePanel.setBorder(BorderFactory.createTitledBorder("Saving Options"));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5,5,5,5);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		
		// ---- Row 1: Folder path and browse button ------ //
		gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
		savePanel.add(new JLabel("Save Folder:"), gbc);
		
		gbc.gridx = 1; gbc.weightx = 1.0;
		folderPathField = new JTextField();
		savePanel.add(folderPathField, gbc);
		
		gbc.gridx = 2; gbc.weightx = 0;
		browseButton = new JButton("Browse");
		browseButton.addActionListener(e -> {
		    JFileChooser chooser = new JFileChooser();
		    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		    chooser.setDialogTitle("Select Save Folder");
		    int result = chooser.showOpenDialog(this);
		    if (result == JFileChooser.APPROVE_OPTION) {
		        folderPathField.setText(chooser.getSelectedFile().getAbsolutePath());
		    }
		});
		savePanel.add(browseButton, gbc);
		
		// ----------- Row 2: Output filename --------------------- //
		gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0;
		savePanel.add(new JLabel("Output Filename:"), gbc);

		gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
		outputFilenameField = new JTextField(); 
		savePanel.add(outputFilenameField, gbc);
		
		
		// -------------------- Real time Chart --------------------- //
				
		// resistance dataset
		resistanceDataset = new XYSeriesCollection();
		//resistanceDataset.addSeries(resistanceSeries);
				
		// resistance chart
		resistanceChart = ChartFactory.createXYLineChart(
				"Resistance vs Time", "Time (s)", "Resistance (Ω)", resistanceDataset
			);
		// XYPlot object of resistance chart for scaling
		XYPlot resistancePlot = resistanceChart.getXYPlot();
		resistancePlot.getRangeAxis().setAutoRange(true);
		//resistancePlot.getRangeAxis().setLowerBound(0);
		//resistancePlot.getRangeAxis().setUpperBound(1000);
	
		resistancePlot.getDomainAxis().setAutoRange(true);
		//resistancePlot.getDomainAxis().setLowerBound(0);
		//resistancePlot.getDomainAxis().setUpperBound(100);
		
		//chart panels
		
		resistanceChartPanel = new ChartPanel(resistanceChart);
		
		// --------- Add dropdown to toggle between plots --------//
		plotSelector = new JComboBox<>(new String[]{"Resistance vs Time"});
		plotSelector.addActionListener(e -> switchPlot());
		
		// ----------- Chart Selector ---------- //
		chartContainer = new JPanel(new BorderLayout());
		chartContainer.add(plotSelector, BorderLayout.NORTH);
		
		// The first plot showing is the current plot
		chartContainer.add(resistanceChartPanel, BorderLayout.CENTER);
		add(chartContainer, BorderLayout.CENTER);
		
		
		
		// --- MAIN WINDOW LAYOUT ---
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.add(inputPanel);
		topPanel.add(savePanel);
		getContentPane().add(topPanel, BorderLayout.NORTH);
		
		getContentPane().add(bottomPanel, BorderLayout.SOUTH);
		getContentPane().add(scrollPane, BorderLayout.EAST);
	}
	
	
	/**
	 * Verify Python environment and dependencies on startup
	 */
	private void verifyPythonEnvironment() {
		try {
			ProcessBuilder pb = new ProcessBuilder(PYTHON_VENV_PATH, "--version");
			pb.redirectErrorStream(true);
			Process process = pb.start();
			
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String version = reader.readLine();
			
			int exitCode = process.waitFor();
			
			if (exitCode == 0 && version != null) {
				System.out.println("Python environment verified: " + version);
				System.out.println("Using Python: " + PYTHON_VENV_PATH);
			} else {
				showPythonWarning();
			}
			
		} catch (Exception e) {
			showPythonWarning();
		}
	}
	
	private void showPythonWarning() {
		JOptionPane.showMessageDialog(this,
				"Warning: Python virtual environment not found or not working.\n" +
				"Please ensure 'venv' folder exists in the application directory.\n\n" +
				"To create it, run:\n" +
				"  python -m venv venv\n" +
				"  venv\\Scripts\\activate\n" +
				"  pip install -r requirements.txt",
				"Python Environment Warning",
				JOptionPane.WARNING_MESSAGE);
	}
	
	// ------------------ Plot switching method ------------ //
	private void switchPlot() {
		
		chartContainer.remove(resistanceChartPanel);
		
		String selected = (String) plotSelector.getSelectedItem();
		
		if(selected.equals("Resistance vs Time")) {
			chartContainer.add(resistanceChartPanel, BorderLayout.CENTER);
		}
		chartContainer.revalidate();
		chartContainer.repaint();
	}
	
	// ---------------- MEASUREMENT CONTROL ---------------- //
	private void startMeasurement(JButton startButton, JButton stopButton) {
		
		String folderPath = folderPathField.getText().trim();
		String outputName = outputFilenameField.getText().trim();
		
		// Validation for empty fields
		if (folderPath.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"Please select a save folder before starting the measurement.",
					"Missing Save Folder",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		if (outputName.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"Please enter an output filename before starting the measurement.",
					"Missing Output Filename",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		
	    // Folder validations
	    File outputFolder = new File(folderPath);
	    if (!outputFolder.exists()) {
	        JOptionPane.showMessageDialog(this, 
	            "Folder does not exist:\n" + folderPath, 
	            "Folder Not Found", JOptionPane.ERROR_MESSAGE);
	        return;
	    }
	    if (!outputFolder.isDirectory()) {
	        JOptionPane.showMessageDialog(this, 
	            "Not a folder:\n" + folderPath, 
	            "Not a Folder", JOptionPane.ERROR_MESSAGE);
	        return;
	    }
	    if (!outputFolder.canWrite()) {
	        JOptionPane.showMessageDialog(this, 
	            "No write access:\n" + folderPath, 
	            "Permission Error", JOptionPane.ERROR_MESSAGE);
	        return;
	    }
		
	    // FILENAME VALIDATIONS
		if (!isValidFilename(outputName)) {
		     JOptionPane.showMessageDialog(this, 
		            "Invalid filename!\n" +
		            "Use only letters, numbers, -, _, ( ), \n" +
		            "No: / \\ : * ? \" < > | .exe", 
		            "Invalid Filename", JOptionPane.ERROR_MESSAGE);
		    return;
		}
		    
		if (outputName.contains(" ")) {
		     JOptionPane.showMessageDialog(this, 
		            "Filename contains spaces - Python may fail.\n" +
		            "Use underscores (_) instead: my_measurement", 
		            "Spaces Detected", JOptionPane.WARNING_MESSAGE);
		    return;
		}
		    
		if (outputName.toLowerCase().endsWith(".py") || 
		    outputName.toLowerCase().endsWith(".exe") ||
		    outputName.toLowerCase().endsWith(".jar")) {
		    JOptionPane.showMessageDialog(this, 
		            "Avoid .py, .exe, .jar extensions - conflicts with code files.", 
		            "Bad Extension", JOptionPane.WARNING_MESSAGE);
		    return;
		}
		    
		// Path too long (Windows limit)
		String fullPath = folderPath + "/" + outputName + ".csv";
		if (fullPath.length() > 260) {
		    JOptionPane.showMessageDialog(this, "Full path too long (max 260 chars).", 
		            "Path Too Long", JOptionPane.WARNING_MESSAGE);
		    return;
		}	
		
		resistanceDataset.removeAllSeries();
		plotCounter = 1;
		
		XYSeries liveSeries = new XYSeries("Run " + plotCounter++);
		resistanceDataset.addSeries(liveSeries);
		
		
		try {
			String scriptPath = getPythonScriptPath();
	        List<String> command = new ArrayList<>();
	        command.add(PYTHON_VENV_PATH);
	        command.add(scriptPath);
	        command.add(voltage.getText());
	        command.add(time.getText());
	        command.add(sampleInterval.getText());
	        command.add(currentRange.getText());
	        command.add(nplc.getText());
	        command.add(complianceCurrent.getText());
	        command.add(folderPathField.getText());
	        command.add(outputFilenameField.getText());
	        
			// Prepare Python command with arguments
			ProcessBuilder pb = new ProcessBuilder(command);
			
			pb.redirectErrorStream(true);
			pythonProcess = pb.start();
			output = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
			
			outputArea.append("Measurement started...\n");
			outputArea.append("Using Python: " + PYTHON_VENV_PATH + "\n");
			
			startButton.setVisible(false);
			stopButton.setVisible(true);
			
			
			
			readerThread = new Thread(() -> 
				readPythonOutput(pythonProcess.getInputStream(), startButton, stopButton, liveSeries)
			);
			readerThread.start();
			
		} catch (IOException ex) {
			outputArea.append("Error starting Python script: " + ex.getMessage() + "\n");
			ex.printStackTrace();
		}
	}

	private void stopMeasurement(JButton startButton, JButton stopButton){
		try {
			if (output != null) {
				output.write("STOP\n");
				output.flush();
			}
		}catch (IOException ex) {
			ex.printStackTrace();
		}
		// GUI updates buttons
		startButton.setVisible(true);
		stopButton.setVisible(false);
	}
	
	private void readPythonOutput(InputStream inputStream, JButton startButton, JButton stopButton, XYSeries targetSeries) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))){
			String line;
			while ((line = reader.readLine()) != null) {
				final String output = line.trim();
				if (output.isEmpty()) continue;
				
				// Update console area
				SwingUtilities.invokeLater(() -> outputArea.append(output + "\n"));
				
				// GETTING PYTHON DATA
				String[] parts = output.split(",");
				if (parts.length == 4) {
					try {
						double absTime = Double.parseDouble(parts[0]);
						double voltage = Double.parseDouble(parts[1]);
						double current = Double.parseDouble(parts[2]);
						double resistance = Double.parseDouble(parts[3]);
						
						SwingUtilities.invokeLater(() -> {
							targetSeries.add(absTime, resistance);
						});						
					}catch (NumberFormatException ignored) {}
				}
			}
		}catch (IOException e) {
			SwingUtilities.invokeLater(() -> outputArea.append("Measurement finished or error: " + e.getMessage() + "\n"));
		}finally {
			SwingUtilities.invokeLater(() -> {
				startButton.setVisible(true);
				stopButton.setVisible(false);
			});
		}
	}
	
	// Validate filename method
	private boolean isValidFilename(String filename) {
	    // Windows invalid chars
	    String invalidChars = "[\\\\/:*?\"<>|]";
	    return filename.matches("^[a-zA-Z0-9._() -]+$") && 
	           !filename.matches(".*" + invalidChars + ".*");
	}
	
	// ------------------ Loading CSV files method ---------------- //
	private void loadCsvFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				"CSV Files", "csv"));
		
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			plotCsv(file);
		}
	}
	
	private void plotCsv(File csvFile) {
		//currentSeries.clear()
		
		XYSeries series = new XYSeries(csvFile.getName());
		resistanceDataset.addSeries(series);
		
		try (BufferedReader br = new BufferedReader(new java.io.FileReader(csvFile))) {
			String line = br.readLine();  //read csv headers
			
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");
				
				double time = Double.parseDouble(parts[0]); // abs_time_s
				double current = Double.parseDouble(parts[3]); // current_A
				
				series.add(time, current);
			} 
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Failed to load CSV:\n" + ex.getMessage());
		}
	}
	
	private void clearPlots() {
		resistanceDataset.removeAllSeries();
		plotCounter = 1;
	}
	
	
	
}

package ecs.resistanceMeasurement;


public class App {
	
	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(() -> {
			try {
				ResistanceMeasurement window = new ResistanceMeasurement();
				window.setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}	
		});
	}	
}	
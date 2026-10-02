import javax.swing.SwingUtilities;

import ui.PrincipalUI;

public class Main {

    public static void main(String[] args) {
        // Swing no es seguro entre hilos: toda la interfaz se crea en su hilo de eventos.
        SwingUtilities.invokeLater(() -> {
            PrincipalUI ventana = new PrincipalUI();
            ventana.setVisible(true);
        });
    }
}

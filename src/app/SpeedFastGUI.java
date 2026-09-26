package app;

import ui.VentanaPrincipal;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;

public class SpeedFastGUI {

    public static void main(String[] args) {
        // Se fuerza UTF-8 en la consola real del proceso (la que ve la
        // consola de IntelliJ) para que quede consistente con el resto del
        // proyecto (archivos y redirección de consola de la GUI), evitando
        // que tildes/ñ se muestren corruptas si el charset por defecto de la
        // plataforma no es UTF-8 (por ejemplo, en Windows).
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        SwingUtilities.invokeLater(() -> new VentanaPrincipal().setVisible(true));
    }
}
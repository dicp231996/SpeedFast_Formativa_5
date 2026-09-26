package ui;

import javax.swing.*;
import java.awt.*;

public class PanelMenuPrincipal extends JPanel {

    public PanelMenuPrincipal(Navegador navegador) {
        setLayout(new GridLayout(5, 1, 12, 12));
        setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));

        JLabel titulo = new JLabel("SpeedFast 🚀", SwingConstants.CENTER);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 24));

        JButton btnAgregarPedido = new JButton("Añadir Pedido");
        JButton btnVerZonaCarga = new JButton("Ver Zona de Carga");
        JButton btnAsignacion = new JButton("Asignar Pedidos");
        JButton btnGestionRepartidores = new JButton("Gestión de Repartidores");

        btnAgregarPedido.addActionListener(e -> navegador.irA(VentanaPrincipal.AGREGAR));
        btnVerZonaCarga.addActionListener(e -> navegador.irA(VentanaPrincipal.ZONA_CARGA));
        btnAsignacion.addActionListener(e -> navegador.irA(VentanaPrincipal.ASIGNACION));
        btnGestionRepartidores.addActionListener(e -> navegador.irA(VentanaPrincipal.REPARTIDORES));

        // La ejecución de los hilos de entrega YA NO se activa desde aquí:
        // solo puede iniciarse desde la pestaña de Asignación de Pedidos,
        // una vez que una asignación (manual o automática) se completó con
        // éxito y aparece el botón "Realizar Entregas".
        add(titulo);
        add(btnAgregarPedido);
        add(btnVerZonaCarga);
        add(btnAsignacion);
        add(btnGestionRepartidores);
    }
}
package ui;

import data.enumerate.EstadoPedido;
import data.persistence.PedidoDAO;
import model.core.Pedido;
import model.entities.business.ZonaCarga;
import model.entities.dealer.Repartidor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class PanelAsignacion extends JPanel {

    private final ArrayList<Pedido> listaPedidos;
    private final ArrayList<Repartidor> listaRepartidores;
    private final ZonaCarga zonaCarga;
    private final Navegador navegador;
    private final PanelEjecucionHilos panelEjecucionHilos;
    private final PedidoDAO pedidoDAO = new PedidoDAO();

    public PanelAsignacion(Navegador navegador, ArrayList<Pedido> listaPedidos, ArrayList<Repartidor> listaRepartidores,
                           ZonaCarga zonaCarga, PanelEjecucionHilos panelEjecucionHilos) {
        this.navegador = navegador;
        this.listaPedidos = listaPedidos;
        this.listaRepartidores = listaRepartidores;
        this.zonaCarga = zonaCarga;
        this.panelEjecucionHilos = panelEjecucionHilos;
        construirInterfaz();
    }

    private void construirInterfaz() {
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Asignación de Pedidos", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        JPanel panelCentro = new JPanel(new GridLayout(3, 1, 15, 15));
        panelCentro.setBorder(BorderFactory.createEmptyBorder(30, 80, 30, 80));

        JLabel etiqueta = new JLabel("Seleccione el método de asignación:", SwingConstants.CENTER);
        JButton botonAutomatica = new JButton("Automática");
        JButton botonManual = new JButton("Manual");

        botonAutomatica.addActionListener(e -> ejecutarAsignacionAutomatica());
        botonManual.addActionListener(e -> navegador.irA(VentanaPrincipal.ASIGNACION_MANUAL));

        panelCentro.add(etiqueta);
        panelCentro.add(botonAutomatica);
        panelCentro.add(botonManual);

        JButton botonVolver = new JButton("⬅ Volver al menú");
        botonVolver.addActionListener(e -> navegador.volver());
        JPanel panelBoton = new JPanel();
        panelBoton.add(botonVolver);

        add(titulo, BorderLayout.NORTH);
        add(panelCentro, BorderLayout.CENTER);
        add(panelBoton, BorderLayout.SOUTH);
    }

    // Reproduce la lógica de GestorFases.asignarAutomatico. Al terminar,
    // muestra el mensaje de asignación exitosa con el botón "Realizar
    // Entregas": este es el ÚNICO punto de entrada a la ejecución de los
    // hilos de entrega, y dispara esa lógica de inmediato al elegirlo.
    private void ejecutarAsignacionAutomatica() {
        int confirmados = 0;
        int sinRepartidor = 0;

        for (Pedido pedido : listaPedidos) {
            if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
                continue;
            }

            boolean asignado = false;
            for (Repartidor candidato : listaRepartidores) {
                pedido.asignarRepartidor(candidato);
                if (pedido.getRepartidorAsignado() != null) {
                    candidato.agregarPedido(pedido);
                    zonaCarga.agregarPedido(pedido);
                    pedidoDAO.actualizarEstado(pedido); // persiste CONFIRMADO + repartidor asignado
                    confirmados++;
                    asignado = true;
                    break;
                }
            }

            if (!asignado) {
                sinRepartidor++;
            }
        }

        String[] opciones = { "Aceptar", "Realizar Entregas ➜" };
        int opcion = JOptionPane.showOptionDialog(this,
                "Asignación automática completada.\n\n"
                        + "Pedidos confirmados: " + confirmados + "\n"
                        + "Pedidos sin repartidor elegible: " + sinRepartidor,
                "Asignación exitosa", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, opciones, opciones[0]);

        if (opcion == 1) {
            navegador.irA(VentanaPrincipal.EJECUCION);
            panelEjecucionHilos.iniciarEjecucion();
        }
    }
}
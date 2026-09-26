package ui;

import data.enumerate.EstadoPedido;
import data.persistence.PedidoDAO;
import data.util.ControladorEnvios;
import model.core.Pedido;
import model.entities.business.ZonaCarga;
import model.entities.dealer.Repartidor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class PanelAsignacionManual extends JPanel {

    private final ArrayList<Pedido> listaPedidos;
    private final ArrayList<Repartidor> listaRepartidores;
    private final ControladorEnvios controlador;
    private final ZonaCarga zonaCarga;
    private final PanelEjecucionHilos panelEjecucionHilos;
    private final PedidoDAO pedidoDAO = new PedidoDAO();
    private Navegador navegador;

    private JComboBox<Pedido> comboPedidosPendientes;
    private JLabel etiquetaEstado;

    public PanelAsignacionManual(Navegador navegador, ArrayList<Pedido> listaPedidos, ArrayList<Repartidor> listaRepartidores,
                                 ControladorEnvios controlador, ZonaCarga zonaCarga, PanelEjecucionHilos panelEjecucionHilos) {
        this.listaPedidos = listaPedidos;
        this.listaRepartidores = listaRepartidores;
        this.controlador = controlador;
        this.zonaCarga = zonaCarga;
        this.panelEjecucionHilos = panelEjecucionHilos;
        construirInterfaz(navegador);
    }

    private void construirInterfaz(Navegador navegador) {
        this.navegador = navegador;
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Asignación Manual", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        comboPedidosPendientes = new JComboBox<>();
        comboPedidosPendientes.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Pedido) {
                    Pedido p = (Pedido) value;
                    setText(p.getIdPedido() + " | " + p.getTipoPedido() + " | " + p.getDireccionEntrega());
                }
                return this;
            }
        });

        JButton botonAsignar = new JButton("Elegir repartidor para este pedido");
        JButton botonVolver = new JButton("⬅ Volver");
        etiquetaEstado = new JLabel("", SwingConstants.CENTER);

        botonAsignar.addActionListener(e -> asignarSeleccionado());
        botonVolver.addActionListener(e -> navegador.volver());

        JPanel panelCentro = new JPanel(new BorderLayout(10, 15));
        panelCentro.setBorder(BorderFactory.createEmptyBorder(20, 60, 20, 60));

        JPanel panelSuperior = new JPanel(new BorderLayout(5, 5));
        panelSuperior.add(new JLabel("Pedidos pendientes:"), BorderLayout.NORTH);
        panelSuperior.add(comboPedidosPendientes, BorderLayout.CENTER);

        panelCentro.add(panelSuperior, BorderLayout.NORTH);
        panelCentro.add(etiquetaEstado, BorderLayout.CENTER);

        JPanel panelBotones = new JPanel();
        panelBotones.add(botonAsignar);
        panelBotones.add(botonVolver);

        add(titulo, BorderLayout.NORTH);
        add(panelCentro, BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);

        actualizarListaPendientes();
    }

    public void actualizarListaPendientes() {
        comboPedidosPendientes.removeAllItems();
        int pendientes = 0;
        for (Pedido pedido : listaPedidos) {
            if (pedido.getEstado() == EstadoPedido.PENDIENTE) {
                comboPedidosPendientes.addItem(pedido);
                pendientes++;
            }
        }

        etiquetaEstado.setText(pendientes == 0
                ? "No quedan pedidos pendientes por asignar."
                : pendientes + " pedido(s) pendiente(s).");
    }

    private void asignarSeleccionado() {
        Pedido pedidoSeleccionado = (Pedido) comboPedidosPendientes.getSelectedItem();
        if (pedidoSeleccionado == null) {
            return;
        }

        ArrayList<Repartidor> candidatos = controlador.filtrarRepartidoresElegibles(pedidoSeleccionado, listaRepartidores);

        if (candidatos.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No hay repartidores elegibles para el pedido " + pedidoSeleccionado.getIdPedido() + ".",
                    "Sin candidatos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String[] opciones = new String[candidatos.size()];
        for (int i = 0; i < candidatos.size(); i++) {
            Repartidor r = candidatos.get(i);
            opciones[i] = r.getNombreCompleto() + " (carga: " + r.getCantidadPedidosAsignados() + "/5)";
        }

        String seleccion = (String) JOptionPane.showInputDialog(this,
                "Elija el repartidor para el pedido " + pedidoSeleccionado.getIdPedido() + ":",
                "Repartidores elegibles", JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[0]);

        if (seleccion == null) {
            return;
        }

        int indice = Arrays.asList(opciones).indexOf(seleccion);
        Repartidor elegido = candidatos.get(indice);

        pedidoSeleccionado.asignarRepartidor(elegido);
        elegido.agregarPedido(pedidoSeleccionado);
        zonaCarga.agregarPedido(pedidoSeleccionado);
        pedidoDAO.actualizarEstado(pedidoSeleccionado); // persiste CONFIRMADO + repartidor asignado

        String[] opcionesExito = { "Aceptar", "Realizar Entregas ➜" };
        int opcion = JOptionPane.showOptionDialog(this,
                "Pedido " + pedidoSeleccionado.getIdPedido() + " asignado a " + elegido.getNombreCompleto() + ".",
                "Asignación exitosa", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, opcionesExito, opcionesExito[0]);

        actualizarListaPendientes();

        if (opcion == 1) {
            navegador.irA(VentanaPrincipal.EJECUCION);
            panelEjecucionHilos.iniciarEjecucion();
        }
    }
}
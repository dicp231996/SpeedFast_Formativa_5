package ui;

import data.enumerate.EstadoPedido;
import model.core.Pedido;
import service.OperacionNoPermitidaException;
import service.ServicioPedidos;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;

public class PanelZonaCarga extends JPanel {

    private static final String FILTRO_TODOS = "Todos";
    private static final String[] TIPOS = {"Comida", "Encomienda", "Express"};

    private final ServicioPedidos servicioPedidos;

    private ArrayList<Pedido> pedidosOrdenados;
    private ArrayList<Pedido> pedidosFiltrados;
    private DefaultTableModel modeloTabla;
    private JTable tabla;
    private JLabel etiquetaResumen;
    private JComboBox<String> comboFiltro;

    public PanelZonaCarga(ServicioPedidos servicioPedidos, Navegador navegador) {
        this.servicioPedidos = servicioPedidos;
        this.pedidosFiltrados = new ArrayList<>();
        construirInterfaz(navegador);
    }

    private void construirInterfaz(Navegador navegador) {
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Zona de Carga - Pedidos Registrados", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        JPanel panelSuperior = new JPanel(new BorderLayout(5, 5));
        panelSuperior.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));

        etiquetaResumen = new JLabel();
        etiquetaResumen.setFont(etiquetaResumen.getFont().deriveFont(Font.BOLD));

        JPanel panelFiltro = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panelFiltro.add(new JLabel("Filtrar por tipo:"));

        String[] opcionesFiltro = new String[TIPOS.length + 1];
        opcionesFiltro[0] = FILTRO_TODOS;
        System.arraycopy(TIPOS, 0, opcionesFiltro, 1, TIPOS.length);

        comboFiltro = new JComboBox<>(opcionesFiltro);
        comboFiltro.addActionListener(e -> aplicarFiltro((String) comboFiltro.getSelectedItem()));
        panelFiltro.add(comboFiltro);

        panelSuperior.add(etiquetaResumen, BorderLayout.NORTH);
        panelSuperior.add(panelFiltro, BorderLayout.SOUTH);

        String[] columnas = {"N°", "ID Pedido", "Tipo", "Dirección de Entrega", "Distancia (km)", "Estado"};
        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabla = new JTable(modeloTabla);
        tabla.setRowHeight(22);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(tabla);

        JPanel panelCentro = new JPanel(new BorderLayout());
        panelCentro.add(panelSuperior, BorderLayout.NORTH);
        panelCentro.add(scroll, BorderLayout.CENTER);

        JButton botonEliminar = new JButton("🗑 Eliminar Pedido Seleccionado");
        botonEliminar.addActionListener(e -> eliminarPedidoSeleccionado());

        JButton botonVolver = new JButton("⬅ Volver al menú");
        botonVolver.addActionListener(e -> navegador.volver());
        JPanel panelBoton = new JPanel();
        panelBoton.add(botonEliminar);
        panelBoton.add(botonVolver);

        add(titulo, BorderLayout.NORTH);
        add(panelCentro, BorderLayout.CENTER);
        add(panelBoton, BorderLayout.SOUTH);

        actualizarDatos();
    }

    public void actualizarDatos() {
        // Los pedidos ya ENTREGADOS no se muestran aquí: tienen su propia
        // pestaña ("Pedidos Entregados"), respaldada por el historial de la
        // base de datos en vez de esta vista en memoria.
        this.pedidosOrdenados = servicioPedidos.getZonaCarga().listarRegistrados();
        this.pedidosOrdenados.removeIf(pedido -> pedido.getEstado() == EstadoPedido.ENTREGADO);
        this.pedidosOrdenados.sort(Comparator.comparingInt(Pedido::getId));
        actualizarResumen();
        comboFiltro.setSelectedItem(FILTRO_TODOS);
        aplicarFiltro(FILTRO_TODOS);
    }

    private void actualizarResumen() {
        int comida = 0;
        int encomienda = 0;
        int express = 0;

        for (Pedido pedido : pedidosOrdenados) {
            switch (pedido.getTipoPedido()) {
                case "Comida":
                    comida++;
                    break;
                case "Encomienda":
                    encomienda++;
                    break;
                case "Express":
                    express++;
                    break;
                default:
                    break;
            }
        }

        etiquetaResumen.setText(String.format("Comida: %d   |   Encomienda: %d   |   Express: %d   |   Total: %d",
                comida, encomienda, express, pedidosOrdenados.size()));
    }

    private void aplicarFiltro(String filtro) {
        modeloTabla.setRowCount(0);
        pedidosFiltrados = new ArrayList<>();

        for (Pedido pedido : pedidosOrdenados) {
            if (FILTRO_TODOS.equals(filtro) || pedido.getTipoPedido().equals(filtro)) {
                modeloTabla.addRow(new Object[]{
                        pedido.getId(),
                        pedido.getIdPedido(),
                        pedido.getTipoPedido(),
                        pedido.getDireccionEntrega(),
                        pedido.getDistanciaKm(),
                        pedido.getEstado().name()
                });
                pedidosFiltrados.add(pedido);
            }
        }
    }

    // Elimina el pedido actualmente seleccionado en la tabla. El panel ya no
    // decide cuándo está permitido ni cómo mantener consistentes la zona de
    // carga, la lista en memoria y la base de datos: solo le pide a
    // ServicioPedidos que lo elimine, y si la operación no es válida (regla
    // de negocio o fallo de base de datos), el servicio lo avisa lanzando
    // OperacionNoPermitidaException con un mensaje ya listo para mostrar.
    private void eliminarPedidoSeleccionado() {
        int filaSeleccionada = tabla.getSelectedRow();
        if (filaSeleccionada < 0 || filaSeleccionada >= pedidosFiltrados.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona primero un pedido de la tabla.",
                    "Ningún pedido seleccionado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Pedido pedido = pedidosFiltrados.get(filaSeleccionada);

        int confirmacion = JOptionPane.showConfirmDialog(this,
                "¿Eliminar definitivamente el pedido " + pedido.getIdPedido()
                        + " (" + pedido.getDireccionEntrega() + ")?\nEsta acción no se puede deshacer.",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirmacion != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            servicioPedidos.eliminarPedido(pedido);
            actualizarDatos();
            JOptionPane.showMessageDialog(this, "Pedido " + pedido.getIdPedido() + " eliminado correctamente.",
                    "Eliminación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "No se pudo eliminar", JOptionPane.WARNING_MESSAGE);
        }
    }
}
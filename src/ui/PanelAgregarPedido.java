package ui;

import data.enumerate.TipoPedido;
import data.persistence.PedidoDAO;
import data.util.GestorArchivoPedidos;
import model.core.Pedido;
import model.entities.business.ZonaCarga;
import model.entities.order.PedidoComida;
import model.entities.order.PedidoEncomienda;
import model.entities.order.PedidoExpress;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class PanelAgregarPedido extends JPanel {

    private final ArrayList<Pedido> listaPedidos;
    private final ZonaCarga zonaCarga;
    private final PedidoDAO pedidoDAO = new PedidoDAO();

    private JComboBox<TipoPedido> comboTipo;
    private JTextField campoId;
    private JTextField campoDireccion;
    private JSpinner spinnerDistancia;
    private JLabel labelPeso;
    private JSpinner spinnerPeso;

    public PanelAgregarPedido(Navegador navegador, ArrayList<Pedido> listaPedidos, ZonaCarga zonaCarga) {
        this.listaPedidos = listaPedidos;
        this.zonaCarga = zonaCarga;
        construirInterfaz(navegador);
    }

    private void construirInterfaz(Navegador navegador) {
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Añadir Pedido", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        JPanel panelFormulario = new JPanel(new GridLayout(0, 2, 10, 12));
        panelFormulario.setBorder(BorderFactory.createEmptyBorder(20, 60, 10, 60));

        comboTipo = new JComboBox<>(TipoPedido.values());
        campoId = new JTextField();
        campoId.setEditable(false);
        campoDireccion = new JTextField();
        spinnerDistancia = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 999.0, 0.1));
        labelPeso = new JLabel("Peso (kg):");
        spinnerPeso = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 999.0, 0.1));

        panelFormulario.add(new JLabel("Tipo de pedido:"));
        panelFormulario.add(comboTipo);
        panelFormulario.add(new JLabel("ID (autogenerado):"));
        panelFormulario.add(campoId);
        panelFormulario.add(new JLabel("Dirección de entrega:"));
        panelFormulario.add(campoDireccion);
        panelFormulario.add(new JLabel("Distancia (km):"));
        panelFormulario.add(spinnerDistancia);
        panelFormulario.add(labelPeso);
        panelFormulario.add(spinnerPeso);

        JButton botonGuardar = new JButton("Guardar Pedido");
        JButton botonVolver = new JButton("⬅ Volver al menú");

        JPanel panelBotones = new JPanel();
        panelBotones.add(botonGuardar);
        panelBotones.add(botonVolver);

        comboTipo.addActionListener(e -> actualizarFormulario());
        botonGuardar.addActionListener(e -> guardarPedido());
        botonVolver.addActionListener(e -> navegador.volver());

        add(titulo, BorderLayout.NORTH);
        add(panelFormulario, BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);

        actualizarFormulario();
    }

    public void actualizarFormulario() {
        TipoPedido tipoSeleccionado = (TipoPedido) comboTipo.getSelectedItem();
        campoId.setText(GestorArchivoPedidos.siguienteId(tipoSeleccionado, listaPedidos));

        boolean esEncomienda = tipoSeleccionado == TipoPedido.ENCOMIENDA;
        labelPeso.setVisible(esEncomienda);
        spinnerPeso.setVisible(esEncomienda);
    }

    private void guardarPedido() {
        TipoPedido tipoSeleccionado = (TipoPedido) comboTipo.getSelectedItem();
        String id = campoId.getText();
        String direccion = campoDireccion.getText().trim();
        double distancia = (double) spinnerDistancia.getValue();

        if (direccion.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debe ingresar una dirección de entrega.",
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Pedido nuevoPedido;

        switch (tipoSeleccionado) {
            case COMIDA:
                nuevoPedido = new PedidoComida(id, direccion, distancia);
                break;
            case EXPRESS:
                nuevoPedido = new PedidoExpress(id, direccion, distancia);
                break;
            case ENCOMIENDA:
                double peso = (double) spinnerPeso.getValue();
                nuevoPedido = new PedidoEncomienda(id, direccion, distancia, peso);
                break;
            default:
                return;
        }

        listaPedidos.add(nuevoPedido);
        zonaCarga.agregarPedido(nuevoPedido);
        pedidoDAO.insertar(nuevoPedido);

        JOptionPane.showMessageDialog(this, "Pedido " + id + " guardado correctamente.",
                "Pedido registrado", JOptionPane.INFORMATION_MESSAGE);

        campoDireccion.setText("");
        actualizarFormulario();
    }
}
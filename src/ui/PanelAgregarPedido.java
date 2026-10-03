package ui;

import data.enumerate.TipoPedido;
import data.util.GestorArchivoPedidos;
import model.core.Pedido;
import model.entities.client.Cliente;
import model.entities.order.PedidoComida;
import model.entities.order.PedidoEncomienda;
import model.entities.order.PedidoExpress;
import service.OperacionNoPermitidaException;
import service.ServicioClientes;
import service.ServicioPedidos;

import javax.swing.*;
import java.awt.*;

public class PanelAgregarPedido extends JPanel {

    private final ServicioPedidos servicioPedidos;
    private final ServicioClientes servicioClientes;

    private JComboBox<TipoPedido> comboTipo;
    private JComboBox<Cliente> comboCliente;
    private JTextField campoId;
    private JTextField campoDireccion;
    private JSpinner spinnerDistancia;
    private JLabel labelPeso;
    private JSpinner spinnerPeso;

    public PanelAgregarPedido(Navegador navegador, ServicioPedidos servicioPedidos, ServicioClientes servicioClientes) {
        this.servicioPedidos = servicioPedidos;
        this.servicioClientes = servicioClientes;
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
        comboCliente = new JComboBox<>();
        campoId = new JTextField();
        campoId.setEditable(false);
        campoDireccion = new JTextField();
        spinnerDistancia = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 999.0, 0.1));
        labelPeso = new JLabel("Peso (kg):");
        spinnerPeso = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 999.0, 0.1));

        panelFormulario.add(new JLabel("Cliente:"));
        panelFormulario.add(comboCliente);
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
        campoId.setText(GestorArchivoPedidos.siguienteId(tipoSeleccionado, servicioPedidos.getListaPedidos()));

        boolean esEncomienda = tipoSeleccionado == TipoPedido.ENCOMIENDA;
        labelPeso.setVisible(esEncomienda);
        spinnerPeso.setVisible(esEncomienda);

        // Refresca la nómina de clientes cada vez que se vuelve a mostrar
        // este panel, por si se registró uno nuevo desde "Gestión de
        // Clientes" mientras tanto. Se intenta conservar el cliente que ya
        // estuviera seleccionado.
        Object clienteSeleccionado = comboCliente.getSelectedItem();
        comboCliente.removeAllItems();
        for (Cliente cliente : servicioClientes.getListaClientes()) {
            comboCliente.addItem(cliente);
        }
        if (clienteSeleccionado != null) {
            comboCliente.setSelectedItem(clienteSeleccionado);
        }
    }

    private void guardarPedido() {
        Cliente clienteSeleccionado = (Cliente) comboCliente.getSelectedItem();
        if (clienteSeleccionado == null) {
            JOptionPane.showMessageDialog(this,
                    "No hay ningún cliente seleccionado. Si todavía no tienes clientes registrados, "
                            + "ve primero a \"Gestión de Clientes\" y registra al menos uno.",
                    "Falta seleccionar un cliente", JOptionPane.WARNING_MESSAGE);
            return;
        }

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

        nuevoPedido.setCliente(clienteSeleccionado);

        // El servicio se encarga de mantener consistentes la lista en
        // memoria, la zona de carga y la base de datos: el panel ya no
        // conoce esos detalles.
        try {
            servicioPedidos.registrarPedido(nuevoPedido);
            JOptionPane.showMessageDialog(this, "Pedido " + id + " guardado correctamente.",
                    "Pedido registrado", JOptionPane.INFORMATION_MESSAGE);

            campoDireccion.setText("");
            actualizarFormulario();
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Error al registrar", JOptionPane.ERROR_MESSAGE);
        }
    }
}
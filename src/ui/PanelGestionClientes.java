package ui;

import model.entities.client.Cliente;
import service.OperacionNoPermitidaException;
import service.ServicioClientes;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

// Panel de Gestión de Clientes: muestra la nómina completa y permite
// registrar, modificar y eliminar clientes. Toda la lógica de persistencia y
// las reglas de negocio viven en ServicioClientes; este panel solo arma la
// tabla y le pide al servicio la operación que corresponda. Es la misma
// nómina que alimenta el combo de selección de cliente en "Añadir Pedido".
public class PanelGestionClientes extends JPanel {

    private final ServicioClientes servicioClientes;

    private DefaultTableModel modeloTabla;
    private JTable tabla;
    private JLabel etiquetaResumen;

    public PanelGestionClientes(Navegador navegador, ServicioClientes servicioClientes) {
        this.servicioClientes = servicioClientes;
        construirInterfaz(navegador);
    }

    private void construirInterfaz(Navegador navegador) {
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Gestión de Clientes - Nómina", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        etiquetaResumen = new JLabel();
        etiquetaResumen.setFont(etiquetaResumen.getFont().deriveFont(Font.BOLD));
        etiquetaResumen.setHorizontalAlignment(SwingConstants.CENTER);
        etiquetaResumen.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        String[] columnas = {"RUT", "Nombre", "Teléfono", "Dirección", "Correo"};
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
        panelCentro.add(etiquetaResumen, BorderLayout.NORTH);
        panelCentro.add(scroll, BorderLayout.CENTER);

        JButton botonNuevo = new JButton("➕ Registrar Nuevo Cliente");
        botonNuevo.addActionListener(e -> abrirFormularioNuevoCliente());

        JButton botonModificar = new JButton("✏ Modificar Cliente Seleccionado");
        botonModificar.addActionListener(e -> abrirFormularioModificarCliente());

        JButton botonEliminar = new JButton("🗑 Eliminar Cliente Seleccionado");
        botonEliminar.addActionListener(e -> eliminarClienteSeleccionado());

        JButton botonVolver = new JButton("⬅ Volver al menú");
        botonVolver.addActionListener(e -> navegador.volver());

        JPanel panelBotones = new JPanel();
        panelBotones.add(botonNuevo);
        panelBotones.add(botonModificar);
        panelBotones.add(botonEliminar);
        panelBotones.add(botonVolver);

        add(titulo, BorderLayout.NORTH);
        add(panelCentro, BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);

        actualizarDatos();
    }

    public void actualizarDatos() {
        ArrayList<Cliente> listaClientes = servicioClientes.getListaClientes();

        modeloTabla.setRowCount(0);
        for (Cliente cliente : listaClientes) {
            modeloTabla.addRow(new Object[]{
                    cliente.getRut(),
                    cliente.getNombreCompleto(),
                    cliente.getTelefono(),
                    cliente.getDireccion(),
                    cliente.getCorreo() != null ? cliente.getCorreo() : "N/D"
            });
        }

        etiquetaResumen.setText(String.format("Total clientes: %d", listaClientes.size()));
    }

    private void abrirFormularioNuevoCliente() {
        JTextField campoRut = new JTextField();
        JTextField campoNombre = new JTextField();
        JTextField campoTelefono = new JTextField();
        JTextField campoDireccion = new JTextField();
        JTextField campoCorreo = new JTextField();

        JPanel panelFormulario = new JPanel(new GridLayout(0, 2, 8, 8));
        panelFormulario.add(new JLabel("RUT:"));
        panelFormulario.add(campoRut);
        panelFormulario.add(new JLabel("Nombre completo:"));
        panelFormulario.add(campoNombre);
        panelFormulario.add(new JLabel("Teléfono:"));
        panelFormulario.add(campoTelefono);
        panelFormulario.add(new JLabel("Dirección:"));
        panelFormulario.add(campoDireccion);
        panelFormulario.add(new JLabel("Correo (opcional):"));
        panelFormulario.add(campoCorreo);

        int opcion = JOptionPane.showConfirmDialog(this, panelFormulario,
                "Registrar Nuevo Cliente", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (opcion != JOptionPane.OK_OPTION) {
            return;
        }

        String rut = campoRut.getText().trim();
        String nombre = campoNombre.getText().trim();
        String telefono = campoTelefono.getText().trim();
        String direccion = campoDireccion.getText().trim();
        String correo = campoCorreo.getText().trim();

        if (rut.isEmpty() || nombre.isEmpty() || direccion.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El RUT, el nombre y la dirección son obligatorios.",
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Cliente nuevoCliente = new Cliente(rut, nombre, telefono, direccion, correo.isEmpty() ? null : correo);

        try {
            servicioClientes.registrarCliente(nuevoCliente);
            actualizarDatos();
            JOptionPane.showMessageDialog(this, "Cliente registrado con éxito.",
                    "Registro exitoso", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Error al registrar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirFormularioModificarCliente() {
        Cliente cliente = obtenerClienteSeleccionado();
        if (cliente == null) {
            return;
        }

        JLabel etiquetaRut = new JLabel(cliente.getRut());
        JTextField campoNombre = new JTextField(cliente.getNombreCompleto());
        JTextField campoTelefono = new JTextField(cliente.getTelefono());
        JTextField campoDireccion = new JTextField(cliente.getDireccion());
        JTextField campoCorreo = new JTextField(cliente.getCorreo() != null ? cliente.getCorreo() : "");

        JPanel panelFormulario = new JPanel(new GridLayout(0, 2, 8, 8));
        panelFormulario.add(new JLabel("RUT:"));
        panelFormulario.add(etiquetaRut);
        panelFormulario.add(new JLabel("Nombre completo:"));
        panelFormulario.add(campoNombre);
        panelFormulario.add(new JLabel("Teléfono:"));
        panelFormulario.add(campoTelefono);
        panelFormulario.add(new JLabel("Dirección:"));
        panelFormulario.add(campoDireccion);
        panelFormulario.add(new JLabel("Correo (opcional):"));
        panelFormulario.add(campoCorreo);

        int opcion = JOptionPane.showConfirmDialog(this, panelFormulario,
                "Modificar Cliente - RUT " + cliente.getRut(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (opcion != JOptionPane.OK_OPTION) {
            return;
        }

        String nombre = campoNombre.getText().trim();
        String telefono = campoTelefono.getText().trim();
        String direccion = campoDireccion.getText().trim();
        String correo = campoCorreo.getText().trim();

        if (nombre.isEmpty() || direccion.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El nombre y la dirección son obligatorios.",
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            servicioClientes.actualizarCliente(cliente, nombre, telefono, direccion,
                    correo.isEmpty() ? null : correo);
            actualizarDatos();
            JOptionPane.showMessageDialog(this, "Cliente actualizado con éxito.",
                    "Actualización exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Error al actualizar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Cliente obtenerClienteSeleccionado() {
        ArrayList<Cliente> listaClientes = servicioClientes.getListaClientes();

        int filaSeleccionada = tabla.getSelectedRow();
        if (filaSeleccionada < 0 || filaSeleccionada >= listaClientes.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona primero un cliente de la tabla.",
                    "Ningún cliente seleccionado", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return listaClientes.get(filaSeleccionada);
    }

    private void eliminarClienteSeleccionado() {
        Cliente cliente = obtenerClienteSeleccionado();
        if (cliente == null) {
            return;
        }

        int confirmacion = JOptionPane.showConfirmDialog(this,
                "¿Eliminar definitivamente al cliente " + cliente.getNombreCompleto()
                        + " (RUT: " + cliente.getRut() + ")?\n\n"
                        + "Los pedidos que tuviera asociados quedarán sin cliente.\n"
                        + "Esta acción no se puede deshacer.",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirmacion != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            servicioClientes.eliminarCliente(cliente);
            actualizarDatos();
            JOptionPane.showMessageDialog(this,
                    "Cliente " + cliente.getNombreCompleto() + " eliminado correctamente.",
                    "Eliminación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "No se pudo eliminar", JOptionPane.WARNING_MESSAGE);
        }
    }
}
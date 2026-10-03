package ui;

import data.enumerate.TipoServicio;
import model.entities.dealer.Repartidor;
import service.OperacionNoPermitidaException;
import service.ServicioRepartidores;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;

// Panel de Gestión de Repartidores: muestra la nómina completa (con la
// cantidad de entregas realizadas hoy por cada uno) y permite registrar y
// eliminar repartidores. Toda la lógica de persistencia y las reglas de
// negocio viven en ServicioRepartidores; este panel solo arma la tabla y le
// pide al servicio la operación que corresponda.
public class PanelGestionRepartidores extends JPanel {

    private final ServicioRepartidores servicioRepartidores;

    private DefaultTableModel modeloTabla;
    private JTable tabla;
    private JLabel etiquetaResumen;

    public PanelGestionRepartidores(Navegador navegador, ServicioRepartidores servicioRepartidores) {
        this.servicioRepartidores = servicioRepartidores;
        construirInterfaz(navegador);
    }

    private void construirInterfaz(Navegador navegador) {
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Gestión de Repartidores - Nómina", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        etiquetaResumen = new JLabel();
        etiquetaResumen.setFont(etiquetaResumen.getFont().deriveFont(Font.BOLD));
        etiquetaResumen.setHorizontalAlignment(SwingConstants.CENTER);
        etiquetaResumen.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        String[] columnas = {"RUT", "Nombre", "Teléfono", "Vehículo", "Tipo de Servicio",
                "Mochila Térmica", "Capacidad Máx (kg)", "Cerca Ubicación", "Entregas Hoy"};
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

        JButton botonNuevo = new JButton("➕ Registrar Nuevo Repartidor");
        botonNuevo.addActionListener(e -> abrirFormularioNuevoRepartidor());

        JButton botonModificar = new JButton("✏ Modificar Repartidor Seleccionado");
        botonModificar.addActionListener(e -> abrirFormularioModificarRepartidor());

        JButton botonEliminar = new JButton("🗑 Eliminar Repartidor Seleccionado");
        botonEliminar.addActionListener(e -> eliminarRepartidorSeleccionado());

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
        ArrayList<Repartidor> listaRepartidores = servicioRepartidores.getListaRepartidores();
        Map<String, Integer> entregasHoy = servicioRepartidores.contarEntregasHoy();

        modeloTabla.setRowCount(0);
        int totalEntregasHoy = 0;

        for (Repartidor repartidor : listaRepartidores) {
            int entregas = entregasHoy.getOrDefault(repartidor.getRut(), 0);
            totalEntregasHoy += entregas;

            modeloTabla.addRow(new Object[]{
                    repartidor.getRut(),
                    repartidor.getNombreCompleto(),
                    repartidor.getTelefono(),
                    repartidor.getVehiculo() != null ? repartidor.getVehiculo() : "N/D",
                    repartidor.getTipoServicio(),
                    repartidor.isTieneMochilaTermica() ? "Sí" : "No",
                    repartidor.getCapacidadPesoMax(),
                    repartidor.isEstaCercaUbicacion() ? "Sí" : "No",
                    entregas
            });
        }

        etiquetaResumen.setText(String.format("Total repartidores: %d   |   Entregas realizadas hoy: %d",
                listaRepartidores.size(), totalEntregasHoy));
    }

    private void abrirFormularioNuevoRepartidor() {
        JTextField campoRut = new JTextField();
        JTextField campoNombre = new JTextField();
        JTextField campoTelefono = new JTextField();
        JTextField campoVehiculo = new JTextField();
        JComboBox<TipoServicio> comboTipoServicio = new JComboBox<>(TipoServicio.values());
        JCheckBox checkMochila = new JCheckBox("Tiene mochila térmica");
        JTextField campoCapacidad = new JTextField("0.0");
        JCheckBox checkCerca = new JCheckBox("Está cerca de la ubicación");

        JPanel panelFormulario = new JPanel(new GridLayout(0, 2, 8, 8));
        panelFormulario.add(new JLabel("RUT:"));
        panelFormulario.add(campoRut);
        panelFormulario.add(new JLabel("Nombre completo:"));
        panelFormulario.add(campoNombre);
        panelFormulario.add(new JLabel("Teléfono:"));
        panelFormulario.add(campoTelefono);
        panelFormulario.add(new JLabel("Vehículo:"));
        panelFormulario.add(campoVehiculo);
        panelFormulario.add(new JLabel("Tipo de servicio:"));
        panelFormulario.add(comboTipoServicio);
        panelFormulario.add(new JLabel("Capacidad máx. (kg):"));
        panelFormulario.add(campoCapacidad);
        panelFormulario.add(checkMochila);
        panelFormulario.add(checkCerca);

        int opcion = JOptionPane.showConfirmDialog(this, panelFormulario,
                "Registrar Nuevo Repartidor", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (opcion != JOptionPane.OK_OPTION) {
            return;
        }

        String rut = campoRut.getText().trim();
        String nombre = campoNombre.getText().trim();
        String telefono = campoTelefono.getText().trim();
        String vehiculo = campoVehiculo.getText().trim();

        // Validación de formulario (campos vacíos, formato numérico): esto es
        // responsabilidad de la UI, no una regla de negocio, así que se queda
        // aquí en el panel.
        if (rut.isEmpty() || nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El RUT y el nombre son obligatorios.",
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double capacidad;
        try {
            capacidad = Double.parseDouble(campoCapacidad.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "La capacidad máxima debe ser un número válido.",
                    "Dato inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Repartidor nuevoRepartidor = new Repartidor(rut, nombre, telefono,
                (TipoServicio) comboTipoServicio.getSelectedItem(),
                checkMochila.isSelected(), capacidad, checkCerca.isSelected());
        nuevoRepartidor.setVehiculo(vehiculo.isEmpty() ? null : vehiculo);

        try {
            servicioRepartidores.registrarRepartidor(nuevoRepartidor);
            actualizarDatos();
            JOptionPane.showMessageDialog(this, "Repartidor registrado con éxito.",
                    "Registro exitoso", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Error al registrar", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Abre un formulario igual al de "Registrar Nuevo Repartidor" pero
    // pre-cargado con los datos actuales del repartidor seleccionado. El RUT
    // se muestra como texto fijo (no editable): es la clave de negocio que
    // usan Pedido y Entrega para ubicar al repartidor, así que no tiene
    // sentido permitir cambiarlo desde aquí.
    private void abrirFormularioModificarRepartidor() {
        Repartidor repartidor = obtenerRepartidorSeleccionado();
        if (repartidor == null) {
            return;
        }

        JLabel etiquetaRut = new JLabel(repartidor.getRut());
        JTextField campoNombre = new JTextField(repartidor.getNombreCompleto());
        JTextField campoTelefono = new JTextField(repartidor.getTelefono());
        JTextField campoVehiculo = new JTextField(repartidor.getVehiculo() != null ? repartidor.getVehiculo() : "");
        JComboBox<TipoServicio> comboTipoServicio = new JComboBox<>(TipoServicio.values());
        comboTipoServicio.setSelectedItem(repartidor.getTipoServicio());
        JCheckBox checkMochila = new JCheckBox("Tiene mochila térmica", repartidor.isTieneMochilaTermica());
        JTextField campoCapacidad = new JTextField(String.valueOf(repartidor.getCapacidadPesoMax()));
        JCheckBox checkCerca = new JCheckBox("Está cerca de la ubicación", repartidor.isEstaCercaUbicacion());

        JPanel panelFormulario = new JPanel(new GridLayout(0, 2, 8, 8));
        panelFormulario.add(new JLabel("RUT:"));
        panelFormulario.add(etiquetaRut);
        panelFormulario.add(new JLabel("Nombre completo:"));
        panelFormulario.add(campoNombre);
        panelFormulario.add(new JLabel("Teléfono:"));
        panelFormulario.add(campoTelefono);
        panelFormulario.add(new JLabel("Vehículo:"));
        panelFormulario.add(campoVehiculo);
        panelFormulario.add(new JLabel("Tipo de servicio:"));
        panelFormulario.add(comboTipoServicio);
        panelFormulario.add(new JLabel("Capacidad máx. (kg):"));
        panelFormulario.add(campoCapacidad);
        panelFormulario.add(checkMochila);
        panelFormulario.add(checkCerca);

        int opcion = JOptionPane.showConfirmDialog(this, panelFormulario,
                "Modificar Repartidor - RUT " + repartidor.getRut(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (opcion != JOptionPane.OK_OPTION) {
            return;
        }

        String nombre = campoNombre.getText().trim();
        String telefono = campoTelefono.getText().trim();
        String vehiculo = campoVehiculo.getText().trim();

        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El nombre es obligatorio.",
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double capacidad;
        try {
            capacidad = Double.parseDouble(campoCapacidad.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "La capacidad máxima debe ser un número válido.",
                    "Dato inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            servicioRepartidores.actualizarRepartidor(repartidor, nombre, telefono,
                    vehiculo.isEmpty() ? null : vehiculo,
                    (TipoServicio) comboTipoServicio.getSelectedItem(),
                    checkMochila.isSelected(), capacidad, checkCerca.isSelected());
            actualizarDatos();
            JOptionPane.showMessageDialog(this, "Repartidor actualizado con éxito.",
                    "Actualización exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Error al actualizar", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Devuelve el repartidor correspondiente a la fila actualmente
    // seleccionada en la tabla, o null (mostrando un aviso) si no hay
    // ninguna fila seleccionada. Como la tabla se llena recorriendo la lista
    // del servicio en orden y sin filtros, el índice de la fila seleccionada
    // corresponde directamente al índice en esa lista.
    private Repartidor obtenerRepartidorSeleccionado() {
        ArrayList<Repartidor> listaRepartidores = servicioRepartidores.getListaRepartidores();

        int filaSeleccionada = tabla.getSelectedRow();
        if (filaSeleccionada < 0 || filaSeleccionada >= listaRepartidores.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona primero un repartidor de la tabla.",
                    "Ningún repartidor seleccionado", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return listaRepartidores.get(filaSeleccionada);
    }

    // Elimina el repartidor actualmente seleccionado en la tabla. Como la
    // tabla se llena recorriendo la lista del servicio en orden y sin
    // filtros, el índice de la fila seleccionada corresponde directamente al
    // índice en esa lista.
    private void eliminarRepartidorSeleccionado() {
        Repartidor repartidor = obtenerRepartidorSeleccionado();
        if (repartidor == null) {
            return;
        }

        int confirmacion = JOptionPane.showConfirmDialog(this,
                "¿Eliminar definitivamente al repartidor " + repartidor.getNombreCompleto()
                        + " (RUT: " + repartidor.getRut() + ")?\n\n"
                        + "Los pedidos que tuviera asignados quedarán sin repartidor, y su historial "
                        + "de entregas también se eliminará.\nEsta acción no se puede deshacer.",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirmacion != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            servicioRepartidores.eliminarRepartidor(repartidor);
            actualizarDatos();
            JOptionPane.showMessageDialog(this,
                    "Repartidor " + repartidor.getNombreCompleto() + " eliminado correctamente.",
                    "Eliminación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "No se pudo eliminar", JOptionPane.WARNING_MESSAGE);
        }
    }
}
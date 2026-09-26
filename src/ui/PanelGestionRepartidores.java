package ui;

import data.enumerate.TipoServicio;
import data.persistence.EntregaDAO;
import data.persistence.RepartidorDAO;
import model.entities.dealer.Repartidor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;

// Panel de Gestión de Repartidores: muestra la nómina completa (con la
// cantidad de entregas realizadas hoy por cada uno) y permite registrar
// nuevos repartidores, persistiéndolos directamente en la base de datos.
public class PanelGestionRepartidores extends JPanel {

    private final ArrayList<Repartidor> listaRepartidores;
    private final RepartidorDAO repartidorDAO;
    private final EntregaDAO entregaDAO;

    private DefaultTableModel modeloTabla;
    private JLabel etiquetaResumen;

    public PanelGestionRepartidores(Navegador navegador, ArrayList<Repartidor> listaRepartidores) {
        this.listaRepartidores = listaRepartidores;
        this.repartidorDAO = new RepartidorDAO();
        this.entregaDAO = new EntregaDAO();
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
        JTable tabla = new JTable(modeloTabla);
        tabla.setRowHeight(22);
        JScrollPane scroll = new JScrollPane(tabla);

        JPanel panelCentro = new JPanel(new BorderLayout());
        panelCentro.add(etiquetaResumen, BorderLayout.NORTH);
        panelCentro.add(scroll, BorderLayout.CENTER);

        JButton botonNuevo = new JButton("➕ Registrar Nuevo Repartidor");
        botonNuevo.addActionListener(e -> abrirFormularioNuevoRepartidor());

        JButton botonVolver = new JButton("⬅ Volver al menú");
        botonVolver.addActionListener(e -> navegador.volver());

        JPanel panelBotones = new JPanel();
        panelBotones.add(botonNuevo);
        panelBotones.add(botonVolver);

        add(titulo, BorderLayout.NORTH);
        add(panelCentro, BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);

        actualizarDatos();
    }

    public void actualizarDatos() {
        Map<String, Integer> entregasHoy = entregaDAO.contarEntregasHoyPorRepartidor();

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

        boolean exito = repartidorDAO.insertar(nuevoRepartidor);

        if (exito) {
            listaRepartidores.add(nuevoRepartidor);
            actualizarDatos();
            JOptionPane.showMessageDialog(this, "Repartidor registrado con éxito.",
                    "Registro exitoso", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "No se pudo registrar el repartidor. Verifica que el RUT no esté repetido "
                            + "y que la conexión a la base de datos esté disponible.",
                    "Error al registrar", JOptionPane.ERROR_MESSAGE);
        }
    }
}
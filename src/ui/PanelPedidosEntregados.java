package ui;

import model.historial.RegistroPedidoEntregado;
import service.OperacionNoPermitidaException;
import service.ServicioPedidos;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

// Pestaña "Pedidos Entregados con Éxito": muestra el HISTORIAL de entregas
// (tabla PedidoEntregado en la base de datos), independiente de la Zona de
// Carga. Un pedido, apenas llega a ENTREGADO, desaparece de la Zona de Carga
// (ver PanelZonaCarga) y pasa a vivir únicamente aquí. Al estar organizado
// por fecha en la base de datos, este panel permite filtrar por rango de
// fechas y deja a la vista las métricas básicas (totales, km recorridos)
// sobre las que más adelante se pueden construir reportes más completos.
public class PanelPedidosEntregados extends JPanel {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ServicioPedidos servicioPedidos;

    private DefaultTableModel modeloTabla;
    private JTable tabla;
    private JLabel etiquetaResumen;
    private JSpinner spinnerDesde;
    private JSpinner spinnerHasta;

    // Mantiene la correspondencia entre cada fila visible de la tabla y su
    // RegistroPedidoEntregado real, en el mismo orden en que se agregaron a
    // modeloTabla, para poder ubicar el registro exacto detrás de la fila
    // seleccionada (editar/eliminar necesitan el objeto completo, no solo lo
    // que se ve en pantalla).
    private ArrayList<RegistroPedidoEntregado> registrosMostrados = new ArrayList<>();

    public PanelPedidosEntregados(Navegador navegador, ServicioPedidos servicioPedidos) {
        this.servicioPedidos = servicioPedidos;
        construirInterfaz(navegador);
    }

    private void construirInterfaz(Navegador navegador) {
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Pedidos Entregados con Éxito", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        // --- Filtro por rango de fechas ---------------------------------
        Date hoy = new Date();
        Date haceUnMes = restarDias(hoy, 30);

        spinnerDesde = new JSpinner(new SpinnerDateModel(haceUnMes, null, null, Calendar.DAY_OF_MONTH));
        spinnerDesde.setEditor(new JSpinner.DateEditor(spinnerDesde, "dd-MM-yyyy"));

        spinnerHasta = new JSpinner(new SpinnerDateModel(hoy, null, null, Calendar.DAY_OF_MONTH));
        spinnerHasta.setEditor(new JSpinner.DateEditor(spinnerHasta, "dd-MM-yyyy"));

        JButton botonFiltrar = new JButton("Filtrar por fecha");
        JButton botonVerTodo = new JButton("Ver todo el historial");

        botonFiltrar.addActionListener(e -> aplicarFiltroPorFecha());
        botonVerTodo.addActionListener(e -> cargarTodoElHistorial());

        JPanel panelFiltro = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        panelFiltro.add(new JLabel("Desde:"));
        panelFiltro.add(spinnerDesde);
        panelFiltro.add(new JLabel("Hasta:"));
        panelFiltro.add(spinnerHasta);
        panelFiltro.add(botonFiltrar);
        panelFiltro.add(botonVerTodo);

        etiquetaResumen = new JLabel();
        etiquetaResumen.setFont(etiquetaResumen.getFont().deriveFont(Font.BOLD));
        etiquetaResumen.setHorizontalAlignment(SwingConstants.CENTER);
        etiquetaResumen.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

        JPanel panelSuperior = new JPanel(new BorderLayout());
        panelSuperior.add(panelFiltro, BorderLayout.NORTH);
        panelSuperior.add(etiquetaResumen, BorderLayout.SOUTH);

        // --- Tabla del historial -----------------------------------------
        String[] columnas = {"Código", "Tipo", "Dirección de Entrega", "Distancia (km)",
                "Peso (kg)", "Repartidor", "Fecha", "Hora"};
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

        JButton botonEditar = new JButton("✏ Editar Registro");
        botonEditar.addActionListener(e -> editarRegistroSeleccionado());

        JButton botonEliminar = new JButton("🗑 Eliminar Registro");
        botonEliminar.addActionListener(e -> eliminarRegistroSeleccionado());

        JButton botonPapelera = new JButton("♻ Papelera / Restaurar");
        botonPapelera.addActionListener(e -> abrirPapelera());

        JButton botonVolver = new JButton("⬅ Volver al menú");
        botonVolver.addActionListener(e -> navegador.volver());
        JPanel panelBoton = new JPanel();
        panelBoton.add(botonEditar);
        panelBoton.add(botonEliminar);
        panelBoton.add(botonPapelera);
        panelBoton.add(botonVolver);

        add(titulo, BorderLayout.NORTH);
        add(panelCentro, BorderLayout.CENTER);
        add(panelBoton, BorderLayout.SOUTH);

        // Por defecto se muestra el último mes; "Ver todo el historial" trae
        // el resto cuando haga falta.
        aplicarFiltroPorFecha();
    }

    // Se invoca cada vez que se vuelve a mostrar este panel (por ejemplo,
    // justo después de que una nueva entrega se complete), para que el
    // historial refleje lo último sin perder el filtro de fecha activo.
    public void actualizarDatos() {
        aplicarFiltroPorFecha();
    }

    private void aplicarFiltroPorFecha() {
        LocalDate desde = convertirADate((Date) spinnerDesde.getValue());
        LocalDate hasta = convertirADate((Date) spinnerHasta.getValue());

        if (desde.isAfter(hasta)) {
            JOptionPane.showMessageDialog(this,
                    "La fecha \"Desde\" no puede ser posterior a la fecha \"Hasta\".",
                    "Rango de fechas inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        mostrarRegistros(servicioPedidos.listarHistorialEntregados(desde, hasta));
    }

    private void cargarTodoElHistorial() {
        mostrarRegistros(servicioPedidos.listarHistorialEntregados());
    }

    private void mostrarRegistros(ArrayList<RegistroPedidoEntregado> registros) {
        modeloTabla.setRowCount(0);
        registrosMostrados = registros;

        double distanciaTotal = 0.0;
        int comida = 0, encomienda = 0, express = 0;

        for (RegistroPedidoEntregado registro : registros) {
            modeloTabla.addRow(new Object[]{
                    registro.getCodigoPedido(),
                    registro.getTipoPedido(),
                    registro.getDireccionDestino(),
                    registro.getDistanciaKm(),
                    registro.getPesoKg() != null ? registro.getPesoKg() : "-",
                    registro.getNombreRepartidor() != null ? registro.getNombreRepartidor() : "N/D",
                    registro.getFechaEntrega().toLocalDate().format(FORMATO_FECHA),
                    registro.getFechaEntrega().toLocalTime().format(FORMATO_HORA)
            });

            distanciaTotal += registro.getDistanciaKm();
            switch (registro.getTipoPedido()) {
                case "Comida": comida++; break;
                case "Encomienda": encomienda++; break;
                case "Express": express++; break;
                default: break;
            }
        }

        etiquetaResumen.setText(String.format(
                "Total entregados: %d   |   Comida: %d   |   Encomienda: %d   |   Express: %d   |   Distancia total recorrida: %.1f km",
                registros.size(), comida, encomienda, express, distanciaTotal));
    }

    // Devuelve el registro correspondiente a la fila seleccionada en la
    // tabla (según la lista guardada en el último mostrarRegistros), o null
    // (mostrando un aviso) si no hay ninguna fila seleccionada.
    private RegistroPedidoEntregado obtenerRegistroSeleccionado() {
        int filaSeleccionada = tabla.getSelectedRow();
        if (filaSeleccionada < 0 || filaSeleccionada >= registrosMostrados.size()) {
            JOptionPane.showMessageDialog(this, "Selecciona primero un registro de la tabla.",
                    "Ningún registro seleccionado", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return registrosMostrados.get(filaSeleccionada);
    }

    // Abre un formulario para editar los datos del registro seleccionado.
    // El código de pedido, el tipo y la fecha/hora de entrega se muestran
    // fijos: identifican el hecho histórico y no tiene sentido alterarlos.
    private void editarRegistroSeleccionado() {
        RegistroPedidoEntregado registro = obtenerRegistroSeleccionado();
        if (registro == null) {
            return;
        }

        JTextField campoDireccion = new JTextField(registro.getDireccionDestino());
        JTextField campoDistancia = new JTextField(String.valueOf(registro.getDistanciaKm()));
        JTextField campoPeso = new JTextField(registro.getPesoKg() != null ? String.valueOf(registro.getPesoKg()) : "");
        JTextField campoRutRepartidor = new JTextField(registro.getRutRepartidor() != null ? registro.getRutRepartidor() : "");
        JTextField campoNombreRepartidor = new JTextField(registro.getNombreRepartidor() != null ? registro.getNombreRepartidor() : "");

        JPanel panelFormulario = new JPanel(new GridLayout(0, 2, 8, 8));
        panelFormulario.add(new JLabel("Código de pedido:"));
        panelFormulario.add(new JLabel(registro.getCodigoPedido() + " (" + registro.getTipoPedido() + ")"));
        panelFormulario.add(new JLabel("Dirección de entrega:"));
        panelFormulario.add(campoDireccion);
        panelFormulario.add(new JLabel("Distancia (km):"));
        panelFormulario.add(campoDistancia);
        panelFormulario.add(new JLabel("Peso (kg, vacío si no aplica):"));
        panelFormulario.add(campoPeso);
        panelFormulario.add(new JLabel("RUT repartidor:"));
        panelFormulario.add(campoRutRepartidor);
        panelFormulario.add(new JLabel("Nombre repartidor:"));
        panelFormulario.add(campoNombreRepartidor);

        int opcion = JOptionPane.showConfirmDialog(this, panelFormulario,
                "Editar Registro - " + registro.getCodigoPedido(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (opcion != JOptionPane.OK_OPTION) {
            return;
        }

        double distancia;
        Double peso;
        try {
            distancia = Double.parseDouble(campoDistancia.getText().trim());
            String textoPeso = campoPeso.getText().trim();
            peso = textoPeso.isEmpty() ? null : Double.parseDouble(textoPeso);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Distancia y peso deben ser números válidos.",
                    "Dato inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String direccion = campoDireccion.getText().trim();
        if (direccion.isEmpty()) {
            JOptionPane.showMessageDialog(this, "La dirección de entrega es obligatoria.",
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String rutRepartidor = campoRutRepartidor.getText().trim();
        String nombreRepartidor = campoNombreRepartidor.getText().trim();

        try {
            servicioPedidos.actualizarRegistroHistorial(registro, direccion, distancia, peso,
                    rutRepartidor.isEmpty() ? null : rutRepartidor,
                    nombreRepartidor.isEmpty() ? null : nombreRepartidor);
            aplicarFiltroPorFecha();
            JOptionPane.showMessageDialog(this, "Registro actualizado con éxito.",
                    "Actualización exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Error al actualizar", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Elimina el registro seleccionado del historial. Queda guardado en la
    // papelera en memoria (ver ♻ Papelera / Restaurar) mientras la aplicación
    // siga abierta; al cerrarla, lo que no se haya restaurado se pierde de
    // verdad, porque ya se borró de la base de datos.
    private void eliminarRegistroSeleccionado() {
        RegistroPedidoEntregado registro = obtenerRegistroSeleccionado();
        if (registro == null) {
            return;
        }

        int confirmacion = JOptionPane.showConfirmDialog(this,
                "¿Eliminar el registro del pedido " + registro.getCodigoPedido() + " del historial?\n\n"
                        + "Quedará disponible en la papelera (♻) para restaurarlo mientras la aplicación "
                        + "siga abierta. Si cierras la aplicación sin restaurarlo, se perderá definitivamente.",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirmacion != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            servicioPedidos.eliminarRegistroHistorial(registro);
            aplicarFiltroPorFecha();
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "No se pudo eliminar", JOptionPane.WARNING_MESSAGE);
        }
    }

    // Diálogo con los registros eliminados en esta misma ejecución y aún no
    // restaurados, con la opción de devolver uno al historial (y a la base
    // de datos).
    private void abrirPapelera() {
        ArrayList<RegistroPedidoEntregado> papelera = servicioPedidos.listarPapeleraHistorial();

        String[] columnas = {"Código", "Tipo", "Dirección", "Repartidor", "Fecha"};
        DefaultTableModel modeloPapelera = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (RegistroPedidoEntregado registro : papelera) {
            modeloPapelera.addRow(new Object[]{
                    registro.getCodigoPedido(),
                    registro.getTipoPedido(),
                    registro.getDireccionDestino(),
                    registro.getNombreRepartidor() != null ? registro.getNombreRepartidor() : "N/D",
                    registro.getFechaEntrega().toLocalDate().format(FORMATO_FECHA)
            });
        }

        JTable tablaPapelera = new JTable(modeloPapelera);
        tablaPapelera.setRowHeight(22);
        tablaPapelera.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPapelera = new JScrollPane(tablaPapelera);
        scrollPapelera.setPreferredSize(new Dimension(520, 220));

        JLabel etiquetaVacia = new JLabel("La papelera está vacía por ahora.", SwingConstants.CENTER);

        JPanel panelPapelera = new JPanel(new BorderLayout());
        panelPapelera.add(papelera.isEmpty() ? etiquetaVacia : scrollPapelera, BorderLayout.CENTER);

        Object[] opciones = papelera.isEmpty()
                ? new Object[]{"Cerrar"}
                : new Object[]{"Restaurar seleccionado", "Cerrar"};

        int opcion = JOptionPane.showOptionDialog(this, panelPapelera, "Papelera del historial de entregas",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opciones, opciones[0]);

        if (papelera.isEmpty() || opcion != 0) {
            return;
        }

        int filaSeleccionada = tablaPapelera.getSelectedRow();
        if (filaSeleccionada < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona primero un registro de la papelera.",
                    "Ningún registro seleccionado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RegistroPedidoEntregado registro = papelera.get(filaSeleccionada);
        try {
            servicioPedidos.restaurarRegistroHistorial(registro);
            aplicarFiltroPorFecha();
            JOptionPane.showMessageDialog(this, "Registro restaurado con éxito al historial.",
                    "Restauración exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (OperacionNoPermitidaException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "No se pudo restaurar", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static Date restarDias(Date fecha, int dias) {
        Calendar calendario = Calendar.getInstance();
        calendario.setTime(fecha);
        calendario.add(Calendar.DAY_OF_MONTH, -dias);
        return calendario.getTime();
    }

    private static LocalDate convertirADate(Date fecha) {
        return fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
}
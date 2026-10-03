package ui;

import data.persistence.ConexionBD;
import data.util.ControladorEnvios;
import model.core.Pedido;
import model.entities.business.ZonaCarga;
import model.entities.client.Cliente;
import model.entities.dealer.Repartidor;
import service.ServicioClientes;
import service.ServicioPedidos;
import service.ServicioRepartidores;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class VentanaPrincipal extends JFrame implements Navegador {

    public static final String MENU = "menu";
    public static final String AGREGAR = "agregar";
    public static final String ZONA_CARGA = "zonaCarga";
    public static final String ASIGNACION = "asignacion";
    public static final String ASIGNACION_MANUAL = "asignacionManual";
    public static final String EJECUCION = "ejecucion";
    public static final String REPARTIDORES = "repartidores";
    public static final String PEDIDOS_ENTREGADOS = "pedidosEntregados";
    public static final String CLIENTES = "clientes";

    private final ServicioPedidos servicioPedidos;
    private final ServicioRepartidores servicioRepartidores;
    private final ServicioClientes servicioClientes;

    // Alias de conveniencia hacia lo que exponen los servicios: varios
    // paneles (PanelEjecucionHilos, PanelAsignacion, PanelAsignacionManual)
    // solo necesitan LEER estas listas, no decidir cómo se persisten, así
    // que siguen recibiéndolas directamente en vez del servicio completo.
    private final ArrayList<Pedido> listaPedidos;
    private final ArrayList<Repartidor> listaRepartidores;
    private final ZonaCarga zonaCarga;
    private final ControladorEnvios controlador;

    private final CardLayout cardLayout;
    private final JPanel panelContenedor;
    private final Deque<String> historial;

    private PanelAgregarPedido panelAgregarPedido;
    private PanelZonaCarga panelZonaCarga;
    private PanelAsignacionManual panelAsignacionManual;
    private PanelEjecucionHilos panelEjecucionHilos;
    private PanelGestionRepartidores panelGestionRepartidores;
    private PanelPedidosEntregados panelPedidosEntregados;
    private PanelGestionClientes panelGestionClientes;

    public VentanaPrincipal() {
        super("SpeedFast - Sistema de Gestión de Despachos");

        // Antes de intentar cargar nada, se verifica la conexión a la base
        // de datos por separado: RepartidorDAO/PedidoDAO atrapan cualquier
        // SQLException y devuelven listas vacías (para no tumbar la GUI), lo
        // que hace que un error de conexión se vea como "no cargó nada" sin
        // explicación. Aquí sí dejamos que el error real llegue y se lo
        // mostramos al usuario en un diálogo antes de seguir.
        try {
            ConexionBD.verificarConexion();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null,
                    "No se pudo conectar a la base de datos 'speedfast_db'.\n\n"
                            + "Motivo: " + e.getMessage()
                            + "\n\nRevisa en ConexionBD.java: que MySQL esté corriendo, que la base "
                            + "y las tablas existan (schema_speedfast.sql), y que el usuario/clave "
                            + "sean correctos. La aplicación seguirá abriendo, pero sin pedidos ni "
                            + "repartidores cargados.",
                    "Error de conexión a la base de datos", JOptionPane.ERROR_MESSAGE);
        }

        // Reemplaza la lectura de pedidos.txt/repartidores.txt: ambas listas
        // se cargan ahora directamente desde la base de datos speedfast_db,
        // a través de la capa de servicio (ServicioPedidos/ServicioRepartidores),
        // que es quien coordina la persistencia con la memoria de la
        // aplicación (zona de carga incluida). Los repartidores se cargan
        // primero porque PedidoDAO necesita esa lista para enlazar cada
        // pedido con el repartidor que tuviera asignado.
        this.servicioRepartidores = new ServicioRepartidores();
        this.servicioRepartidores.cargarDesdeBaseDeDatos();

        this.servicioClientes = new ServicioClientes();
        this.servicioClientes.cargarDesdeBaseDeDatos();

        this.zonaCarga = new ZonaCarga();
        this.servicioPedidos = new ServicioPedidos(zonaCarga);
        this.servicioPedidos.cargarDesdeBaseDeDatos(servicioRepartidores.getListaRepartidores(),
                servicioClientes.getListaClientes());

        this.listaRepartidores = servicioRepartidores.getListaRepartidores();
        this.listaPedidos = servicioPedidos.getListaPedidos();
        this.controlador = new ControladorEnvios();

        this.cardLayout = new CardLayout();
        this.panelContenedor = new JPanel(cardLayout);
        this.historial = new ArrayDeque<>();

        construirInterfaz();
    }

    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(820, 560);
        setLocationRelativeTo(null);
        setResizable(true);

        PanelMenuPrincipal panelMenu = new PanelMenuPrincipal(this);
        panelAgregarPedido = new PanelAgregarPedido(this, servicioPedidos, servicioClientes);
        panelZonaCarga = new PanelZonaCarga(servicioPedidos, this);
        panelEjecucionHilos = new PanelEjecucionHilos(this, listaPedidos, zonaCarga, controlador);
        panelAsignacionManual = new PanelAsignacionManual(this, listaPedidos, listaRepartidores, controlador, servicioPedidos, panelEjecucionHilos);
        PanelAsignacion panelAsignacion = new PanelAsignacion(this, listaPedidos, listaRepartidores, servicioPedidos, panelEjecucionHilos);
        panelGestionRepartidores = new PanelGestionRepartidores(this, servicioRepartidores);
        panelPedidosEntregados = new PanelPedidosEntregados(this, servicioPedidos);
        panelGestionClientes = new PanelGestionClientes(this, servicioClientes);

        panelContenedor.add(panelMenu, MENU);
        panelContenedor.add(panelAgregarPedido, AGREGAR);
        panelContenedor.add(panelZonaCarga, ZONA_CARGA);
        panelContenedor.add(panelAsignacion, ASIGNACION);
        panelContenedor.add(panelAsignacionManual, ASIGNACION_MANUAL);
        panelContenedor.add(panelEjecucionHilos, EJECUCION);
        panelContenedor.add(panelGestionRepartidores, REPARTIDORES);
        panelContenedor.add(panelPedidosEntregados, PEDIDOS_ENTREGADOS);
        panelContenedor.add(panelGestionClientes, CLIENTES);

        setContentPane(panelContenedor);

        historial.push(MENU);
        cardLayout.show(panelContenedor, MENU);
    }

    @Override
    public void irA(String nombrePanel) {
        refrescarPanel(nombrePanel);
        historial.push(nombrePanel);
        cardLayout.show(panelContenedor, nombrePanel);
    }

    @Override
    public void volver() {
        if (historial.size() <= 1) {
            return;
        }
        historial.pop();
        String anterior = historial.peek();
        refrescarPanel(anterior);
        cardLayout.show(panelContenedor, anterior);
    }

    private void refrescarPanel(String nombrePanel) {
        if (ZONA_CARGA.equals(nombrePanel)) {
            panelZonaCarga.actualizarDatos();
        } else if (AGREGAR.equals(nombrePanel)) {
            panelAgregarPedido.actualizarFormulario();
        } else if (ASIGNACION_MANUAL.equals(nombrePanel)) {
            panelAsignacionManual.actualizarListaPendientes();
        } else if (EJECUCION.equals(nombrePanel)) {
            panelEjecucionHilos.actualizarEstadoBoton();
        } else if (REPARTIDORES.equals(nombrePanel)) {
            panelGestionRepartidores.actualizarDatos();
        } else if (PEDIDOS_ENTREGADOS.equals(nombrePanel)) {
            panelPedidosEntregados.actualizarDatos();
        } else if (CLIENTES.equals(nombrePanel)) {
            panelGestionClientes.actualizarDatos();
        }
    }
}
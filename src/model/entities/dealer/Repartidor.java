package model.entities.dealer;

import data.enumerate.EstadoPedido;
import data.enumerate.TipoServicio;
import model.core.Pedido;
import model.core.Persona;
import model.entities.business.ZonaCarga;
import model.interfaces.IRunnable;

// Clase Repartidor hereda de Persona e implementa tanto tu interfaz como la nativa de Java
public class Repartidor extends Persona implements IRunnable, Runnable {

    private static final int CAPACIDAD_MAXIMA_PEDIDOS = 5;

    // Identificador de negocio (columna 'rut' en la tabla Repartidor de la
    // base de datos). Se usa como clave natural para relacionar un Pedido
    // con su repartidor asignado sin tener que exponer el id_repartidor
    // autogenerado de la base de datos dentro del modelo de dominio.
    private String rut;

    // Columna 'vehiculo' de la tabla Repartidor. Es puramente informativo
    // (no participa en ninguna regla de asignación), por eso no está en los
    // constructores y se maneja solo con getter/setter.
    private String vehiculo;

    private TipoServicio tipoServicio;
    private boolean tieneMochilaTermica;
    private double capacidadPesoMax;
    private boolean estaCercaUbicacion;

    private Pedido[] pedidosAsignados;
    private int cantidadPedidosAsignados;

    private ZonaCarga zonaCarga;

    public Repartidor() {
        super();
        this.rut = "SIN-RUT";
        this.tipoServicio = TipoServicio.COMIDA;
        this.tieneMochilaTermica = false;
        this.capacidadPesoMax = 0.0;
        this.estaCercaUbicacion = false;
        this.pedidosAsignados = new Pedido[CAPACIDAD_MAXIMA_PEDIDOS];
        this.cantidadPedidosAsignados = 0;
    }

    // Constructor histórico (sin RUT), usado por ejemplo por la asignación
    // nominal (Pedido.asignarRepartidor(String nombre)), donde no existe un
    // repartidor real de la base de datos detrás. Se le asigna un RUT
    // placeholder para que igual pueda pasar por la capa de persistencia
    // sin romper la restricción NOT NULL de la columna 'rut'.
    public Repartidor(String nombreCompleto, String telefonoContacto, TipoServicio tipoServicio,
                      boolean tieneMochilaTermica, double capacidadPesoMax, boolean estaCercaUbicacion) {
        this("SIN-RUT-" + System.nanoTime(), nombreCompleto, telefonoContacto, tipoServicio,
                tieneMochilaTermica, capacidadPesoMax, estaCercaUbicacion);
    }

    // Constructor completo, usado por RepartidorDAO al reconstruir un
    // repartidor real desde la fila de la base de datos (incluye su RUT).
    public Repartidor(String rut, String nombreCompleto, String telefonoContacto, TipoServicio tipoServicio,
                      boolean tieneMochilaTermica, double capacidadPesoMax, boolean estaCercaUbicacion) {
        super(nombreCompleto, telefonoContacto);
        this.rut = rut;
        this.tipoServicio = tipoServicio;
        this.tieneMochilaTermica = tieneMochilaTermica;
        this.capacidadPesoMax = capacidadPesoMax;
        this.estaCercaUbicacion = estaCercaUbicacion;
        this.pedidosAsignados = new Pedido[CAPACIDAD_MAXIMA_PEDIDOS];
        this.cantidadPedidosAsignados = 0;
    }

    public String getRut() { return rut; }
    public void setRut(String rut) { this.rut = rut; }

    public String getVehiculo() { return vehiculo; }
    public void setVehiculo(String vehiculo) { this.vehiculo = vehiculo; }

    public void setZonaCarga(ZonaCarga zonaCarga) {
        this.zonaCarga = zonaCarga;
    }

    // =========================================================
    // ORQUESTACIÓN COMPLETA DEL CICLO DE ENTREGA
    // =========================================================
    @Override
    public void run() {
        String nombreHilo = Thread.currentThread().getName();
        System.out.println("\n>>> [ZONA DE CARGA - " + nombreHilo + "] " + this.getNombreCompleto()
                + " comienza a retirar carga.");

        if (this.zonaCarga == null) {
            System.out.println("    -> No se ha vinculado ninguna Zona de Carga a este repartidor.");
            return;
        }

        while (true) {
            Pedido pedido = this.zonaCarga.retirarUnPedido(this);

            if (pedido == null) {
                if (this.zonaCarga.estaVacia()) {
                    System.out.println("    -> [ZONA DE CARGA VACÍA] Ya no quedan pedidos por asignar.");
                } else {
                    System.out.println("    -> " + this.getNombreCompleto() + " está en espera de paquetes aptos.");
                }
                break;
            }

            System.out.println("-> " + this.getNombreCompleto()
                    + " retira 1 pedido desde la zona de carga y sale a entregarlo.");

            procesarEntrega(pedido);

            System.out.println("-> " + this.getNombreCompleto()
                    + " completó la entrega y vuelve a la zona de carga por otro pedido.");
        }

        System.out.println("\n>>> [FIN DE RUTA] " + this.getNombreCompleto() + " ha finalizado su recorrido.\n");
    }

    private void procesarEntrega(Pedido pedido) {
        if (pedido.isCancelado()) {
            System.out.println("    -> Pedido " + pedido.getIdPedido() + " fue cancelado antes de iniciar la entrega. Se omite.");
            return;
        }

        System.out.println("-> [ENTREGA] " + this.getNombreCompleto() + " comienza a entregar el pedido "
                + pedido.getIdPedido() + ".");

        pedido.nuevoEstado(EstadoPedido.EN_REPARTO);

        Thread hiloEntrega = new Thread(new model.valueobjects.HiloEntrega(pedido),
                "Entrega-" + pedido.getIdPedido());
        hiloEntrega.start();
        try {
            hiloEntrega.join();
        } catch (InterruptedException e) {
            System.err.println("-> Alerta: El recorrido de " + this.getNombreCompleto() + " fue interrumpido.");
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================
    // GESTIÓN DEL VECTOR FIJO DE PEDIDOS (máx. 5 posiciones)
    // =========================================================
    public Pedido[] getPedidosAsignados() {
        return pedidosAsignados;
    }

    public int getCantidadPedidosAsignados() {
        return cantidadPedidosAsignados;
    }

    public boolean tieneCupoDisponible() {
        return cantidadPedidosAsignados < CAPACIDAD_MAXIMA_PEDIDOS;
    }

    public void agregarPedido(Pedido pedido) {
        if (!tieneCupoDisponible()) {
            System.out.println("-> Alerta: " + this.getNombreCompleto()
                    + " ya alcanzó su capacidad máxima de " + CAPACIDAD_MAXIMA_PEDIDOS + " pedidos.");
            return;
        }
        this.pedidosAsignados[cantidadPedidosAsignados] = pedido;
        cantidadPedidosAsignados++;
    }

    public void removerPedido(Pedido pedido) {
        int indice = -1;
        for (int i = 0; i < cantidadPedidosAsignados; i++) {
            if (this.pedidosAsignados[i] == pedido) {
                indice = i;
                break;
            }
        }

        if (indice == -1) {
            return;
        }

        for (int i = indice; i < cantidadPedidosAsignados - 1; i++) {
            this.pedidosAsignados[i] = this.pedidosAsignados[i + 1];
        }
        this.pedidosAsignados[cantidadPedidosAsignados - 1] = null;
        cantidadPedidosAsignados--;
    }

    public void limpiarPedidos() {
        for (int i = 0; i < cantidadPedidosAsignados; i++) {
            this.pedidosAsignados[i] = null;
        }
        cantidadPedidosAsignados = 0;
    }

    public String getTelefono() {
        return super.getTelefonoContacto();
    }

    public TipoServicio getTipoServicio() { return tipoServicio; }
    public boolean isTieneMochilaTermica() { return tieneMochilaTermica; }
    public double getCapacidadPesoMax() { return capacidadPesoMax; }
    public boolean isEstaCercaUbicacion() { return estaCercaUbicacion; }

    public void setTipoServicio(TipoServicio tipoServicio) { this.tipoServicio = tipoServicio; }
    public void setTieneMochilaTermica(boolean tieneMochilaTermica) { this.tieneMochilaTermica = tieneMochilaTermica; }
    public void setCapacidadPesoMax(double capacidadPesoMax) { this.capacidadPesoMax = capacidadPesoMax; }
    public void setEstaCercaUbicacion(boolean estaCercaUbicacion) { this.estaCercaUbicacion = estaCercaUbicacion; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
                .append("\n   -> Perfil Operativo:")
                .append("\n      | RUT: ").append(this.rut)
                .append("\n      | Vehículo: ").append(this.vehiculo != null ? this.vehiculo : "N/D")
                .append("\n      | Tipo de Servicio: ").append(this.tipoServicio)
                .append("\n      | Mochila Térmica: ").append(this.tieneMochilaTermica ? "Sí" : "No")
                .append("\n      | Capacidad Máx: ").append(this.capacidadPesoMax).append(" kg")
                .append("\n      | Cerca de ubicación: ").append(this.estaCercaUbicacion ? "Sí" : "No")
                .append("\n      | Carga actual: ").append(this.cantidadPedidosAsignados)
                .append("/").append(CAPACIDAD_MAXIMA_PEDIDOS).append(" pedidos asignados");
        return sb.toString();
    }
}
package model.historial;

import java.time.LocalDateTime;

// Representa UNA FILA del historial de pedidos entregados (tabla
// PedidoEntregado). A diferencia de Pedido (la entidad de dominio "viva",
// con comportamiento y reglas de negocio), esto es solo una fotografía de
// solo lectura de cómo quedó un pedido en el momento en que se entregó: no
// tiene comportamiento, no se vincula a un Repartidor/Cliente real, y existe
// únicamente para mostrarse en pantalla o alimentar métricas. Por eso vive
// en su propio paquete (model.historial) en vez de junto a model.core.Pedido.
public class RegistroPedidoEntregado {

    // Clave primaria de la fila en la tabla PedidoEntregado (columna
    // id_pedido_entregado). Antes no se exponía porque el registro solo se
    // mostraba; ahora se necesita para poder editar o eliminar la fila exacta
    // (codigo_pedido no alcanza como clave: nada impide, a futuro, más de un
    // registro histórico asociado al mismo código).
    private final int idPedidoEntregado;
    private final String codigoPedido;
    private final String tipoPedido;
    private final String direccionDestino;
    private final double distanciaKm;
    private final Double pesoKg; // null si no es PedidoEncomienda
    private final String rutRepartidor;
    private final String nombreRepartidor;
    // Igual que rutRepartidor/nombreRepartidor: guardado como texto plano
    // (denormalizado), no como llave foránea a Cliente, para que el
    // historial conserve quién recibió el pedido aunque ese Cliente se
    // elimine más adelante desde la gestión de clientes.
    private final String rutCliente;
    private final String nombreCliente;
    private final LocalDateTime fechaEntrega;

    public RegistroPedidoEntregado(int idPedidoEntregado, String codigoPedido, String tipoPedido,
                                   String direccionDestino, double distanciaKm, Double pesoKg,
                                   String rutRepartidor, String nombreRepartidor,
                                   String rutCliente, String nombreCliente, LocalDateTime fechaEntrega) {
        this.idPedidoEntregado = idPedidoEntregado;
        this.codigoPedido = codigoPedido;
        this.tipoPedido = tipoPedido;
        this.direccionDestino = direccionDestino;
        this.distanciaKm = distanciaKm;
        this.pesoKg = pesoKg;
        this.rutRepartidor = rutRepartidor;
        this.nombreRepartidor = nombreRepartidor;
        this.rutCliente = rutCliente;
        this.nombreCliente = nombreCliente;
        this.fechaEntrega = fechaEntrega;
    }

    // Construye una copia de "original" con algunos campos editados. Como la
    // clase es inmutable (es una fotografía de solo lectura), "editar" un
    // registro significa reemplazarlo por una copia nueva con el mismo id y
    // los valores corregidos; ServicioPedidos es quien persiste esa copia.
    // El cliente, igual que el código/tipo/fecha, no se edita: identifica a
    // quién iba dirigido el pedido entregado, no un dato libre del registro.
    public static RegistroPedidoEntregado conCambios(RegistroPedidoEntregado original, String direccionDestino,
                                                     double distanciaKm, Double pesoKg, String rutRepartidor,
                                                     String nombreRepartidor) {
        return new RegistroPedidoEntregado(original.idPedidoEntregado, original.codigoPedido, original.tipoPedido,
                direccionDestino, distanciaKm, pesoKg, rutRepartidor, nombreRepartidor,
                original.rutCliente, original.nombreCliente, original.fechaEntrega);
    }

    public int getIdPedidoEntregado() { return idPedidoEntregado; }
    public String getCodigoPedido() { return codigoPedido; }
    public String getTipoPedido() { return tipoPedido; }
    public String getDireccionDestino() { return direccionDestino; }
    public double getDistanciaKm() { return distanciaKm; }
    public Double getPesoKg() { return pesoKg; }
    public String getRutRepartidor() { return rutRepartidor; }
    public String getNombreRepartidor() { return nombreRepartidor; }
    public String getRutCliente() { return rutCliente; }
    public String getNombreCliente() { return nombreCliente; }
    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
}
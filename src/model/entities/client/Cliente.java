package model.entities.client;

import model.core.Persona;

// Cliente hereda de Persona igual que Repartidor: comparte nombre completo y
// teléfono de contacto, y agrega sus propios datos (rut, dirección, correo).
// Es quien "recibe" el pedido (Pedido.cliente): la relación Pedido -> Cliente
// es obligatoria a nivel de negocio (un pedido siempre es de alguien), pero
// la columna id_cliente en la base de datos se dejó NULLABLE para no romper
// los pedidos de prueba que ya existían antes de esta entidad; la regla
// "todo pedido nuevo debe tener cliente" se aplica en ServicioPedidos.
public class Cliente extends Persona {

    // Identificador de negocio (columna 'rut' en la tabla Cliente), igual que
    // en Repartidor: se usa como clave natural para relacionar un Pedido con
    // su cliente sin exponer el id_cliente autogenerado en el modelo de
    // dominio.
    private String rut;
    private String direccion;
    private String correo; // opcional

    public Cliente() {
        super();
        this.rut = "SIN-RUT";
        this.direccion = "No especificada";
        this.correo = null;
    }

    // Constructor completo, usado por ClienteDAO al reconstruir un cliente
    // real desde la fila de la base de datos, y por el panel de Gestión de
    // Clientes al registrar uno nuevo.
    public Cliente(String rut, String nombreCompleto, String telefonoContacto, String direccion, String correo) {
        super(nombreCompleto, telefonoContacto);
        this.rut = rut;
        this.direccion = direccion;
        this.correo = correo;
    }

    public String getRut() { return rut; }
    public void setRut(String rut) { this.rut = rut; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getTelefono() {
        return super.getTelefonoContacto();
    }

    // Representación corta: la usa, por ejemplo, el JComboBox de selección de
    // cliente en "Añadir Pedido", donde solo hay espacio para una línea.
    @Override
    public String toString() {
        return getNombreCompleto() + " (RUT: " + rut + ")";
    }
}
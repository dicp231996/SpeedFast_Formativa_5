package service;

// Excepción no verificada (unchecked) que lanza la capa de servicio cuando
// una operación no puede realizarse: ya sea porque viola una regla de
// negocio (por ejemplo, eliminar un pedido que está EN_REPARTO) o porque la
// base de datos rechazó la operación (por ejemplo, un RUT repetido, o la
// conexión caída). El mensaje queda redactado en términos que la interfaz
// puede mostrarle directamente al usuario en un JOptionPane, sin que el
// panel tenga que conocer la razón técnica exacta.
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
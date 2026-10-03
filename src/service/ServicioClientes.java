package service;

import data.persistence.ClienteDAO;
import model.entities.client.Cliente;

import java.util.ArrayList;

// =========================================================================
// CAPA DE SERVICIO: ServicioClientes
// =========================================================================
// Mismo rol que ServicioRepartidores, pero para Cliente: coordina la lista en
// memoria con ClienteDAO. El panel de Gestión de Clientes y PanelAgregarPedido
// (que necesita la nómina para el combo de selección) le piden la operación a
// este servicio en vez de llamar al DAO directamente.
public class ServicioClientes {

    private final ClienteDAO clienteDAO = new ClienteDAO();

    private final ArrayList<Cliente> listaClientes = new ArrayList<>();

    public ArrayList<Cliente> getListaClientes() {
        return listaClientes;
    }

    // Carga inicial desde la base de datos. Debe llamarse antes de cargar los
    // pedidos, que necesitan esta lista para enlazar cada pedido con el
    // cliente que tuviera asociado de una ejecución anterior (por RUT).
    public void cargarDesdeBaseDeDatos() {
        listaClientes.clear();
        listaClientes.addAll(clienteDAO.listarTodos());
    }

    // Registra un cliente nuevo: lo persiste y, solo si tuvo éxito, lo agrega
    // también a la lista en memoria. Lanza OperacionNoPermitidaException si
    // la base de datos rechaza el registro (por ejemplo, RUT repetido).
    public void registrarCliente(Cliente cliente) {
        boolean exito = clienteDAO.insertar(cliente);
        if (!exito) {
            throw new OperacionNoPermitidaException(
                    "No se pudo registrar el cliente. Verifica que el RUT no esté repetido "
                            + "y que la conexión a la base de datos esté disponible.");
        }
        listaClientes.add(cliente);
    }

    // Modifica los datos de un cliente ya existente (el RUT no cambia).
    // Actualiza primero la base de datos; solo si tuvo éxito se reflejan los
    // cambios en el objeto en memoria. Lanza OperacionNoPermitidaException si
    // la base de datos rechaza la actualización.
    public void actualizarCliente(Cliente cliente, String nombre, String telefono, String direccion, String correo) {
        String nombreAnterior = cliente.getNombreCompleto();
        String telefonoAnterior = cliente.getTelefono();
        String direccionAnterior = cliente.getDireccion();
        String correoAnterior = cliente.getCorreo();

        cliente.setNombreCompleto(nombre);
        cliente.setTelefonoContacto(telefono);
        cliente.setDireccion(direccion);
        cliente.setCorreo(correo);

        boolean exito = clienteDAO.actualizar(cliente);
        if (!exito) {
            cliente.setNombreCompleto(nombreAnterior);
            cliente.setTelefonoContacto(telefonoAnterior);
            cliente.setDireccion(direccionAnterior);
            cliente.setCorreo(correoAnterior);
            throw new OperacionNoPermitidaException(
                    "No se pudo actualizar el cliente en la base de datos. Revisa la conexión.");
        }
    }

    // Elimina un cliente de la base de datos y, si tuvo éxito, también de la
    // memoria. Lanza OperacionNoPermitidaException si la base de datos
    // rechaza el borrado.
    public void eliminarCliente(Cliente cliente) {
        boolean exito = clienteDAO.eliminar(cliente.getRut());
        if (!exito) {
            throw new OperacionNoPermitidaException(
                    "No se pudo eliminar el cliente de la base de datos. Revisa la conexión.");
        }
        listaClientes.remove(cliente);
    }
}
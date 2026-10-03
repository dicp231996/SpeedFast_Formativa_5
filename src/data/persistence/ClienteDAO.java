package data.persistence;

import model.entities.client.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

// Acceso a la tabla Cliente. Sigue el mismo patrón que RepartidorDAO: rut
// como clave de negocio (UNIQUE en la base de datos), insertar/actualizar
// devuelven boolean según si la operación tuvo éxito, y eliminar() libera
// primero los pedidos que lo referencian para no violar la llave foránea
// Pedido.id_cliente -> Cliente.id_cliente.
public class ClienteDAO {

    public ArrayList<Cliente> listarTodos() {
        ArrayList<Cliente> listaClientes = new ArrayList<>();

        String sql = "SELECT rut, nombre, telefono, direccion, correo FROM Cliente";

        try (Connection conexion = ConexionBD.obtenerConexion();
             Statement stmt = conexion.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                listaClientes.add(new Cliente(
                        rs.getString("rut"),
                        rs.getString("nombre"),
                        rs.getString("telefono"),
                        rs.getString("direccion"),
                        rs.getString("correo")
                ));
            }

        } catch (SQLException e) {
            System.err.println("Error al leer clientes desde la base de datos: " + e.getMessage());
        }

        return listaClientes;
    }

    // Inserta un cliente nuevo. Devuelve false si falla (por ejemplo, RUT
    // repetido, ya que la columna es UNIQUE, o conexión caída).
    public boolean insertar(Cliente cliente) {
        String sql = "INSERT INTO Cliente (rut, nombre, telefono, direccion, correo) VALUES (?, ?, ?, ?, ?)";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, cliente.getRut());
            ps.setString(2, cliente.getNombreCompleto());
            ps.setString(3, cliente.getTelefono());
            ps.setString(4, cliente.getDireccion());
            ps.setString(5, cliente.getCorreo());

            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.err.println("Error al registrar el cliente " + cliente.getRut()
                    + " en la base de datos: " + e.getMessage());
            return false;
        }
    }

    // Actualiza los datos de un cliente ya existente. El RUT no se modifica:
    // es la clave de negocio que usa Pedido para ubicarlo.
    public boolean actualizar(Cliente cliente) {
        String sql = "UPDATE Cliente SET nombre = ?, telefono = ?, direccion = ?, correo = ? WHERE rut = ?";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, cliente.getNombreCompleto());
            ps.setString(2, cliente.getTelefono());
            ps.setString(3, cliente.getDireccion());
            ps.setString(4, cliente.getCorreo());
            ps.setString(5, cliente.getRut());

            int filasAfectadas = ps.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            System.err.println("Error al actualizar el cliente " + cliente.getRut()
                    + " en la base de datos: " + e.getMessage());
            return false;
        }
    }

    // Elimina un cliente, identificado por su RUT. Antes de borrarlo, dentro
    // de la MISMA transacción, deja en NULL el id_cliente de cualquier Pedido
    // que lo tuviera asociado (el pedido queda "sin cliente" en vez de
    // impedir el borrado), igual que RepartidorDAO.eliminar(...) hace con los
    // pedidos asignados a un repartidor.
    public boolean eliminar(String rut) {
        String sqlLiberarPedidos = "UPDATE Pedido SET id_cliente = NULL "
                + "WHERE id_cliente = (SELECT id_cliente FROM Cliente WHERE rut = ?)";
        String sqlBorrarCliente = "DELETE FROM Cliente WHERE rut = ?";

        try (Connection conexion = ConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);

            try (PreparedStatement psLiberar = conexion.prepareStatement(sqlLiberarPedidos);
                 PreparedStatement psCliente = conexion.prepareStatement(sqlBorrarCliente)) {

                psLiberar.setString(1, rut);
                psLiberar.executeUpdate();

                psCliente.setString(1, rut);
                int filasAfectadas = psCliente.executeUpdate();

                conexion.commit();
                return filasAfectadas > 0;

            } catch (SQLException e) {
                conexion.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println("Error al eliminar el cliente " + rut
                    + " de la base de datos: " + e.getMessage());
            return false;
        }
    }
}
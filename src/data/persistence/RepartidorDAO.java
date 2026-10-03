package data.persistence;

import data.enumerate.TipoServicio;
import model.entities.dealer.Repartidor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

// Reemplaza a GestorInstancias.cargarRepartidores(rutaArchivo): en vez de
// leer repartidores.txt, los trae directamente desde la tabla Repartidor.
public class RepartidorDAO {

    public ArrayList<Repartidor> listarTodos() {
        ArrayList<Repartidor> listaRepartidores = new ArrayList<>();

        String sql = "SELECT rut, nombre, vehiculo, telefono, tipo_servicio, tiene_mochila_termica, "
                + "capacidad_peso_max, esta_cerca_ubicacion FROM Repartidor";

        try (Connection conexion = ConexionBD.obtenerConexion();
             Statement stmt = conexion.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Repartidor repartidor = new Repartidor(
                        rs.getString("rut"),
                        rs.getString("nombre"),
                        rs.getString("telefono"),
                        TipoServicio.valueOf(rs.getString("tipo_servicio")),
                        rs.getBoolean("tiene_mochila_termica"),
                        rs.getDouble("capacidad_peso_max"),
                        rs.getBoolean("esta_cerca_ubicacion")
                );
                repartidor.setVehiculo(rs.getString("vehiculo"));
                listaRepartidores.add(repartidor);
            }

        } catch (SQLException e) {
            System.err.println("Error al leer repartidores desde la base de datos: " + e.getMessage());
        }

        return listaRepartidores;
    }

    // Inserta un nuevo repartidor en la base de datos. Se usa desde el panel
    // de Gestión de Repartidores al registrar un empleado nuevo. Devuelve
    // true si la inserción fue exitosa (por ejemplo, false si el RUT ya
    // existe, ya que la columna es UNIQUE).
    public boolean insertar(Repartidor repartidor) {
        String sql = "INSERT INTO Repartidor (rut, nombre, vehiculo, telefono, tipo_servicio, "
                + "tiene_mochila_termica, capacidad_peso_max, esta_cerca_ubicacion) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, repartidor.getRut());
            ps.setString(2, repartidor.getNombreCompleto());
            ps.setString(3, repartidor.getVehiculo());
            ps.setString(4, repartidor.getTelefono());
            ps.setString(5, repartidor.getTipoServicio().name());
            ps.setBoolean(6, repartidor.isTieneMochilaTermica());
            ps.setDouble(7, repartidor.getCapacidadPesoMax());
            ps.setBoolean(8, repartidor.isEstaCercaUbicacion());

            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.err.println("Error al registrar el repartidor " + repartidor.getRut()
                    + " en la base de datos: " + e.getMessage());
            return false;
        }
    }

    // Actualiza los datos de un repartidor ya existente, identificado por su
    // RUT (el RUT en sí no se modifica: es la clave de negocio que usan tanto
    // Pedido como Entrega para ubicarlo, así que se deja fija y editable solo
    // el resto de sus datos). Se usa desde el panel de Gestión de
    // Repartidores al modificar un registro de la nómina.
    public boolean actualizar(Repartidor repartidor) {
        String sql = "UPDATE Repartidor SET nombre = ?, vehiculo = ?, telefono = ?, tipo_servicio = ?, "
                + "tiene_mochila_termica = ?, capacidad_peso_max = ?, esta_cerca_ubicacion = ? "
                + "WHERE rut = ?";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, repartidor.getNombreCompleto());
            ps.setString(2, repartidor.getVehiculo());
            ps.setString(3, repartidor.getTelefono());
            ps.setString(4, repartidor.getTipoServicio().name());
            ps.setBoolean(5, repartidor.isTieneMochilaTermica());
            ps.setDouble(6, repartidor.getCapacidadPesoMax());
            ps.setBoolean(7, repartidor.isEstaCercaUbicacion());
            ps.setString(8, repartidor.getRut());

            int filasAfectadas = ps.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            System.err.println("Error al actualizar el repartidor " + repartidor.getRut()
                    + " en la base de datos: " + e.getMessage());
            return false;
        }
    }

    // Elimina un repartidor de la base de datos, identificado por su RUT.
    // Antes de borrarlo, dentro de la MISMA transacción:
    //   1) Libera cualquier Pedido que lo tuviera asignado (deja
    //      id_repartidor_asignado en NULL, el pedido vuelve a quedar sin
    //      repartidor en vez de impedir el borrado).
    //   2) Borra las filas de Entrega que lo referencian (historial de
    //      entregas de ese repartidor).
    // Así se evita violar las restricciones de llave foránea
    // Pedido.id_repartidor_asignado -> Repartidor.id_repartidor y
    // Entrega.id_repartidor -> Repartidor.id_repartidor.
    public boolean eliminar(String rut) {
        String sqlLiberarPedidos = "UPDATE Pedido SET id_repartidor_asignado = NULL "
                + "WHERE id_repartidor_asignado = (SELECT id_repartidor FROM Repartidor WHERE rut = ?)";
        String sqlBorrarEntregas = "DELETE FROM Entrega WHERE id_repartidor = "
                + "(SELECT id_repartidor FROM Repartidor WHERE rut = ?)";
        String sqlBorrarRepartidor = "DELETE FROM Repartidor WHERE rut = ?";

        try (Connection conexion = ConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);

            try (PreparedStatement psLiberar = conexion.prepareStatement(sqlLiberarPedidos);
                 PreparedStatement psEntregas = conexion.prepareStatement(sqlBorrarEntregas);
                 PreparedStatement psRepartidor = conexion.prepareStatement(sqlBorrarRepartidor)) {

                // OJO: aquí usamos una subconsulta que lee Repartidor ANTES de
                // borrarlo más abajo en esta misma transacción; el orden de
                // ejecución (liberar -> borrar entregas -> borrar repartidor)
                // es el que hace esto seguro.
                psLiberar.setString(1, rut);
                psLiberar.executeUpdate();

                psEntregas.setString(1, rut);
                psEntregas.executeUpdate();

                psRepartidor.setString(1, rut);
                int filasAfectadas = psRepartidor.executeUpdate();

                conexion.commit();
                return filasAfectadas > 0;

            } catch (SQLException e) {
                conexion.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println("Error al eliminar el repartidor " + rut
                    + " de la base de datos: " + e.getMessage());
            return false;
        }
    }
}
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
}
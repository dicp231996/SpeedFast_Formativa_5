package data.persistence;

import model.core.Pedido;
import model.entities.dealer.Repartidor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

// Deja la traza histórica de cada entrega en la tabla Entrega: qué pedido
// entregó qué repartidor y cuándo. Se usa desde HiloEntrega justo cuando un
// pedido termina su simulación de entrega.
public class EntregaDAO {

    public void registrarEntrega(Pedido pedido, Repartidor repartidor, String estadoEntrega) {
        String sql = "INSERT INTO Entrega (id_pedido, id_repartidor, fecha_entrega, estado_entrega) "
                + "VALUES ((SELECT id_pedido FROM Pedido WHERE codigo_pedido = ?), "
                + "(SELECT id_repartidor FROM Repartidor WHERE rut = ?), NOW(), ?)";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, pedido.getIdPedido());
            if (repartidor != null && repartidor.getRut() != null) {
                ps.setString(2, repartidor.getRut());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, estadoEntrega);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error al registrar la entrega del pedido " + pedido.getIdPedido()
                    + " en la base de datos: " + e.getMessage());
        }
    }

    // Cuenta cuántas entregas con estado 'ENTREGADO' ha completado cada
    // repartidor durante el día de hoy. Se usa en el panel de Gestión de
    // Repartidores para mostrar la columna "Entregas Hoy" de la nómina. La
    // clave del mapa es el RUT del repartidor.
    public Map<String, Integer> contarEntregasHoyPorRepartidor() {
        Map<String, Integer> entregasPorRut = new HashMap<>();

        String sql = "SELECT r.rut AS rut, COUNT(*) AS cantidad "
                + "FROM Entrega e "
                + "JOIN Repartidor r ON e.id_repartidor = r.id_repartidor "
                + "WHERE DATE(e.fecha_entrega) = CURDATE() AND e.estado_entrega = 'ENTREGADO' "
                + "GROUP BY r.rut";

        try (Connection conexion = ConexionBD.obtenerConexion();
             Statement stmt = conexion.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                entregasPorRut.put(rs.getString("rut"), rs.getInt("cantidad"));
            }

        } catch (SQLException e) {
            System.err.println("Error al contar las entregas de hoy por repartidor: " + e.getMessage());
        }

        return entregasPorRut;
    }
}
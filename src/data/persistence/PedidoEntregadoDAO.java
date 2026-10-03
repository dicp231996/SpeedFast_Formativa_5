package data.persistence;

import model.core.Pedido;
import model.entities.client.Cliente;
import model.entities.dealer.Repartidor;
import model.entities.order.PedidoEncomienda;
import model.historial.RegistroPedidoEntregado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

// Acceso a la tabla PedidoEntregado: el historial de pedidos entregados con
// éxito. A propósito es independiente de PedidoDAO: no reconstruye objetos
// Pedido (ya cumplieron su ciclo de vida), solo guarda y consulta una
// fotografía de solo lectura (RegistroPedidoEntregado) pensada para mostrarse
// en pantalla o para calcular métricas por fecha.
public class PedidoEntregadoDAO {

    // Guarda en el historial la "fotografía" de un pedido justo en el
    // momento en que terminó de entregarse. Se llama una sola vez, desde
    // HiloEntrega, inmediatamente después de pedido.marcarEntregado().
    public void registrar(Pedido pedido) {
        Double peso = (pedido instanceof PedidoEncomienda) ? ((PedidoEncomienda) pedido).getPesoKg() : null;
        Repartidor repartidor = pedido.getRepartidorAsignado();
        Cliente cliente = pedido.getCliente();

        String sql = "INSERT INTO PedidoEntregado (codigo_pedido, tipo_pedido, direccion_destino, "
                + "distancia_km, peso_kg, rut_repartidor, nombre_repartidor, rut_cliente, nombre_cliente, fecha_entrega) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, pedido.getIdPedido());
            ps.setString(2, pedido.getTipoPedido());
            ps.setString(3, pedido.getDireccionEntrega());
            ps.setDouble(4, pedido.getDistanciaKm());

            if (peso != null) {
                ps.setDouble(5, peso);
            } else {
                ps.setNull(5, Types.DOUBLE);
            }

            if (repartidor != null) {
                ps.setString(6, repartidor.getRut());
                ps.setString(7, repartidor.getNombreCompleto());
            } else {
                ps.setNull(6, Types.VARCHAR);
                ps.setNull(7, Types.VARCHAR);
            }

            if (cliente != null) {
                ps.setString(8, cliente.getRut());
                ps.setString(9, cliente.getNombreCompleto());
            } else {
                ps.setNull(8, Types.VARCHAR);
                ps.setNull(9, Types.VARCHAR);
            }

            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error al guardar en el historial el pedido entregado " + pedido.getIdPedido()
                    + ": " + e.getMessage());
        }
    }

    // Todo el historial, del más reciente al más antiguo.
    public ArrayList<RegistroPedidoEntregado> listarTodos() {
        return listarConFiltro("SELECT * FROM PedidoEntregado ORDER BY fecha_entrega DESC", null, null);
    }

    // Solo los pedidos entregados entre dos fechas (inclusive), para poder
    // filtrar el historial por un rango y sacar métricas de un período
    // específico.
    public ArrayList<RegistroPedidoEntregado> listarEntreFechas(LocalDate desde, LocalDate hasta) {
        String sql = "SELECT * FROM PedidoEntregado "
                + "WHERE fecha_entrega >= ? AND fecha_entrega < ? "
                + "ORDER BY fecha_entrega DESC";
        return listarConFiltro(sql, desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
    }

    private ArrayList<RegistroPedidoEntregado> listarConFiltro(String sql, LocalDateTime desde, LocalDateTime hasta) {
        ArrayList<RegistroPedidoEntregado> registros = new ArrayList<>();

        try (Connection conexion = ConexionBD.obtenerConexion()) {

            if (desde == null) {
                try (Statement stmt = conexion.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        registros.add(construirDesdeFila(rs));
                    }
                }
            } else {
                try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                    ps.setTimestamp(1, Timestamp.valueOf(desde));
                    ps.setTimestamp(2, Timestamp.valueOf(hasta));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            registros.add(construirDesdeFila(rs));
                        }
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Error al leer el historial de pedidos entregados: " + e.getMessage());
        }

        return registros;
    }

    private RegistroPedidoEntregado construirDesdeFila(ResultSet rs) throws SQLException {
        double peso = rs.getDouble("peso_kg");
        Double pesoKg = rs.wasNull() ? null : peso;

        return new RegistroPedidoEntregado(
                rs.getInt("id_pedido_entregado"),
                rs.getString("codigo_pedido"),
                rs.getString("tipo_pedido"),
                rs.getString("direccion_destino"),
                rs.getDouble("distancia_km"),
                pesoKg,
                rs.getString("rut_repartidor"),
                rs.getString("nombre_repartidor"),
                rs.getString("rut_cliente"),
                rs.getString("nombre_cliente"),
                rs.getTimestamp("fecha_entrega").toLocalDateTime()
        );
    }

    // Actualiza los datos editables de una fila del historial (identificada
    // por su id_pedido_entregado). El código y tipo de pedido no se tocan:
    // identifican QUÉ se entregó, no cómo quedó registrada la entrega.
    public boolean actualizar(RegistroPedidoEntregado registro) {
        String sql = "UPDATE PedidoEntregado SET direccion_destino = ?, distancia_km = ?, peso_kg = ?, "
                + "rut_repartidor = ?, nombre_repartidor = ? WHERE id_pedido_entregado = ?";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, registro.getDireccionDestino());
            ps.setDouble(2, registro.getDistanciaKm());
            if (registro.getPesoKg() != null) {
                ps.setDouble(3, registro.getPesoKg());
            } else {
                ps.setNull(3, Types.DOUBLE);
            }
            ps.setString(4, registro.getRutRepartidor());
            ps.setString(5, registro.getNombreRepartidor());
            ps.setInt(6, registro.getIdPedidoEntregado());

            int filasAfectadas = ps.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            System.err.println("Error al actualizar el registro histórico " + registro.getIdPedidoEntregado()
                    + ": " + e.getMessage());
            return false;
        }
    }

    // Elimina del historial una fila puntual, identificada por su
    // id_pedido_entregado. Quien llama (ServicioPedidos) es responsable de
    // conservar el registro en memoria (papelera) si quiere poder deshacer
    // el borrado durante lo que queda de la ejecución.
    public boolean eliminarPorId(int idPedidoEntregado) {
        String sql = "DELETE FROM PedidoEntregado WHERE id_pedido_entregado = ?";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setInt(1, idPedidoEntregado);
            int filasAfectadas = ps.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            System.err.println("Error al eliminar el registro histórico " + idPedidoEntregado
                    + ": " + e.getMessage());
            return false;
        }
    }

    // Vuelve a insertar en la base de datos un registro que había sido
    // eliminado (lo usa ServicioPedidos al "restaurar" algo desde la
    // papelera en memoria). Como es una fila nueva para MySQL, recibe un
    // id_pedido_entregado distinto al que tenía antes de borrarse; eso no
    // afecta nada porque ninguna otra tabla referencia esta por clave
    // foránea. Devuelve el registro ya con el id nuevo asignado, o null si la
    // inserción falló.
    public RegistroPedidoEntregado insertarDesdeRegistro(RegistroPedidoEntregado registro) {
        String sql = "INSERT INTO PedidoEntregado (codigo_pedido, tipo_pedido, direccion_destino, "
                + "distancia_km, peso_kg, rut_repartidor, nombre_repartidor, rut_cliente, nombre_cliente, fecha_entrega) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, registro.getCodigoPedido());
            ps.setString(2, registro.getTipoPedido());
            ps.setString(3, registro.getDireccionDestino());
            ps.setDouble(4, registro.getDistanciaKm());
            if (registro.getPesoKg() != null) {
                ps.setDouble(5, registro.getPesoKg());
            } else {
                ps.setNull(5, Types.DOUBLE);
            }
            ps.setString(6, registro.getRutRepartidor());
            ps.setString(7, registro.getNombreRepartidor());
            ps.setString(8, registro.getRutCliente());
            ps.setString(9, registro.getNombreCliente());
            ps.setTimestamp(10, Timestamp.valueOf(registro.getFechaEntrega()));

            int filasAfectadas = ps.executeUpdate();
            if (filasAfectadas == 0) {
                return null;
            }

            try (ResultSet llaves = ps.getGeneratedKeys()) {
                int nuevoId = llaves.next() ? llaves.getInt(1) : registro.getIdPedidoEntregado();
                return new RegistroPedidoEntregado(nuevoId, registro.getCodigoPedido(), registro.getTipoPedido(),
                        registro.getDireccionDestino(), registro.getDistanciaKm(), registro.getPesoKg(),
                        registro.getRutRepartidor(), registro.getNombreRepartidor(),
                        registro.getRutCliente(), registro.getNombreCliente(), registro.getFechaEntrega());
            }

        } catch (SQLException e) {
            System.err.println("Error al restaurar el registro histórico " + registro.getCodigoPedido()
                    + ": " + e.getMessage());
            return null;
        }
    }

    // Cantidad de pedidos entregados por día (clave = fecha, sin hora).
    // Pensado como base para métricas futuras (gráficos de entregas por día,
    // tendencias semanales, etc.), manteniendo el orden cronológico.
    public Map<LocalDate, Integer> contarEntregasPorDia() {
        Map<LocalDate, Integer> conteo = new LinkedHashMap<>();

        String sql = "SELECT DATE(fecha_entrega) AS dia, COUNT(*) AS cantidad "
                + "FROM PedidoEntregado GROUP BY DATE(fecha_entrega) ORDER BY dia";

        try (Connection conexion = ConexionBD.obtenerConexion();
             Statement stmt = conexion.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                conteo.put(rs.getDate("dia").toLocalDate(), rs.getInt("cantidad"));
            }

        } catch (SQLException e) {
            System.err.println("Error al calcular las entregas por día: " + e.getMessage());
        }

        return conteo;
    }
}
package data.persistence;

import data.enumerate.EstadoPedido;
import model.core.Pedido;
import model.entities.dealer.Repartidor;
import model.entities.order.PedidoComida;
import model.entities.order.PedidoEncomienda;
import model.entities.order.PedidoExpress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;

// Reemplaza a GestorInstancias.cargarPedidos(rutaArchivo) (que usaba
// reflexión sobre pedidos.txt) y a GestorArchivoPedidos.agregarLinea(...)
// (que anexaba pedidos nuevos al final del .txt). Ahora todo eso vive en la
// tabla Pedido de speedfast_db.
public class PedidoDAO {

    // Reconstruye todos los pedidos guardados en la base de datos, incluido
    // el repartidor que ya tuvieran asignado de una ejecución anterior (si
    // corresponde), enlazándolo con los objetos Repartidor ya cargados en
    // memoria (repartidoresCargados) a través de su RUT.
    public ArrayList<Pedido> listarTodos(ArrayList<Repartidor> repartidoresCargados) {
        ArrayList<Pedido> listaPedidos = new ArrayList<>();

        String sql = "SELECT p.codigo_pedido, p.tipo_pedido, p.direccion_destino, p.distancia_km, "
                + "p.peso_kg, p.estado, p.motivo_cancelacion, r.rut AS rut_asignado "
                + "FROM Pedido p LEFT JOIN Repartidor r ON p.id_repartidor_asignado = r.id_repartidor "
                + "ORDER BY p.id_pedido";

        try (Connection conexion = ConexionBD.obtenerConexion();
             Statement stmt = conexion.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Pedido pedido = construirPedidoDesdeFila(rs);
                if (pedido == null) {
                    continue; // tipo_pedido desconocido/corrupto: se avisa y se omite esa fila
                }

                String estadoTexto = rs.getString("estado");
                if (estadoTexto != null) {
                    EstadoPedido estado = EstadoPedido.valueOf(estadoTexto);
                    pedido.restaurarEstado(estado);
                    if (estado == EstadoPedido.CANCELADO) {
                        pedido.restaurarCancelacion(rs.getString("motivo_cancelacion"));
                    }
                }

                String rutAsignado = rs.getString("rut_asignado");
                if (rutAsignado != null) {
                    for (Repartidor candidato : repartidoresCargados) {
                        if (rutAsignado.equals(candidato.getRut())) {
                            pedido.setRepartidorAsignado(candidato);
                            break;
                        }
                    }
                }

                listaPedidos.add(pedido);
            }

        } catch (SQLException e) {
            System.err.println("Error al leer pedidos desde la base de datos: " + e.getMessage());
        }

        return listaPedidos;
    }

    private Pedido construirPedidoDesdeFila(ResultSet rs) throws SQLException {
        String codigo = rs.getString("codigo_pedido");
        String tipo = rs.getString("tipo_pedido");
        String direccion = rs.getString("direccion_destino");
        double distancia = rs.getDouble("distancia_km");

        switch (tipo) {
            case "PedidoComida":
                return new PedidoComida(codigo, direccion, distancia);
            case "PedidoExpress":
                return new PedidoExpress(codigo, direccion, distancia);
            case "PedidoEncomienda":
                double peso = rs.getDouble("peso_kg");
                return new PedidoEncomienda(codigo, direccion, distancia, peso);
            default:
                System.err.println("Alerta: tipo_pedido desconocido en la base de datos para "
                        + codigo + ": \"" + tipo + "\". Se omite esa fila.");
                return null;
        }
    }

    // Inserta un pedido nuevo (estado PENDIENTE, sin repartidor asignado
    // todavía), reemplazando lo que antes hacía
    // GestorArchivoPedidos.agregarLinea(...) sobre pedidos.txt.
    public void insertar(Pedido pedido) {
        String descripcion = "Pedido de " + pedido.getTipoPedido() + " a " + pedido.getDireccionEntrega();
        Double peso = (pedido instanceof PedidoEncomienda) ? ((PedidoEncomienda) pedido).getPesoKg() : null;

        String sql = "INSERT INTO Pedido (codigo_pedido, tipo_pedido, descripcion, direccion_destino, "
                + "distancia_km, peso_kg, estado) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, pedido.getIdPedido());
            ps.setString(2, nombreClaseDe(pedido));
            ps.setString(3, descripcion);
            ps.setString(4, pedido.getDireccionEntrega());
            ps.setDouble(5, pedido.getDistanciaKm());
            if (peso != null) {
                ps.setDouble(6, peso);
            } else {
                ps.setNull(6, Types.DOUBLE);
            }
            ps.setString(7, pedido.getEstado().name());

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error al guardar el pedido " + pedido.getIdPedido()
                    + " en la base de datos: " + e.getMessage());
        }
    }

    // Persiste el estado actual de un pedido (y, si tiene, el repartidor que
    // quedó asignado o el motivo de cancelación). Se llama después de cada
    // asignación manual/automática y después de cada entrega, para que la
    // base de datos siempre refleje el estado real del pedido.
    public void actualizarEstado(Pedido pedido) {
        String sql = "UPDATE Pedido SET estado = ?, motivo_cancelacion = ?, "
                + "id_repartidor_asignado = (SELECT id_repartidor FROM Repartidor WHERE rut = ?) "
                + "WHERE codigo_pedido = ?";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, pedido.getEstado().name());

            if (pedido.isCancelado()) {
                ps.setString(2, pedido.getMotivoCancelacion());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }

            Repartidor asignado = pedido.getRepartidorAsignado();
            if (asignado != null && asignado.getRut() != null) {
                ps.setString(3, asignado.getRut());
            } else {
                ps.setNull(3, Types.VARCHAR);
            }

            ps.setString(4, pedido.getIdPedido());

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error al actualizar el pedido " + pedido.getIdPedido()
                    + " en la base de datos: " + e.getMessage());
        }
    }

    private String nombreClaseDe(Pedido pedido) {
        if (pedido instanceof PedidoComida) return "PedidoComida";
        if (pedido instanceof PedidoEncomienda) return "PedidoEncomienda";
        if (pedido instanceof PedidoExpress) return "PedidoExpress";
        return pedido.getClass().getSimpleName();
    }
}
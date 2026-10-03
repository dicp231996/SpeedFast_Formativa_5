package service;

import data.enumerate.EstadoPedido;
import data.persistence.EntregaDAO;
import data.persistence.PedidoDAO;
import data.persistence.PedidoEntregadoDAO;
import model.core.Pedido;
import model.entities.business.ZonaCarga;
import model.entities.client.Cliente;
import model.entities.dealer.Repartidor;
import model.historial.RegistroPedidoEntregado;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

// =========================================================================
// CAPA DE SERVICIO: ServicioPedidos
// =========================================================================
// Concentra todas las reglas de negocio y la coordinación relacionadas con
// Pedido: cuándo se puede confirmar una asignación, cuándo se puede eliminar
// un pedido, qué pasa en memoria y en la base de datos cuando se completa
// una entrega, etc.
//
// Antes de esta clase, los paneles de la interfaz (ui.*) llamaban
// directamente a PedidoDAO y después, "a mano", mantenían sincronizados la
// ZonaCarga y el ArrayList<Pedido> en memoria. Esa responsabilidad no le
// corresponde a la UI ni al DAO (el DAO solo debe saber hablar con la base
// de datos): por eso vive aquí, en una capa intermedia. Los paneles ahora
// solo le piden a este servicio la operación que necesitan ("registra este
// pedido", "elimina este otro") y confían en que el servicio deja todo
// consistente.
public class ServicioPedidos {

    private final PedidoDAO pedidoDAO = new PedidoDAO();
    private final EntregaDAO entregaDAO = new EntregaDAO();
    private final PedidoEntregadoDAO pedidoEntregadoDAO = new PedidoEntregadoDAO();

    private final ArrayList<Pedido> listaPedidos = new ArrayList<>();
    private final ZonaCarga zonaCarga;

    // "Papelera" del historial de entregas: SOLO vive en memoria (nunca se
    // persiste). Cuando se elimina un registro de PedidoEntregado, primero se
    // borra de la base de datos y, si tuvo éxito, se guarda aquí una copia;
    // así el registro puede restaurarse mientras la aplicación siga abierta,
    // pero si se cierra sin restaurarlo, se pierde de verdad (ya no está en
    // la base de datos ni en ninguna otra parte).
    private final ArrayList<RegistroPedidoEntregado> papeleraHistorial = new ArrayList<>();

    public ServicioPedidos(ZonaCarga zonaCarga) {
        this.zonaCarga = zonaCarga;
    }

    public ArrayList<Pedido> getListaPedidos() {
        return listaPedidos;
    }

    public ZonaCarga getZonaCarga() {
        return zonaCarga;
    }

    // Carga inicial desde la base de datos. Debe llamarse una sola vez, al
    // arrancar la aplicación, y después de que los repartidores ya estén
    // cargados (para poder enlazar cada pedido con el repartidor que tuviera
    // asignado de una ejecución anterior, por RUT).
    public void cargarDesdeBaseDeDatos(ArrayList<Repartidor> repartidoresCargados, ArrayList<Cliente> clientesCargados) {
        listaPedidos.clear();
        listaPedidos.addAll(pedidoDAO.listarTodos(repartidoresCargados, clientesCargados));
        for (Pedido pedido : listaPedidos) {
            zonaCarga.agregarPedido(pedido);
        }
    }

    // Registra un pedido nuevo: lo agrega a memoria (lista general + zona de
    // carga) y lo persiste en la base de datos. Regla de negocio nueva: todo
    // pedido debe tener un cliente asociado (Pedido.setCliente(...) ya debe
    // haberse llamado antes, normalmente desde el formulario de la UI).
    public void registrarPedido(Pedido pedido) {
        if (pedido.getCliente() == null) {
            throw new OperacionNoPermitidaException(
                    "El pedido " + pedido.getIdPedido() + " no tiene un cliente asociado. "
                            + "Selecciona un cliente antes de guardar el pedido.");
        }

        listaPedidos.add(pedido);
        zonaCarga.agregarPedido(pedido);
        pedidoDAO.insertar(pedido);
    }

    // Intenta confirmar la asignación de "candidato" a "pedido": valida los
    // requisitos (a través de Pedido.asignarRepartidor, que ya conoce esa
    // regla), y si el candidato es apto, actualiza al repartidor, la zona de
    // carga y la base de datos. Devuelve true si la asignación se concretó,
    // false si el candidato fue rechazado.
    //
    // La usan tanto la asignación automática (que prueba varios candidatos
    // en orden hasta que uno acepte) como la manual (que ya trae un único
    // candidato elegido por el usuario desde el diálogo).
    public boolean confirmarAsignacion(Pedido pedido, Repartidor candidato) {
        pedido.asignarRepartidor(candidato);
        if (pedido.getRepartidorAsignado() == null) {
            return false;
        }

        candidato.agregarPedido(pedido);
        zonaCarga.agregarPedido(pedido);
        pedidoDAO.actualizarEstado(pedido); // persiste CONFIRMADO + repartidor asignado

        return true;
    }

    // Disponible para cuando la simulación de entregas (Repartidor/
    // HiloEntrega) quiera delegar aquí la persistencia al completarse una
    // entrega. Por ahora HiloEntrega sigue llamando directamente a
    // PedidoDAO/EntregaDAO: son clases del motor de concurrencia, no de la
    // interfaz, y hacerlas depender de la capa de servicio invertiría la
    // dirección de dependencias (UI -> Servicio -> DAO/Modelo) que se buscó
    // en este refactor. Este método queda listo si en el futuro se decide
    // unificar ambos caminos.
    public void completarEntrega(Pedido pedido) {
        pedido.marcarEntregado();
        pedidoDAO.actualizarEstado(pedido);
        entregaDAO.registrarEntrega(pedido, pedido.getRepartidorAsignado(), "ENTREGADO");
        pedidoEntregadoDAO.registrar(pedido);
    }

    // =====================================================================
    // HISTORIAL DE PEDIDOS ENTREGADOS
    // =====================================================================
    // Un pedido ENTREGADO deja de aparecer en la Zona de Carga (ver
    // PanelZonaCarga, que ahora filtra ese estado) y pasa a vivir en esta
    // tabla de historial aparte, pensada para consultarse por fecha. Estos
    // métodos son los que usa la nueva pestaña "Pedidos Entregados".

    // Todo el historial, del más reciente al más antiguo.
    public ArrayList<RegistroPedidoEntregado> listarHistorialEntregados() {
        return pedidoEntregadoDAO.listarTodos();
    }

    // Solo los pedidos entregados entre dos fechas (ambas incluidas).
    public ArrayList<RegistroPedidoEntregado> listarHistorialEntregados(LocalDate desde, LocalDate hasta) {
        return pedidoEntregadoDAO.listarEntreFechas(desde, hasta);
    }

    // Cantidad de pedidos entregados por día, para métricas (por ejemplo, un
    // gráfico de tendencia de entregas diarias).
    public Map<LocalDate, Integer> contarEntregasPorDia() {
        return pedidoEntregadoDAO.contarEntregasPorDia();
    }

    // Modifica un registro del historial de entregas (dirección, distancia,
    // peso y datos del repartidor). El código de pedido, el tipo y la fecha
    // de entrega no se editan: identifican el hecho histórico en sí. Lanza
    // OperacionNoPermitidaException si la base de datos rechaza el cambio.
    public void actualizarRegistroHistorial(RegistroPedidoEntregado original, String direccionDestino,
                                            double distanciaKm, Double pesoKg, String rutRepartidor,
                                            String nombreRepartidor) {
        RegistroPedidoEntregado actualizado = RegistroPedidoEntregado.conCambios(
                original, direccionDestino, distanciaKm, pesoKg, rutRepartidor, nombreRepartidor);

        boolean exito = pedidoEntregadoDAO.actualizar(actualizado);
        if (!exito) {
            throw new OperacionNoPermitidaException(
                    "No se pudo actualizar el registro del historial. Revisa la conexión.");
        }
    }

    // Elimina un registro del historial de entregas de la base de datos y,
    // si tuvo éxito, lo guarda en la papelera en memoria para que pueda
    // restaurarse mientras la aplicación siga abierta (ver papeleraHistorial).
    // Lanza OperacionNoPermitidaException si la base de datos rechaza el
    // borrado.
    public void eliminarRegistroHistorial(RegistroPedidoEntregado registro) {
        boolean exito = pedidoEntregadoDAO.eliminarPorId(registro.getIdPedidoEntregado());
        if (!exito) {
            throw new OperacionNoPermitidaException(
                    "No se pudo eliminar el registro del historial. Revisa la conexión.");
        }
        papeleraHistorial.add(registro);
    }

    // Registros del historial eliminados durante esta ejecución y aún no
    // restaurados. Se pierden (de verdad) al cerrar la aplicación.
    public ArrayList<RegistroPedidoEntregado> listarPapeleraHistorial() {
        return papeleraHistorial;
    }

    // Vuelve a insertar en la base de datos un registro que estaba en la
    // papelera y lo saca de ahí. Si la base de datos rechaza la reinserción,
    // el registro se deja en la papelera (no se pierde) y se lanza
    // OperacionNoPermitidaException.
    public void restaurarRegistroHistorial(RegistroPedidoEntregado registro) {
        RegistroPedidoEntregado restaurado = pedidoEntregadoDAO.insertarDesdeRegistro(registro);
        if (restaurado == null) {
            throw new OperacionNoPermitidaException(
                    "No se pudo restaurar el registro en la base de datos. Revisa la conexión.");
        }
        papeleraHistorial.remove(registro);
    }

    // Elimina un pedido de la base de datos y, si tuvo éxito, también de la
    // memoria (zona de carga + lista general). Lanza
    // OperacionNoPermitidaException —con un mensaje ya redactado para
    // mostrarle al usuario— si la regla de negocio lo impide (el pedido está
    // EN_REPARTO en este momento) o si la base de datos rechaza el borrado.
    public void eliminarPedido(Pedido pedido) {
        if (pedido.getEstado() == EstadoPedido.EN_REPARTO) {
            throw new OperacionNoPermitidaException(
                    "No se puede eliminar el pedido " + pedido.getIdPedido()
                            + " porque está EN_REPARTO en este momento.");
        }

        boolean exito = pedidoDAO.eliminar(pedido.getIdPedido());
        if (!exito) {
            throw new OperacionNoPermitidaException(
                    "No se pudo eliminar el pedido de la base de datos. Revisa la conexión.");
        }

        zonaCarga.eliminarPedido(pedido);
        listaPedidos.remove(pedido);
    }
}
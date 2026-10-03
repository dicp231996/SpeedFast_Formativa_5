package model.valueobjects;

import data.persistence.EntregaDAO;
import data.persistence.PedidoDAO;
import data.persistence.PedidoEntregadoDAO;
import model.core.Pedido;

import java.util.concurrent.ThreadLocalRandom;

public class HiloEntrega implements Runnable {

    private static final int ESPERA_MINIMA_MS = 1000;
    private static final int ESPERA_MAXIMA_MS = 3000;

    private Pedido pedido;
    private final PedidoDAO pedidoDAO = new PedidoDAO();
    private final EntregaDAO entregaDAO = new EntregaDAO();
    private final PedidoEntregadoDAO pedidoEntregadoDAO = new PedidoEntregadoDAO();

    public HiloEntrega(Pedido pedido) {
        this.pedido = pedido;
    }

    @Override
    public void run() {
        try {
            System.out.println("[" + pedido.getIdPedido() + "] Tu repartidor está en el punto de recogida.");
            Thread.sleep(tiempoAleatorio());

            System.out.println("[" + pedido.getIdPedido() + "] Tu pedido está en ruta.");
            Thread.sleep(tiempoAleatorio());

            System.out.println("[" + pedido.getIdPedido() + "] Ya casi está en tus manos.");
            Thread.sleep(tiempoAleatorio());

            System.out.println("[" + pedido.getIdPedido() + "] Tu pedido ha sido entregado con éxito.");
            pedido.marcarEntregado();

            // Persiste el estado ENTREGADO, deja la traza en la tabla
            // Entrega, y además archiva una "fotografía" del pedido en el
            // historial PedidoEntregado (tabla dedicada, pensada para que la
            // nueva pestaña "Pedidos Entregados" la filtre por fecha sin
            // tener que tocar la Zona de Carga).
            pedidoDAO.actualizarEstado(pedido);
            entregaDAO.registrarEntrega(pedido, pedido.getRepartidorAsignado(), "ENTREGADO");
            pedidoEntregadoDAO.registrar(pedido);

        } catch (InterruptedException e) {
            System.err.println("-> Alerta: La simulación del pedido " + pedido.getIdPedido() + " fue interrumpida.");
            Thread.currentThread().interrupt();
        }
    }

    private int tiempoAleatorio() {
        return ThreadLocalRandom.current().nextInt(ESPERA_MINIMA_MS, ESPERA_MAXIMA_MS + 1);
    }
}
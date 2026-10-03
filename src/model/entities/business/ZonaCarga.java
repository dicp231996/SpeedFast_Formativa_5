package model.entities.business;

import data.enumerate.EstadoPedido;
import model.core.Pedido;
import model.entities.dealer.Repartidor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Representa la zona de carga física del sistema: el punto en común donde
 * ingresan todas las instancias de Pedido creadas, y desde donde cada
 * repartidor retira ÚNICAMENTE los pedidos que ya le fueron asignados a él
 * (según las reglas de negocio de la Fase 1) y que se encuentran CONFIRMADOS.
 */
public class ZonaCarga {

    private final ArrayList<Pedido> pedidosRegistrados;
    private final BlockingQueue<Pedido> pedidosConfirmados;

    public ZonaCarga() {
        this.pedidosRegistrados = new ArrayList<>();
        this.pedidosConfirmados = new LinkedBlockingQueue<>();
    }

    public synchronized void agregarPedido(Pedido pedido) {
        if (pedido == null) {
            return;
        }

        if (!pedidosRegistrados.contains(pedido)) {
            pedidosRegistrados.add(pedido);
        }

        if (pedido.getEstado() == EstadoPedido.CONFIRMADO && !pedidosConfirmados.contains(pedido)) {
            pedidosConfirmados.offer(pedido);
            System.out.println("-> [ZONA DE CARGA] Pedido " + pedido.getId() + " disponible en el pool | Dirección de entrega: "
                    + pedido.getDireccionEntrega());
        }
    }

    // Retira, en una sola visita, TODA la carga de pedidos confirmados que
    // pertenecen a ese repartidor específico. Operación atómica.
    public synchronized ArrayList<Pedido> retirarCarga(Repartidor repartidor) {
        ArrayList<Pedido> carga = new ArrayList<>();
        if (repartidor == null) {
            return carga;
        }

        Iterator<Pedido> iterador = pedidosConfirmados.iterator();
        while (iterador.hasNext()) {
            Pedido pedido = iterador.next();
            if (pedido.getRepartidorAsignado() == repartidor) {
                carga.add(pedido);
                iterador.remove();
            }
        }

        return carga;
    }

    // Retira UN SOLO pedido confirmado perteneciente a ese repartidor (el
    // primero disponible en la cola), o null si no tiene ninguno pendiente
    // en este momento. Operación atómica: el repartidor debe volver a la
    // zona de carga por cada pedido, uno a la vez.
    public synchronized Pedido retirarUnPedido(Repartidor repartidor) {
        if (repartidor == null) {
            return null;
        }

        Iterator<Pedido> iterador = pedidosConfirmados.iterator();
        while (iterador.hasNext()) {
            Pedido pedido = iterador.next();
            if (pedido.getRepartidorAsignado() == repartidor) {
                iterador.remove();
                return pedido;
            }
        }

        return null;
    }

    // Quita un pedido por completo de la Zona de Carga: tanto del historial
    // de registrados (lo que muestra PanelZonaCarga) como de la cola de
    // confirmados en espera de ser retirados, si es que todavía estaba ahí.
    // Se usa al eliminar un pedido desde la interfaz para mantener la
    // memoria consistente con lo que queda en la base de datos.
    public synchronized void eliminarPedido(Pedido pedido) {
        if (pedido == null) {
            return;
        }
        pedidosRegistrados.remove(pedido);
        pedidosConfirmados.remove(pedido);
    }

    public synchronized boolean estaVacia() {
        return pedidosConfirmados.isEmpty();
    }

    public synchronized ArrayList<Pedido> listarRegistrados() {
        return new ArrayList<>(pedidosRegistrados);
    }
}
package data.util;

import data.enumerate.TipoPedido;
import model.core.Pedido;

import java.util.ArrayList;

// Ya NO escribe en pedidos.txt (esa responsabilidad ahora es de
// PedidoDAO.insertar, que guarda el pedido en la tabla Pedido de la base de
// datos). Se conserva únicamente la generación del siguiente ID correlativo
// por tipo de pedido (COM-001, ENC-002, EXP-003, ...), que es pura lógica de
// negocio y no tiene nada que ver con el mecanismo de persistencia.
public class GestorArchivoPedidos {

    public static String siguienteId(TipoPedido tipo, ArrayList<Pedido> listaPedidos) {
        String sigla = tipo.getSigla();
        int maxCorrelativo = 0;

        for (Pedido pedido : listaPedidos) {
            String idPedido = pedido.getIdPedido();
            String[] partes = idPedido.split("-");
            if (partes.length == 2 && partes[0].equalsIgnoreCase(sigla)) {
                try {
                    int numero = Integer.parseInt(partes[1]);
                    if (numero > maxCorrelativo) {
                        maxCorrelativo = numero;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return sigla + "-" + String.format("%03d", maxCorrelativo + 1);
    }
}
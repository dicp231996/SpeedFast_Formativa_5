package service;

import data.enumerate.TipoServicio;
import data.persistence.EntregaDAO;
import data.persistence.RepartidorDAO;
import model.entities.dealer.Repartidor;

import java.util.ArrayList;
import java.util.Map;

// =========================================================================
// CAPA DE SERVICIO: ServicioRepartidores
// =========================================================================
// Igual que ServicioPedidos, pero para todo lo relacionado con Repartidor:
// coordina entre la memoria de la aplicación (listaRepartidores) y la
// persistencia (RepartidorDAO para los datos del repartidor, EntregaDAO
// para sus estadísticas de entregas). La interfaz (PanelGestionRepartidores)
// ya no llama a los DAO directamente, le pide la operación a este servicio.
public class ServicioRepartidores {

    private final RepartidorDAO repartidorDAO = new RepartidorDAO();
    private final EntregaDAO entregaDAO = new EntregaDAO();

    private final ArrayList<Repartidor> listaRepartidores = new ArrayList<>();

    public ArrayList<Repartidor> getListaRepartidores() {
        return listaRepartidores;
    }

    // Carga inicial desde la base de datos. Debe llamarse una sola vez, al
    // arrancar la aplicación (y antes de cargar los pedidos, que necesitan
    // esta lista para enlazar al repartidor que tuvieran asignado).
    public void cargarDesdeBaseDeDatos() {
        listaRepartidores.clear();
        listaRepartidores.addAll(repartidorDAO.listarTodos());
    }

    // Cuántas entregas con estado ENTREGADO completó cada repartidor hoy,
    // indexado por RUT. Lo usa la nómina en PanelGestionRepartidores.
    public Map<String, Integer> contarEntregasHoy() {
        return entregaDAO.contarEntregasHoyPorRepartidor();
    }

    // Registra un repartidor nuevo: lo persiste en la base de datos y, solo
    // si tuvo éxito, lo agrega también a la lista en memoria. Lanza
    // OperacionNoPermitidaException si la base de datos rechaza el registro
    // (por ejemplo, un RUT repetido, o la conexión caída).
    public void registrarRepartidor(Repartidor repartidor) {
        boolean exito = repartidorDAO.insertar(repartidor);
        if (!exito) {
            throw new OperacionNoPermitidaException(
                    "No se pudo registrar el repartidor. Verifica que el RUT no esté repetido "
                            + "y que la conexión a la base de datos esté disponible.");
        }
        listaRepartidores.add(repartidor);
    }

    // Modifica los datos de un repartidor ya existente (el RUT no cambia).
    // Actualiza primero la base de datos; solo si la actualización tuvo
    // éxito se reflejan los cambios en el objeto en memoria (que es el mismo
    // que ya vive dentro de listaRepartidores), de modo que un fallo de la
    // base de datos no deje la nómina en memoria desincronizada con lo
    // realmente guardado. Lanza OperacionNoPermitidaException si la base de
    // datos rechaza la actualización.
    public void actualizarRepartidor(Repartidor repartidor, String nombre, String telefono, String vehiculo,
                                     TipoServicio tipoServicio, boolean tieneMochilaTermica,
                                     double capacidadPesoMax, boolean estaCercaUbicacion) {
        // Se guardan los valores anteriores por si la base de datos rechaza
        // el cambio y hay que revertir el objeto en memoria.
        String nombreAnterior = repartidor.getNombreCompleto();
        String telefonoAnterior = repartidor.getTelefono();
        String vehiculoAnterior = repartidor.getVehiculo();
        TipoServicio tipoServicioAnterior = repartidor.getTipoServicio();
        boolean mochilaAnterior = repartidor.isTieneMochilaTermica();
        double capacidadAnterior = repartidor.getCapacidadPesoMax();
        boolean cercaAnterior = repartidor.isEstaCercaUbicacion();

        repartidor.setNombreCompleto(nombre);
        repartidor.setTelefonoContacto(telefono);
        repartidor.setVehiculo(vehiculo);
        repartidor.setTipoServicio(tipoServicio);
        repartidor.setTieneMochilaTermica(tieneMochilaTermica);
        repartidor.setCapacidadPesoMax(capacidadPesoMax);
        repartidor.setEstaCercaUbicacion(estaCercaUbicacion);

        boolean exito = repartidorDAO.actualizar(repartidor);
        if (!exito) {
            repartidor.setNombreCompleto(nombreAnterior);
            repartidor.setTelefonoContacto(telefonoAnterior);
            repartidor.setVehiculo(vehiculoAnterior);
            repartidor.setTipoServicio(tipoServicioAnterior);
            repartidor.setTieneMochilaTermica(mochilaAnterior);
            repartidor.setCapacidadPesoMax(capacidadAnterior);
            repartidor.setEstaCercaUbicacion(cercaAnterior);
            throw new OperacionNoPermitidaException(
                    "No se pudo actualizar el repartidor en la base de datos. Revisa la conexión.");
        }
    }

    // Elimina un repartidor de la base de datos y, si tuvo éxito, también de
    // la memoria. Lanza OperacionNoPermitidaException si la base de datos
    // rechaza el borrado.
    public void eliminarRepartidor(Repartidor repartidor) {
        boolean exito = repartidorDAO.eliminar(repartidor.getRut());
        if (!exito) {
            throw new OperacionNoPermitidaException(
                    "No se pudo eliminar el repartidor de la base de datos. Revisa la conexión.");
        }
        listaRepartidores.remove(repartidor);
    }
}
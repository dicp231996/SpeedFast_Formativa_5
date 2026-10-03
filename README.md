# SpeedFast 🚀

Sistema de gestión de despachos/logística desarrollado en **Java (Swing)** como
proyecto para la asignatura de **Programación Orientada a Objetos II**.

SpeedFast simula el ciclo completo de una empresa de delivery: se registran
pedidos, se asignan a repartidores (manual o automáticamente), y luego se
"ejecuta" la entrega mediante **hilos concurrentes**, dejando registro
persistente de todo en una base de datos **MySQL**.

---

## Índice

1. [Características principales](#características-principales)
2. [Arquitectura del proyecto](#arquitectura-del-proyecto)
3. [Modelo de dominio](#modelo-de-dominio)
4. [Concurrencia](#concurrencia)
5. [Persistencia (base de datos)](#persistencia-base-de-datos)
6. [Capa de servicio](#capa-de-servicio)
7. [Interfaz gráfica (Swing)](#interfaz-gráfica-swing)
8. [Cómo ejecutar el proyecto](#cómo-ejecutar-el-proyecto)
9. [Estructura de carpetas](#estructura-de-carpetas)
10. [Limitaciones conocidas](#limitaciones-conocidas)

---

## Características principales

- **Registro de pedidos** de 3 tipos distintos (Comida, Encomienda, Compra
  Express), cada uno con sus propias reglas de negocio.
- **Asignación de repartidores**, tanto manual (el usuario elige a quién
  asignar) como automática (el sistema busca el repartidor más apto según
  tipo de servicio, capacidad de peso, mochila térmica y cercanía).
- **Ejecución de entregas mediante hilos**: cada repartidor con pedidos
  asignados corre en su propio hilo (`Thread`), retira **un pedido a la vez**
  desde la Zona de Carga, lo entrega (simulado con tiempos de espera
  aleatorios) y vuelve por el siguiente, hasta agotar sus pedidos.
- **Consola en vivo con código de colores**: el panel de ejecución muestra el
  progreso de cada hilo en tiempo real, coloreando cada línea según el estado
  del pedido (confirmado, en reparto, entregado, cancelado, etc.).
- **Persistencia en base de datos MySQL**: pedidos, repartidores y el
  historial de entregas se guardan y leen directamente desde una base de
  datos, reemplazando el enfoque inicial de archivos `.txt`.
- **Gestión de repartidores**: nómina completa de empleados, alta, **baja** y
  **modificación** de repartidores, y conteo de cuántas entregas realizó cada
  uno durante el día.
- **Gestión de clientes**: todo pedido pertenece a un cliente (RUT, nombre,
  teléfono, dirección, correo); nómina con alta, baja y modificación, igual
  que la de repartidores.
- **Edición del historial de entregas**: los registros de `PedidoEntregado`
  se pueden corregir o eliminar desde la pestaña "Pedidos Entregados"; lo
  eliminado queda en una **papelera en memoria** restaurable mientras la
  aplicación siga abierta.
- Manejo correcto de **tildes/ñ (UTF-8)** en toda la aplicación (consola,
  archivos y base de datos).

---

## Arquitectura del proyecto

El proyecto está organizado **por rol/responsabilidad** (no por feature),
siguiendo principios de POO: herencia, polimorfismo, interfaces, y separación
de capas (modelo de dominio / persistencia / interfaz gráfica).

```
app       -> Puntos de entrada de la aplicación (consola y GUI)
model     -> Entidades de dominio (Pedido, Repartidor, Persona, etc.)
data      -> Enumeradores, utilidades y capa de acceso a datos (DAO)
service   -> Capa de servicio: reglas de negocio + coordinación entre UI y DAO
ui        -> Paneles y ventanas Swing
```

La interfaz **no llama a los DAO directamente**: le pide la operación que
necesita a la capa de `service`, y es esa capa quien decide cómo ejecutarla
y mantener todo consistente (ver [Capa de servicio](#capa-de-servicio)).

---

## Modelo de dominio

### Jerarquía de Pedido

```
Pedido (clase abstracta)
 ├── PedidoComida
 ├── PedidoEncomienda
 └── PedidoExpress
```

`Pedido` implementa:
- `IDespachable` → `despachar()`: lógica de envío propia de cada subtipo.
- `ICancelable` → `cancelar(motivo)`, `isCancelado()`, `getMotivoCancelacion()`.
- `IRastreable` → `rastrear()`: devuelve el estado/ubicación simulada del pedido.

Cada pedido tiene un **ciclo de vida** representado por el enum
`EstadoPedido`:

```
PENDIENTE → CONFIRMADO → EN_REPARTO → ENTREGADO
                                   ↘ CANCELADO
```

Las transiciones normales se hacen con `pedido.nuevoEstado(...)` (imprime el
cambio en consola). Al reconstruir un pedido desde la base de datos se usan
`restaurarEstado(...)` / `restaurarCancelacion(...)`, que fijan el estado
**sin** disparar esos mensajes (evita "ruido" en la consola al simplemente
cargar datos existentes).

### Repartidor

`Repartidor` hereda de `Persona` e implementa `Runnable` (además de una
interfaz propia `IRunnable`). Contiene:

- Datos personales (heredados de `Persona`: nombre, teléfono).
- `rut`: identificador de negocio, usado como clave natural para relacionar
  un pedido con su repartidor en la base de datos.
- `vehiculo`: dato informativo, no afecta ninguna regla de asignación.
- `tipoServicio`, `tieneMochilaTermica`, `capacidadPesoMax`,
  `estaCercaUbicacion`: usados por el algoritmo de asignación automática.
- Un arreglo fijo de máximo 5 pedidos asignados simultáneamente.

### Cliente

`Cliente` también hereda de `Persona` (nombre, teléfono) y agrega `rut`
(clave de negocio, igual que en `Repartidor`), `direccion` y `correo`
(opcional). Es quien "recibe" el pedido: `Pedido.cliente` guarda esa
relación, asignada con `setCliente(...)` **después** de construir el pedido
(no se agregó a los constructores de `PedidoComida`/`PedidoEncomienda`/
`PedidoExpress` para no tener que tocar los tres), tal como
`repartidorAsignado` se asigna aparte con `asignarRepartidor(...)`.

La relación es obligatoria a nivel de negocio — `ServicioPedidos.
registrarPedido(...)` rechaza guardar un pedido sin cliente — pero la
columna `Pedido.id_cliente` en la base de datos quedó `NULL`-able, para no
romper los pedidos de prueba que ya existían antes de que esta entidad se
agregara al proyecto.

### ZonaCarga

Actúa como una bodega/cola compartida de pedidos pendientes por retirar.
Es **thread-safe**: sus métodos están sincronizados para que varios
repartidores (hilos) puedan retirar pedidos al mismo tiempo sin condiciones
de carrera. El método clave es `retirarUnPedido(repartidor)`, que entrega
**un solo pedido apto** a la vez (no el lote completo), forzando a cada
repartidor a volver a la zona de carga después de cada entrega.

---

## Concurrencia

El flujo de ejecución de entregas usa dos niveles de hilos:

1. **Un `ExecutorService`** lanza un hilo por cada repartidor que tiene al
   menos un pedido asignado (`Repartidor implements Runnable`).
2. Dentro de `Repartidor.run()`, por cada pedido que retira se crea y lanza
   un **hilo hijo** (`HiloEntrega implements Runnable`, ejecutado como
   `Thread`) que simula el tiempo de viaje/entrega con
   `ThreadLocalRandom`, y el hilo padre espera con `join()` antes de volver
   por el siguiente pedido.

Esto modela de forma realista que un repartidor **no puede llevar dos
pedidos a la vez**: retira uno, viaja, entrega, y solo entonces vuelve por
el próximo.

Toda la salida de estos hilos (`System.out`/`System.err`) se redirige a la
consola visual de Swing (`ConsolaSwingOutputStream`), que decodifica los
bytes como UTF-8 respetando los límites de línea (evita que tildes/ñ se
corrompan al mezclarse con la escritura concurrente de varios hilos).

---

## Persistencia (base de datos)

El proyecto usa **JDBC puro** (sin frameworks ORM) contra una base de datos
**MySQL** llamada `speedfast_db`. Los scripts SQL están en `resources/`:

| Script | Uso |
|---|---|
| `schema_speedfast.sql` | Crea la base de datos y las 5 tablas desde cero. |
| `migracion_alter_tablas.sql` | Migra un esquema simple preexistente al esquema extendido (agrega columnas). |
| `migracion_pedidos_entregados.sql` | Agrega la tabla `PedidoEntregado` (historial) a una base ya existente. |
| `migracion_clientes.sql` | Agrega la tabla `Cliente` y la columna `Pedido.id_cliente` a una base ya existente, con 10 clientes de ejemplo. |
| `migracion_cliente_en_entregados.sql` | Agrega las columnas `rut_cliente`/`nombre_cliente` a una tabla `PedidoEntregado` ya existente (creada antes de que el historial guardara el cliente). |
| `datos_iniciales.sql` | Carga de datos de ejemplo (15 pedidos, 30 repartidores). |
| `datos_prueba_pedidos_con_clientes.sql` | 30 pedidos de prueba (10 de cada tipo), cada uno ya asociado a uno de los 10 clientes de ejemplo. |
| `reset_datos_prueba.sql` | Vacía `Pedido`/`Entrega`/`PedidoEntregado` y carga 30 pedidos de prueba nuevos (reseteo completo). |
| `reset_solo_pedidos.sql` | Igual que el anterior pero sin tocar `PedidoEntregado` (conserva el historial). |

> **Entrega del proyecto:** la base de datos se entrega como estos scripts
> `.sql` sueltos dentro de `resources/` (no como un dump único), tal como
> están. La sección [Cómo ejecutar el proyecto](#cómo-ejecutar-el-proyecto)
> indica el orden exacto en que deben ejecutarse para dejar la base de datos
> igual a la usada en el desarrollo.

### Tablas

- **Repartidor**: `id_repartidor`, `rut` (clave de negocio, único),
  `nombre`, `vehiculo`, `telefono`, `tipo_servicio`,
  `tiene_mochila_termica`, `capacidad_peso_max`, `esta_cerca_ubicacion`.
- **Cliente**: `id_cliente`, `rut` (clave de negocio, único), `nombre`,
  `telefono`, `direccion`, `correo` (opcional).
- **Pedido**: `id_pedido`, `codigo_pedido` (clave de negocio, único),
  `id_cliente` (FK, `NULL`-able), `tipo_pedido`, `descripcion`,
  `direccion_destino`, `distancia_km`, `peso_kg`, `estado`,
  `motivo_cancelacion`, `id_repartidor_asignado` (FK). Un pedido permanece
  aquí durante TODO su ciclo de vida, incluido después de `ENTREGADO` (así
  `Entrega` conserva su integridad referencial).
- **Entrega**: historial técnico de entregas — `id_pedido` (FK),
  `id_repartidor` (FK), `fecha_entrega`, `estado_entrega`.
- **PedidoEntregado**: historial de negocio de pedidos **entregados con
  éxito**, pensado para la pestaña "Pedidos Entregados" y para métricas
  futuras. A propósito es una tabla **denormalizada** (sin llaves foráneas):
  guarda una copia de `codigo_pedido`, `tipo_pedido`, `direccion_destino`,
  `distancia_km`, `peso_kg`, `rut_repartidor`/`nombre_repartidor` y
  `rut_cliente`/`nombre_cliente` como texto plano, más `fecha_entrega` (con
  índice dedicado). Así:
  - Las consultas por fecha (o las métricas que se agreguen más adelante)
    no necesitan hacer `JOIN` con `Pedido`/`Repartidor`.
  - El historial sobrevive intacto aunque el pedido o el repartidor
    original se eliminen después desde la interfaz.

### Capa DAO (`data.persistence`)

- `ConexionBD`: punto único de conexión (`DriverManager` + URL/usuario/clave).
  Expone `verificarConexion()`, usado al arrancar la GUI para mostrar un
  diálogo de error claro si la base no está disponible (en vez de que la
  aplicación abra "vacía" sin explicación).
- `RepartidorDAO`: `listarTodos()`, `insertar(Repartidor)`,
  `actualizar(Repartidor)` y `eliminar(rut)`. `eliminar(...)` corre en una
  transacción: primero libera (deja en `NULL`) cualquier pedido que tuviera
  asignado, luego borra su historial en `Entrega`, y solo entonces borra la
  fila de `Repartidor` — así nunca viola las llaves foráneas que apuntan a él.
- `ClienteDAO`: mismo patrón que `RepartidorDAO` —  `listarTodos()`,
  `insertar(Cliente)`, `actualizar(Cliente)` y `eliminar(rut)` (libera los
  pedidos asociados, dejando `id_cliente` en `NULL`, antes de borrar al
  cliente).
- `PedidoDAO`: `listarTodos(repartidores, clientes)`, `insertar(Pedido)`,
  `actualizarEstado(Pedido)` y `eliminar(codigoPedido)`. `eliminar(...)`
  también usa una transacción: borra primero las filas de `Entrega` que
  referencian a ese pedido, y luego el pedido mismo. Tanto
  `id_repartidor_asignado` como `id_cliente` se resuelven en el `INSERT`/
  `UPDATE` con una subconsulta por RUT (si el pedido no tiene cliente, la
  subconsulta no encuentra fila y el valor queda `NULL`, sin necesitar una
  rama de SQL aparte).
- `EntregaDAO`: `registrarEntrega(...)` y
  `contarEntregasHoyPorRepartidor()` (usado en la nómina para mostrar
  cuántas entregas hizo cada repartidor durante el día).
- `PedidoEntregadoDAO`: `registrar(Pedido)` (archiva la "fotografía" de un
  pedido justo al entregarse), `listarTodos()`, `listarEntreFechas(desde,
  hasta)`, `contarEntregasPorDia()`, y además `actualizar(...)` /
  `eliminarPorId(...)` / `insertarDesdeRegistro(...)` para editar, borrar y
  restaurar un registro puntual del historial. Devuelve
  `RegistroPedidoEntregado` (`model.historial`), un DTO — no un `Pedido` —
  porque esa fila representa un hecho histórico ya cerrado, sin
  comportamiento; "editarlo" significa reemplazarlo por una copia nueva
  (`RegistroPedidoEntregado.conCambios(...)`).

Las relaciones se resuelven por **clave de negocio** (`rut`,
`codigo_pedido`) mediante subconsultas SQL, en vez de exponer los IDs
autoincrementales de la base de datos dentro del modelo de dominio Java.

> **Requisito para ejecutar:** MySQL corriendo localmente, con la base
> `speedfast_db` creada (`schema_speedfast.sql` + `datos_iniciales.sql`), y
> el driver `mysql-connector-j` agregado como librería del proyecto. Ajusta
> usuario/clave en `ConexionBD.java`.

---

## Capa de servicio

Siguiendo la convención de la industria (DAO = acceso a datos puro, sin
reglas de negocio), el proyecto incorpora una capa intermedia de
**servicio** entre la interfaz y los DAO:

```
UI (paneles Swing)
   ↓  "registra este pedido", "elimina este repartidor"...
service.ServicioPedidos / service.ServicioRepartidores / service.ServicioClientes
   ↓  valida reglas de negocio, decide cómo persistir
data.persistence (DAO)
   ↓
Base de datos
```

Antes de este refactor, los paneles llamaban directamente a los DAO y
además mantenían "a mano" la coherencia entre la base de datos, la
`ZonaCarga` y las listas en memoria. Esa responsabilidad no le corresponde
ni a la UI ni al DAO, así que se extrajo a dos clases de servicio:

- **`ServicioPedidos`**: dueño de la lista de pedidos en memoria y de la
  `ZonaCarga`. Expone operaciones de alto nivel: `registrarPedido(...)`
  (rechaza el pedido si no tiene cliente asociado — ver [Cliente](#cliente)),
  `confirmarAsignacion(pedido, candidato)` (usada tanto por la asignación
  automática como la manual), `eliminarPedido(...)`,
  `cargarDesdeBaseDeDatos(repartidores, clientes)` (carga inicial) y, para el
  historial, `listarHistorialEntregados()` /
  `listarHistorialEntregados(desde, hasta)`, `contarEntregasPorDia()`,
  `actualizarRegistroHistorial(...)`, `eliminarRegistroHistorial(...)` y
  `restaurarRegistroHistorial(...)`.
- **`ServicioRepartidores`**: dueño de la lista de repartidores en memoria.
  Expone `registrarRepartidor(...)`, `actualizarRepartidor(...)`,
  `eliminarRepartidor(...)`, `contarEntregasHoy()` (combina `RepartidorDAO` y
  `EntregaDAO` para la nómina) y `cargarDesdeBaseDeDatos()`.
- **`ServicioClientes`**: dueño de la lista de clientes en memoria. Mismo
  patrón que `ServicioRepartidores`: `registrarCliente(...)`,
  `actualizarCliente(...)`, `eliminarCliente(...)` y
  `cargarDesdeBaseDeDatos()`. La alimentan tanto `PanelGestionClientes` como
  el combo de selección de cliente en `PanelAgregarPedido`.

**Papelera del historial de entregas:** a diferencia de las otras
eliminaciones (que son definitivas), `ServicioPedidos.
eliminarRegistroHistorial(...)` guarda una copia del registro borrado en una
lista **solo en memoria** (`papeleraHistorial`, nunca persistida). Mientras
la aplicación siga abierta, `restaurarRegistroHistorial(...)` puede volver a
insertarla en la base de datos; si la aplicación se cierra sin restaurarla,
se pierde de verdad.

Cuando una operación no es válida —ya sea por una regla de negocio (por
ejemplo, intentar eliminar un pedido que está `EN_REPARTO`) o porque la base
de datos la rechaza (RUT repetido, conexión caída)— el servicio lanza una
**`OperacionNoPermitidaException`** con un mensaje ya redactado para
mostrarle al usuario. Así, el panel no necesita conocer la razón técnica:
solo captura la excepción y muestra su mensaje en un diálogo.

```java
try {
    servicioRepartidores.eliminarRepartidor(repartidor);
    actualizarDatos();
} catch (OperacionNoPermitidaException e) {
    JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo eliminar", JOptionPane.WARNING_MESSAGE);
}
```

**Nota de diseño:** el motor de concurrencia (`Repartidor`/`HiloEntrega`,
dentro de `model.*`) sigue llamando directamente a
`PedidoDAO`/`EntregaDAO`/`PedidoEntregadoDAO` en vez de pasar por
`ServicioPedidos`. Es una excepción intencional: esas clases pertenecen a la
simulación de entregas, no a la interfaz, y hacerlas depender de la capa de
servicio invertiría la dirección de dependencias que se buscó con este
refactor (UI → Servicio → DAO/Modelo). `ServicioPedidos` ya tiene un método
`completarEntrega(...)` (que también archiva en el historial) listo por si
en el futuro se decide unificar ambos caminos.

---

## Interfaz gráfica (Swing)

La navegación usa un `CardLayout` central (`VentanaPrincipal`), con un
historial (`ArrayDeque<String>`) que permite "volver" a la pantalla
anterior. Los paneles principales son:

| Panel | Función |
|---|---|
| `PanelMenuPrincipal` | Menú principal con acceso a las demás secciones. |
| `PanelAgregarPedido` | Formulario para registrar un nuevo pedido; incluye un combo **obligatorio** para elegir el cliente. |
| `PanelZonaCarga` | Lista/filtra los pedidos registrados **aún no entregados** y permite **eliminarlos**. |
| `PanelAsignacion` | Asignación **automática** de pedidos a repartidores. |
| `PanelAsignacionManual` | Asignación **manual**, elegida por el usuario. |
| `PanelEjecucionHilos` | Consola en vivo de la ejecución de entregas (colores por estado). |
| `PanelGestionRepartidores` | Nómina de repartidores, entregas del día, alta, **baja** y **modificación** de repartidores. |
| `PanelGestionClientes` | Nómina de clientes: alta, baja y modificación. |
| `PanelPedidosEntregados` | Historial de pedidos **entregados con éxito**, filtrable por rango de fechas, con edición, eliminación y papelera restaurable. |

La ejecución de hilos (`PanelEjecucionHilos`) **solo** se puede iniciar
después de completar una asignación (manual o automática) exitosa, mediante
el botón "Realizar Entregas ➜" que aparece en el diálogo de confirmación.

**Zona de Carga vs. Pedidos Entregados:** apenas un pedido llega a
`ENTREGADO` (dentro de `HiloEntrega`), desaparece de `PanelZonaCarga` y pasa
a vivir únicamente en `PanelPedidosEntregados`, respaldado por la tabla
`PedidoEntregado`. Este último panel trae por defecto el último mes del
historial, con filtros "Desde"/"Hasta" para acotar el rango de fechas (o un
botón para ver el historial completo), y un resumen con totales por tipo de
pedido y distancia recorrida — la base para construir métricas más
elaboradas (entregas por semana, kilómetros por repartidor, etc.) más
adelante sin tener que tocar el esquema de nuevo.

Desde esta misma pestaña se puede **editar** un registro (dirección,
distancia, peso y datos del repartidor; el código, tipo y fecha de entrega
quedan fijos porque identifican el hecho histórico) o **eliminarlo**. Un
registro eliminado no desaparece del todo: queda en una papelera en memoria
accesible con el botón "♻ Papelera / Restaurar", disponible mientras la
aplicación no se cierre.

---

## Cómo ejecutar el proyecto

> **Nota sobre la entrega:** la base de datos se entrega como los scripts
> `.sql` sueltos de `resources/` (no como un dump/backup único). Quien reciba
> el proyecto debe crear la base de datos ejecutando esos scripts **en el
> orden indicado abajo**, en su propia instancia de MySQL.

### A) Base de datos nueva (caso normal: clonar el proyecto desde cero)

1. Instalar y levantar MySQL localmente.
2. Ejecutar, en este orden, con un cliente MySQL (consola, Workbench, el
   plugin de MySQL de IntelliJ, etc.):
  1. `resources/schema_speedfast.sql` — crea la base `speedfast_db` y las
     5 tablas ya actualizadas (incluye `Cliente` y las columnas de cliente
     en `PedidoEntregado`).
  2. `resources/migracion_clientes.sql` — carga los 10 clientes de ejemplo
     (necesarios para los pedidos de prueba del siguiente paso).
  3. `resources/datos_prueba_pedidos_con_clientes.sql` — carga 30 pedidos
     de prueba, cada uno ya asociado a un cliente.
3. Ajustar usuario/clave en `data/persistence/ConexionBD.java` según tu
   instalación de MySQL.
4. Agregar el driver `mysql-connector-j-x.x.x.jar` como librería del
   proyecto (IntelliJ: *File → Project Structure → Libraries → "+"*).
5. Ejecutar `app.SpeedFastGUI` (interfaz gráfica) o `app.SpeedFast`
   (versión por consola).

### B) Ya tenías una base de datos de una versión anterior del proyecto

Ejecuta solo los scripts de migración que correspondan a lo que te falte,
en este orden:

1. `migracion_alter_tablas.sql` — si tu base venía del esquema simple
   original (antes de `Entrega`/`PedidoEntregado`).
2. `migracion_pedidos_entregados.sql` — si te falta la tabla
   `PedidoEntregado` (historial de entregas).
3. `migracion_clientes.sql` — si te falta la tabla `Cliente` y la columna
   `Pedido.id_cliente`.
4. `migracion_cliente_en_entregados.sql` — si ya tenías `PedidoEntregado`
   pero sin las columnas `rut_cliente`/`nombre_cliente` (es decir, si
   ejecutaste el script del punto 2 antes de que existiera `Cliente`).

Cada script de migración es idempotente en la práctica (usa
`CREATE TABLE IF NOT EXISTS` o columnas nuevas), pero están pensados para
ejecutarse **una sola vez** cada uno.

---

## Estructura de carpetas

```
src/
├── app/
│   ├── SpeedFast.java          # Punto de entrada por consola
│   └── SpeedFastGUI.java       # Punto de entrada de la interfaz gráfica
├── data/
│   ├── enumerate/              # EstadoPedido, TipoPedido, TipoServicio
│   ├── persistence/            # ConexionBD, RepartidorDAO, ClienteDAO, PedidoDAO, EntregaDAO, PedidoEntregadoDAO
│   └── util/                   # ControladorEnvios, GestorFases, GestorArchivoPedidos
├── model/
│   ├── core/                   # Pedido (abstracta), Persona
│   ├── entities/
│   │   ├── business/           # ZonaCarga
│   │   ├── client/              # Cliente
│   │   ├── dealer/             # Repartidor
│   │   └── order/               # PedidoComida, PedidoEncomienda, PedidoExpress
│   ├── historial/                # RegistroPedidoEntregado (DTO de solo lectura)
│   ├── interfaces/              # IDespachable, ICancelable, IRastreable, IRunnable
│   └── valueobjects/            # HiloEntrega
├── service/                     # ServicioPedidos, ServicioRepartidores, ServicioClientes, OperacionNoPermitidaException
└── ui/                          # Ventanas y paneles Swing
resources/                       # Scripts SQL y archivos .txt originales (legado)
```

---

## Limitaciones conocidas

- El flujo por **consola** (`app.SpeedFast` / `GestorFases`) no persiste en
  la base de datos los cambios de estado durante la asignación/cancelación
  ni las entregas — solo la carga inicial usa la base de datos. Esta lógica
  sí está completamente integrada en el flujo de la **interfaz gráfica**.
- `resources/pedidos.txt` y `resources/repartidores.txt` se mantienen solo
  como referencia histórica; ya no son leídos por la aplicación (fueron
  reemplazados por la base de datos).
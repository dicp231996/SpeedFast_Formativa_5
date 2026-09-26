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
6. [Interfaz gráfica (Swing)](#interfaz-gráfica-swing)
7. [Cómo ejecutar el proyecto](#cómo-ejecutar-el-proyecto)
8. [Estructura de carpetas](#estructura-de-carpetas)
9. [Limitaciones conocidas](#limitaciones-conocidas)

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
- **Gestión de repartidores**: nómina completa de empleados, alta de nuevos
  repartidores y conteo de cuántas entregas realizó cada uno durante el día.
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
ui        -> Paneles y ventanas Swing
```

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
| `schema_speedfast.sql` | Crea la base de datos y las 3 tablas desde cero. |
| `migracion_alter_tablas.sql` | Migra un esquema simple preexistente al esquema extendido (agrega columnas). |
| `datos_iniciales.sql` | Carga de datos de ejemplo (15 pedidos, 30 repartidores). |

### Tablas

- **Repartidor**: `id_repartidor`, `rut` (clave de negocio, único),
  `nombre`, `vehiculo`, `telefono`, `tipo_servicio`,
  `tiene_mochila_termica`, `capacidad_peso_max`, `esta_cerca_ubicacion`.
- **Pedido**: `id_pedido`, `codigo_pedido` (clave de negocio, único),
  `tipo_pedido`, `descripcion`, `direccion_destino`, `distancia_km`,
  `peso_kg`, `estado`, `motivo_cancelacion`, `id_repartidor_asignado` (FK).
- **Entrega**: historial de entregas — `id_pedido` (FK), `id_repartidor`
  (FK), `fecha_entrega`, `estado_entrega`.

### Capa DAO (`data.persistence`)

- `ConexionBD`: punto único de conexión (`DriverManager` + URL/usuario/clave).
  Expone `verificarConexion()`, usado al arrancar la GUI para mostrar un
  diálogo de error claro si la base no está disponible (en vez de que la
  aplicación abra "vacía" sin explicación).
- `RepartidorDAO`: `listarTodos()` e `insertar(Repartidor)`.
- `PedidoDAO`: `listarTodos(repartidores)`, `insertar(Pedido)`,
  `actualizarEstado(Pedido)`.
- `EntregaDAO`: `registrarEntrega(...)` y
  `contarEntregasHoyPorRepartidor()` (usado en la nómina para mostrar
  cuántas entregas hizo cada repartidor durante el día).

Las relaciones se resuelven por **clave de negocio** (`rut`,
`codigo_pedido`) mediante subconsultas SQL, en vez de exponer los IDs
autoincrementales de la base de datos dentro del modelo de dominio Java.

> **Requisito para ejecutar:** MySQL corriendo localmente, con la base
> `speedfast_db` creada (`schema_speedfast.sql` + `datos_iniciales.sql`), y
> el driver `mysql-connector-j` agregado como librería del proyecto. Ajusta
> usuario/clave en `ConexionBD.java`.

---

## Interfaz gráfica (Swing)

La navegación usa un `CardLayout` central (`VentanaPrincipal`), con un
historial (`ArrayDeque<String>`) que permite "volver" a la pantalla
anterior. Los paneles principales son:

| Panel | Función |
|---|---|
| `PanelMenuPrincipal` | Menú principal con acceso a las demás secciones. |
| `PanelAgregarPedido` | Formulario para registrar un nuevo pedido. |
| `PanelZonaCarga` | Lista/filtra todos los pedidos registrados. |
| `PanelAsignacion` | Asignación **automática** de pedidos a repartidores. |
| `PanelAsignacionManual` | Asignación **manual**, elegida por el usuario. |
| `PanelEjecucionHilos` | Consola en vivo de la ejecución de entregas (colores por estado). |
| `PanelGestionRepartidores` | Nómina de repartidores, entregas del día y alta de nuevos repartidores. |

La ejecución de hilos (`PanelEjecucionHilos`) **solo** se puede iniciar
después de completar una asignación (manual o automática) exitosa, mediante
el botón "Realizar Entregas ➜" que aparece en el diálogo de confirmación.

---

## Cómo ejecutar el proyecto

1. Instalar y levantar MySQL localmente.
2. Ejecutar `resources/schema_speedfast.sql` y luego
   `resources/datos_iniciales.sql` (o `migracion_alter_tablas.sql` si ya
   tenías un esquema simple previo).
3. Ajustar usuario/clave en `data/persistence/ConexionBD.java` si es
   necesario.
4. Agregar el driver `mysql-connector-j-x.x.x.jar` como librería del
   proyecto (IntelliJ: *File → Project Structure → Libraries → "+"*).
5. Ejecutar `app.SpeedFastGUI` (interfaz gráfica) o `app.SpeedFast`
   (versión por consola).

---

## Estructura de carpetas

```
src/
├── app/
│   ├── SpeedFast.java          # Punto de entrada por consola
│   └── SpeedFastGUI.java       # Punto de entrada de la interfaz gráfica
├── data/
│   ├── enumerate/              # EstadoPedido, TipoPedido, TipoServicio
│   ├── persistence/            # ConexionBD, RepartidorDAO, PedidoDAO, EntregaDAO
│   └── util/                   # ControladorEnvios, GestorFases, GestorArchivoPedidos
├── model/
│   ├── core/                   # Pedido (abstracta), Persona
│   ├── entities/
│   │   ├── business/           # ZonaCarga
│   │   ├── dealer/             # Repartidor
│   │   └── order/               # PedidoComida, PedidoEncomienda, PedidoExpress
│   ├── interfaces/              # IDespachable, ICancelable, IRastreable, IRunnable
│   └── valueobjects/            # HiloEntrega
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
-- =========================================================
-- SpeedFast - Esquema de base de datos (versión extendida)
-- =========================================================
-- Este es tu esquema original, EXTENDIDO con las columnas que la lógica de
-- negocio actual del proyecto necesita y que no estaban contempladas:
--   - Pedido: para reconstruir el pedido correcto (PedidoComida/
--     PedidoEncomienda/PedidoExpress) hace falta tipo_pedido, la distancia
--     (usada por calcularTiempoEntrega()) y el peso (solo Encomienda usa
--     capacidad de peso al validar un repartidor). También se agregó
--     codigo_pedido (tu ID de negocio, p.ej. "COM-001") como clave única,
--     motivo_cancelacion y el FK id_repartidor_asignado.
--   - Repartidor: la asignación automática/manual filtra candidatos según
--     tipo_servicio, capacidad_peso_max, tiene_mochila_termica y
--     esta_cerca_ubicacion, así que esas columnas son indispensables.
--
-- Usa este script si vas a crear la base desde cero. Si ya ejecutaste tu
-- script original (con las 3 tablas simples), usa en cambio
-- migracion_alter_tablas.sql para agregarle estas columnas sin perder datos.

CREATE DATABASE IF NOT EXISTS speedfast_db;
USE speedfast_db;

CREATE TABLE Repartidor (
    id_repartidor INT AUTO_INCREMENT PRIMARY KEY,
    rut VARCHAR(12) UNIQUE NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    vehiculo VARCHAR(50),
    telefono VARCHAR(20),
    tipo_servicio VARCHAR(20) NOT NULL,              -- COMIDA | ENCOMIENDA | COMPRA_EXPRESS
    tiene_mochila_termica BOOLEAN NOT NULL DEFAULT FALSE,
    capacidad_peso_max DOUBLE NOT NULL DEFAULT 0,
    esta_cerca_ubicacion BOOLEAN NOT NULL DEFAULT FALSE
);

-- Quién hace el pedido. id_cliente es NULLABLE en Pedido a propósito (no
-- todos los flujos de prueba antiguos tienen cliente), pero la regla de
-- negocio real ("todo pedido nuevo debe tener cliente") se aplica en
-- ServicioPedidos.registrarPedido(...), no aquí en el esquema.
CREATE TABLE Cliente (
    id_cliente INT AUTO_INCREMENT PRIMARY KEY,
    rut VARCHAR(12) UNIQUE NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    telefono VARCHAR(20),
    direccion VARCHAR(155),
    correo VARCHAR(100)
);

CREATE TABLE Pedido (
    id_pedido INT AUTO_INCREMENT PRIMARY KEY,
    codigo_pedido VARCHAR(20) UNIQUE NOT NULL,       -- ID de negocio, p.ej. "COM-001"
    id_cliente INT NULL,
    tipo_pedido VARCHAR(30) NOT NULL,                -- PedidoComida | PedidoEncomienda | PedidoExpress
    descripcion VARCHAR(255) NOT NULL,
    direccion_destino VARCHAR(155) NOT NULL,
    distancia_km DOUBLE NOT NULL DEFAULT 0,
    peso_kg DOUBLE NULL,                             -- solo aplica a PedidoEncomienda
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE | CONFIRMADO | EN_REPARTO | ENTREGADO | CANCELADO
    id_repartidor_asignado INT NULL,
    motivo_cancelacion VARCHAR(255) NULL,
    FOREIGN KEY (id_cliente) REFERENCES Cliente(id_cliente),
    FOREIGN KEY (id_repartidor_asignado) REFERENCES Repartidor(id_repartidor)
);

CREATE TABLE Entrega (
    id_entrega INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT,
    id_repartidor INT,
    fecha_entrega DATETIME,
    estado_entrega VARCHAR(50),
    FOREIGN KEY (id_pedido) REFERENCES Pedido(id_pedido),
    FOREIGN KEY (id_repartidor) REFERENCES Repartidor(id_repartidor)
);

-- Historial de pedidos ENTREGADOS con éxito. A propósito es una tabla
-- "denormalizada" (sin llaves foráneas, con los datos del pedido y del
-- repartidor copiados como texto): así las consultas de métricas no
-- necesitan JOIN, y el historial se conserva intacto aunque el pedido o el
-- repartidor original se eliminen después desde la interfaz. Se llena desde
-- HiloEntrega en el momento exacto en que un pedido termina de entregarse.
-- Ver también resources/migracion_pedidos_entregados.sql.
CREATE TABLE PedidoEntregado (
    id_pedido_entregado INT AUTO_INCREMENT PRIMARY KEY,
    codigo_pedido VARCHAR(20) NOT NULL,
    tipo_pedido VARCHAR(30) NOT NULL,
    direccion_destino VARCHAR(155) NOT NULL,
    distancia_km DOUBLE NOT NULL DEFAULT 0,
    peso_kg DOUBLE NULL,
    rut_repartidor VARCHAR(12) NULL,
    nombre_repartidor VARCHAR(100) NULL,
    rut_cliente VARCHAR(12) NULL,
    nombre_cliente VARCHAR(100) NULL,
    fecha_entrega DATETIME NOT NULL,
    INDEX idx_pedido_entregado_fecha (fecha_entrega)
);

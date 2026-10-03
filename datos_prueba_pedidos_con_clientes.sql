-- =========================================================
-- SpeedFast - Pedidos de prueba YA con cliente asignado
-- =========================================================
-- Versión de reset_solo_pedidos.sql pensada para después de haber corrido
-- migracion_clientes.sql: en vez de dejar id_cliente en NULL, cada uno de
-- los 30 pedidos queda vinculado a uno de los 10 clientes de ejemplo
-- (RUTs 11111111-1 ... 10101010-1), repartidos de forma rotativa.
--
-- REQUISITO: la tabla Cliente debe tener esos 10 RUTs cargados (correr
-- primero resources/migracion_clientes.sql si todavía no lo hiciste). Si un
-- RUT no existe en Cliente, la subconsulta no encuentra fila y ese pedido
-- queda con id_cliente en NULL (no falla, pero tampoco queda vinculado).
--
--   1) Borra el contenido de Entrega y Pedido. Cliente, Repartidor y
--      PedidoEntregado NO se tocan.
--   2) Carga 30 pedidos nuevos en PENDIENTE (10 Comida, 10 Encomienda, 10
--      Express), cada uno con su cliente asignado.
--
-- Cómo ejecutarlo en MySQL Workbench: "Execute SQL Script" (el ícono del
-- rayo con hojas apiladas, o Ctrl+Shift+Enter), nunca sentencia por
-- sentencia, para que corran todas las líneas del archivo en orden.

USE speedfast_db;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE Entrega;
TRUNCATE TABLE Pedido;
SET FOREIGN_KEY_CHECKS = 1;

-- Comida (10) -> clientes 11111111-1 a 10101010-1, en orden
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-001', (SELECT id_cliente FROM Cliente WHERE rut = '11111111-1'), 'PedidoComida', 'Pedido de Comida a Av. Apoquindo 4500, Las Condes', 'Av. Apoquindo 4500, Las Condes', 2.1, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-002', (SELECT id_cliente FROM Cliente WHERE rut = '22222222-2'), 'PedidoComida', 'Pedido de Comida a Av. Irarrázaval 2850, Ñuñoa', 'Av. Irarrázaval 2850, Ñuñoa', 3.2, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-003', (SELECT id_cliente FROM Cliente WHERE rut = '33333333-3'), 'PedidoComida', 'Pedido de Comida a Av. Providencia 1760, Providencia', 'Av. Providencia 1760, Providencia', 2.6, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-004', (SELECT id_cliente FROM Cliente WHERE rut = '44444444-4'), 'PedidoComida', 'Pedido de Comida a Av. Manuel Antonio Matta 300, Santiago Centro', 'Av. Manuel Antonio Matta 300, Santiago Centro', 1.8, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-005', (SELECT id_cliente FROM Cliente WHERE rut = '55555555-5'), 'PedidoComida', 'Pedido de Comida a Av. Pedro de Valdivia 2100, Providencia', 'Av. Pedro de Valdivia 2100, Providencia', 4.5, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-006', (SELECT id_cliente FROM Cliente WHERE rut = '66666666-6'), 'PedidoComida', 'Pedido de Comida a Av. Vicuña Mackenna 3939, San Joaquín', 'Av. Vicuña Mackenna 3939, San Joaquín', 2.3, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-007', (SELECT id_cliente FROM Cliente WHERE rut = '77777777-7'), 'PedidoComida', 'Pedido de Comida a Av. Grecia 2000, Ñuñoa', 'Av. Grecia 2000, Ñuñoa', 3.7, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-008', (SELECT id_cliente FROM Cliente WHERE rut = '88888888-8'), 'PedidoComida', 'Pedido de Comida a Av. Suecia 180, Providencia', 'Av. Suecia 180, Providencia', 1.4, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-009', (SELECT id_cliente FROM Cliente WHERE rut = '99999999-9'), 'PedidoComida', 'Pedido de Comida a Av. Larraín 6200, La Reina', 'Av. Larraín 6200, La Reina', 5.1, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('COM-010', (SELECT id_cliente FROM Cliente WHERE rut = '10101010-1'), 'PedidoComida', 'Pedido de Comida a Av. Tobalaba 1300, Peñalolén', 'Av. Tobalaba 1300, Peñalolén', 4.0, NULL, 'PENDIENTE');

-- Encomienda (10) -> mismos 10 clientes, de nuevo en orden
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-001', (SELECT id_cliente FROM Cliente WHERE rut = '11111111-1'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Las Condes 8520, Las Condes', 'Av. Las Condes 8520, Las Condes', 9.1, 4.0, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-002', (SELECT id_cliente FROM Cliente WHERE rut = '22222222-2'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Macul 3100, Macul', 'Av. Macul 3100, Macul', 5.7, 12.3, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-003', (SELECT id_cliente FROM Cliente WHERE rut = '33333333-3'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Departamental 2000, La Florida', 'Av. Departamental 2000, La Florida', 10.4, 7.8, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-004', (SELECT id_cliente FROM Cliente WHERE rut = '44444444-4'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Independencia 1600, Independencia', 'Av. Independencia 1600, Independencia', 4.2, 1.5, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-005', (SELECT id_cliente FROM Cliente WHERE rut = '55555555-5'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Pajaritos 3030, Maipú', 'Av. Pajaritos 3030, Maipú', 12.6, 18.9, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-006', (SELECT id_cliente FROM Cliente WHERE rut = '66666666-6'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Américo Vespucio 1501, Renca', 'Av. Américo Vespucio 1501, Renca', 11.2, 9.4, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-007', (SELECT id_cliente FROM Cliente WHERE rut = '77777777-7'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Ossa 1400, La Reina', 'Av. Ossa 1400, La Reina', 6.8, 3.2, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-008', (SELECT id_cliente FROM Cliente WHERE rut = '88888888-8'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Santa Rosa 7200, La Pintana', 'Av. Santa Rosa 7200, La Pintana', 15.3, 22.0, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-009', (SELECT id_cliente FROM Cliente WHERE rut = '99999999-9'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Kennedy 5600, Vitacura', 'Av. Kennedy 5600, Vitacura', 8.5, 5.6, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('ENC-010', (SELECT id_cliente FROM Cliente WHERE rut = '10101010-1'), 'PedidoEncomienda', 'Pedido de Encomienda a Av. Gran Avenida 5200, San Miguel', 'Av. Gran Avenida 5200, San Miguel', 7.9, 2.7, 'PENDIENTE');

-- Express (10) -> mismos 10 clientes, de nuevo en orden
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-001', (SELECT id_cliente FROM Cliente WHERE rut = '11111111-1'), 'PedidoExpress', 'Pedido de Express a Merced 480, Santiago Centro', 'Merced 480, Santiago Centro', 1.1, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-002', (SELECT id_cliente FROM Cliente WHERE rut = '22222222-2'), 'PedidoExpress', 'Pedido de Express a Monjitas 650, Santiago Centro', 'Monjitas 650, Santiago Centro', 0.8, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-003', (SELECT id_cliente FROM Cliente WHERE rut = '33333333-3'), 'PedidoExpress', 'Pedido de Express a Av. Libertador Bernardo O''Higgins 949, Santiago Centro', 'Av. Libertador Bernardo O''Higgins 949, Santiago Centro', 2.0, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-004', (SELECT id_cliente FROM Cliente WHERE rut = '44444444-4'), 'PedidoExpress', 'Pedido de Express a Rosas 1350, Santiago Centro', 'Rosas 1350, Santiago Centro', 1.6, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-005', (SELECT id_cliente FROM Cliente WHERE rut = '55555555-5'), 'PedidoExpress', 'Pedido de Express a Teatinos 280, Santiago Centro', 'Teatinos 280, Santiago Centro', 3.0, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-006', (SELECT id_cliente FROM Cliente WHERE rut = '66666666-6'), 'PedidoExpress', 'Pedido de Express a Agustinas 1235, Santiago Centro', 'Agustinas 1235, Santiago Centro', 1.3, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-007', (SELECT id_cliente FROM Cliente WHERE rut = '77777777-7'), 'PedidoExpress', 'Pedido de Express a Huérfanos 1055, Santiago Centro', 'Huérfanos 1055, Santiago Centro', 1.0, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-008', (SELECT id_cliente FROM Cliente WHERE rut = '88888888-8'), 'PedidoExpress', 'Pedido de Express a San Antonio 220, Santiago Centro', 'San Antonio 220, Santiago Centro', 1.7, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-009', (SELECT id_cliente FROM Cliente WHERE rut = '99999999-9'), 'PedidoExpress', 'Pedido de Express a Compañía 1175, Santiago Centro', 'Compañía 1175, Santiago Centro', 2.4, NULL, 'PENDIENTE');
INSERT INTO Pedido (codigo_pedido, id_cliente, tipo_pedido, descripcion, direccion_destino, distancia_km, peso_kg, estado) VALUES ('EXP-010', (SELECT id_cliente FROM Cliente WHERE rut = '10101010-1'), 'PedidoExpress', 'Pedido de Express a Catedral 1250, Santiago Centro', 'Catedral 1250, Santiago Centro', 1.9, NULL, 'PENDIENTE');

package data.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Punto único de acceso a la base de datos MySQL 'speedfast_db'. Todas las
// clases DAO (RepartidorDAO, PedidoDAO, EntregaDAO) obtienen su conexión
// llamando a ConexionBD.obtenerConexion().
//
// IMPORTANTE - Requisitos para que esto funcione en tu máquina:
//   1) MySQL debe estar corriendo y la base 'speedfast_db' ya creada (ver
//      resources/schema_speedfast.sql).
//   2) El driver JDBC de MySQL (mysql-connector-j, p.ej. mysql-connector-j-8.x.x.jar)
//      debe estar agregado como librería del proyecto en IntelliJ:
//      File -> Project Structure -> Libraries -> "+" -> selecciona el .jar
//      (o, si usas Maven/Gradle, agrega la dependencia com.mysql:mysql-connector-j).
//   3) Ajusta USUARIO y CLAVE más abajo con tus credenciales reales de MySQL.
public class ConexionBD {

    private static final String URL = "jdbc:mysql://localhost:3306/speedfast_db?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String USUARIO = "root";
    private static final String CLAVE = "Daniel@Campos30";

    private ConexionBD() {
        // Clase utilitaria: no se instancia.
    }

    public static Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CLAVE);
    }

    // Abre y cierra una conexión de prueba, dejando pasar la SQLException tal
    // cual si falla. A diferencia de los DAO (que atrapan el error y solo lo
    // imprimen en consola, devolviendo listas vacías), este método se usa al
    // arrancar la aplicación para poder mostrarle al usuario el motivo EXACTO
    // por el que no se pudo conectar (credenciales, base inexistente, puerto,
    // servidor caído, etc.) en vez de que la GUI simplemente aparezca vacía.
    public static void verificarConexion() throws SQLException {
        try (Connection conexion = obtenerConexion()) {
            // conexión abierta y cerrada correctamente: todo OK
        }
    }
}
package app;

import data.persistence.PedidoDAO;
import data.persistence.RepartidorDAO;
import data.util.ControladorEnvios;
import data.util.GestorFases;
import model.core.Pedido;
import model.entities.business.ZonaCarga;
import model.entities.dealer.Repartidor;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;

public class SpeedFast {

    public static void main(String[] args) {
        // Ver comentario equivalente en SpeedFastGUI.main: fuerza UTF-8 en
        // consola para que tildes/ñ no se corrompan según el charset por
        // defecto de la plataforma.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.println("=========================================");
        System.out.println("       INICIANDO SISTEMA SPEEDFAST       ");
        System.out.println("=========================================\n");

        // Reemplaza la lectura de pedidos.txt/repartidores.txt: ambas listas
        // se cargan ahora directamente desde la base de datos speedfast_db.
        ArrayList<Repartidor> listaRepartidores = new RepartidorDAO().listarTodos();
        ArrayList<Pedido> listaPedidos = new PedidoDAO().listarTodos(listaRepartidores);
        ControladorEnvios controlador = new ControladorEnvios();
        ZonaCarga zonaCarga = new ZonaCarga();

        GestorFases.ejecutarFaseAsignacion(listaPedidos, listaRepartidores, controlador, scanner, zonaCarga);
        GestorFases.ejecutarFaseDespacho(listaPedidos);
        GestorFases.ejecutarFaseCancelaciones(listaPedidos, scanner);
        GestorFases.ejecutarFaseRutas(listaPedidos, zonaCarga);
        GestorFases.ejecutarFaseReportes(listaPedidos, controlador);

        scanner.close();
    }
}
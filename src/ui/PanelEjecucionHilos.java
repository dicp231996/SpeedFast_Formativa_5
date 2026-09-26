package ui;

import data.enumerate.EstadoPedido;
import data.util.ControladorEnvios;
import model.core.Pedido;
import model.entities.business.ZonaCarga;
import model.entities.dealer.Repartidor;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Pantalla que muestra, en vivo, la lógica de concurrencia de la Fase 4:
// cada Repartidor con pedidos CONFIRMADOS se lanza en su propio hilo dentro
// de un ExecutorService, retira su carga de la ZonaCarga y la entrega
// simulando cada etapa con HiloEntrega. Toda la salida de consola que ya
// producen Repartidor/HiloEntrega/ZonaCarga (System.out/System.err) se
// redirige en vivo a un JTextPane mediante ConsolaSwingOutputStream, y cada
// línea se pinta de un color distinto según el estado que representa (ver
// determinarColorDeLinea), para poder seguir de un vistazo qué pedido está
// CONFIRMADO, EN_REPARTO, ENTREGADO, CANCELADO, etc.
public class PanelEjecucionHilos extends JPanel {

    private final ArrayList<Pedido> listaPedidos;
    private final ZonaCarga zonaCarga;
    private final ControladorEnvios controlador;

    private final AtomicBoolean ejecucionEnCurso = new AtomicBoolean(false);

    private JTextPane areaConsola;
    private JLabel etiquetaEstado;
    private JButton botonIniciar;
    private JButton botonVolver;

    // Paleta de colores por tipo de evento (fondo negro, colores tipo consola).
    private static final Color COLOR_DEFECTO = new Color(0, 230, 0);        // línea genérica / informativa
    private static final Color COLOR_CONFIRMADO = new Color(80, 190, 255);  // pedido pasa a CONFIRMADO / disponible en pool
    private static final Color COLOR_EN_REPARTO = new Color(255, 180, 60);  // pedido pasa a EN_REPARTO / en camino
    private static final Color COLOR_ENTREGADO = new Color(60, 255, 90);    // pedido ENTREGADO con éxito
    private static final Color COLOR_CANCELADO = new Color(255, 90, 90);    // pedido CANCELADO
    private static final Color COLOR_ESPERA = new Color(170, 170, 170);     // zona vacía / repartidor en espera
    private static final Color COLOR_FIN_RUTA = new Color(210, 130, 255);   // un repartidor termina su recorrido
    private static final Color COLOR_ERROR = new Color(255, 60, 60);        // System.err / alertas / fallos
    private static final Color COLOR_RESUMEN = new Color(255, 215, 0);      // encabezados y resumen final

    public PanelEjecucionHilos(Navegador navegador, ArrayList<Pedido> listaPedidos, ZonaCarga zonaCarga,
                               ControladorEnvios controlador) {
        this.listaPedidos = listaPedidos;
        this.zonaCarga = zonaCarga;
        this.controlador = controlador;
        construirInterfaz(navegador);
    }

    private void construirInterfaz(Navegador navegador) {
        setLayout(new BorderLayout());

        JLabel titulo = new JLabel("Ejecución de Entregas (Hilos)", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        etiquetaEstado = new JLabel("Listo para iniciar.", SwingConstants.CENTER);
        etiquetaEstado.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        areaConsola = new JTextPane();
        areaConsola.setEditable(false);
        areaConsola.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        areaConsola.setBackground(Color.BLACK);
        areaConsola.setForeground(COLOR_DEFECTO);
        JScrollPane scroll = new JScrollPane(areaConsola);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));

        botonIniciar = new JButton("▶ Iniciar Ejecución de Entregas");
        botonVolver = new JButton("⬅ Volver al menú");

        botonIniciar.addActionListener(e -> iniciarEjecucion());
        botonVolver.addActionListener(e -> navegador.volver());

        JPanel panelBotones = new JPanel();
        panelBotones.add(botonIniciar);
        panelBotones.add(botonVolver);

        JPanel panelSuperior = new JPanel(new BorderLayout());
        panelSuperior.add(titulo, BorderLayout.NORTH);
        panelSuperior.add(etiquetaEstado, BorderLayout.SOUTH);

        add(panelSuperior, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);
    }

    // Se invoca cada vez que se vuelve a mostrar este panel: si ya hay una
    // ejecución en curso, el botón permanece deshabilitado.
    public void actualizarEstadoBoton() {
        botonIniciar.setEnabled(!ejecucionEnCurso.get());
    }

    // Punto de entrada público: lo invoca el flujo de Asignación (botón
    // "Realizar Entregas") justo después de navegar a este panel, para que
    // la ejecución de los hilos arranque de inmediato sin un clic adicional.
    public void iniciarEjecucion() {
        if (!ejecucionEnCurso.compareAndSet(false, true)) {
            return; // Ya hay una ejecución corriendo; evitamos doble lanzamiento
        }

        // 1. Identificamos qué repartidores tienen carga CONFIRMADA pendiente
        //    de entrega (misma lógica que GestorFases.ejecutarFaseRutas).
        ArrayList<Repartidor> repartidoresEnRuta = new ArrayList<>();
        for (Pedido p : listaPedidos) {
            Repartidor r = p.getRepartidorAsignado();
            if (r != null && !p.isCancelado() && !repartidoresEnRuta.contains(r)) {
                repartidoresEnRuta.add(r);
            }
        }

        if (repartidoresEnRuta.isEmpty()) {
            agregarLineaConsola("No hay repartidores con pedidos asignados. Realice primero una asignación.");
            ejecucionEnCurso.set(false);
            return;
        }

        botonIniciar.setEnabled(false);
        areaConsola.setText("");
        etiquetaEstado.setText("Ejecutando... " + repartidoresEnRuta.size() + " repartidor(es) en ruta.");

        // 2. Redirigimos System.out y System.err hacia el JTextArea, sin dejar
        //    de escribir también en la consola real. Cada línea escrita desde
        //    CUALQUIER hilo (cada Repartidor, cada HiloEntrega) llega aquí de
        //    forma segura y se reenvía al hilo de Swing con invokeLater.
        PrintStream outOriginal = System.out;
        PrintStream errOriginal = System.err;

        // Se fuerza explícitamente UTF-8 al crear estos PrintStream: así, cuando
        // Repartidor/Pedido/HiloEntrega hacen System.out.println("...á/é/í/ó/ú/ñ..."),
        // los bytes que le llegan a ConsolaSwingOutputStream son UTF-8 válidos,
        // que es el charset con el que esa clase los decodifica de vuelta a texto.
        PrintStream outRedirigido = new PrintStream(
                new ConsolaSwingOutputStream(outOriginal, this::agregarLineaConsola), true, StandardCharsets.UTF_8);
        PrintStream errRedirigido = new PrintStream(
                new ConsolaSwingOutputStream(errOriginal, this::agregarLineaConsola), true, StandardCharsets.UTF_8);

        System.setOut(outRedirigido);
        System.setErr(errRedirigido);

        // 3. Vinculamos la Zona de Carga a cada repartidor en ruta.
        for (Repartidor r : repartidoresEnRuta) {
            r.setZonaCarga(zonaCarga);
        }

        // 4. El ExecutorService (y la espera de su finalización) se lanzan en
        //    un hilo gestor aparte, NUNCA en el hilo de Swing (EDT), para no
        //    congelar la interfaz mientras las entregas se simulan.
        Thread hiloGestor = new Thread(() -> {
            ExecutorService pool = Executors.newFixedThreadPool(repartidoresEnRuta.size());

            for (Repartidor r : repartidoresEnRuta) {
                pool.execute(r);
            }

            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 5. Al terminar TODOS los repartidores, restauramos la consola
            //    real y actualizamos la UI en el hilo de Swing.
            System.setOut(outOriginal);
            System.setErr(errOriginal);

            int entregados = 0;
            for (Pedido p : listaPedidos) {
                controlador.registrarEntregaExitosa(p);
                if (p.getEstado() == EstadoPedido.ENTREGADO) {
                    entregados++;
                }
            }
            int totalEntregados = entregados;

            SwingUtilities.invokeLater(() -> {
                agregarLineaConsola("\n=========================================");
                agregarLineaConsola("EJECUCIÓN FINALIZADA. Pedidos entregados: " + totalEntregados);
                agregarLineaConsola("=========================================");
                etiquetaEstado.setText("Ejecución finalizada. Pedidos entregados: " + totalEntregados);
                ejecucionEnCurso.set(false);
                botonIniciar.setEnabled(true);
            });
        }, "HiloGestorEjecucion");

        hiloGestor.setDaemon(true);
        hiloGestor.start();
    }

    // Cualquier hilo puede llamar a este método (viene de
    // ConsolaSwingOutputStream, que corre en el hilo que hizo el println);
    // reenviamos siempre al hilo de Swing antes de tocar el JTextPane.
    private void agregarLineaConsola(String linea) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument documento = areaConsola.getStyledDocument();
            SimpleAttributeSet estilo = new SimpleAttributeSet();
            StyleConstants.setForeground(estilo, determinarColorDeLinea(linea));
            StyleConstants.setBold(estilo, esLineaDestacada(linea));

            try {
                documento.insertString(documento.getLength(), linea + "\n", estilo);
            } catch (BadLocationException e) {
                // No debería ocurrir insertando siempre al final; si pasara, no
                // interrumpimos la ejecución de las entregas por esto.
                System.err.println("No se pudo escribir en la consola de la GUI: " + e.getMessage());
            }

            areaConsola.setCaretPosition(documento.getLength());
        });
    }

    // Decide el color de una línea según las palabras clave que ya imprimen
    // Pedido/Repartidor/ZonaCarga/HiloEntrega, para representar visualmente
    // en qué estado del ciclo de vida se encuentra cada pedido dentro del hilo.
    // El orden de los "if" importa: se revisan primero los casos más
    // específicos para que no los tape una coincidencia más genérica.
    private Color determinarColorDeLinea(String linea) {
        String l = linea.toUpperCase();

        if (l.contains("=> ENTREGADO") || l.contains("ENTREGADO CON ÉXITO") || l.contains("ENTREGADO CON EXITO")) {
            return COLOR_ENTREGADO;
        }
        if (l.contains("=> CANCELADO") || l.contains("CANCELADO")) {
            return COLOR_CANCELADO;
        }
        if (l.contains("=> EN_REPARTO") || l.contains("[ENTREGA]") || l.contains("EN RUTA")
                || l.contains("PUNTO DE RECOGIDA") || l.contains("CASI ESTÁ") || l.contains("CASI ESTA")) {
            return COLOR_EN_REPARTO;
        }
        if (l.contains("=> CONFIRMADO") || l.contains("[ZONA DE CARGA]")) {
            return COLOR_CONFIRMADO;
        }
        if (l.contains("[ZONA DE CARGA VACÍA]") || l.contains("[ZONA DE CARGA VACIA]")
                || l.contains("EN ESPERA DE PAQUETES APTOS")) {
            return COLOR_ESPERA;
        }
        if (l.contains("[FIN DE RUTA]")) {
            return COLOR_FIN_RUTA;
        }
        if (l.contains("ALERTA") || l.contains("ERROR") || l.contains("FALLO") || l.contains("INTERRUMPID")) {
            return COLOR_ERROR;
        }
        if (l.contains("EJECUCIÓN FINALIZADA") || l.contains("EJECUCION FINALIZADA") || l.contains("====")) {
            return COLOR_RESUMEN;
        }

        return COLOR_DEFECTO;
    }

    // Los encabezados/resúmenes y las entregas exitosas se destacan en negrita
    // para que resalten dentro del flujo continuo de mensajes.
    private boolean esLineaDestacada(String linea) {
        String l = linea.toUpperCase();
        return l.contains("=> ENTREGADO") || l.contains("EJECUCIÓN FINALIZADA") || l.contains("EJECUCION FINALIZADA")
                || l.contains("====") || l.contains("[FIN DE RUTA]");
    }
}
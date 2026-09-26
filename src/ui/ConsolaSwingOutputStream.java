package ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

// Redirige la salida de consola línea por línea hacia un consumidor (por
// ejemplo, un JTextArea), sin dejar de escribir también en la consola real.
// Es thread-safe: varios hilos (cada Repartidor, cada HiloEntrega) escriben
// en System.out/System.err de forma concurrente durante la Fase de entrega.
//
// IMPORTANTE: los caracteres acentuados y la 'ñ' se codifican en UTF-8 como
// VARIOS bytes (por ejemplo, 'é' son 2 bytes). Por eso NO se puede convertir
// cada byte recibido en un char por separado (eso corta esos caracteres a la
// mitad y los muestra como "?"): los bytes de una línea se acumulan en un
// buffer y recién al llegar el salto de línea se decodifican todos juntos
// como UTF-8, para reconstruir el texto correctamente.
public class ConsolaSwingOutputStream extends OutputStream {

    private final PrintStream salidaOriginal;
    private final Consumer<String> consumidorLinea;
    private final ByteArrayOutputStream bufferLinea = new ByteArrayOutputStream();

    public ConsolaSwingOutputStream(PrintStream salidaOriginal, Consumer<String> consumidorLinea) {
        this.salidaOriginal = salidaOriginal;
        this.consumidorLinea = consumidorLinea;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        salidaOriginal.write(b); // seguimos viendo la salida también en la consola real
        procesarByte(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        salidaOriginal.write(b, off, len); // idem, en bloque (así escribe println normalmente)
        for (int i = off; i < off + len; i++) {
            procesarByte(b[i]);
        }
    }

    private void procesarByte(int b) {
        int byteSinSigno = b & 0xFF;
        if (byteSinSigno == '\n') {
            consumidorLinea.accept(bufferLinea.toString(StandardCharsets.UTF_8));
            bufferLinea.reset();
        } else if (byteSinSigno != '\r') {
            bufferLinea.write(byteSinSigno);
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        salidaOriginal.flush();
    }
}
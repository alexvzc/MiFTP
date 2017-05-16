/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package mx.itesm.gda.tc4003_1.miftp.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author alexv
 */
public class MiFTPClient {

    private static final Log LOGGER = LogFactory.getLog(MiFTPClient.class);

    /**
     * Expresión regular para identificar la longitud del archivo por
     * transmitir.
     */
    private static final Pattern FILE_LENGTH_PATTERN =
            Pattern.compile("File-Length: (\\d+)");

    /**
     * Método principal para la ejecución del cliente.
     * @param args los datos del host y puerto al cual hay que conectarse.
     */
    public static void main(String[] args) {
        if(args.length != 2) {
            // Valida que vengan los parametros requeridos de la línea de
            // comandos. Despliega mensaje si es que faltan o sobran.
            System.out.println("Usage: java -jar MiFTPClient.jar host port");
            System.exit(-1);
        }

        String host = args[0]; // Captura el nombre del host
        int port;

        try {
            // Parsea el número del puerto
            port = Integer.parseInt(args[1]);

        } catch(NumberFormatException nfe) {
            // Despliega mensaje si se suministró algo distinto a un número.
            System.err.println("Invalid port number");
            System.out.println("Usage: java -jar MiFTPClient.jar host port");
            System.exit(-1);
            return;

        }

        // Crea una dirección de un socket basado en el host y port.
        SocketAddress address = new InetSocketAddress(host, port);

        // Crea un socket sin vincular.
        Socket connection = new Socket();
        try {
            // Conecta al host con un timeout definido en ms.
            connection.connect(address, 2000);

        } catch(IOException ioe) {
            // Error de conexión. Despliega mensaje y termina.
            System.err.println("Unable to connect to " + address);
            System.out.println("Usage: java -jar MiFTPClient.jar host port");
            System.exit(-1);

        }

        try {
            // Recupera los streams de entrada y salida.
            InputStream in = connection.getInputStream();
            OutputStream out = connection.getOutputStream();

            // Crea versiones más amigables de los streams.
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in), 4096);
            BufferedReader console_in =
                    new BufferedReader(new InputStreamReader(System.in), 128);
            PrintWriter writer = new PrintWriter(out, true);
            PrintStream console_out = System.out;

            // Bandera que implica que debe continuar la ejecución del cliente.
            boolean keepRunning = true;

            while(keepRunning) {
                // Recupera mensajes del socket para desplegarse en la consola.
                while(true) {
                    String line = reader.readLine();
                    if("===".equals(line)) {
                        // Si aparecen tres iguales en una sola línea, los
                        // mensajes del server ya concluyeron.
                        break;
                    }
                    console_out.println(line);
                }

                // Despliega el prompt de la línea de comando.
                console_out.print("miftp>");
                console_out.flush();

                // Solicita un comando de la consola, y se lo envía al server.
                String cmd = console_in.readLine();
                writer.println(cmd);

                if(cmd.startsWith("get ")) {
                    // Si el comando es get, recupera el tamaño del archivo
                    // por recibir.
                    String line = reader.readLine();
                    Matcher m = FILE_LENGTH_PATTERN.matcher(line);
                    if(!m.matches()) {
                        // Si el mensaje del server no es la longitud del
                        // archivo, entonces hay error: despliega el mensaje
                        // del server y continua al comienzo del ciclo while
                        // para seguir desplegando más mensajes de error.
                        console_out.println(line);

                    } else {
                        long bytes_to_read;
                        try {
                            // Si el mensaje del server sí es la longitud del
                            // archivo, parsearlo en un número.
                            bytes_to_read = Long.parseLong(m.group(1));

                        } catch(NumberFormatException nfe) {
                            // Esto jamás debería pasar...
                            LOGGER.error("Unable to parse the file length. "
                                    + "Closing connection", nfe);
                            keepRunning = false;
                            break;
                        }

                        // Recupera el nombre del archivo a recibir.
                        String file_name = cmd.substring(4);
                        File file = new File(new File(file_name).getName());

                        // Crea un buffer de recepción.
                        byte[] buffer = new byte[64 * 1024];

                        // Abre el archivo a recibir.
                        FileOutputStream file_out = new FileOutputStream(file);
                        try {
                            while(bytes_to_read > 0) {
                                // Mientras haya bytes por recibir...
                                int bytes_to_read_now = buffer.length;
                                if(bytes_to_read < bytes_to_read_now) {
                                    bytes_to_read_now = (int)bytes_to_read;
                                }
                                //... los recibe en el buffer
                                int bytes_read = in.read(buffer, 0,
                                        bytes_to_read_now);

                                if(bytes_read < 0) {
                                    // Si llegamos al final de la conexión,
                                    // es una conexión de error porque debíamos
                                    // seguir recibiendo datos.
                                    LOGGER.error("Unable to complete reading "
                                            + "the file. Closing connection.");
                                    keepRunning = false;
                                    break;
                                }

                                // Escribe el buffer de recepción en el archivo
                                // y decrementa el contador de bytes por
                                // recibir.
                                file_out.write(buffer, 0, bytes_read);
                                bytes_to_read -= bytes_read;

                            }
                        } catch(IOException ioe) {
                            // Hubo una excepción en la recepción del archivo,
                            // aborta todo.
                            LOGGER.error("Unable to complete reading the"
                                    + " file. Closing connection.");
                            keepRunning = false;
                            break;

                        } finally {
                            // Cierra el archivo.
                            file_out.close();
                        }
                    }

                } else if(cmd.startsWith("end")) {
                    // Si el comando es "end", despliega mensajes de despedida
                    // del server y termina ejecución del cliente.
                    while(true) {
                        String line = reader.readLine();
                        if("===".equals(line)) {
                            keepRunning = false;
                            break;
                        }
                        console_out.println(line);
                    }
                }
            }

        } catch(IOException ioe) {
            // Hubo un error, aborta todo!!
            LOGGER.error("Connection exception", ioe);

        } finally {
            try {
                // Cierra la conexión
                connection.close();

            } catch(IOException ioe) {
                // Hubo excepción cerrando la conexión...
                // too bad we don't care anymore
                LOGGER.error("Error closing connection", ioe);
            }
        }
    }

}

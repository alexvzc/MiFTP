/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package mx.itesm.gda.tc4003_1.miftp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

/**
 *
 * @author alexv
 */
public class MiFTPServer {

    private static final Logger LOGGER = getLogger(MiFTPServer.class);

    /**
     * Método principal para la ejecución del servidor.
     * @param args los datos del puerto en el cual hay que escuchar conexiones.
     */
    public static void main(String[] args) {
        if(args.length != 1) {
            System.out.println("Usage: java -jar MiFTPServer.jar port");
            System.exit(-1);
        }

        boolean keepRunning = true;
        int port;

        try {
            port = Integer.parseInt(args[0]);

        } catch(NumberFormatException nfe) {
            System.err.println("Invalid port number");
            System.out.println("Usage: java -jar MiFTPServer.jar port");
            System.exit(-1);
            return;

        }

        ThreadGroup server_thread_group = new ThreadGroup("Server threads");
        ServerSocket server_socket = null;

        try {
            LOGGER.info("Creating listening socket for port " + port);
            server_socket = new ServerSocket(port);
            LOGGER.debug("Listening socket created");

            do {
                Socket client_connection = server_socket.accept();
                LOGGER.info("Accepted connection from " + client_connection);

                new ConnectionServer(client_connection, server_thread_group);

            } while(keepRunning);

        } catch(IOException ioe) {
            LOGGER.error("Processing connection exception", ioe);

        } finally {
            try {
                LOGGER.info("Closing listening socket");
                server_socket.close();

            } catch(IOException ioe) {
                LOGGER.error("Error closing server socket", ioe);

            }

        }

    }

}

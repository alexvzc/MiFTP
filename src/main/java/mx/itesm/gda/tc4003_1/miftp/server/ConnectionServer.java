/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package mx.itesm.gda.tc4003_1.miftp.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

/**
 *
 * @author alexv
 */
public class ConnectionServer implements Runnable {

    private static final Logger LOGGER = getLogger(ConnectionServer.class);

    private static final String CHARSET = "UTF-8";

    private static final Pattern CMD_PATTERN =
            Pattern.compile("(end|dir|cd|get)(\\s+(.+))?");

    private final Socket clientConnection;

    private boolean keepRunning;

    private Thread executingThread;

    public ConnectionServer(Socket client_connection,
            ThreadGroup server_group) {
        clientConnection = client_connection;
        keepRunning = true;
        executingThread = new Thread(server_group, this);
        executingThread.start();
    }

    @Override
    public void run() {
        LOGGER.info("New connection received from:"
                + clientConnection.toString());

        try {
            InputStream in = clientConnection.getInputStream();
            OutputStream out = clientConnection.getOutputStream();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, CHARSET), 4096);
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(out, CHARSET));

            File working_dir =
                    new File(System.getProperty("user.dir")).getCanonicalFile();

            LOGGER.info("Welcoming user");
            writer.println("Welcome user. Current directory: " + working_dir.
                    getPath());

            OUTER: while(keepRunning) {
                writer.println("===");
                writer.flush();

                LOGGER.info("Waiting for command");
                String cmd = reader.readLine();
                if(cmd == null) {
                    break;
                }

                LOGGER.info("Processing command " + cmd);
                Matcher m = CMD_PATTERN.matcher(cmd);

                if(!m.matches()) {
                    LOGGER.info("Invalid command " + cmd);
                    writer.println("Invalid command: " + cmd);
                    continue;
                }

                String command = m.group(1);
                String params = m.group(3);

                LOGGER.info("Command parsed: " + command);

                if("end".equals(command)) {
                    LOGGER.info("Ending connection");
                    writer.println("Ending connection. Bye.");
                    writer.println("===");
                    writer.flush();
                    keepRunning = false;
                    break;

                } else if("cd".equals(command)) {
                    LOGGER.info("Changing working directory");
                    if(params == null || params.length() == 0) {
                        LOGGER.error("Missing parameter required");
                        writer.println("Missing parameter required");
                        continue;

                    }

                    File new_dir =
                            new File(working_dir, params).getCanonicalFile();

                    if(!new_dir.exists() || !new_dir.isDirectory()) {
                        LOGGER.error("Cannot change to " + params);
                        writer.println("Cannot change to " + params);

                    } else {
                        working_dir = new_dir;
                        LOGGER.info("Changed directory to "
                                + working_dir.getPath());
                        writer.println("Changed directory to "
                                + working_dir.getPath());

                    }

                } else if("dir".equals(command)) {
                    LOGGER.info("Listing working directory");
                    File dir_to_list;

                    if(params == null || params.length() == 0) {
                        dir_to_list = working_dir;

                    } else {
                        dir_to_list = new File(working_dir, params);

                        if(!dir_to_list.exists()
                                || !dir_to_list.isDirectory()) {
                            LOGGER.error("Cannot list diretcory contents: "
                                    + params);
                            writer.println("Cannot list diretcory contents: "
                                    + params);
                            continue;
                        }
                    }

                    File[] dir_contents = dir_to_list.listFiles();

                    for(File dir_entry : dir_contents) {
                        String file_name = dir_entry.getName();
                        boolean is_directory = dir_entry.isDirectory();
                        long file_size = dir_entry.length();
                        long last_modified = dir_entry.lastModified();

                        if(!is_directory) {
                            writer.format("%1$tF %1$tT %2$12d %3$s\r\n",
                                          last_modified, file_size,
                                          file_name);
                        } else {
                            writer.format("%1$tF %1$tT %2$12d [%3$s]\r\n",
                                          last_modified, file_size,
                                          file_name);
                        }
                    }

                    LOGGER.info("Listed " + dir_contents.length
                            + " directory entries");
                    writer.println(dir_contents.length + " directory entries");

                } else if("get".equals(command)) {
                    LOGGER.info("Retrieving file");
                    if(params == null || params.length() == 0) {
                        LOGGER.error("Missing parameter required");
                        writer.println("Missing parameter required");
                        continue;

                    }

                    File file_to_retrieve = new File(working_dir, params);

                    if(!file_to_retrieve.exists()
                            || !file_to_retrieve.isFile()
                            || !file_to_retrieve.canRead()) {
                        LOGGER.error("Cannot retrieve " + params);
                        writer.println("Cannot retrieve " + params);
                        continue;

                    }

                    long bytes_to_read = file_to_retrieve.length();

                    LOGGER.info("Transfering " + bytes_to_read + " bytes");

                    try {
                        writer.println("File-Length: " + bytes_to_read);
                        writer.flush();

                        byte[] buffer = new byte[64 * 1024];
                        int bytes_read;

                        FileInputStream file_in =
                                new FileInputStream(file_to_retrieve);

                        try {

                            while(bytes_to_read > 0) {
                                bytes_read = file_in.read(buffer);

                                if(bytes_read > 0) {
                                    out.write(buffer, 0, bytes_read);
                                } else {
                                    LOGGER.error("Error transfering file. "
                                            + "Closing connection.");
                                    keepRunning = false;
                                    break OUTER;
                                }

                                bytes_to_read -= bytes_read;

                            }

                        } finally {
                            file_in.close();

                        }

                        out.flush();

                    } catch(IOException ioe) {
                        LOGGER.error(
                                "Error transferring file. Closing connection",
                                ioe);
                        keepRunning = false;
                        break;
                    }

                    LOGGER.info("Completed file transfer");
                    writer.println("Completed file transfer");

                }

            }

        } catch(IOException ioe) {
            LOGGER.error("I/O Exception, closing connection", ioe);

        } finally {
            try {
                if(clientConnection != null) {
                    clientConnection.close();
                }
            } catch(IOException ioe) {
                LOGGER.debug("I/O Exception while closing connection", ioe);
            }
        }
    }

}

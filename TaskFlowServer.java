package taskflow.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TaskFlow Server — always-on multi-threaded server.
 *
 * ServerSocket pattern from m6 slide 25:
 *   ServerSocket serverSocket = new ServerSocket(8080);
 *   while (true) { Socket clientSocket = serverSocket.accept(); ... }
 *
 * Thread-per-client from m7 slide 7:
 *   new Thread(new ClientHandler(clientSocket)).start();
 */
public class TaskFlowServer {

    private static final int PORT = 8080;

    public static void main(String[] args) {
        DataStore store = new DataStore();
        System.out.println("====================================");
        System.out.println("  TaskFlow Server  |  port " + PORT);
        System.out.println("====================================");
        System.out.println("Default accounts:");
        System.out.println("  manager  / pass123  (MANAGER)");
        System.out.println("  rahil    / pass123  (MANAGER)");
        System.out.println("  krisha   / pass123  (EMPLOYEE)");
        System.out.println("  employee / pass123  (EMPLOYEE)");
        System.out.println("------------------------------------");
        System.out.println("Waiting for connections on port " + PORT + " ...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Blocks until a client connects — m6 slide 25
                Socket clientSocket = serverSocket.accept();

                // Spawn a new thread per client — m7 slide 7
                ClientHandler handler = new ClientHandler(clientSocket, store);
                Thread thread = new Thread(handler,
                        "Client-" + clientSocket.getInetAddress().getHostAddress());
                thread.start();
                System.out.println("[Server] New thread started: " + thread.getName());
            }
        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
        }
    }
}

package taskflow.client;

import taskflow.common.Protocol;
import java.io.*;
import java.net.Socket;

/**
 * TCP socket connection to the server.
 * Socket + PrintWriter + BufferedReader — m6 slides 26-28.
 * sendCommand() is synchronized so SwingWorker threads don't interleave — m7 slide 26.
 */
public class ServerConnection {

    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;
    private boolean        connected = false;

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);                              // m6 slide 26
            out = new PrintWriter(socket.getOutputStream(), true);        // m6 slide 27
            in  = new BufferedReader(                                     // m6 slide 28
                      new InputStreamReader(socket.getInputStream()));
            connected = true;
            return true;
        } catch (IOException e) {
            System.err.println("[Client] Cannot connect: " + e.getMessage());
            return false;
        }
    }

    public synchronized String sendCommand(String command) {             // m7 slide 26
        if (!connected) return Protocol.ERROR + "|Not connected";
        try {
            out.println(command);
            return in.readLine();
        } catch (IOException e) {
            connected = false;
            return Protocol.ERROR + "|Connection lost: " + e.getMessage();
        }
    }

    public void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        connected = false;
    }

    public boolean isConnected() { return connected; }
}

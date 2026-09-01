package taskflow.server;

import taskflow.common.Protocol;
import taskflow.common.Task;
import taskflow.common.User;
import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Handles one client connection in its own thread.
 * implements Runnable — m7 slide 7 pattern.
 * Uses PrintWriter + BufferedReader over the socket — m6 slides 27-28.
 */
public class ClientHandler implements Runnable {

    private final Socket    socket;
    private final DataStore store;
    private PrintWriter    out;
    private BufferedReader in;
    private User           currentUser;

    public ClientHandler(Socket socket, DataStore store) {
        this.socket = socket;
        this.store  = store;
    }

    @Override
    public void run() {
        System.out.println("[Server] Client connected: "
                + socket.getInetAddress().getHostAddress());
        try {
            // PrintWriter over socket output stream  — m6 slide 27
            out = new PrintWriter(socket.getOutputStream(), true);
            // BufferedReader over socket input stream — m6 slide 28
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String line;
            // Check Thread.interrupted() for graceful shutdown — m7 slide 9
            while ((line = in.readLine()) != null && !Thread.interrupted()) {
                out.println(handleCommand(line.trim()));
            }
        } catch (IOException e) {
            System.out.println("[Server] Client disconnected: "
                    + socket.getInetAddress().getHostAddress());
        } finally {
            // Always close in finally — m3 best practices
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private String handleCommand(String raw) {
        if (raw.isEmpty()) return err("Empty command");
        String[] p = raw.split("\\|", -1);
        String   cmd = p[0];

        if (Protocol.LOGIN.equals(cmd))  return handleLogin(p);
        if (currentUser == null)         return err("Not authenticated. Please LOGIN first.");

        switch (cmd) {
            case Protocol.LOGOUT:                return handleLogout();
            case Protocol.CREATE_TASK:           return handleCreateTask(p);
            case Protocol.GET_TASKS:             return handleGetTasks(p);
            case Protocol.UPDATE_STATUS:         return handleUpdateStatus(p);
            case Protocol.UPDATE_NOTES:          return handleUpdateNotes(p);
            case Protocol.DELETE_TASK:           return handleDeleteTask(p);
            case Protocol.DELETE_PERSONAL_TASK:  return handleDeletePersonalTask(p);
            case Protocol.CREATE_PERSONAL_TASK:  return handleCreatePersonalTask(p);
            case Protocol.GET_PERSONAL_TASKS:    return handleGetPersonalTasks();
            case Protocol.GET_FOLDERS:           return handleGetFolders(p);
            case Protocol.GET_USERS:             return handleGetUsers();
            case Protocol.ADD_USER:              return handleAddUser(p);
            case Protocol.DELETE_USER:           return handleDeleteUser(p);
            default:                             return err("Unknown command: " + cmd);
        }
    }

    // LOGIN|username|password
    private String handleLogin(String[] p) {
        if (p.length < 3) return err("Usage: LOGIN|username|password");
        User u = store.authenticate(p[1], p[2]);
        if (u == null) return err("Invalid username or password");
        currentUser = u;
        System.out.println("[Server] Logged in: " + u.getUsername() + " (" + u.getRole() + ")");
        return ok(u.getUsername() + "|" + u.getRole());
    }

    private String handleLogout() {
        System.out.println("[Server] Logged out: " + currentUser.getUsername());
        currentUser = null;
        return ok("Logged out");
    }

    // CREATE_TASK|title|desc|assignedTo|priority|dueDate
    // split gives p[0]=cmd p[1]=title p[2]=desc p[3]=assignedTo p[4]=priority p[5]=dueDate
    // that is 6 elements total, so check p.length < 6
    private String handleCreateTask(String[] p) {
        if (!isManager()) return err("Only managers can create team tasks");
        System.out.println("[Server] CREATE_TASK parts=" + p.length + " raw=" + String.join("|", p));
        if (p.length < 6) return err("Usage: CREATE_TASK|title|desc|assignedTo|priority|dueDate");
        try {
            String title      = p[1];
            String desc       = p[2];
            String assignedTo = p[3];
            String priority   = p[4];
            String dueDate    = p[5];
            if (title.isEmpty())      return err("Title cannot be empty");
            if (assignedTo.isEmpty()) return err("Assignee cannot be empty");
            Task.Priority prio = Task.Priority.valueOf(priority.toUpperCase());
            Task t = store.createTask(title, desc, assignedTo, currentUser.getUsername(), prio, dueDate);
            return ok("Task created with ID " + t.getId());
        } catch (IllegalArgumentException e) {
            return err("Invalid priority. Use: LOW, MEDIUM, HIGH, URGENT");
        }
    }

    // GET_TASKS|filterStatus|filterPriority
    private String handleGetTasks(String[] p) {
        String fs = (p.length > 1 && !p[1].isEmpty()) ? p[1] : "ALL";
        String fp = (p.length > 2 && !p[2].isEmpty()) ? p[2] : "ALL";
        List<Task> tasks = store.getTasks(currentUser.getUsername(), isManager(), fs, fp);
        if (tasks.isEmpty()) return data("");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) sb.append(Protocol.ITEM_SEP);
            sb.append(tasks.get(i).toFileLine());
        }
        return data(sb.toString());
    }

    // UPDATE_STATUS|taskId|newStatus
    private String handleUpdateStatus(String[] p) {
        if (p.length < 3) return err("Usage: UPDATE_STATUS|taskId|newStatus");
        try {
            int taskId = Integer.parseInt(p[1]);
            Task.Status status = Task.Status.valueOf(p[2].toUpperCase());
            return store.updateStatus(taskId, status) ? ok("Status updated") : err("Task not found: " + taskId);
        } catch (NumberFormatException e) { return err("Invalid task ID"); }
        catch (IllegalArgumentException e) { return err("Invalid status. Use: TODO, IN_PROGRESS, REVIEW, DONE"); }
    }

    // UPDATE_NOTES|taskId|notes
    private String handleUpdateNotes(String[] p) {
        if (p.length < 3) return err("Usage: UPDATE_NOTES|taskId|notes");
        try {
            int taskId = Integer.parseInt(p[1]);
            return store.updateNotes(taskId, p[2]) ? ok("Notes updated") : err("Task not found: " + taskId);
        } catch (NumberFormatException e) { return err("Invalid task ID"); }
    }

    // DELETE_TASK|taskId
    private String handleDeleteTask(String[] p) {
        if (!isManager()) return err("Only managers can delete tasks");
        if (p.length < 2) return err("Usage: DELETE_TASK|taskId");
        try {
            int taskId = Integer.parseInt(p[1]);
            return store.deleteTask(taskId) ? ok("Task deleted") : err("Task not found: " + taskId);
        } catch (NumberFormatException e) { return err("Invalid task ID"); }
    }

    // DELETE_PERSONAL_TASK|taskId — any user can delete their own personal task
    private String handleDeletePersonalTask(String[] p) {
        if (p.length < 2) return err("Usage: DELETE_PERSONAL_TASK|taskId");
        try {
            int taskId = Integer.parseInt(p[1]);
            // Verify this task belongs to the current user before deleting
            boolean ok = store.deletePersonalTask(taskId, currentUser.getUsername());
            return ok ? ok("Personal task deleted") : err("Task not found or not yours");
        } catch (NumberFormatException e) { return err("Invalid task ID"); }
    }

    // CREATE_PERSONAL_TASK|title|desc|priority|dueDate|folder
    // p[0]=cmd p[1]=title p[2]=desc p[3]=priority p[4]=dueDate p[5]=folder → 6 parts
    private String handleCreatePersonalTask(String[] p) {
        System.out.println("[Server] CREATE_PERSONAL_TASK parts=" + p.length);
        if (p.length < 6) return err("Usage: CREATE_PERSONAL_TASK|title|desc|priority|dueDate|folder");
        try {
            Task.Priority priority = Task.Priority.valueOf(p[3].toUpperCase());
            Task t = store.createPersonalTask(p[1], p[2], currentUser.getUsername(), priority, p[4], p[5]);
            return ok("Personal task created with ID " + t.getId());
        } catch (IllegalArgumentException e) { return err("Invalid priority"); }
    }

    // GET_PERSONAL_TASKS — returns all tasks with a folder owned by current user
    private String handleGetPersonalTasks() {
        List<Task> tasks = store.getPersonalTasks(currentUser.getUsername());
        if (tasks.isEmpty()) return data("");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) sb.append(Protocol.ITEM_SEP);
            sb.append(tasks.get(i).toFileLine());
        }
        return data(sb.toString());
    }

    // GET_FOLDERS|username
    private String handleGetFolders(String[] p) {
        String username = (p.length > 1 && !p[1].isEmpty()) ? p[1] : currentUser.getUsername();
        List<String> folders = store.getFolders(username);
        if (folders.isEmpty()) return data("");
        return data(String.join(Protocol.ITEM_SEP, folders));
    }

    // GET_USERS
    private String handleGetUsers() {
        if (!isManager()) return err("Only managers can list users");
        List<User> users = store.getAllUsers();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) sb.append(Protocol.ITEM_SEP);
            sb.append(users.get(i).getUsername()).append(":").append(users.get(i).getRole());
        }
        return data(sb.toString());
    }

    // ADD_USER|username|password|role
    private String handleAddUser(String[] p) {
        if (!isManager()) return err("Only managers can add users");
        if (p.length < 4)  return err("Usage: ADD_USER|username|password|role");
        try {
            User.Role role = User.Role.valueOf(p[3].toUpperCase());
            return store.addUser(p[1], p[2], role) ? ok("User added: " + p[1]) : err("Username already exists");
        } catch (IllegalArgumentException e) { return err("Invalid role. Use: MANAGER or EMPLOYEE"); }
    }

    // DELETE_USER|username  (MANAGER only, cannot delete self)
    private String handleDeleteUser(String[] p) {
        if (!isManager()) return err("Only managers can delete users");
        if (p.length < 2)  return err("Usage: DELETE_USER|username");
        String target = p[1].trim();
        if (target.equals(currentUser.getUsername()))
            return err("You cannot delete your own account");
        boolean ok = store.deleteUser(target, currentUser.getUsername());
        return ok ? ok("User deleted: " + target) : err("User not found: " + target);
    }

    private boolean isManager() {
        return currentUser != null && currentUser.getRole() == User.Role.MANAGER;
    }
    private String ok(String s)   { return Protocol.OK    + "|" + s; }
    private String err(String s)  { return Protocol.ERROR + "|" + s; }
    private String data(String s) { return Protocol.DATA  + "|" + s; }
}
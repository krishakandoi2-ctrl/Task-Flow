package taskflow.server;

import taskflow.common.Task;
import taskflow.common.User;
import java.io.*;
import java.util.*;

/**
 * File I/O persistence layer.
 * Reads: FileReader -> BufferedReader -> readLine()   (m5 slides 9)
 * Writes: PrintWriter -> println()                    (m5 slide 10)
 * All mutating methods are synchronized               (m7 slide 26)
 */
public class DataStore {

    private static final String TASKS_FILE = "taskflow_tasks.txt";
    private static final String USERS_FILE = "taskflow_users.txt";

    private final List<Task> tasks = new ArrayList<>();
    private final List<User> users = new ArrayList<>();
    private int nextTaskId = 1;

    public DataStore() {
        loadUsers();
        loadTasks();
        if (users.isEmpty()) {
            users.add(new User("manager",  "pass123", User.Role.MANAGER));
            users.add(new User("rahil",    "pass123", User.Role.MANAGER));
            users.add(new User("krisha",   "pass123", User.Role.EMPLOYEE));
            users.add(new User("employee", "pass123", User.Role.EMPLOYEE));
            saveUsers();
        }
    }

    // ── File I/O: load ───────────────────────────────────────────────────────

    private void loadTasks() {
        File f = new File(TASKS_FILE);
        if (!f.exists()) return;
        try {
            FileReader   fileReader = new FileReader(TASKS_FILE);
            BufferedReader input    = new BufferedReader(fileReader);
            String line;
            while ((line = input.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    Task t = Task.fromFileLine(line);
                    if (t != null) {
                        tasks.add(t);
                        if (t.getId() >= nextTaskId) nextTaskId = t.getId() + 1;
                    }
                }
            }
            input.close();
        } catch (IOException e) {
            System.err.println("[DataStore] loadTasks: " + e.getMessage());
        }
    }

    private void loadUsers() {
        File f = new File(USERS_FILE);
        if (!f.exists()) return;
        try {
            FileReader   fileReader = new FileReader(USERS_FILE);
            BufferedReader input    = new BufferedReader(fileReader);
            String line;
            while ((line = input.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    User u = User.fromFileLine(line);
                    if (u != null) users.add(u);
                }
            }
            input.close();
        } catch (IOException e) {
            System.err.println("[DataStore] loadUsers: " + e.getMessage());
        }
    }

    // File I/O: save

    private synchronized void saveTasks() {
        try {
            PrintWriter output = new PrintWriter(TASKS_FILE);
            for (Task t : tasks) output.println(t.toFileLine());
            output.close();
        } catch (IOException e) {
            System.err.println("[DataStore] saveTasks: " + e.getMessage());
        }
    }

    private synchronized void saveUsers() {
        try {
            PrintWriter output = new PrintWriter(USERS_FILE);
            for (User u : users) output.println(u.toFileLine());
            output.close();
        } catch (IOException e) {
            System.err.println("[DataStore] saveUsers: " + e.getMessage());
        }
    }

    // User operations
    public synchronized User authenticate(String username, String password) {
        for (User u : users) {
            if (u.getUsername().equals(username) && u.getPassword().equals(password))
                return u;
        }
        return null;
    }

    public synchronized List<User> getAllUsers() { return new ArrayList<>(users); }

    public synchronized boolean deleteUser(String username, String requestedBy) {
        // Cannot delete yourself
        if (username.equals(requestedBy)) return false;
        Iterator<User> it = users.iterator();
        while (it.hasNext()) {
            if (it.next().getUsername().equalsIgnoreCase(username)) {
                it.remove();
                saveUsers();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean addUser(String username, String password, User.Role role) {
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(username)) return false;
        }
        users.add(new User(username, password, role));
        saveUsers();
        return true;
    }

    // Task operations

    public synchronized Task createTask(String title, String desc, String assignedTo,
                                        String createdBy, Task.Priority priority, String due) {
        Task t = new Task(nextTaskId++, title, desc, assignedTo, createdBy, priority, due);
        tasks.add(t);
        saveTasks();
        return t;
    }

    public synchronized Task createPersonalTask(String title, String desc, String owner,
                                                Task.Priority priority, String due, String folder) {
        Task t = new Task(nextTaskId++, title, desc, owner, owner, priority, due);
        t.setFolder(folder);
        tasks.add(t);
        saveTasks();
        return t;
    }

    public synchronized List<Task> getTasks(String username, boolean isManager,
                                            String filterStatus, String filterPriority) {
        List<Task> result = new ArrayList<>();
        for (Task t : tasks) {
            boolean inScope = isManager
                    ? (t.getFolder() == null)
                    : t.getAssignedTo().equals(username) && t.getFolder() == null;
            if (!inScope) continue;

            if (!"ALL".equals(filterStatus)) {
                try { if (!t.getStatus().equals(Task.Status.valueOf(filterStatus))) continue; }
                catch (IllegalArgumentException ignored) {}
            }
            if (!"ALL".equals(filterPriority)) {
                try { if (!t.getPriority().equals(Task.Priority.valueOf(filterPriority))) continue; }
                catch (IllegalArgumentException ignored) {}
            }
            result.add(t);
        }
        return result;
    }

    public synchronized List<Task> getPersonalTasks(String username) {
        List<Task> result = new ArrayList<>();
        for (Task t : tasks) {
            if (t.getAssignedTo().equals(username) && t.getFolder() != null)
                result.add(t);
        }
        return result;
    }

    public synchronized List<String> getFolders(String username) {
        Set<String> folders = new LinkedHashSet<>();
        for (Task t : tasks) {
            if (t.getAssignedTo().equals(username) && t.getFolder() != null)
                folders.add(t.getFolder());
        }
        return new ArrayList<>(folders);
    }

    public synchronized boolean updateStatus(int taskId, Task.Status status) {
        for (Task t : tasks) {
            if (t.getId() == taskId) { t.setStatus(status); saveTasks(); return true; }
        }
        return false;
    }

    public synchronized boolean updateNotes(int taskId, String notes) {
        for (Task t : tasks) {
            if (t.getId() == taskId) { t.setNotes(notes); saveTasks(); return true; }
        }
        return false;
    }

    // Delete a personal task — only if it belongs to the requesting user
    public synchronized boolean deletePersonalTask(int taskId, String username) {
        Iterator<Task> it = tasks.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.getId() == taskId
                    && t.getFolder() != null
                    && t.getAssignedTo().equals(username)) {
                it.remove();
                saveTasks();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean deleteTask(int taskId) {
        Iterator<Task> it = tasks.iterator();
        while (it.hasNext()) {
            if (it.next().getId() == taskId) { it.remove(); saveTasks(); return true; }
        }
        return false;
    }
}
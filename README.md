# TaskFlow — Shared Task Manager
**CSCI 2020U: Software Systems Development and Integration**

---

## Project Information

A multi-client, multi-threaded task management application written in Java.  
The server hosts a shared task board over TCP sockets. Clients connect with a persistent session, authenticate via login, and exchange pipe-delimited text commands — a stateful, long-lived protocol. The client provides a dark-themed Swing GUI with a sidebar for navigation, a team task board with filtering, a personal task section with folders, and a manager panel for user administration.

---

## Demo Video
https://github.com/user-attachments/assets/6af3f749-4d5c-489e-8b5d-3bc72dd2d54d

---

## Features

### Login
When you launch the client, a login screen appears. Enter the server address, your username, and password. The app connects to the server over a live TCP socket and keeps that connection open for your entire session. Wrong credentials? You'll get an error message right on the screen without the app freezing.

### Team Task Board
The main screen shows all tasks shared across your team. Each task has a title, who it's assigned to, a priority level (Low → Urgent), a status (To Do → Done), a due date, and notes. The table colour-codes rows — red for urgent tasks, amber for high priority, green for completed ones — so you can spot what needs attention at a glance.

### Filter Tasks
Above the task table are two dropdowns: one for **Status** and one for **Priority**. Select any combination and hit Refresh to narrow down the list. Useful when you only want to see tasks that are still in progress, or only the urgent ones.

### Create Task *(Manager only)*
Managers can open a form to create a new team task. Fill in the title, description, who to assign it to, priority, and due date — then submit. The task instantly appears on everyone's board.

### Update Status & Notes
Any user can select a task from the table and update its status (e.g. move it from *In Progress* to *Review*) or add/edit notes. Changes are saved to the server immediately and reflected the next time the board refreshes.

### Delete Task *(Manager only)*
Managers can select any task and delete it permanently. A confirmation prompt prevents accidental deletions.

### Personal Tasks
Every user has a private section for their own to-do items, completely separate from the team board. Personal tasks are organized into named **folders** (e.g. *Study*, *Work*, *Personal*). You can create, view, and delete your own tasks without anyone else seeing them.

### User Management *(Manager only)*
Managers have access to a dedicated panel showing all registered accounts and their roles. From here a manager can:
- **Add a new user** — set their username, password, and role (Employee or Manager).
- **Delete a user** — permanently remove an account (you can't delete your own).

### Multi-Client Support
Multiple people can be logged in at the same time from different machines (or different windows on the same machine). The server handles each client on its own thread, and all shared data is kept in sync safely.

### Data Persistence
Everything is saved to plain text files on the server. Tasks and user accounts survive a server restart — nothing is lost when the server is shut down and started again.

---

## 🗂️ Project Structure

```
taskflow/
├── common/                         (shared between server and client)
│   ├── Protocol.java               (command and separator constants)
│   ├── Task.java                   (task model + file serialization)
│   └── User.java                   (user model + file serialization)
│
├── server/
│   ├── TaskFlowServer.java         (entry point, ServerSocket accept loop)
│   ├── ClientHandler.java          (Runnable, one thread per client)
│   └── DataStore.java              (file I/O persistence layer, synchronized)
│
└── client/
    ├── TaskFlowClient.java         (entry point, main JFrame GUI)
    ├── LoginDialog.java            (login JDialog with SwingWorker)
    ├── ServerConnection.java       (TCP socket wrapper)
    └── TaskTableModel.java         (AbstractTableModel for JTable)
```

---

## How to Run

### Prerequisites
- Java 11 or later (Java 17+ recommended)
- No external libraries or build tools required — pure Java SE

### 1 — Compile
From the project root (the folder containing `taskflow/`):
```bash
javac -d out taskflow/common/*.java taskflow/server/*.java taskflow/client/*.java
```

### 2 — Start the server
```bash
java -cp out taskflow.server.TaskFlowServer
```
The server binds to **port 8080** and prints a startup message. Leave this terminal open.

```
====================================
  TaskFlow Server  |  port 8080
====================================
Waiting for connections on port 8080 ...
```

### 3 — Start a client
In a **separate terminal**:
```bash
java -cp out taskflow.client.TaskFlowClient
```
The login dialog appears. Enter the server address (`localhost:8080` for local runs), a username, and password. You can launch **multiple client windows** at the same time — each gets its own server thread.

### 4 — Connecting from another machine
1. Find the server machine's local IP address (e.g. `192.168.1.10`).
2. In the client login dialog, replace `localhost:8080` with `192.168.1.10:8080`.
3. Ensure port 8080 is reachable (not blocked by a firewall).

---

## Default Accounts

| Username | Password | Role |
|:---------|:---------|:-----|
| `manager` | `pass123` | MANAGER |
| `rahil` | `pass123` | MANAGER |
| `krisha` | `pass123` | EMPLOYEE |
| `employee` | `pass123` | EMPLOYEE |

---

## Protocol Reference

All commands are pipe-delimited (`|`). The connection stays open for the entire session. Responses are prefixed with `OK|`, `ERROR|`, or `DATA|`.

### Authentication

| Command | Description |
|:--------|:------------|
| `LOGIN\|username\|password` | Authenticate. Reply: `OK\|username\|role` on success, `ERROR\|...` on failure |
| `LOGOUT` | End the session |

### Team Tasks (Manager only for create/delete)

| Command | Description |
|:--------|:------------|
| `GET_TASKS\|filterStatus\|filterPriority` | List tasks visible to the current user |
| `CREATE_TASK\|title\|desc\|assignedTo\|priority\|dueDate` | Create a new team task |
| `UPDATE_STATUS\|taskId\|newStatus` | Change a task's status |
| `UPDATE_NOTES\|taskId\|notes` | Update notes on a task |
| `DELETE_TASK\|taskId` | Delete a team task |

### Personal Tasks

| Command | Description |
|:--------|:------------|
| `GET_PERSONAL_TASKS` | List all personal tasks for the current user |
| `CREATE_PERSONAL_TASK\|title\|desc\|priority\|dueDate\|folder` | Create a personal task in a named folder |
| `DELETE_PERSONAL_TASK\|taskId` | Delete a personal task owned by the current user |
| `GET_FOLDERS\|username` | List all folder names for a user |

### User Management (Manager only)

| Command | Description |
|:--------|:------------|
| `GET_USERS` | List all users and their roles |
| `ADD_USER\|username\|password\|role` | Create a new user account |
| `DELETE_USER\|username` | Delete a user account (cannot delete self) |

### Response format

| Prefix | Meaning |
|:-------|:--------|
| `OK\|...` | Command succeeded |
| `DATA\|...` | Response contains records separated by `~~` |
| `ERROR\|...` | Command failed with a reason |

---

## Improvements

### Role-based access control
- Two roles: `MANAGER` and `EMPLOYEE`. Role is returned at login and governs what commands succeed server-side.
- Managers see all team tasks; employees see only tasks assigned to them.
- Only managers can create/delete team tasks, manage users, or view the full user list.

### Personal task folders
- Any user can create personal tasks isolated from the shared board.
- Tasks are grouped into named folders (e.g. `Personal`, `Study`, `Work`).
- The GUI provides a folder filter dropdown and a per-folder task view.

### Thread-safe persistence
- All `DataStore` mutating methods are `synchronized` so concurrent client threads never corrupt the file state.
- Data is written to flat text files (`taskflow_tasks.txt`, `taskflow_users.txt`) after every mutation and reloaded on server startup — sessions survive restarts.

### Responsive GUI
- Every network call runs in a `SwingWorker.doInBackground()` thread; the Event Dispatch Thread (EDT) is never blocked.
- Dark theme applied globally via `UIManager` defaults so all Swing widgets (combo boxes, dialogs, scroll bars) inherit the palette.
- Priority and status columns use colour-coded cell renderers (red for URGENT, amber for HIGH, green for DONE, etc.).

---

## Other Resources

### Course module references

| Feature | Reference |
|:--------|:----------|
| `ServerSocket` accept loop | Module 6, Slide 25 |
| `Socket` + `PrintWriter` + `BufferedReader` | Module 6, Slides 26–28 |
| Thread-per-client with `Runnable` | Module 7, Slide 7 |
| `Thread.interrupted()` for graceful shutdown | Module 7, Slide 9 |
| `synchronized` for shared mutable state | Module 7, Slide 26 |
| `SwingWorker` for background network calls | Module 7, Slide 31 |
| `FileReader` → `BufferedReader` → `readLine()` | Module 5, Slide 9 |
| `PrintWriter` → `println()` for file write | Module 5, Slide 10 |

### External documentation
- [Java ServerSocket documentation](https://docs.oracle.com/en/java/docs/api/java.base/java/net/ServerSocket.html)
- [Java Swing tutorial (Oracle)](https://docs.oracle.com/javase/tutorial/uiswing/)
- [SwingWorker guide](https://docs.oracle.com/javase/tutorial/uiswing/concurrency/worker.html)
- [AbstractTableModel (Oracle)](https://docs.oracle.com/javase/8/docs/api/javax/swing/table/AbstractTableModel.html)

### Data files

The server creates these files automatically on first run in the working directory:

- `taskflow_tasks.txt` — one task per line, pipe-delimited
- `taskflow_users.txt` — one user per line, pipe-delimited

To reset all data, delete both files and restart the server.

---

## 👥 Authors

- **Rahil Sanketbhai Vaghasia** - [RahilVaghasia01](https://github.com/RahilVaghasia01)
- **Krisha Kandoi** - [Krisha30](https://github.com/Krisha30)

**Built with Java**

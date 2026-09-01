package taskflow.common;

public class Task {
    public enum Priority { LOW, MEDIUM, HIGH, URGENT }
    public enum Status   { TODO, IN_PROGRESS, REVIEW, DONE }

    private int      id;
    private String   title;
    private String   description;
    private String   assignedTo;
    private String   createdBy;
    private Priority priority;
    private Status   status;
    private String   dueDate;
    private String   notes;
    private String   folder;

    public Task(int id, String title, String description,
                String assignedTo, String createdBy,
                Priority priority, String dueDate) {
        this.id = id; this.title = title; this.description = description;
        this.assignedTo = assignedTo; this.createdBy = createdBy;
        this.priority = priority; this.status = Status.TODO;
        this.dueDate = dueDate; this.notes = ""; this.folder = null;
    }

    public int      getId()          { return id; }
    public String   getTitle()       { return title; }
    public String   getDescription() { return description; }
    public String   getAssignedTo()  { return assignedTo; }
    public String   getCreatedBy()   { return createdBy; }
    public Priority getPriority()    { return priority; }
    public Status   getStatus()      { return status; }
    public String   getDueDate()     { return dueDate; }
    public String   getNotes()       { return notes; }
    public String   getFolder()      { return folder; }

    public void setStatus(Status s)     { this.status = s; }
    public void setNotes(String n)      { this.notes = n; }
    public void setFolder(String f)     { this.folder = f; }
    public void setPriority(Priority p) { this.priority = p; }
    public void setDueDate(String d)    { this.dueDate = d; }
    public void setTitle(String t)      { this.title = t; }
    public void setDescription(String d){ this.description = d; }
    public void setAssignedTo(String u) { this.assignedTo = u; }

    public String toFileLine() {
        return id + "|" + title + "|" + description + "|"
             + assignedTo + "|" + createdBy + "|"
             + priority + "|" + status + "|" + dueDate + "|"
             + notes.replace("|",";;") + "|"
             + (folder == null ? "" : folder);
    }

    public static Task fromFileLine(String line) {
        String[] p = line.split("\\|", -1);
        if (p == null || p.length < 10) return null;
        Task t = new Task(Integer.parseInt(p[0]), p[1], p[2], p[3], p[4],
                          Priority.valueOf(p[5]), p[7]);
        t.setStatus(Status.valueOf(p[6]));
        t.setNotes(p[8].replace(";;","|"));
        t.setFolder(p[9].isEmpty() ? null : p[9]);
        return t;
    }

    @Override
    public String toString() {
        return "[" + id + "] " + title + " | " + priority
             + " | " + status + " | Assigned: " + assignedTo
             + " | Due: " + dueDate;
    }
}

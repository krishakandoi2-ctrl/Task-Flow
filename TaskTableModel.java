package taskflow.client;

import taskflow.common.Task;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class TaskTableModel extends AbstractTableModel {

    private static final String[] COLS =
        {"ID", "Title", "Assigned To", "Priority", "Status", "Due Date", "Notes"};

    private List<Task> tasks = new ArrayList<>();

    public void setTasks(List<Task> tasks) {
        this.tasks = new ArrayList<>(tasks);
        fireTableDataChanged();
    }

    public Task getTaskAt(int row) {
        if (row < 0 || row >= tasks.size()) return null;
        return tasks.get(row);
    }

    public int    getRowCount()              { return tasks.size(); }
    public int    getColumnCount()           { return COLS.length; }
    public String getColumnName(int col)     { return COLS[col]; }

    public Object getValueAt(int row, int col) {
        Task t = tasks.get(row);
        switch (col) {
            case 0: return t.getId();
            case 1: return t.getTitle();
            case 2: return t.getAssignedTo();
            case 3: return t.getPriority();
            case 4: return t.getStatus();
            case 5: return t.getDueDate();
            case 6: return t.getNotes();
            default: return "";
        }
    }
}

package taskflow.client;

import taskflow.common.Protocol;
import taskflow.common.Task;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Swing client window for TaskFlow — redesigned dark UI.
 *
 * GUI         : Java Swing (JFrame, CardLayout, JTable, JDialog)
 * Networking  : ServerConnection (Socket + PrintWriter + BufferedReader) — m6 slides 26-28
 *               Each client instance holds its own independent TCP socket to the server.
 *               Multiple instances of this client can run simultaneously; the server
 *               spawns a new thread per connection (m7 slide 7) and all DataStore
 *               mutations are synchronized (m7 slide 26).
 * Concurrency : SwingWorker for every network call — m7 slide 31
 *               Never blocks the Event Dispatch Thread (EDT)
 */
public class TaskFlowClient extends JFrame {

    // Session
    private final ServerConnection connection;
    private final String  currentUser;
    private final boolean isManager;

    //  Palette
    private static final Color BG       = new Color(10, 12, 20);
    private static final Color PANEL    = new Color(15, 18, 30);
    private static final Color CARD     = new Color(20, 24, 38);
    private static final Color ROW_ALT  = new Color(17, 21, 34);
    private static final Color BORDER_C = new Color(35, 45, 72);
    private static final Color ACCENT   = new Color(94, 206, 255);   // cyan
    private static final Color GREEN    = new Color(72, 210, 150);   // green
    private static final Color AMBER    = new Color(255, 175, 75);   // amber
    private static final Color RED      = new Color(220, 75, 85);    // red
    private static final Color PURPLE   = new Color(160, 110, 255);  // purple
    private static final Color TXT      = new Color(210, 225, 248);
    private static final Color TXT_DIM  = new Color(95, 115, 158);
    private static final Color SEL_BG   = new Color(35, 75, 140);
    private static final Color ROW_URG  = new Color(40, 16, 20);
    private static final Color ROW_HIGH = new Color(38, 26, 12);
    private static final Color ROW_DONE = new Color(12, 34, 26);

    // ── UI components ─────────────────────────────────────────────────────────
    private JLabel         statusBar;
    private JPanel         contentStack;
    private CardLayout     cards;
    private JButton[]      navBtns;

    private TaskTableModel    teamModel;
    private JTable            teamTable;
    private JComboBox<String> cboStatus;
    private JComboBox<String> cboPriority;

    private TaskTableModel    personalModel;
    private JTable            personalTable;
    private JComboBox<String> cboFolder;

    private JTextArea         usersArea;
    // Entry point

    public static void main(String[] args) {
        applyDarkDefaults();
        SwingUtilities.invokeLater(() -> {
            LoginDialog dlg = new LoginDialog(null);
            dlg.setVisible(true);
            if (dlg.getLoggedInUser() == null) System.exit(0);
            new TaskFlowClient(
                    dlg.getConnection(),
                    dlg.getLoggedInUser(),
                    "MANAGER".equals(dlg.getLoggedInRole())
            ).setVisible(true);
        });
    }

    /**
     * Sets UIManager defaults so that standard Swing widgets (JOptionPane,
     * JComboBox drop-downs, JScrollBar) inherit the dark palette even when
     * they paint themselves outside our custom renderers.
     */
    private static void applyDarkDefaults() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        Color fieldBg = new Color(16, 20, 34);
        Color fieldFg = new Color(210, 225, 248);
        UIManager.put("TextField.background",         fieldBg);
        UIManager.put("TextField.foreground",         fieldFg);
        UIManager.put("TextField.caretForeground",    new Color(94, 206, 255));
        UIManager.put("PasswordField.background",     fieldBg);
        UIManager.put("PasswordField.foreground",     fieldFg);
        UIManager.put("TextArea.background",          new Color(14, 18, 30));
        UIManager.put("TextArea.foreground",          fieldFg);
        UIManager.put("ComboBox.background",          new Color(18, 22, 36));
        UIManager.put("ComboBox.foreground",          fieldFg);
        UIManager.put("ComboBox.selectionBackground", new Color(35, 75, 140));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("List.background",              new Color(18, 22, 36));
        UIManager.put("List.foreground",              fieldFg);
        UIManager.put("List.selectionBackground",     new Color(35, 75, 140));
        UIManager.put("OptionPane.background",        new Color(20, 24, 38));
        UIManager.put("OptionPane.messageForeground", fieldFg);
        UIManager.put("Panel.background",             new Color(20, 24, 38));
        UIManager.put("Button.background",            new Color(30, 38, 62));
        UIManager.put("Button.foreground",            fieldFg);
        UIManager.put("ScrollBar.background",         new Color(15, 18, 30));
        UIManager.put("ScrollBar.thumb",              new Color(45, 58, 90));
        UIManager.put("ScrollBar.track",              new Color(15, 18, 30));
    }

    // Constructor
    public TaskFlowClient(ServerConnection conn, String user, boolean manager) {
        super("TaskFlow");
        this.connection  = conn;
        this.currentUser = user;
        this.isManager   = manager;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { doExit(); }
        });
        setSize(1120, 740);
        setMinimumSize(new Dimension(880, 580));
        setLocationRelativeTo(null);
        setIconImage(buildIcon());

        buildUI();
        refreshTeamTasks();
        refreshPersonalTasks();
        if (isManager) refreshUsers();
    }

    /** Programmatically generated "T" icon — no external file needed. */
    private Image buildIcon() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(37, 99, 235));
        g.fillRoundRect(0, 0, 32, 32, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Serif", Font.BOLD, 20));
        g.drawString("T", 8, 24);
        g.dispose();
        return img;
    }

    // UI Build
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setOpaque(true);
        setContentPane(root);
        root.add(buildTopBar(),    BorderLayout.NORTH);
        root.add(buildSidebar(),   BorderLayout.WEST);
        root.add(buildContent(),   BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
    }

    //  Top bar
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(PANEL); g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(BORDER_C); g.fillRect(0, getHeight()-1, getWidth(), 1);
            }
        };
        bar.setPreferredSize(new Dimension(0, 56));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 22));

        // Logo
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        JLabel gem = new JLabel("◆");
        gem.setFont(new Font("Dialog", Font.PLAIN, 10));
        gem.setForeground(ACCENT);
        JLabel logo = new JLabel("TaskFlow");
        logo.setFont(new Font("Serif", Font.BOLD, 20));
        logo.setForeground(TXT);
        left.add(gem); left.add(logo);
        bar.add(left, BorderLayout.WEST);

        // Centre — socket connection indicator
        // This label makes the multi-client socket support visible in the UI.
        // Each running instance has its own independent TCP socket to the server.
        JPanel centre = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        centre.setOpaque(false);
        JLabel socketDot = new JLabel("●");
        socketDot.setFont(new Font("Dialog", Font.PLAIN, 9));
        socketDot.setForeground(GREEN);
        JLabel socketLbl = new JLabel("Socket connected  ·  port 8080  ·  independent session");
        socketLbl.setFont(new Font("Dialog", Font.PLAIN, 11));
        socketLbl.setForeground(TXT_DIM);
        centre.add(socketDot); centre.add(socketLbl);
        bar.add(centre, BorderLayout.CENTER);

        // Right — user info + role badge + sign-out
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);

        JLabel who = new JLabel(currentUser);
        who.setFont(new Font("Dialog", Font.PLAIN, 13));
        who.setForeground(TXT);

        // Role badge — painted with rounded fill + coloured border
        JLabel badge = new JLabel(isManager ? "MANAGER" : "EMPLOYEE") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = isManager ? new Color(20,55,35,200) : new Color(20,40,80,200);
                Color edge = isManager ? GREEN : ACCENT;
                g2.setColor(fill);  g2.fillRoundRect(0,0,getWidth(),getHeight(),6,6);
                g2.setColor(edge);  g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);
                g2.dispose(); super.paintComponent(g);
            }
        };
        badge.setFont(new Font("Dialog", Font.BOLD, 10));
        badge.setForeground(isManager ? GREEN : ACCENT);
        badge.setOpaque(false);
        badge.setBorder(BorderFactory.createEmptyBorder(3,10,3,10));

        JButton signOut = pillBtn("Sign Out", new Color(50,20,25), RED);
        signOut.addActionListener(e -> doExit());

        right.add(who); right.add(badge); right.add(signOut);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // Sidebar
    private JPanel buildSidebar() {
        JPanel side = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(PANEL);  g2.fillRect(0,0,getWidth(),getHeight());
                g2.setColor(BORDER_C); g2.fillRect(getWidth()-1,0,1,getHeight());
                g2.dispose();
            }
        };
        side.setPreferredSize(new Dimension(168, 0));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setOpaque(false);
        side.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));

        JLabel nav = new JLabel("NAVIGATION");
        nav.setFont(new Font("Dialog", Font.BOLD, 9));
        nav.setForeground(new Color(55, 70, 105));
        nav.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 0));
        side.add(nav);

        String[] labels = isManager
                ? new String[]{"Team Tasks", "My Folders", "Manage Users"}
                : new String[]{"Team Tasks", "My Folders"};

        navBtns = new JButton[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            navBtns[i] = navBtn(labels[i], i == 0);
            navBtns[i].addActionListener(e -> switchTab(idx));
            side.add(navBtns[i]);
        }

        side.add(Box.createVerticalGlue());

        // Divider
        JPanel div = new JPanel();
        div.setBackground(BORDER_C);
        div.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        side.add(div);
        side.add(Box.createVerticalStrut(10));

        JLabel ver = new JLabel("v1.0  ·  multi-client");
        ver.setFont(new Font("Dialog", Font.PLAIN, 9));
        ver.setForeground(new Color(45, 58, 90));
        ver.setAlignmentX(Component.CENTER_ALIGNMENT);
        side.add(ver);
        return side;
    }

    private JButton navBtn(String label, boolean startActive) {
        JButton btn = new JButton() {
            boolean hov;
            {
                putClientProperty("active", startActive);
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                    public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                boolean active = Boolean.TRUE.equals(getClientProperty("active"));
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (active) {
                    g2.setColor(new Color(25, 52, 95));
                    g2.fillRoundRect(10,3,getWidth()-20,getHeight()-6,8,8);
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(4,getHeight()/2-10,3,20,3,3);
                } else if (hov) {
                    g2.setColor(new Color(20,28,50));
                    g2.fillRoundRect(10,3,getWidth()-20,getHeight()-6,8,8);
                }
                int cy = getHeight()/2;
                g2.setColor(active ? ACCENT : new Color(65,82,120));
                if (active) g2.fillOval(22,cy-3,6,6);
                else { g2.setStroke(new BasicStroke(1.2f)); g2.drawOval(22,cy-3,6,6); }
                g2.setFont(new Font("Dialog", active ? Font.BOLD : Font.PLAIN, 12));
                g2.setColor(active ? TXT : new Color(130,150,190));
                g2.drawString(label, 38, cy+5);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(168,38));
        btn.setMaximumSize(new Dimension(168,38));
        btn.setMinimumSize(new Dimension(168,38));
        btn.setContentAreaFilled(false); btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void switchTab(int idx) {
        cards.show(contentStack, "tab" + idx);
        for (int i = 0; i < navBtns.length; i++) {
            navBtns[i].putClientProperty("active", i == idx);
            navBtns[i].repaint();
        }
    }

    //Content
     private JPanel buildContent() {
        contentStack = new JPanel();
        cards = new CardLayout();
        contentStack.setLayout(cards);
        contentStack.setBackground(BG);
        contentStack.setOpaque(true);
        contentStack.add(buildTeamTab(),     "tab0");
        contentStack.add(buildPersonalTab(), "tab1");
        if (isManager) contentStack.add(buildUsersTab(), "tab2");
        return contentStack;
    }

    //Team Tasks tab
    private JPanel buildTeamTab() {
        JPanel p = darkPanel();
        p.setBorder(BorderFactory.createEmptyBorder(22, 22, 16, 22));

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        hdr.add(sectionTitle("Team Tasks"), BorderLayout.WEST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        filters.add(dimLabel("Status"));
        cboStatus = styledCombo(new String[]{"ALL","TODO","IN_PROGRESS","REVIEW","DONE"});
        filters.add(cboStatus);
        filters.add(dimLabel("Priority"));
        cboPriority = styledCombo(new String[]{"ALL","LOW","MEDIUM","HIGH","URGENT"});
        filters.add(cboPriority);
        JButton refresh = ghostBtn("↻  Refresh");
        refresh.addActionListener(e -> refreshTeamTasks());
        filters.add(refresh);
        hdr.add(filters, BorderLayout.EAST);
        cboStatus.addActionListener(e   -> refreshTeamTasks());
        cboPriority.addActionListener(e -> refreshTeamTasks());
        p.add(hdr, BorderLayout.NORTH);

        teamModel = new TaskTableModel();
        teamTable = buildTable(teamModel);
        teamTable.getColumnModel().getColumn(0).setMaxWidth(52);
        teamTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        teamTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        teamTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        teamTable.getColumnModel().getColumn(5).setPreferredWidth(86);
        teamTable.setDefaultRenderer(Object.class, new TaskRenderer());
        p.add(wrapScroll(teamTable), BorderLayout.CENTER);

        JPanel acts = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        acts.setOpaque(false);
        JButton bSt = accentBtn("Update Status", ACCENT);  bSt.addActionListener(e -> doUpdateStatus());   acts.add(bSt);
        JButton bNt = accentBtn("Add Notes",    PURPLE);   bNt.addActionListener(e -> doUpdateNotes());    acts.add(bNt);
        if (isManager) {
            JButton bNw = accentBtn("+ New Task",   GREEN); bNw.addActionListener(e -> doCreateTask());    acts.add(bNw);
            JButton bDl = accentBtn("Delete Task",  RED);   bDl.addActionListener(e -> doDeleteTask());    acts.add(bDl);
        }
        p.add(acts, BorderLayout.SOUTH);
        return p;
    }

    //My Folders tab
    private JPanel buildPersonalTab() {
        JPanel p = darkPanel();
        p.setBorder(BorderFactory.createEmptyBorder(22, 22, 16, 22));

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        hdr.add(sectionTitle("My Folders"), BorderLayout.WEST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        filters.add(dimLabel("Folder"));
        cboFolder = styledCombo(new String[]{"All Folders"});
        filters.add(cboFolder);
        JButton ref = ghostBtn("↻  Refresh");
        ref.addActionListener(e -> refreshPersonalTasks());
        filters.add(ref);
        hdr.add(filters, BorderLayout.EAST);
        p.add(hdr, BorderLayout.NORTH);

        personalModel = new TaskTableModel();
        personalTable = buildTable(personalModel);
        personalTable.setDefaultRenderer(Object.class, new TaskRenderer());
        p.add(wrapScroll(personalTable), BorderLayout.CENTER);

        JPanel acts = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        acts.setOpaque(false);
        JButton bNw = accentBtn("+ New Task",    GREEN); bNw.addActionListener(e -> doCreatePersonalTask());    acts.add(bNw);
        JButton bSt = accentBtn("Update Status", ACCENT); bSt.addActionListener(e -> doUpdatePersonalStatus()); acts.add(bSt);
        JButton bDl = accentBtn("Delete Task",   RED);   bDl.addActionListener(e -> doDeletePersonalTask());   acts.add(bDl);
        p.add(acts, BorderLayout.SOUTH);
        return p;
    }

    //  Manage Users tab
    private JPanel buildUsersTab() {
        JPanel p = darkPanel();
        p.setBorder(BorderFactory.createEmptyBorder(22, 22, 16, 22));

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        hdr.add(sectionTitle("Manage Users"), BorderLayout.WEST);

        // Multi-client note in the users tab header
        JLabel note = new JLabel("Each connected client runs in its own server thread");
        note.setFont(new Font("Dialog", Font.PLAIN, 11));
        note.setForeground(TXT_DIM);
        hdr.add(note, BorderLayout.EAST);
        p.add(hdr, BorderLayout.NORTH);

        usersArea = new JTextArea();
        usersArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        usersArea.setEditable(false);
        usersArea.setBackground(CARD);
        usersArea.setForeground(TXT);
        usersArea.setCaretColor(ACCENT);
        usersArea.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        p.add(wrapScroll(usersArea), BorderLayout.CENTER);

        JPanel acts = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        acts.setOpaque(false);
        JButton bRf = ghostBtn("↻  Refresh");    bRf.addActionListener(e -> refreshUsers()); acts.add(bRf);
        JButton bAd = accentBtn("+ Add User", GREEN); bAd.addActionListener(e -> doAddUser());    acts.add(bAd);
        JButton bDl = accentBtn("Delete User", RED);  bDl.addActionListener(e -> doDeleteUser()); acts.add(bDl);
        p.add(acts, BorderLayout.SOUTH);
        return p;
    }

    //  Status bar
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(PANEL);    g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(BORDER_C); g.fillRect(0,0,getWidth(),1);
            }
        };
        bar.setPreferredSize(new Dimension(0, 28));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Dialog", Font.PLAIN, 9));
        dot.setForeground(GREEN);
        statusBar = new JLabel("Connected  ·  Ready");
        statusBar.setFont(new Font("Dialog", Font.PLAIN, 11));
        statusBar.setForeground(TXT_DIM);
        left.add(dot); left.add(statusBar);

        // Right side — shows that this is one socket among potentially many
        JLabel rightLbl = new JLabel("Multi-client TCP server  ·  thread-per-connection  ·  synchronized DataStore");
        rightLbl.setFont(new Font("Dialog", Font.PLAIN, 10));
        rightLbl.setForeground(new Color(45, 58, 90));

        bar.add(left,     BorderLayout.WEST);
        bar.add(rightLbl, BorderLayout.EAST);
        return bar;
    }

    // Table

    private JTable buildTable(TaskTableModel model) {
        JTable t = new JTable(model);
        t.setFont(new Font("Dialog", Font.PLAIN, 12));
        t.setRowHeight(32);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(BORDER_C);
        t.setBackground(CARD);
        t.setForeground(TXT);
        t.setSelectionBackground(SEL_BG);
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader hdr = t.getTableHeader();
        hdr.setReorderingAllowed(false);
        hdr.setFont(new Font("Dialog", Font.BOLD, 11));
        hdr.setBackground(PANEL);
        hdr.setForeground(TXT_DIM);
        hdr.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER_C));
        hdr.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl, Object val, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tbl, val, sel, foc, r, c);
                setBackground(PANEL); setForeground(TXT_DIM);
                setFont(new Font("Dialog", Font.BOLD, 11));
                setText(val != null ? val.toString().toUpperCase() : "");
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0,0,1,0,BORDER_C),
                        BorderFactory.createEmptyBorder(0,10,0,10)));
                return this;
            }
        });
        return t;
    }

    private class TaskRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable tbl, Object val, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(tbl, val, sel, foc, row, col);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            if (sel) { setBackground(SEL_BG); setForeground(Color.WHITE); return this; }

            TaskTableModel m = (TaskTableModel) tbl.getModel();
            Task task = m.getTaskAt(row);
            setBackground(row % 2 == 0 ? CARD : ROW_ALT);
            setForeground(TXT);
            if (task != null) {
                if (task.getPriority() == Task.Priority.URGENT) {
                    setBackground(ROW_URG);
                    if (col == 3) setForeground(RED);
                } else if (task.getPriority() == Task.Priority.HIGH) {
                    setBackground(ROW_HIGH);
                    if (col == 3) setForeground(AMBER);
                } else if (task.getStatus() == Task.Status.DONE) {
                    setBackground(ROW_DONE);
                    if (col == 4) setForeground(GREEN);
                }
                if (col == 4 && task.getStatus() != Task.Status.DONE) {
                    switch (task.getStatus()) {
                        case TODO:        setForeground(TXT_DIM); break;
                        case IN_PROGRESS: setForeground(ACCENT);  break;
                        case REVIEW:      setForeground(AMBER);   break;
                        default: break;
                    }
                }
                if (col == 0) setForeground(TXT_DIM);
            }
            return this;
        }
    }

    // Component factories

    private JPanel darkPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG); p.setOpaque(true); return p;
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Serif", Font.BOLD, 20)); l.setForeground(TXT); return l;
    }

    private JLabel dimLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Dialog", Font.PLAIN, 11)); l.setForeground(TXT_DIM); return l;
    }

    private JComboBox<String> styledCombo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(new Font("Dialog", Font.PLAIN, 12));
        cb.setBackground(new Color(18,22,36)); cb.setForeground(TXT);
        cb.setPreferredSize(new Dimension(136, 30)); return cb;
    }

    private JButton ghostBtn(String text) {
        JButton b = new JButton(text) {
            boolean hov;
            { addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){hov=true; repaint();}
                public void mouseExited(MouseEvent e) {hov=false;repaint();}
            }); }
            @Override protected void paintComponent(Graphics g) {
                if (hov) {
                    Graphics2D g2=(Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(30,40,65)); g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Dialog",Font.PLAIN,12)); b.setForeground(TXT_DIM);
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(4,12,4,12));
        b.setPreferredSize(new Dimension(100,30)); return b;
    }

    private JButton accentBtn(String label, Color accent) {
        JButton b = new JButton(label) {
            boolean hov;
            { addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){hov=true; repaint();}
                public void mouseExited(MouseEvent e) {hov=false;repaint();}
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int ar=accent.getRed(),ag=accent.getGreen(),ab=accent.getBlue();
                Color fill = hov ? new Color((int)(ar*.30),(int)(ag*.30),(int)(ab*.30),230)
                        : new Color((int)(ar*.20),(int)(ag*.20),(int)(ab*.20),200);
                g2.setColor(fill); g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.setColor(new Color(ar,ag,ab,80)); g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);
                g2.dispose(); super.paintComponent(g);
            }
        };
        b.setFont(new Font("Dialog",Font.BOLD,11)); b.setForeground(accent);
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(6,14,6,14)); return b;
    }

    private JButton pillBtn(String label, Color bgColor, Color fgColor) {
        JButton b = new JButton(label) {
            boolean hov;
            { addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){hov=true; repaint();}
                public void mouseExited(MouseEvent e) {hov=false;repaint();}
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hov?bgColor.brighter():bgColor);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),20,20);
                g2.dispose(); super.paintComponent(g);
            }
        };
        b.setFont(new Font("Dialog",Font.BOLD,11)); b.setForeground(fgColor);
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(5,14,5,14)); return b;
    }

    private JScrollPane wrapScroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new LoginDialog.OutlineBorder(BORDER_C, 1, 6));
        sp.setBackground(CARD);
        sp.getViewport().setBackground(CARD);
        return sp;
    }

    private JPanel formPanel() {
        JPanel p = new JPanel(new GridLayout(0,2,10,10));
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createEmptyBorder(14,14,14,14)); return p;
    }

    private void addRow(JPanel form, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Dialog",Font.BOLD,12)); l.setForeground(TXT_DIM);
        form.add(l); form.add(field);
    }

    private JTextField styledInput(String val) {
        JTextField f = new JTextField(val, 20);
        f.setBackground(new Color(14,18,30)); f.setForeground(TXT); f.setCaretColor(ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
                new LoginDialog.OutlineBorder(BORDER_C,1,6),
                BorderFactory.createEmptyBorder(4,8,4,8)));
        return f;
    }

    private JComboBox<String> formCombo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(new Font("Dialog",Font.PLAIN,12));
        cb.setBackground(new Color(14,18,30)); cb.setForeground(TXT); return cb;
    }

    // Data refresh — SwingWorker keeps EDT responsive (m7 slide 31)

    private void refreshTeamTasks() {
        String fs = (String)cboStatus.getSelectedItem();
        String fp = (String)cboPriority.getSelectedItem();
        setStatus("Loading team tasks…");
        new SwingWorker<List<Task>,Void>() {
            protected List<Task> doInBackground() {
                return parseTasks(connection.sendCommand(Protocol.GET_TASKS+"|"+fs+"|"+fp), false);
            }
            protected void done() {
                try { List<Task> list=get(); teamModel.setTasks(list); setStatus("Team tasks: "+list.size()+" records"); }
                catch (Exception ex) { setStatus("Error: "+ex.getMessage()); }
            }
        }.execute();
    }

    private void refreshPersonalTasks() {
        setStatus("Loading personal tasks…");
        new SwingWorker<List<Task>,Void>() {
            protected List<Task> doInBackground() {
                return parsePersonalResponse(connection.sendCommand(Protocol.GET_PERSONAL_TASKS));
            }
            protected void done() {
                try {
                    List<Task> list=get();
                    personalModel.setTasks(list);
                    cboFolder.removeAllItems(); cboFolder.addItem("All Folders");
                    for (Task t : list) {
                        String f=t.getFolder();
                        if (f!=null) {
                            boolean found=false;
                            for (int i=0;i<cboFolder.getItemCount();i++)
                                if (f.equals(cboFolder.getItemAt(i))){found=true;break;}
                            if (!found) cboFolder.addItem(f);
                        }
                    }
                    setStatus("Personal tasks: "+list.size());
                } catch (Exception ex) { setStatus("Error: "+ex.getMessage()); }
            }
        }.execute();
    }

    private void refreshUsers() {
        new SwingWorker<String,Void>() {
            protected String doInBackground() { return connection.sendCommand(Protocol.GET_USERS); }
            protected void done() {
                try {
                    String resp=get();
                    if (resp!=null&&resp.startsWith("DATA|")) {
                        StringBuilder sb=new StringBuilder();
                        sb.append(String.format("  %-22s  %s%n","USERNAME","ROLE"));
                        sb.append("  "+"\u2500".repeat(32)+"\n");
                        for (String entry : resp.substring(5).split(Protocol.ITEM_SEP)) {
                            if (!entry.isEmpty()) {
                                String[] up=entry.split(":");
                                sb.append(String.format("  %-22s  %s%n",up[0],up.length>1?up[1]:""));
                            }
                        }
                        usersArea.setText(sb.toString());
                    }
                } catch (Exception ex) { usersArea.setText("Error: "+ex.getMessage()); }
            }
        }.execute();
    }

    // Actions

    private void doCreateTask() {
        setStatus("Loading team members…");
        new SwingWorker<String[],Void>() {
            protected String[] doInBackground() {
                String[] m=fetchTeamMembers();
                return m.length==0?new String[]{currentUser}:m;
            }
            protected void done() {
                try { openCreateTaskDialog(get()); }
                catch (Exception ex) { openCreateTaskDialog(new String[]{currentUser}); }
            }
        }.execute();
    }

    private void openCreateTaskDialog(String[] members) {
        JTextField titleF = styledInput("");
        JTextField descF  = styledInput("");
        JComboBox<String> assignBox = formCombo(members);
        JComboBox<String> prioBox   = formCombo(new String[]{"LOW","MEDIUM","HIGH","URGENT"});
        prioBox.setSelectedItem("MEDIUM");
        JTextField dueF = styledInput("2025-12-31");

        JPanel form = formPanel();
        addRow(form,"Title:",       titleF);
        addRow(form,"Description:", descF);
        addRow(form,"Assign To:",   assignBox);
        addRow(form,"Priority:",    prioBox);
        addRow(form,"Due Date:",    dueF);

        if (JOptionPane.showConfirmDialog(this,form,"Create New Task",
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) return;

        String title  = titleF.getText().trim();
        String desc   = descF.getText().trim().replace("|"," ");
        String assign = assignBox.getSelectedItem()!=null?(String)assignBox.getSelectedItem():currentUser;
        String prio   = prioBox.getSelectedItem()  !=null?(String)prioBox.getSelectedItem()  :"MEDIUM";
        String due    = dueF.getText().trim();
        if (title.isEmpty())  {alert("Title is required.");    return;}
        if (assign.isEmpty()) {alert("Assignee is required."); return;}
        if (due.isEmpty())    due="TBD";

        setStatus("Creating task…");
        final String fT=title,fD=desc,fA=assign,fP=prio,fDu=due;
        new SwingWorker<String,Void>() {
            protected String doInBackground() {
                return connection.sendCommand(Protocol.CREATE_TASK+"|"+fT+"|"+fD+"|"+fA+"|"+fP+"|"+fDu);
            }
            protected void done() {
                try {
                    String r=get();
                    if (r!=null&&r.startsWith("OK|")){setStatus("Task created");refreshTeamTasks();}
                    else alert(r!=null?r.split("\\|",2)[1]:"Error creating task");
                } catch (Exception ex){alert(ex.getMessage());}
            }
        }.execute();
    }

    private void doUpdateStatus()         { doUpdateStatusFor(teamTable,     teamModel,     this::refreshTeamTasks); }
    private void doUpdatePersonalStatus() { doUpdateStatusFor(personalTable, personalModel, this::refreshPersonalTasks); }

    private void doUpdateStatusFor(JTable table, TaskTableModel model, Runnable refresh) {
        int row=table.getSelectedRow();
        if (row<0){alert("Please select a task first.");return;}
        Task t=model.getTaskAt(row); if (t==null) return;
        String[] opts={"TODO","IN_PROGRESS","REVIEW","DONE"};
        String chosen=(String)JOptionPane.showInputDialog(this,
                "New status for: "+t.getTitle(),"Update Status",
                JOptionPane.PLAIN_MESSAGE,null,opts,t.getStatus().name());
        if (chosen==null) return;
        int id=t.getId();
        new SwingWorker<String,Void>() {
            protected String doInBackground(){return connection.sendCommand(Protocol.UPDATE_STATUS+"|"+id+"|"+chosen);}
            protected void done(){
                try{String r=get();if(r!=null&&r.startsWith("OK|")){setStatus("Status updated");refresh.run();}
                else alert(r!=null?r.split("\\|",2)[1]:"Error");}
                catch(Exception ex){alert(ex.getMessage());}
            }
        }.execute();
    }

    private void doUpdateNotes() {
        int row=teamTable.getSelectedRow();
        if (row<0){alert("Please select a task first.");return;}
        Task t=teamModel.getTaskAt(row); if (t==null) return;
        JTextArea area=new JTextArea(t.getNotes(),5,32);
        area.setBackground(new Color(14,18,30)); area.setForeground(TXT); area.setCaretColor(ACCENT);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        if (JOptionPane.showConfirmDialog(this,new JScrollPane(area),"Notes: "+t.getTitle(),
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) return;
        String notes=area.getText().trim().replace("|"," ");
        int id=t.getId();
        new SwingWorker<String,Void>() {
            protected String doInBackground(){return connection.sendCommand(Protocol.UPDATE_NOTES+"|"+id+"|"+notes);}
            protected void done(){
                try{String r=get();if(r!=null&&r.startsWith("OK|")){setStatus("Notes saved");refreshTeamTasks();}
                else alert(r!=null?r.split("\\|",2)[1]:"Error");}
                catch(Exception ex){alert(ex.getMessage());}
            }
        }.execute();
    }

    private void doDeleteTask() {
        int row=teamTable.getSelectedRow();
        if (row<0){alert("Please select a task first.");return;}
        Task t=teamModel.getTaskAt(row); if (t==null) return;
        if (JOptionPane.showConfirmDialog(this,"Delete task: \""+t.getTitle()+"\"?",
                "Confirm Delete",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
        int id=t.getId();
        new SwingWorker<String,Void>() {
            protected String doInBackground(){return connection.sendCommand(Protocol.DELETE_TASK+"|"+id);}
            protected void done(){
                try{String r=get();if(r!=null&&r.startsWith("OK|")){setStatus("Task deleted");refreshTeamTasks();}
                else alert(r!=null?r.split("\\|",2)[1]:"Error");}
                catch(Exception ex){alert(ex.getMessage());}
            }
        }.execute();
    }

    private void doDeletePersonalTask() {
        int row=personalTable.getSelectedRow();
        if (row<0){alert("Please select a task to delete.");return;}
        Task t=personalModel.getTaskAt(row); if (t==null) return;
        if (JOptionPane.showConfirmDialog(this,"Delete personal task: \""+t.getTitle()+"\"?",
                "Confirm Delete",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
        int id=t.getId();
        new SwingWorker<String,Void>() {
            protected String doInBackground(){return connection.sendCommand(Protocol.DELETE_PERSONAL_TASK+"|"+id);}
            protected void done(){
                try{String r=get();if(r!=null&&r.startsWith("OK|")){setStatus("Personal task deleted");refreshPersonalTasks();}
                else alert(r!=null?r.split("\\|",2)[1]:"Error deleting task");}
                catch(Exception ex){alert(ex.getMessage());}
            }
        }.execute();
    }

    private void doCreatePersonalTask() {
        JTextField titleF  = styledInput("");
        JTextField descF   = styledInput("");
        JComboBox<String> prioBox = formCombo(new String[]{"LOW","MEDIUM","HIGH","URGENT"});
        JTextField dueF    = styledInput("2025-12-31");
        JTextField folderF = styledInput("Personal");

        JPanel form = formPanel();
        addRow(form,"Title:",       titleF);
        addRow(form,"Description:", descF);
        addRow(form,"Priority:",    prioBox);
        addRow(form,"Due Date:",    dueF);
        addRow(form,"Folder:",      folderF);

        if (JOptionPane.showConfirmDialog(this,form,"New Personal Task",
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) return;

        String title  = titleF.getText().trim();
        String desc   = descF.getText().trim().replace("|"," ");
        String prio   = (String)prioBox.getSelectedItem();
        String due    = dueF.getText().trim();
        String folder = folderF.getText().trim();
        if (title.isEmpty()||folder.isEmpty()){alert("Title and folder are required.");return;}

        new SwingWorker<String,Void>() {
            protected String doInBackground(){
                return connection.sendCommand(Protocol.CREATE_PERSONAL_TASK+"|"+title+"|"+desc+"|"+prio+"|"+due+"|"+folder);
            }
            protected void done(){
                try{String r=get();if(r!=null&&r.startsWith("OK|")){setStatus("Personal task created");refreshPersonalTasks();}
                else alert(r!=null?r.split("\\|",2)[1]:"Error");}
                catch(Exception ex){alert(ex.getMessage());}
            }
        }.execute();
    }

    private void doAddUser() {
        JTextField unameF = styledInput("");
        JPasswordField passF = new JPasswordField(16);
        passF.setBackground(new Color(14,18,30)); passF.setForeground(TXT); passF.setCaretColor(ACCENT);
        passF.setBorder(BorderFactory.createCompoundBorder(
                new LoginDialog.OutlineBorder(BORDER_C,1,6),
                BorderFactory.createEmptyBorder(4,8,4,8)));
        JComboBox<String> roleBox = formCombo(new String[]{"EMPLOYEE","MANAGER"});

        JPanel form = formPanel();
        addRow(form,"Username:",unameF);
        addRow(form,"Password:",passF);
        addRow(form,"Role:",    roleBox);

        if (JOptionPane.showConfirmDialog(this,form,"Add New User",
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) return;

        String uname=unameF.getText().trim();
        String pass =new String(passF.getPassword());
        String role =(String)roleBox.getSelectedItem();
        if (uname.isEmpty()||pass.isEmpty()){alert("Username and password required.");return;}

        new SwingWorker<String,Void>() {
            protected String doInBackground(){return connection.sendCommand(Protocol.ADD_USER+"|"+uname+"|"+pass+"|"+role);}
            protected void done(){
                try{String r=get();if(r!=null&&r.startsWith("OK|")){setStatus("User added: "+uname);refreshUsers();}
                else alert(r!=null?r.split("\\|",2)[1]:"Error");}
                catch(Exception ex){alert(ex.getMessage());}
            }
        }.execute();
    }

    private void doDeleteUser() {
        String target=JOptionPane.showInputDialog(this,"Enter the username to delete:","Delete User",JOptionPane.WARNING_MESSAGE);
        if (target==null||target.trim().isEmpty()) return;
        target=target.trim();
        if (target.equals(currentUser)){alert("You cannot delete your own account.");return;}
        if (JOptionPane.showConfirmDialog(this,"Permanently delete user: "+target+"?\nThis cannot be undone.",
                "Confirm Delete User",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION) return;
        final String ft=target;
        new SwingWorker<String,Void>() {
            protected String doInBackground(){return connection.sendCommand(Protocol.DELETE_USER+"|"+ft);}
            protected void done(){
                try{String r=get();if(r!=null&&r.startsWith("OK|")){setStatus("User deleted: "+ft);refreshUsers();}
                else alert(r!=null?r.split("\\|",2)[1]:"Error deleting user");}
                catch(Exception ex){alert(ex.getMessage());}
            }
        }.execute();
    }

    // Helpers
    private List<Task> parseTasks(String resp, boolean personalOnly) {
        List<Task> list=new ArrayList<>();
        if (resp==null||!resp.startsWith("DATA|")) return list;
        String data=resp.substring(5); if (data.isEmpty()) return list;
        for (String entry : data.split(Protocol.ITEM_SEP)) {
            if (!entry.isEmpty()) {
                Task t=Task.fromFileLine(entry); if (t==null) continue;
                if (personalOnly&&t.getFolder()!=null&&t.getAssignedTo().equals(currentUser)) list.add(t);
                else if (!personalOnly&&t.getFolder()==null) list.add(t);
            }
        }
        return list;
    }

    private List<Task> parsePersonalResponse(String resp) {
        List<Task> list=new ArrayList<>();
        if (resp==null||!resp.startsWith("DATA|")) return list;
        String data=resp.substring(5); if (data.isEmpty()) return list;
        for (String entry : data.split(Protocol.ITEM_SEP))
            if (!entry.isEmpty()){Task t=Task.fromFileLine(entry);if(t!=null)list.add(t);}
        return list;
    }

    private String[] fetchTeamMembers() {
        String resp=connection.sendCommand(Protocol.GET_USERS);
        if (resp==null||!resp.startsWith("DATA|")) return new String[0];
        List<String> names=new ArrayList<>();
        for (String entry : resp.substring(5).split(Protocol.ITEM_SEP))
            if (!entry.isEmpty()) names.add(entry.split(":")[0]);
        return names.toArray(new String[0]);
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(()->statusBar.setText(msg));
    }

    private void alert(String msg) {
        JOptionPane.showMessageDialog(this,msg,"TaskFlow",JOptionPane.ERROR_MESSAGE);
    }

    private void doExit() {
        connection.sendCommand(Protocol.LOGOUT);
        connection.disconnect();
        dispose();
        System.exit(0);
    }
}
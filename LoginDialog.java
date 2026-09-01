package taskflow.client;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Login dialog — dark-themed to match TaskFlowClient.
 * Uses SwingWorker for the network call so the EDT stays responsive — m7 slide 31.
 *
 * Exposes OutlineBorder as a public static inner class so TaskFlowClient
 * can reference it for consistent rounded-rect borders across the whole UI.
 */
public class LoginDialog extends JDialog {

    // Shared palette
    static final Color DLG_BG     = new Color(10, 12, 20);
    static final Color DLG_PANEL  = new Color(15, 18, 30);
    static final Color DLG_CARD   = new Color(20, 24, 38);
    static final Color DLG_BORDER = new Color(35, 45, 72);
    static final Color DLG_ACCENT = new Color(94, 206, 255);
    static final Color DLG_TXT    = new Color(210, 225, 248);
    static final Color DLG_DIM    = new Color(95, 115, 158);

    // Form fields
    private JTextField     hostField;
    private JTextField     userField;
    private JPasswordField passField;
    private JButton        loginBtn;
    private JLabel         statusLabel;

    // Result
    private String           loggedInUser;
    private String           loggedInRole;
    private ServerConnection connection;

    // OutlineBorder — shared rounded-rect border used by both classes

    /**
     * Anti-aliased rounded-rectangle border.
     * Referenced by TaskFlowClient as LoginDialog.OutlineBorder.
     */
    public static class OutlineBorder extends AbstractBorder {
        private final Color color;
        private final int   thickness;
        private final int   radius;

        public OutlineBorder(Color color, int thickness, int radius) {
            this.color = color; this.thickness = thickness; this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c)                    { int t=thickness+2; return new Insets(t,t,t,t); }
        @Override public Insets getBorderInsets(Component c, Insets i)          { int t=thickness+2; i.set(t,t,t,t); return i; }
    }

    // Constructor

    public LoginDialog(Frame owner) {
        super(owner, "TaskFlow — Login", true);
        buildUI();
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    // UI


    private void buildUI() {
        setBackground(DLG_BG);
        getContentPane().setBackground(DLG_BG);
        setLayout(new BorderLayout());

        // Left brand panel ─
        JPanel left = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                GradientPaint gp = new GradientPaint(0, 0, new Color(12,16,28), 0, getHeight(), new Color(18,24,44));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(DLG_BORDER);
                g2.fillRect(getWidth()-1, 0, 1, getHeight());
                g2.dispose();
            }
        };
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(220, 0));
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(44, 28, 36, 28));

        JLabel gem = new JLabel("◆");
        gem.setFont(new Font("Dialog", Font.PLAIN, 34));
        gem.setForeground(DLG_ACCENT);
        gem.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel brand = new JLabel("TaskFlow");
        brand.setFont(new Font("Serif", Font.BOLD, 26));
        brand.setForeground(DLG_TXT);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("Shared Task Manager");
        tagline.setFont(new Font("Dialog", Font.PLAIN, 12));
        tagline.setForeground(DLG_DIM);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(gem);
        left.add(Box.createVerticalStrut(10));
        left.add(brand);
        left.add(Box.createVerticalStrut(6));
        left.add(tagline);
        left.add(Box.createVerticalGlue());

        for (String f : new String[]{""}) {
            JLabel fl = new JLabel(f);
            fl.setFont(new Font("Dialog", Font.PLAIN, 11));
            fl.setForeground(new Color(60, 80, 120));
            fl.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(fl);
            left.add(Box.createVerticalStrut(5));
        }
        add(left, BorderLayout.WEST);

        // Right form panel
        JPanel right = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(DLG_CARD); g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(310, 420));
        right.setBorder(BorderFactory.createEmptyBorder(40, 32, 32, 32));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;

        gc.gridy=0; gc.insets=new Insets(0,0,2,0);
        JLabel heading = new JLabel("Sign In");
        heading.setFont(new Font("Serif", Font.BOLD, 20)); heading.setForeground(DLG_TXT);
        right.add(heading, gc);

        gc.gridy=1; gc.insets=new Insets(0,0,22,0);
        JLabel sub = new JLabel("Enter your credentials to continue");
        sub.setFont(new Font("Dialog", Font.PLAIN, 11)); sub.setForeground(DLG_DIM);
        right.add(sub, gc);

        gc.gridy=2; gc.insets=new Insets(0,0,4,0); right.add(fldLabel("Server"), gc);
        gc.gridy=3; gc.insets=new Insets(0,0,12,0); hostField = darkField("localhost:8080"); right.add(hostField, gc);

        gc.gridy=4; gc.insets=new Insets(0,0,4,0);  right.add(fldLabel("Username"), gc);
        gc.gridy=5; gc.insets=new Insets(0,0,12,0); userField = darkField(""); right.add(userField, gc);

        gc.gridy=6; gc.insets=new Insets(0,0,4,0);  right.add(fldLabel("Password"), gc);
        gc.gridy=7; gc.insets=new Insets(0,0,6,0);
        passField = new JPasswordField(); styleField(passField); right.add(passField, gc);

        gc.gridy=8; gc.insets=new Insets(0,0,14,0);
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(220,75,85));
        right.add(statusLabel, gc);

        gc.gridy=9; gc.insets=new Insets(0,0,0,0);
        loginBtn = new JButton("Sign In") {
            boolean hov;
            { setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
                setFont(new Font("Dialog",Font.BOLD,13)); setForeground(Color.WHITE);
                setCursor(new Cursor(Cursor.HAND_CURSOR)); setPreferredSize(new Dimension(0,40));
                addMouseListener(new MouseAdapter(){
                    public void mouseEntered(MouseEvent e){hov=true; repaint();}
                    public void mouseExited(MouseEvent e) {hov=false;repaint();}
                }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled()?(hov?new Color(30,90,200):new Color(37,99,235)):new Color(40,50,75));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.dispose(); super.paintComponent(g);
            }
        };
        right.add(loginBtn, gc);

        gc.gridy=10; gc.insets=new Insets(18,0,0,0);
        JPanel hint = new JPanel(new BorderLayout()){
            @Override protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(14,18,30)); g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.setColor(DLG_BORDER); g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8); g2.dispose();
            }
        };
        hint.setOpaque(false);
        hint.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));
        JLabel hl = new JLabel("Demo:  manager / pass123   ·   krisha / pass123");
        hl.setFont(new Font("Dialog",Font.PLAIN,10)); hl.setForeground(new Color(55,70,105));
        hint.add(hl); right.add(hint, gc);

        add(right, BorderLayout.CENTER);

        ActionListener go = e -> attemptLogin();
        loginBtn.addActionListener(go);
        passField.addActionListener(go);
        userField.addActionListener(go);
    }

    private JLabel fldLabel(String text) {
        JLabel l=new JLabel(text); l.setFont(new Font("Dialog",Font.BOLD,11)); l.setForeground(DLG_DIM); return l;
    }
    private JTextField darkField(String val) {
        JTextField f=new JTextField(val); styleField(f); return f;
    }
    private void styleField(JTextField f) {
        f.setFont(new Font("Dialog",Font.PLAIN,13)); f.setForeground(DLG_TXT);
        f.setBackground(new Color(14,18,30)); f.setCaretColor(DLG_ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
                new OutlineBorder(DLG_BORDER,1,6), BorderFactory.createEmptyBorder(7,10,7,10)));
        f.setPreferredSize(new Dimension(0,38));
        f.addFocusListener(new FocusAdapter(){
            public void focusGained(FocusEvent e){
                f.setBorder(BorderFactory.createCompoundBorder(
                        new OutlineBorder(DLG_ACCENT,1,6),BorderFactory.createEmptyBorder(7,10,7,10)));
            }
            public void focusLost(FocusEvent e){
                f.setBorder(BorderFactory.createCompoundBorder(
                        new OutlineBorder(DLG_BORDER,1,6),BorderFactory.createEmptyBorder(7,10,7,10)));
            }
        });
    }
    // Login logic — SwingWorker keeps EDT responsive (m7 slide 31)

    private void attemptLogin() {
        String hostPort = hostField.getText().trim();
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (username.isEmpty()||password.isEmpty()){statusLabel.setText("Username and password are required.");return;}

        loginBtn.setEnabled(false);
        statusLabel.setForeground(DLG_ACCENT);
        statusLabel.setText("Connecting…");

        SwingWorker<String,Void> worker = new SwingWorker<String,Void>(){
            @Override protected String doInBackground(){
                String host="localhost"; int port=8080;
                if (hostPort.contains(":")){
                    String[] hp=hostPort.split(":");
                    host=hp[0];
                    try{port=Integer.parseInt(hp[1]);}catch(NumberFormatException ignored){}
                }
                connection=new ServerConnection();
                if (!connection.connect(host,port)) return "ERROR|Cannot connect to server at "+hostPort;
                return connection.sendCommand("LOGIN|"+username+"|"+password);
            }
            @Override protected void done(){
                try{
                    String resp=get();
                    if (resp!=null&&resp.startsWith("OK|")){
                        String[] parts=resp.substring(3).split("\\|");
                        loggedInUser=parts[0];
                        loggedInRole=parts.length>1?parts[1]:"EMPLOYEE";
                        dispose();
                    } else {
                        statusLabel.setForeground(new Color(220,75,85));
                        statusLabel.setText(resp!=null&&resp.contains("|")?resp.split("\\|",2)[1]:"Login failed");
                    }
                } catch(Exception ex){
                    statusLabel.setForeground(new Color(220,75,85));
                    statusLabel.setText("Error: "+ex.getMessage());
                }
                loginBtn.setEnabled(true);
            }
        };
        worker.execute();
    }

    public String           getLoggedInUser() { return loggedInUser; }
    public String           getLoggedInRole() { return loggedInRole; }
    public ServerConnection getConnection()   { return connection; }
}
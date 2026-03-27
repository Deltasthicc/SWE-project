package bas.ui;

import bas.auth.SessionManager;
import bas.db.DatabaseManager;
import bas.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Login screen with JWT authentication.
 * Staff log in with User ID + Password → JWT token generated.
 * Customers click "Browse as Customer" — no login needed.
 */
public class LoginFrame extends JFrame {

    private JTextField     userIdField;
    private JPasswordField passField;
    private JLabel         statusLbl;

    // ── Colour palette ───────────────────────────────────────────────────────
    private static final Color BG       = new Color(248, 249, 253);
    private static final Color PRIMARY  = new Color(37, 99, 235);
    private static final Color SUCCESS  = new Color(22, 163, 74);
    private static final Color TEXT_DIM = new Color(100, 116, 139);

    public LoginFrame() {
        super("BAS — Bookshop Inventory & Sales Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        init();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void init() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(36, 56, 36, 56));

        // Header
        JLabel icon = lbl("\uD83D\uDCDA", 32, Font.PLAIN, PRIMARY);
        JLabel t1 = lbl("Bookshop Inventory &", 22, Font.BOLD, new Color(15, 23, 42));
        JLabel t2 = lbl("Sales Management System", 22, Font.BOLD, new Color(15, 23, 42));
        JLabel t3 = lbl("Shiv Nadar IoE — Group G01  |  Supabase + JWT", 11, Font.PLAIN, TEXT_DIM);
        icon.setAlignmentX(CENTER_ALIGNMENT);
        t1.setAlignmentX(CENTER_ALIGNMENT);
        t2.setAlignmentX(CENTER_ALIGNMENT);
        t3.setAlignmentX(CENTER_ALIGNMENT);
        root.add(icon);
        root.add(Box.createVerticalStrut(8));
        root.add(t1); root.add(t2);
        root.add(Box.createVerticalStrut(6));
        root.add(t3);
        root.add(Box.createVerticalStrut(28));

        // Divider
        root.add(divider());
        root.add(Box.createVerticalStrut(20));

        // Customer button
        JButton custBtn = new JButton("Browse as Customer  (No Login Required)");
        custBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        custBtn.setBackground(SUCCESS);
        custBtn.setForeground(Color.WHITE);
        custBtn.setFocusPainted(false);
        custBtn.setOpaque(true);
        custBtn.setBorderPainted(false);
        custBtn.setMaximumSize(new Dimension(440, 46));
        custBtn.setAlignmentX(CENTER_ALIGNMENT);
        custBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        custBtn.addActionListener(e -> openCustomer());
        root.add(custBtn);
        root.add(Box.createVerticalStrut(22));

        // OR separator
        JPanel orRow = new JPanel(new BorderLayout());
        orRow.setBackground(BG);
        orRow.setMaximumSize(new Dimension(440, 18));
        JLabel orLbl = new JLabel("  or staff login  ", SwingConstants.CENTER);
        orLbl.setForeground(TEXT_DIM);
        orLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        orRow.add(new JSeparator(), BorderLayout.WEST);
        orRow.add(orLbl, BorderLayout.CENTER);
        orRow.add(new JSeparator(), BorderLayout.EAST);
        root.add(orRow);
        root.add(Box.createVerticalStrut(18));

        // Login form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG);
        form.setMaximumSize(new Dimension(440, 120));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        JLabel uidLbl = new JLabel("User ID:");
        uidLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        form.add(uidLbl, g);
        g.gridx = 1; g.weightx = 1;
        userIdField = new JTextField(18);
        userIdField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        form.add(userIdField, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        JLabel pwLbl = new JLabel("Password:");
        pwLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        form.add(pwLbl, g);
        g.gridx = 1; g.weightx = 1;
        passField = new JPasswordField(18);
        passField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        form.add(passField, g);

        root.add(form);
        root.add(Box.createVerticalStrut(14));

        // Login button
        JButton loginBtn = new JButton("Login as Staff");
        loginBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        loginBtn.setBackground(PRIMARY);
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setOpaque(true);
        loginBtn.setBorderPainted(false);
        loginBtn.setMaximumSize(new Dimension(440, 42));
        loginBtn.setAlignmentX(CENTER_ALIGNMENT);
        loginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginBtn.addActionListener(e -> doLogin(loginBtn));
        root.add(loginBtn);

        // Status
        root.add(Box.createVerticalStrut(8));
        statusLbl = new JLabel(" ", SwingConstants.CENTER);
        statusLbl.setForeground(new Color(220, 38, 38));
        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLbl.setAlignmentX(CENTER_ALIGNMENT);
        root.add(statusLbl);

        // Credentials hint
        root.add(Box.createVerticalStrut(18));
        root.add(divider());
        root.add(Box.createVerticalStrut(10));
        JLabel hint = new JLabel(
            "<html><center><font color='#94a3b8' size='2'>" +
            "Owner: owner1 / owner123 &nbsp;|&nbsp; " +
            "Manager: manager1 / mgr123 &nbsp;|&nbsp; " +
            "Clerk: clerk1 / clerk123" +
            "</font></center></html>", SwingConstants.CENTER);
        hint.setAlignmentX(CENTER_ALIGNMENT);
        root.add(hint);

        // Enter triggers login
        passField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) doLogin(loginBtn);
            }
        });

        setContentPane(root);
    }

    // ── Login with JWT ────────────────────────────────────────────────────────

    private void doLogin(JButton btn) {
        String id  = userIdField.getText().trim();
        String pwd = new String(passField.getPassword());
        if (id.isEmpty() || pwd.isEmpty()) {
            statusLbl.setText("Please enter User ID and Password.");
            return;
        }
        btn.setEnabled(false);
        btn.setText("Authenticating...");

        SwingWorker<User, Void> w = new SwingWorker<>() {
            @Override protected User doInBackground() {
                return DatabaseManager.getInstance().authenticate(id, pwd);
            }
            @Override protected void done() {
                try {
                    User user = get();
                    if (user != null) {
                        // Generate JWT and establish session
                        SessionManager.getInstance().login(user);
                        DatabaseManager.getInstance().addLog(
                            user.getUserId(), "LOGIN",
                            user.getName() + " logged in. JWT issued.");
                        dispose();
                        new MainFrame(user);
                    } else {
                        statusLbl.setText("Invalid User ID or Password.");
                        passField.setText("");
                    }
                } catch (Exception ex) {
                    statusLbl.setText("Error: " + ex.getMessage());
                } finally {
                    btn.setEnabled(true);
                    btn.setText("Login as Staff");
                }
            }
        };
        w.execute();
    }

    private void openCustomer() {
        JFrame f = new JFrame("Customer Terminal — BAS Bookshop");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SUCCESS);
        header.setBorder(new EmptyBorder(10, 16, 10, 16));
        JLabel hl = new JLabel("BAS Bookshop — Customer Search & Request Terminal");
        hl.setForeground(Color.WHITE);
        hl.setFont(new Font("SansSerif", Font.BOLD, 15));
        header.add(hl, BorderLayout.WEST);
        f.add(header, BorderLayout.NORTH);

        f.add(new CustomerTerminalPanel(), BorderLayout.CENTER);
        f.setMinimumSize(new Dimension(920, 600));
        f.pack();
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JLabel lbl(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private JSeparator divider() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(440, 2));
        return sep;
    }
}

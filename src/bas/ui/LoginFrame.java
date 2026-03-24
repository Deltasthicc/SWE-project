package bas.ui;

import bas.db.DatabaseManager;
import bas.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Login screen.
 * Staff log in with User ID + Password.
 * Customers click "Browse as Customer" — no login needed.
 */
public class LoginFrame extends JFrame {

    private JTextField     userIdField;
    private JPasswordField passField;
    private JLabel         statusLbl;

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
        // ── Root ─────────────────────────────────────────────────────────────
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(new Color(245, 247, 252));
        root.setBorder(new EmptyBorder(30, 50, 30, 50));

        // ── Header ───────────────────────────────────────────────────────────
        JLabel t1 = lbl("📚  Bookshop Inventory &", 20, Font.BOLD, new Color(30, 60, 130));
        JLabel t2 = lbl("     Sales Management System", 20, Font.BOLD, new Color(30, 60, 130));
        JLabel t3 = lbl("Shiv Nadar Institution of Eminence — Group G01",
                         11, Font.ITALIC, Color.GRAY);
        t1.setAlignmentX(CENTER_ALIGNMENT);
        t2.setAlignmentX(CENTER_ALIGNMENT);
        t3.setAlignmentX(CENTER_ALIGNMENT);
        root.add(t1); root.add(t2);
        root.add(Box.createVerticalStrut(4));
        root.add(t3);
        root.add(Box.createVerticalStrut(24));

        // ── Divider ──────────────────────────────────────────────────────────
        root.add(divider());
        root.add(Box.createVerticalStrut(18));

        // ── Customer button (BIG — most prominent) ───────────────────────────
        JButton custBtn = new JButton("🛍   Browse as Customer  (No Login Required)");
        custBtn.setFont(new Font("SansSerif", Font.BOLD, 15));
        custBtn.setBackground(new Color(22, 160, 90));
        custBtn.setForeground(Color.WHITE);
        custBtn.setFocusPainted(false);
        custBtn.setOpaque(true);
        custBtn.setBorderPainted(false);
        custBtn.setMaximumSize(new Dimension(420, 48));
        custBtn.setAlignmentX(CENTER_ALIGNMENT);
        custBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        custBtn.addActionListener(e -> openCustomer());
        root.add(custBtn);
        root.add(Box.createVerticalStrut(20));

        // ── OR separator ─────────────────────────────────────────────────────
        JPanel orRow = new JPanel(new BorderLayout());
        orRow.setBackground(new Color(245, 247, 252));
        orRow.setMaximumSize(new Dimension(420, 18));
        JSeparator ls = new JSeparator(); JSeparator rs2 = new JSeparator();
        JLabel orLbl = new JLabel("  or staff login  ", SwingConstants.CENTER);
        orLbl.setForeground(Color.GRAY); orLbl.setFont(new Font("SansSerif",Font.PLAIN,11));
        orRow.add(ls, BorderLayout.WEST); orRow.add(orLbl, BorderLayout.CENTER);
        orRow.add(rs2, BorderLayout.EAST);
        root.add(orRow);
        root.add(Box.createVerticalStrut(16));

        // ── Login form ────────────────────────────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(245, 247, 252));
        form.setMaximumSize(new Dimension(420, 120));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill   = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        form.add(new JLabel("User ID:"), g);
        g.gridx = 1; g.weightx = 1;
        userIdField = new JTextField(18);
        userIdField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        form.add(userIdField, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        form.add(new JLabel("Password:"), g);
        g.gridx = 1; g.weightx = 1;
        passField = new JPasswordField(18);
        passField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        form.add(passField, g);

        root.add(form);
        root.add(Box.createVerticalStrut(12));

        // ── Login button ──────────────────────────────────────────────────────
        JButton loginBtn = new JButton("Login as Staff  →");
        loginBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        loginBtn.setBackground(new Color(30, 80, 200));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setOpaque(true);
        loginBtn.setBorderPainted(false);
        loginBtn.setMaximumSize(new Dimension(420, 40));
        loginBtn.setAlignmentX(CENTER_ALIGNMENT);
        loginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginBtn.addActionListener(e -> doLogin(loginBtn));
        root.add(loginBtn);

        // ── Status label ──────────────────────────────────────────────────────
        root.add(Box.createVerticalStrut(8));
        statusLbl = new JLabel(" ", SwingConstants.CENTER);
        statusLbl.setForeground(Color.RED);
        statusLbl.setAlignmentX(CENTER_ALIGNMENT);
        root.add(statusLbl);

        // ── Credentials hint ──────────────────────────────────────────────────
        root.add(Box.createVerticalStrut(16));
        root.add(divider());
        root.add(Box.createVerticalStrut(10));
        JLabel hint = new JLabel(
            "<html><center><font color='#888888' size='2'>" +
            "Owner: owner1 / owner123 &nbsp;|&nbsp; " +
            "Manager: manager1 / mgr123 &nbsp;|&nbsp; " +
            "Clerk: clerk1 / clerk123" +
            "</font></center></html>", SwingConstants.CENTER);
        hint.setAlignmentX(CENTER_ALIGNMENT);
        root.add(hint);

        // ── Enter key on password triggers login ──────────────────────────────
        passField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) doLogin(loginBtn);
            }
        });

        setContentPane(root);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doLogin(JButton btn) {
        String id  = userIdField.getText().trim();
        String pwd = new String(passField.getPassword());
        if (id.isEmpty() || pwd.isEmpty()) {
            statusLbl.setText("Please enter User ID and Password.");
            return;
        }
        btn.setEnabled(false);
        btn.setText("Logging in…");

        SwingWorker<User, Void> w = new SwingWorker<>() {
            @Override protected User doInBackground() {
                return DatabaseManager.getInstance().authenticate(id, pwd);
            }
            @Override protected void done() {
                try {
                    User user = get();
                    if (user != null) {
                        DatabaseManager.getInstance().addLog(
                            user.getUserId(), "LOGIN",
                            user.getName() + " logged in.");
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
                    btn.setText("Login as Staff  →");
                }
            }
        };
        w.execute();
    }

    private void openCustomer() {
        JFrame f = new JFrame("Customer Terminal — BAS Bookshop");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setLayout(new BorderLayout());

        // Header bar
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(22, 160, 90));
        header.setBorder(new EmptyBorder(8, 14, 8, 14));
        JLabel hl = new JLabel("📚  BAS Bookshop — Customer Search & Request Terminal");
        hl.setForeground(Color.WHITE);
        hl.setFont(new Font("SansSerif", Font.BOLD, 14));
        header.add(hl, BorderLayout.WEST);
        f.add(header, BorderLayout.NORTH);

        f.add(new CustomerTerminalPanel(), BorderLayout.CENTER);
        f.setMinimumSize(new Dimension(860, 580));
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
        sep.setMaximumSize(new Dimension(420, 2));
        return sep;
    }
}

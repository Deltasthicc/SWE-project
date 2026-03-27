package bas.ui;

import bas.auth.SessionManager;
import bas.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainFrame extends JFrame {

    private final User user;

    public MainFrame(User user) {
        super("BAS — " + user.getName() + "  [" + user.getRole() + "]");
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        build();
        setMinimumSize(new Dimension(1080, 720));
        setExtendedState(MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void build() {
        // ── Top bar ─────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(15, 23, 42));
        topBar.setBorder(new EmptyBorder(10, 18, 10, 18));

        JLabel appLbl = new JLabel("BAS — Bookshop Inventory & Sales Management");
        appLbl.setForeground(Color.WHITE);
        appLbl.setFont(new Font("SansSerif", Font.BOLD, 15));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightPanel.setOpaque(false);

        JLabel userLbl = new JLabel(user.getName() + "  |  " + user.getRole().name());
        userLbl.setForeground(new Color(148, 163, 184));
        userLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // JWT indicator
        JLabel jwtLbl = new JLabel("JWT Active");
        jwtLbl.setForeground(new Color(74, 222, 128));
        jwtLbl.setFont(new Font("SansSerif", Font.BOLD, 10));

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFocusPainted(false);
        logoutBtn.setBackground(new Color(220, 38, 38));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setOpaque(true);
        logoutBtn.setBorderPainted(false);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Logout and invalidate session?",
                    "Confirm Logout", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                SessionManager.getInstance().logout();
                dispose();
                new LoginFrame();
            }
        });

        rightPanel.add(jwtLbl);
        rightPanel.add(userLbl);
        rightPanel.add(logoutBtn);

        topBar.add(appLbl,     BorderLayout.WEST);
        topBar.add(rightPanel, BorderLayout.EAST);

        // ── Tabs ────────────────────────────────────────────────────────────
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));

        tabs.addTab("Book Search", new CustomerTerminalPanel());

        if (user.getRole() != User.Role.CUSTOMER) {
            tabs.addTab("POS Terminal", new POSTerminalPanel(user.getUserId()));
        }
        if (user.getRole() == User.Role.MANAGER || user.getRole() == User.Role.OWNER) {
            tabs.addTab("Inventory", new InventoryPanel(user.getUserId()));
        }
        if (user.getRole() == User.Role.OWNER) {
            tabs.addTab("Reports & Analytics", new OwnerPanel());
        }

        JPanel root = new JPanel(new BorderLayout());
        root.add(topBar, BorderLayout.NORTH);
        root.add(tabs,   BorderLayout.CENTER);
        setContentPane(root);
    }
}

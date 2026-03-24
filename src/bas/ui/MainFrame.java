package bas.ui;

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
        setMinimumSize(new Dimension(1050, 700));
        setExtendedState(MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void build() {
        // ── Top bar ──────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(25, 55, 120));
        topBar.setBorder(new EmptyBorder(8, 16, 8, 16));

        JLabel appLbl = new JLabel("📚  Bookshop Inventory & Sales Management System");
        appLbl.setForeground(Color.WHITE);
        appLbl.setFont(new Font("SansSerif", Font.BOLD, 15));

        JLabel userLbl = new JLabel(
            user.getName() + "  |  " + user.getRole().name());
        userLbl.setForeground(new Color(180, 210, 255));
        userLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFocusPainted(false);
        logoutBtn.setBackground(new Color(200, 50, 50));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setOpaque(true); logoutBtn.setBorderPainted(false);
        logoutBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Logout?", "Confirm",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                dispose();
                new LoginFrame();
            }
        });

        topBar.add(appLbl,   BorderLayout.WEST);
        topBar.add(userLbl,  BorderLayout.CENTER);
        topBar.add(logoutBtn,BorderLayout.EAST);

        // ── Tabs ─────────────────────────────────────────────────────────────
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));

        // All roles see the customer search
        tabs.addTab("🔍  Book Search", new CustomerTerminalPanel());

        // Clerk, Manager, Owner → POS
        if (user.getRole() != User.Role.CUSTOMER) {
            tabs.addTab("🧾  POS Terminal", new POSTerminalPanel(user.getUserId()));
        }

        // Manager, Owner → Inventory
        if (user.getRole() == User.Role.MANAGER || user.getRole() == User.Role.OWNER) {
            tabs.addTab("📦  Inventory", new InventoryPanel(user.getUserId()));
        }

        // Owner only → Reports + Admin
        if (user.getRole() == User.Role.OWNER) {
            tabs.addTab("📊  Reports & Analytics", new OwnerPanel());
        }

        JPanel root = new JPanel(new BorderLayout());
        root.add(topBar, BorderLayout.NORTH);
        root.add(tabs,   BorderLayout.CENTER);
        setContentPane(root);
    }
}

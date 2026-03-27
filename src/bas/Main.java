package bas;

import bas.db.DatabaseManager;
import bas.ui.LoginFrame;

import javax.swing.*;

/**
 * BAS Entry Point — Supabase + FlatLaf + JWT edition.
 *
 * Required JARs in lib/:
 *   postgresql-42.x.jar, javax.mail-1.6.2.jar, javax.activation-1.2.0.jar
 *   (optional) flatlaf-3.x.jar for modern UI
 *
 * Default logins (seeded on first run):
 *   Owner:   owner1   / owner123
 *   Manager: manager1 / mgr123
 *   Clerk:   clerk1 or clerk2 / clerk123
 *   Customer: click "Browse as Customer" — no login needed
 */
public class Main {

    public static void main(String[] args) {
        // Try FlatLaf first, then System L&F as fallback
        try {
            Class.forName("com.formdev.flatlaf.FlatLightLaf");
            com.formdev.flatlaf.FlatLightLaf.setup();
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2,2,2,2));
            System.out.println("[BAS] FlatLaf loaded.");
        } catch (ClassNotFoundException e) {
            System.out.println("[BAS] FlatLaf not found, trying System L&F.");
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ex) { System.err.println("[BAS] System L&F not available."); }
        }

        // Init DB on calling thread before EDT starts
        try {
            System.out.println("[BAS] Connecting to Supabase...");
            System.out.println("[BAS] Host: " + bas.config.AppConfig.DB_HOST + ":" + bas.config.AppConfig.DB_PORT);
            System.out.println("[BAS] User: " + bas.config.AppConfig.DB_USER);
            DatabaseManager.getInstance().initializeDatabase();
        } catch (RuntimeException e) {
            System.err.println("[BAS] FULL ERROR:");
            e.printStackTrace();
            // Extract root cause
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            JOptionPane.showMessageDialog(null,
                "<html><b>Fatal: Cannot connect to Supabase database.</b><br><br>" +
                "<b>Error:</b> " + root.getMessage() + "<br><br>" +
                "<b>Host:</b> " + bas.config.AppConfig.DB_HOST + ":" + bas.config.AppConfig.DB_PORT + "<br>" +
                "<b>User:</b> " + bas.config.AppConfig.DB_USER + "<br><br>" +
                "Check your internet connection and AppConfig.java credentials.<br>" +
                "Make sure <b>postgresql-42.x.jar</b> is in the <b>lib/</b> folder.<br>" +
                "<i>See terminal/console for full stack trace.</i>",
                "BAS — Startup Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Launch UI on Event Dispatch Thread
        SwingUtilities.invokeLater(LoginFrame::new);
    }
}

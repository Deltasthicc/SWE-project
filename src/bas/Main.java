package bas;

import bas.db.DatabaseManager;
import bas.ui.LoginFrame;

import javax.swing.*;

/**
 * BAS Entry Point.
 *
 * Run:  java -cp "lib/*;out" bas.Main      (Windows)
 *       java -cp "lib/*:out" bas.Main      (Linux/Mac)
 *
 * Default logins (seeded on first run):
 *   Owner   : owner1   / owner123
 *   Manager : manager1 / mgr123
 *   Clerk   : clerk1 or clerk2 / clerk123
 *   Customer: click "Browse as Customer" — no login needed
 */
public class Main {

    public static void main(String[] args) {
        // System look & feel for native Windows appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("[BAS] System L&F not available, using default.");
        }

        // Init DB on calling thread before EDT starts
        try {
            DatabaseManager.getInstance().initializeDatabase();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(null,
                "<html><b>Fatal: Cannot initialise database.</b><br><br>" +
                e.getMessage() + "<br><br>" +
                "Make sure <b>sqlite-jdbc-*.jar</b> is in the <b>lib/</b> folder.",
                "BAS — Startup Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Launch UI on Event Dispatch Thread
        SwingUtilities.invokeLater(LoginFrame::new);
    }
}

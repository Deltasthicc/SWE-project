package bas.service;

import java.util.List;
import java.util.Properties;

/**
 * SMTP email service (NFR-4: SSL/TLS).
 * Works without javax.mail.jar — calls become no-ops with a console warning.
 * Add javax.mail-1.6.2.jar to lib/ to enable actual sending.
 */
public class EmailService {
    private static String  host     = "smtp.gmail.com";
    private static int     port     = 587;
    private static String  email    = "";
    private static String  password = "";
    private static boolean ready    = false;

    public static void configure(String h, int p, String e, String pw) {
        host = h; port = p; email = e; password = pw;
        ready = e != null && !e.isBlank() && pw != null && !pw.isBlank();
    }

    public static boolean isConfigured() { return ready; }
    public static String  getHost()      { return host; }
    public static int     getPort()      { return port; }
    public static String  getEmail()     { return email; }

    public static boolean sendRestockAlert(String to, String title, String isbn) {
        return send(to,
            "Back in Stock: " + title,
            "Dear Customer,\n\nGreat news! The book you requested is available:\n\n" +
            "  Title: " + title + "\n  ISBN:  " + isbn + "\n\n" +
            "Visit us soon before it sells out!\n\nRegards,\nBAS Bookshop");
    }

    public static boolean send(String to, String subject, String body) {
        if (!ready) {
            System.err.println("[Email] Not configured. Would have sent to: " + to);
            return false;
        }
        try { Class.forName("javax.mail.Session"); }
        catch (ClassNotFoundException ex) {
            System.err.println("[Email] javax.mail.jar not found in lib/. Skipping email to: " + to);
            return false;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth",            "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host",             host);
            props.put("mail.smtp.port",             String.valueOf(port));
            props.put("mail.smtp.ssl.protocols",   "TLSv1.2 TLSv1.3");
            final String u = email, pw = password;
            javax.mail.Session session = javax.mail.Session.getInstance(props, null);
            javax.mail.internet.MimeMessage msg = new javax.mail.internet.MimeMessage(session);
            msg.setFrom(new javax.mail.internet.InternetAddress(u));
            msg.setRecipients(javax.mail.Message.RecipientType.TO,
                javax.mail.internet.InternetAddress.parse(to));
            msg.setSubject(subject);
            msg.setText(body);
            javax.mail.Transport t = session.getTransport("smtp");
            t.connect(host, port, u, pw);
            t.sendMessage(msg, msg.getAllRecipients());
            t.close();
            System.out.println("[Email] Sent to: " + to);
            return true;
        } catch (Exception e) {
            System.err.println("[Email] Failed: " + e.getMessage());
            return false;
        }
    }

    public static int sendBulkAlerts(List<String> emails, String title, String isbn) {
        int sent = 0;
        for (String e : emails) if (sendRestockAlert(e, title, isbn)) sent++;
        return sent;
    }
}

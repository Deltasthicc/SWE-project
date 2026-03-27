package bas.service;

import bas.config.AppConfig;

import java.util.List;
import java.util.Properties;

/**
 * SMTP email service (NFR-4: SSL/TLS).
 * Pre-configured with Gmail App Password credentials.
 * Requires javax.mail-1.6.2.jar and javax.activation-1.2.0.jar in lib/.
 */
public class EmailService {

    private static String  host     = AppConfig.SMTP_HOST;
    private static int     port     = AppConfig.SMTP_PORT;
    private static String  email    = AppConfig.SMTP_EMAIL;
    private static String  password = AppConfig.SMTP_PASSWORD;
    private static boolean ready    = true;  // pre-configured from AppConfig

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
            "Dear Customer,\n\nGreat news! The book you requested is now available:\n\n" +
            "  Title: " + title + "\n  ISBN:  " + isbn + "\n\n" +
            "Visit us soon before it sells out!\n\nWarm regards,\nBAS Bookshop\n" +
            "Shiv Nadar Institution of Eminence — Group G01");
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
            props.put("mail.smtp.starttls.enable",  "true");
            props.put("mail.smtp.host",             host);
            props.put("mail.smtp.port",             String.valueOf(port));
            props.put("mail.smtp.ssl.protocols",    "TLSv1.2 TLSv1.3");
            props.put("mail.smtp.ssl.trust",        host);

            // Use Authenticator for proper credential handling
            final String u = email, pw = password;
            javax.mail.Authenticator auth = new javax.mail.Authenticator() {
                @Override
                protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new javax.mail.PasswordAuthentication(u, pw);
                }
            };
            javax.mail.Session session = javax.mail.Session.getInstance(props, auth);

            javax.mail.internet.MimeMessage msg = new javax.mail.internet.MimeMessage(session);
            msg.setFrom(new javax.mail.internet.InternetAddress(u, "BAS Bookshop"));
            msg.setRecipients(javax.mail.Message.RecipientType.TO,
                javax.mail.internet.InternetAddress.parse(to));
            msg.setSubject(subject);
            msg.setText(body, "UTF-8");
            msg.setSentDate(new java.util.Date());

            javax.mail.Transport.send(msg);
            System.out.println("[Email] Sent to: " + to);
            return true;
        } catch (Exception e) {
            System.err.println("[Email] Failed to " + to + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static int sendBulkAlerts(List<String> emails, String title, String isbn) {
        int sent = 0;
        for (String e : emails) if (sendRestockAlert(e, title, isbn)) sent++;
        return sent;
    }
}

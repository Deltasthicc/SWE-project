package bas.service;

import bas.config.AppConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class EmailService {
    private static String  host = AppConfig.SMTP_HOST, email = AppConfig.SMTP_EMAIL, password = AppConfig.SMTP_PASSWORD;
    private static int     port = AppConfig.SMTP_PORT;
    // Initial state reflects whether the values baked into AppConfig at startup
    // are actually complete — host + port + email + password must all be present.
    private static boolean ready = computeReady(host, port, email, password);

    /**
     * Returns true only when every field looks usable:
     * <ul>
     *   <li>host: non-blank, no whitespace (basic hostname shape)</li>
     *   <li>port: in the legal TCP range 1..65535</li>
     *   <li>email: passes {@link bas.util.EmailValidator#isValid}</li>
     *   <li>password: non-blank and at least 8 characters
     *       (Gmail App Passwords are 16 chars; anything shorter is almost
     *       certainly invalid)</li>
     * </ul>
     * Any failure yields {@code false} — status label reads "Not Configured".
     */
    private static boolean computeReady(String h, int p, String e, String pw) {
        if (h  == null || h.isBlank() || h.contains(" ") || h.contains("\t")) return false;
        if (p  < 1 || p > 65535)                                               return false;
        if (e  == null || e.isBlank() || !bas.util.EmailValidator.isValid(e))  return false;
        if (pw == null || pw.isBlank() || pw.length() < 8)                     return false;
        return true;
    }

    public static void configure(String h, int p, String e, String pw) {
        host = h; port = p; email = e; password = pw;
        ready = computeReady(h, p, e, pw);
    }
    public static boolean isConfigured() { return ready; }
    public static String  getHost()      { return host; }
    public static int     getPort()      { return port; }
    public static String  getEmail()     { return email; }

    public static boolean sendRestockAlert(String to, String title, String isbn) {
        return send(to, "Back in Stock: " + title,
            "Dear Customer,\n\nGreat news! The book you requested is now available:\n\n" +
            "  Title: " + title + "\n  ISBN:  " + isbn + "\n\n" +
            "Visit us soon before it sells out!\n\nWarm regards,\nBAS Bookshop\n" +
            "Shiv Nadar Institution of Eminence — Group G01");
    }

    public static boolean send(String to, String subject, String body) {
        if (!ready) { System.err.println("[Email] Not configured. Would send to: " + to); return false; }
        try { Class.forName("javax.mail.Session"); }
        catch (ClassNotFoundException ex) { System.err.println("[Email] javax.mail.jar missing."); return false; }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth","true"); props.put("mail.smtp.starttls.enable","true");
            props.put("mail.smtp.host",host); props.put("mail.smtp.port",String.valueOf(port));
            props.put("mail.smtp.ssl.protocols","TLSv1.2 TLSv1.3"); props.put("mail.smtp.ssl.trust",host);
            final String u = email, pw = password;
            javax.mail.Authenticator auth = new javax.mail.Authenticator() {
                @Override protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new javax.mail.PasswordAuthentication(u, pw);
                }
            };
            javax.mail.Session session = javax.mail.Session.getInstance(props, auth);
            javax.mail.internet.MimeMessage msg = new javax.mail.internet.MimeMessage(session);
            msg.setFrom(new javax.mail.internet.InternetAddress(u, "BAS Bookshop"));
            msg.setRecipients(javax.mail.Message.RecipientType.TO, javax.mail.internet.InternetAddress.parse(to));
            msg.setSubject(subject); msg.setText(body, "UTF-8"); msg.setSentDate(new java.util.Date());
            javax.mail.Transport.send(msg);
            System.out.println("[Email] Sent to: " + to); return true;
        } catch (Exception e) { System.err.println("[Email] Failed to " + to + ": " + e.getMessage()); return false; }
    }

    /** Returns the LIST of emails that were successfully sent (for selective marking). */
    public static List<String> sendBulkAlertsSelective(List<String> emails, String title, String isbn) {
        List<String> sent = new ArrayList<>();
        for (String e : emails) if (sendRestockAlert(e, title, isbn)) sent.add(e);
        return sent;
    }

    /** Legacy method — returns count only. */
    public static int sendBulkAlerts(List<String> emails, String title, String isbn) {
        return sendBulkAlertsSelective(emails, title, isbn).size();
    }
}

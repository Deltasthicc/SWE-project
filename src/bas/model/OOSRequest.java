package bas.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OOSRequest {
    public enum Status { PENDING, NOTIFIED, CLOSED }
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String requestId, isbn, title, author, publisher, email;
    private LocalDateTime timestamp;
    private Status status;

    /** New request — timestamp = now */
    public OOSRequest(String requestId, String isbn, String title,
                      String author, String publisher, String email) {
        this.requestId = requestId; this.isbn = isbn; this.title = title;
        this.author = author; this.publisher = publisher;
        this.email = (email == null || email.isBlank()) ? null : email.trim();
        this.timestamp = LocalDateTime.now();
        this.status = Status.PENDING;
    }

    /** Load from DB — preserves the stored timestamp instead of replacing with now() */
    public OOSRequest(String requestId, String isbn, String title,
                      String author, String publisher, String email, String storedTimestamp) {
        this(requestId, isbn, title, author, publisher, email);
        if (storedTimestamp != null && !storedTimestamp.isBlank()) {
            try { this.timestamp = LocalDateTime.parse(storedTimestamp, FMT); }
            catch (Exception e) { /* keep now() if unparseable */ }
        }
    }

    public String getFormattedTimestamp() { return timestamp.format(FMT); }

    public String getRequestId() { return requestId; }
    public String getIsbn()      { return isbn; }
    public String getTitle()     { return title; }
    public String getAuthor()    { return author; }
    public String getPublisher() { return publisher; }
    public String getEmail()     { return email; }
    public Status getStatus()    { return status; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setRequestId(String v)       { requestId = v; }
    public void setIsbn(String v)            { isbn = v; }
    public void setTitle(String v)           { title = v; }
    public void setAuthor(String v)          { author = v; }
    public void setPublisher(String v)       { publisher = v; }
    public void setEmail(String v)           { email = v; }
    public void setStatus(Status v)          { status = v; }
    public void setTimestamp(LocalDateTime v) { timestamp = v; }
}

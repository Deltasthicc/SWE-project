package bas.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OOSRequest {
    public enum Status { PENDING, NOTIFIED, CLOSED }

    private String        requestId;
    private String        isbn;
    private String        title;
    private String        author;
    private String        publisher;
    private String        email;
    private LocalDateTime timestamp;
    private Status        status;

    public OOSRequest(String requestId, String isbn, String title,
                      String author, String publisher, String email) {
        this.requestId = requestId; this.isbn = isbn; this.title = title;
        this.author = author; this.publisher = publisher;
        this.email = (email == null || email.isBlank()) ? null : email.trim();
        this.timestamp = LocalDateTime.now();
        this.status = Status.PENDING;
    }

    public String getFormattedTimestamp() {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String   getRequestId() { return requestId; }
    public String   getIsbn()      { return isbn; }
    public String   getTitle()     { return title; }
    public String   getAuthor()    { return author; }
    public String   getPublisher() { return publisher; }
    public String   getEmail()     { return email; }
    public Status   getStatus()    { return status; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setRequestId(String v)       { requestId = v; }
    public void setIsbn(String v)            { isbn = v; }
    public void setTitle(String v)           { title = v; }
    public void setAuthor(String v)          { author = v; }
    public void setPublisher(String v)       { publisher = v; }
    public void setEmail(String v)           { email = v; }
    public void setStatus(Status v)          { status = v; }
    public void setTimestamp(LocalDateTime v){ timestamp = v; }
}

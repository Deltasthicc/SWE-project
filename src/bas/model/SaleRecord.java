package bas.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SaleRecord {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String        saleId;
    private LocalDateTime timestamp;
    private String        clerkId;
    private List<LineItem> items = new ArrayList<>();
    private double        totalAmount;

    public SaleRecord(String saleId, String clerkId) {
        this.saleId = saleId; this.clerkId = clerkId;
        this.timestamp = LocalDateTime.now();
    }

    public void addItem(LineItem item) {
        for (LineItem e : items) {
            if (e.getIsbn().equals(item.getIsbn())) {
                e.setQuantity(e.getQuantity() + item.getQuantity());
                recalc(); return;
            }
        }
        items.add(item); recalc();
    }

    public void removeItem(String isbn) {
        items.removeIf(i -> i.getIsbn().equals(isbn)); recalc();
    }

    private void recalc() {
        totalAmount = items.stream().mapToDouble(LineItem::getSubtotal).sum();
    }

    public String getFormattedTimestamp() { return timestamp.format(FMT); }

    public String        getSaleId()      { return saleId; }
    public LocalDateTime getTimestamp()   { return timestamp; }
    public String        getClerkId()     { return clerkId; }
    public List<LineItem> getItems()      { return items; }
    public double        getTotalAmount() { return totalAmount; }

    public void setSaleId(String v)       { saleId = v; }
    public void setTimestamp(LocalDateTime v) { timestamp = v; }
    public void setClerkId(String v)      { clerkId = v; }
    public void setTotalAmount(double v)  { totalAmount = v; }
}

package bas.model;

public class LineItem {
    private String isbn;
    private String title;
    private int    quantity;
    private double unitPrice;

    public LineItem(String isbn, String title, int quantity, double unitPrice) {
        this.isbn = isbn; this.title = title;
        this.quantity = quantity; this.unitPrice = unitPrice;
    }

    public double getSubtotal() { return quantity * unitPrice; }

    public String getIsbn()     { return isbn; }
    public String getTitle()    { return title; }
    public int    getQuantity() { return quantity; }
    public double getUnitPrice(){ return unitPrice; }
    public void   setQuantity(int q) { quantity = q; }
}

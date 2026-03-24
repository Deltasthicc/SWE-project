package bas.model;

public class Book {
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String publisherAddress;
    private double unitPrice;
    private String rackLocation;
    private int    stockCount;
    private int    restockThreshold;
    private int    requestCount;
    private double weeklySales;
    private int    procurementLeadTimeWeeks;

    public Book() {}

    public Book(String isbn, String title, String author, String publisher,
                String publisherAddress, double unitPrice, String rackLocation,
                int stockCount, int restockThreshold, int requestCount,
                double weeklySales, int procurementLeadTimeWeeks) {
        this.isbn = isbn; this.title = title; this.author = author;
        this.publisher = publisher; this.publisherAddress = publisherAddress;
        this.unitPrice = unitPrice; this.rackLocation = rackLocation;
        this.stockCount = stockCount; this.restockThreshold = restockThreshold;
        this.requestCount = requestCount; this.weeklySales = weeklySales;
        this.procurementLeadTimeWeeks = procurementLeadTimeWeeks;
    }

    /** FR-4.3: Required Qty = max(0, ceil(weeklySales * leadTime) - stock) */
    public int getRequiredProcurementQty() {
        return Math.max(0, (int) Math.ceil(weeklySales * procurementLeadTimeWeeks) - stockCount);
    }

    public boolean needsRestock() { return stockCount <= restockThreshold; }
    public boolean isInStock()    { return stockCount > 0; }

    // Getters
    public String getIsbn()                    { return isbn; }
    public String getTitle()                   { return title; }
    public String getAuthor()                  { return author; }
    public String getPublisher()               { return publisher; }
    public String getPublisherAddress()        { return publisherAddress; }
    public double getUnitPrice()               { return unitPrice; }
    public String getRackLocation()            { return rackLocation; }
    public int    getStockCount()              { return stockCount; }
    public int    getRestockThreshold()        { return restockThreshold; }
    public int    getRequestCount()            { return requestCount; }
    public double getWeeklySales()             { return weeklySales; }
    public int    getProcurementLeadTimeWeeks(){ return procurementLeadTimeWeeks; }

    // Setters
    public void setIsbn(String v)                     { isbn = v; }
    public void setTitle(String v)                    { title = v; }
    public void setAuthor(String v)                   { author = v; }
    public void setPublisher(String v)                { publisher = v; }
    public void setPublisherAddress(String v)         { publisherAddress = v; }
    public void setUnitPrice(double v)                { unitPrice = v; }
    public void setRackLocation(String v)             { rackLocation = v; }
    public void setStockCount(int v)                  { stockCount = v; }
    public void setRestockThreshold(int v)            { restockThreshold = v; }
    public void setRequestCount(int v)                { requestCount = v; }
    public void setWeeklySales(double v)              { weeklySales = v; }
    public void setProcurementLeadTimeWeeks(int v)    { procurementLeadTimeWeeks = v; }
}

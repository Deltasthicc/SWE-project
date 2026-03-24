package bas.db;

import bas.model.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Singleton DatabaseManager — all SQLite CRUD.
 * Seeded with 20 books across genres + demo sales + OOS requests
 * so every role's demo works out-of-the-box.
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:bas.db";
    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static DatabaseManager instance;
    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private Connection connect() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) {
            throw new SQLException(
                "sqlite-jdbc.jar missing from lib/. Download from " +
                "https://github.com/xerial/sqlite-jdbc/releases and place in lib/", e);
        }
        return DriverManager.getConnection(DB_URL);
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    public void initializeDatabase() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA journal_mode = WAL");

            s.execute("CREATE TABLE IF NOT EXISTS users (" +
                "user_id TEXT PRIMARY KEY, name TEXT NOT NULL, " +
                "password_hash TEXT NOT NULL, role TEXT NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS books (" +
                "isbn TEXT PRIMARY KEY, title TEXT NOT NULL, " +
                "author TEXT NOT NULL, publisher TEXT NOT NULL, " +
                "publisher_address TEXT, unit_price REAL NOT NULL DEFAULT 0, " +
                "rack_location TEXT, stock_count INTEGER NOT NULL DEFAULT 0, " +
                "restock_threshold INTEGER NOT NULL DEFAULT 5, " +
                "request_count INTEGER NOT NULL DEFAULT 0, " +
                "weekly_sales REAL NOT NULL DEFAULT 0, " +
                "procurement_lead_time_wks INTEGER NOT NULL DEFAULT 2)");

            s.execute("CREATE TABLE IF NOT EXISTS sales (" +
                "sale_id TEXT PRIMARY KEY, timestamp TEXT NOT NULL, " +
                "clerk_id TEXT NOT NULL, total_amount REAL NOT NULL DEFAULT 0)");

            s.execute("CREATE TABLE IF NOT EXISTS sale_items (" +
                "item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sale_id TEXT NOT NULL REFERENCES sales(sale_id) ON DELETE CASCADE, " +
                "isbn TEXT NOT NULL, title TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, unit_price REAL NOT NULL, subtotal REAL NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS oos_requests (" +
                "request_id TEXT PRIMARY KEY, isbn TEXT NOT NULL, " +
                "title TEXT NOT NULL, author TEXT NOT NULL, publisher TEXT NOT NULL, " +
                "email TEXT, timestamp TEXT NOT NULL, " +
                "status TEXT NOT NULL DEFAULT 'PENDING')");

            s.execute("CREATE TABLE IF NOT EXISTS app_logs (" +
                "log_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "timestamp TEXT NOT NULL, event_type TEXT NOT NULL, " +
                "actor TEXT, message TEXT)");

            seedIfEmpty(c);
            System.out.println("[BAS] Database initialised.");
        } catch (SQLException e) {
            throw new RuntimeException("DB init failed: " + e.getMessage(), e);
        }
    }

    // ── Seed ──────────────────────────────────────────────────────────────────

    private void seedIfEmpty(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM users")) {
            if (p.executeQuery().getInt(1) > 0) return;
        }

        // Users
        insertUser(c, "owner1",   "Ravi Sharma (Owner)",   hash("owner123"), "OWNER");
        insertUser(c, "manager1", "Priya Mehta (Manager)", hash("mgr123"),   "MANAGER");
        insertUser(c, "clerk1",   "Arjun Gupta (Clerk)",   hash("clerk123"), "CLERK");
        insertUser(c, "clerk2",   "Sneha Rao (Clerk)",     hash("clerk123"), "CLERK");

        // 20 books: isbn, title, author, publisher, pub_address, price, rack,
        //           stock, threshold, req_count, weekly_sales, lead_weeks
        Object[][] books = {
            // ─ Normal stock ─
            {"9780451524935","1984","George Orwell",
             "Signet Classic","123 Publisher Row, New York NY 10001",
             199.0,"A-01",22,5,0, 4.5,2},
            {"9780743273565","The Great Gatsby","F. Scott Fitzgerald",
             "Scribner","456 Book Street, New York NY 10002",
             249.0,"A-02",15,5,0, 2.5,2},
            {"9780061096525","To Kill a Mockingbird","Harper Lee",
             "HarperCollins","195 Broadway, New York NY 10007",
             299.0,"A-03",18,5,0, 3.0,2},
            {"9780316769174","The Catcher in the Rye","J.D. Salinger",
             "Little Brown & Co.","100 Novel Road, Boston MA 02101",
             220.0,"A-04",10,5,0, 2.0,3},
            {"9780062315007","The Alchemist","Paulo Coelho",
             "HarperOne","195 Broadway, New York NY 10007",
             310.0,"B-01",25,5,0, 5.0,2},
            {"9780439023481","The Hunger Games","Suzanne Collins",
             "Scholastic Press","557 Broadway, New York NY 10012",
             350.0,"B-02",14,5,0, 6.0,1},
            {"9780525559474","The Fault in Our Stars","John Green",
             "Dutton Books","375 Hudson St, New York NY 10014",
             275.0,"B-03",12,5,0, 3.5,2},
            {"9781501156700","It Ends with Us","Colleen Hoover",
             "Atria Books","1230 Avenue of Americas, NY 10020",
             380.0,"B-04",8,5,0, 7.0,1},
            {"9780385737951","The Maze Runner","James Dashner",
             "Delacorte Press","1745 Broadway, New York NY 10019",
             330.0,"C-01",20,5,0, 4.0,2},
            {"9780316346627","Verity","Colleen Hoover",
             "Grand Central Publishing","1290 Ave of Americas, NY 10104",
             395.0,"C-02",6,5,0, 5.5,1},
            {"9781250301697","Where the Crawdads Sing","Delia Owens",
             "G.P. Putnam's Sons","375 Hudson St, New York NY 10014",
             420.0,"C-03",11,5,0, 4.5,2},
            {"9780385545990","The Midnight Library","Matt Haig",
             "Viking","375 Hudson St, New York NY 10014",
             360.0,"C-04",9,5,0, 3.5,2},
            {"9781982173593","Atomic Habits","James Clear",
             "Avery Publishing","1745 Broadway, New York NY 10019",
             499.0,"D-01",30,8,0, 8.0,1},
            {"9780735224292","Little Fires Everywhere","Celeste Ng",
             "Penguin Press","375 Hudson St, New York NY 10014",
             340.0,"D-02",7,5,0, 3.0,2},
            {"9781250178619","A Court of Thorns and Roses","Sarah J. Maas",
             "Bloomsbury","50 Bedford Square, London WC1B 3DP",
             395.0,"D-03",13,5,0, 5.0,2},
            // ─ Low stock (yellow) ─
            {"9780679783268","Crime and Punishment","Fyodor Dostoevsky",
             "Vintage Books","201 E 50th St, New York NY 10022",
             280.0,"E-01",4,5,0, 2.0,3},
            {"9780140449136","Anna Karenina","Leo Tolstoy",
             "Penguin Classics","80 Strand, London WC2R 0RL",
             320.0,"E-02",3,5,0, 1.5,4},
            {"9780062409850","The Book Thief","Markus Zusak",
             "Picador","20 New Wharf Rd, London N1 9RR",
             299.0,"E-03",2,5,0, 2.5,3},
            // ─ Out of stock (red) ─
            {"9781501197277","It","Stephen King",
             "Scribner","1230 Ave of Americas, NY 10020",
             450.0,"F-01",0,5,5, 6.0,2},
            {"9780307474278","The Girl with the Dragon Tattoo","Stieg Larsson",
             "Vintage Crime","201 E 50th St, New York NY 10022",
             399.0,"F-02",0,5,3, 4.0,2},
        };

        String bSql = "INSERT INTO books " +
            "(isbn,title,author,publisher,publisher_address,unit_price," +
            "rack_location,stock_count,restock_threshold,request_count," +
            "weekly_sales,procurement_lead_time_wks) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(bSql)) {
            for (Object[] b : books) {
                ps.setString(1,(String)b[0]); ps.setString(2,(String)b[1]);
                ps.setString(3,(String)b[2]); ps.setString(4,(String)b[3]);
                ps.setString(5,(String)b[4]); ps.setDouble(6,(Double)b[5]);
                ps.setString(7,(String)b[6]); ps.setInt(8,(Integer)b[7]);
                ps.setInt(9,(Integer)b[8]);   ps.setInt(10,(Integer)b[9]);
                ps.setDouble(11,(Double)b[10]); ps.setInt(12,(Integer)b[11]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Demo sales spread across last 30 days
        // Format: sale_id, date_offset, clerk, isbn, title, qty, unit_price
        Object[][] sales = {
            {"SALE-D001", 0,  "clerk1","9780451524935","1984",                         2, 199.0},
            {"SALE-D002", 0,  "clerk1","9780062315007","The Alchemist",                1, 310.0},
            {"SALE-D003", 0,  "clerk2","9781982173593","Atomic Habits",                3, 499.0},
            {"SALE-D004", 0,  "clerk1","9780439023481","The Hunger Games",             2, 350.0},
            {"SALE-D005", 0,  "clerk2","9781501156700","It Ends with Us",              1, 380.0},
            {"SALE-D006",-1,  "clerk1","9780743273565","The Great Gatsby",             2, 249.0},
            {"SALE-D007",-1,  "clerk2","9780062315007","The Alchemist",                1, 310.0},
            {"SALE-D008",-2,  "clerk1","9781982173593","Atomic Habits",                2, 499.0},
            {"SALE-D009",-3,  "clerk2","9780316346627","Verity",                       1, 395.0},
            {"SALE-D010",-5,  "clerk1","9781250301697","Where the Crawdads Sing",      2, 420.0},
            {"SALE-D011",-7,  "clerk2","9780385545990","The Midnight Library",         1, 360.0},
            {"SALE-D012",-7,  "clerk1","9780061096525","To Kill a Mockingbird",        3, 299.0},
            {"SALE-D013",-10, "clerk2","9781982173593","Atomic Habits",                4, 499.0},
            {"SALE-D014",-14, "clerk1","9780439023481","The Hunger Games",             1, 350.0},
            {"SALE-D015",-21, "clerk2","9780525559474","The Fault in Our Stars",       2, 275.0},
            {"SALE-D016",-3,  "clerk1","9781250178619","A Court of Thorns and Roses",  2, 395.0},
            {"SALE-D017",-6,  "clerk2","9780385737951","The Maze Runner",              1, 330.0},
            {"SALE-D018",-9,  "clerk1","9780316769174","The Catcher in the Rye",       2, 220.0},
            {"SALE-D019",-12, "clerk2","9780735224292","Little Fires Everywhere",      1, 340.0},
            {"SALE-D020",-15, "clerk1","9780743273565","The Great Gatsby",             3, 249.0},
        };

        String sSql = "INSERT INTO sales (sale_id,timestamp,clerk_id,total_amount) VALUES (?,?,?,?)";
        String iSql = "INSERT INTO sale_items (sale_id,isbn,title,quantity,unit_price,subtotal) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement sp = c.prepareStatement(sSql);
             PreparedStatement ip = c.prepareStatement(iSql)) {
            for (Object[] row : sales) {
                int    qty   = (Integer) row[5];
                double price = (Double)  row[6];
                double total = qty * price;
                String date  = LocalDate.now().plusDays((Integer)row[1]).toString()
                             + " 10:00:00";

                sp.setString(1,(String)row[0]); sp.setString(2,date);
                sp.setString(3,(String)row[2]); sp.setDouble(4,total); sp.addBatch();

                ip.setString(1,(String)row[0]); ip.setString(2,(String)row[3]);
                ip.setString(3,(String)row[4]); ip.setInt(4,qty);
                ip.setDouble(5,price);           ip.setDouble(6,total); ip.addBatch();
            }
            sp.executeBatch(); ip.executeBatch();
        }

        // OOS requests for the 2 out-of-stock books
        String oSql = "INSERT INTO oos_requests " +
            "(request_id,isbn,title,author,publisher,email,timestamp,status) VALUES (?,?,?,?,?,?,?,?)";
        Object[][] oos = {
            {"REQ-D001","9781501197277","It","Stephen King","Scribner",
             "rahul.k@email.com",   day(-3)+" 10:00:00","PENDING"},
            {"REQ-D002","9781501197277","It","Stephen King","Scribner",
             "priya.s@gmail.com",   day(-2)+" 14:00:00","PENDING"},
            {"REQ-D003","9781501197277","It","Stephen King","Scribner",
             null,                  day(-1)+" 09:00:00","PENDING"},
            {"REQ-D004","9780307474278","The Girl with the Dragon Tattoo","Stieg Larsson","Vintage Crime",
             "amit.sharma@yahoo.com",day(-5)+" 11:00:00","PENDING"},
            {"REQ-D005","9780307474278","The Girl with the Dragon Tattoo","Stieg Larsson","Vintage Crime",
             null,                   day(-4)+" 15:00:00","PENDING"},
        };
        try (PreparedStatement ps = c.prepareStatement(oSql)) {
            for (Object[] o : oos) {
                ps.setString(1,(String)o[0]); ps.setString(2,(String)o[1]);
                ps.setString(3,(String)o[2]); ps.setString(4,(String)o[3]);
                ps.setString(5,(String)o[4]); ps.setString(6,(String)o[5]);
                ps.setString(7,(String)o[6]); ps.setString(8,(String)o[7]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        addLog(c, "SYSTEM","INIT","Database seeded with 20 books, 20 demo sales, 5 OOS requests.");
        System.out.println("[BAS] Demo data seeded.");
    }

    private String day(int offset) {
        return LocalDate.now().plusDays(offset).toString();
    }

    private void insertUser(Connection c, String id, String name, String h, String role)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO users (user_id,name,password_hash,role) VALUES (?,?,?,?)")) {
            ps.setString(1,id); ps.setString(2,name); ps.setString(3,h); ps.setString(4,role);
            ps.execute();
        }
    }

    // ── Password hashing ──────────────────────────────────────────────────────

    public static String hash(String password) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] bytes = d.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public User authenticate(String userId, String plainPwd) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM users WHERE user_id=? AND password_hash=?")) {
            ps.setString(1, userId); ps.setString(2, hash(plainPwd));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new User(
                rs.getString("user_id"), rs.getString("name"),
                rs.getString("password_hash"),
                User.Role.valueOf(rs.getString("role")));
        } catch (SQLException e) { err("authenticate", e); }
        return null;
    }

    // ── Book queries ──────────────────────────────────────────────────────────

    public List<Book> searchByTitle(String q)  { return search("title",  q); }
    public List<Book> searchByAuthor(String q) { return search("author", q); }

    private List<Book> search(String col, String q) {
        List<Book> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM books WHERE " + col + " LIKE ? ORDER BY title")) {
            ps.setString(1, "%" + q + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { err("search", e); }
        return list;
    }

    public Book getByISBN(String isbn) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM books WHERE isbn=?")) {
            ps.setString(1, isbn);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);
        } catch (SQLException e) { err("getByISBN", e); }
        return null;
    }

    public List<Book> getAllBooks() {
        List<Book> list = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT * FROM books ORDER BY title");
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { err("getAllBooks", e); }
        return list;
    }

    public List<Book> getBooksNeedingRestock() {
        List<Book> list = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT * FROM books WHERE stock_count<=restock_threshold ORDER BY stock_count");
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { err("getBooksNeedingRestock", e); }
        return list;
    }

    public boolean addBook(Book b) {
        String sql = "INSERT INTO books " +
            "(isbn,title,author,publisher,publisher_address,unit_price," +
            "rack_location,stock_count,restock_threshold,request_count," +
            "weekly_sales,procurement_lead_time_wks) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,b.getIsbn()); ps.setString(2,b.getTitle());
            ps.setString(3,b.getAuthor()); ps.setString(4,b.getPublisher());
            ps.setString(5,b.getPublisherAddress()); ps.setDouble(6,b.getUnitPrice());
            ps.setString(7,b.getRackLocation()); ps.setInt(8,b.getStockCount());
            ps.setInt(9,b.getRestockThreshold()); ps.setInt(10,0);
            ps.setDouble(11,b.getWeeklySales()); ps.setInt(12,b.getProcurementLeadTimeWeeks());
            ps.execute();
            addLog("MANAGER","INVENTORY","Book added: "+b.getIsbn()+" — "+b.getTitle());
            return true;
        } catch (SQLException e) { err("addBook", e); return false; }
    }

    public boolean updateBook(Book b) {
        String sql = "UPDATE books SET title=?,author=?,publisher=?,publisher_address=?," +
            "unit_price=?,rack_location=?,stock_count=?,restock_threshold=?," +
            "weekly_sales=?,procurement_lead_time_wks=? WHERE isbn=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,b.getTitle()); ps.setString(2,b.getAuthor());
            ps.setString(3,b.getPublisher()); ps.setString(4,b.getPublisherAddress());
            ps.setDouble(5,b.getUnitPrice()); ps.setString(6,b.getRackLocation());
            ps.setInt(7,b.getStockCount()); ps.setInt(8,b.getRestockThreshold());
            ps.setDouble(9,b.getWeeklySales()); ps.setInt(10,b.getProcurementLeadTimeWeeks());
            ps.setString(11,b.getIsbn()); ps.executeUpdate();
            addLog("MANAGER","INVENTORY","Book updated: "+b.getIsbn());
            return true;
        } catch (SQLException e) { err("updateBook", e); return false; }
    }

    // ── Atomic sale (FR-3.5, Atomic Transaction Rule) ─────────────────────────

    public boolean saveSaleAtomically(SaleRecord sale) {
        Connection c = null;
        try {
            c = connect(); c.setAutoCommit(false);

            for (LineItem it : sale.getItems()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT stock_count FROM books WHERE isbn=?")) {
                    ps.setString(1, it.getIsbn());
                    int avail = ps.executeQuery().getInt(1);
                    if (avail < it.getQuantity()) { c.rollback(); return false; }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sales (sale_id,timestamp,clerk_id,total_amount) VALUES (?,?,?,?)")) {
                ps.setString(1,sale.getSaleId()); ps.setString(2,sale.getFormattedTimestamp());
                ps.setString(3,sale.getClerkId()); ps.setDouble(4,sale.getTotalAmount());
                ps.execute();
            }

            try (PreparedStatement ip = c.prepareStatement(
                        "INSERT INTO sale_items " +
                        "(sale_id,isbn,title,quantity,unit_price,subtotal) VALUES (?,?,?,?,?,?)");
                 PreparedStatement sp = c.prepareStatement(
                        "UPDATE books SET stock_count=stock_count-? WHERE isbn=?")) {
                for (LineItem it : sale.getItems()) {
                    ip.setString(1,sale.getSaleId()); ip.setString(2,it.getIsbn());
                    ip.setString(3,it.getTitle()); ip.setInt(4,it.getQuantity());
                    ip.setDouble(5,it.getUnitPrice()); ip.setDouble(6,it.getSubtotal());
                    ip.addBatch();
                    sp.setInt(1,it.getQuantity()); sp.setString(2,it.getIsbn()); sp.addBatch();
                }
                ip.executeBatch(); sp.executeBatch();
            }

            addLog(c, sale.getClerkId(), "SALE",
                "Completed: "+sale.getSaleId()+" | Total INR "+
                String.format("%.2f",sale.getTotalAmount())+
                " | "+sale.getItems().size()+" item(s)");
            c.commit(); return true;

        } catch (SQLException e) {
            err("saveSaleAtomically", e);
            try { if (c != null) c.rollback(); } catch (SQLException ignored) {}
            return false;
        } finally {
            try { if (c != null) { c.setAutoCommit(true); c.close(); } }
            catch (SQLException ignored) {}
        }
    }

    // ── Stock arrival (FR-4.1) ────────────────────────────────────────────────

    public boolean addStock(String isbn, int qty, String actor) {
        if (qty <= 0) return false;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                "UPDATE books SET stock_count=stock_count+? WHERE isbn=?")) {
            ps.setInt(1, qty); ps.setString(2, isbn);
            if (ps.executeUpdate() > 0) {
                addLog(actor,"INVENTORY","Stock +"+qty+" for ISBN "+isbn);
                return true;
            }
        } catch (SQLException e) { err("addStock", e); }
        return false;
    }

    // ── Sales reporting (FR-4.2) ──────────────────────────────────────────────

    public List<Object[]> getSalesStats(String from, String to) {
        List<Object[]> list = new ArrayList<>();
        String sql =
            "SELECT b.isbn,b.title,b.author,b.publisher," +
            "COALESCE(SUM(si.quantity),0) copies,COALESCE(SUM(si.subtotal),0) revenue " +
            "FROM books b LEFT JOIN sale_items si ON si.isbn=b.isbn " +
            "LEFT JOIN sales s ON s.sale_id=si.sale_id " +
            "AND s.timestamp BETWEEN ? AND ? " +
            "GROUP BY b.isbn ORDER BY revenue DESC";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from+" 00:00:00"); ps.setString(2, to+" 23:59:59");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Object[]{
                rs.getString("isbn"), rs.getString("title"),
                rs.getString("author"), rs.getString("publisher"),
                rs.getInt("copies"), rs.getDouble("revenue")});
        } catch (SQLException e) { err("getSalesStats", e); }
        return list;
    }

    public double getTotalRevenue(String from, String to) {
        String sql = "SELECT COALESCE(SUM(total_amount),0) FROM sales " +
                     "WHERE timestamp BETWEEN ? AND ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from+" 00:00:00"); ps.setString(2, to+" 23:59:59");
            return ps.executeQuery().getDouble(1);
        } catch (SQLException e) { err("getTotalRevenue", e); return 0; }
    }

    // ── OOS requests (F2) ─────────────────────────────────────────────────────

    public boolean addOOSRequest(OOSRequest req) {
        try (Connection c = connect()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE books SET request_count=request_count+1 WHERE isbn=?")) {
                ps.setString(1, req.getIsbn()); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO oos_requests " +
                    "(request_id,isbn,title,author,publisher,email,timestamp,status)" +
                    " VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1,req.getRequestId()); ps.setString(2,req.getIsbn());
                ps.setString(3,req.getTitle()); ps.setString(4,req.getAuthor());
                ps.setString(5,req.getPublisher()); ps.setString(6,req.getEmail());
                ps.setString(7,req.getFormattedTimestamp());
                ps.setString(8,req.getStatus().name()); ps.execute();
            }
            addLog(c,"CUSTOMER","OOS","Request: "+req.getTitle()+" ("+req.getIsbn()+")");
            return true;
        } catch (SQLException e) { err("addOOSRequest", e); return false; }
    }

    public List<OOSRequest> getAllOOSRequests() {
        List<OOSRequest> list = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT * FROM oos_requests ORDER BY timestamp DESC");
            while (rs.next()) {
                OOSRequest req = new OOSRequest(
                    rs.getString("request_id"), rs.getString("isbn"),
                    rs.getString("title"), rs.getString("author"),
                    rs.getString("publisher"), rs.getString("email"));
                req.setStatus(OOSRequest.Status.valueOf(rs.getString("status")));
                list.add(req);
            }
        } catch (SQLException e) { err("getAllOOSRequests", e); }
        return list;
    }

    public List<String> getPendingEmails(String isbn) {
        List<String> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                "SELECT email FROM oos_requests " +
                "WHERE isbn=? AND status='PENDING' AND email IS NOT NULL")) {
            ps.setString(1, isbn);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) { err("getPendingEmails", e); }
        return list;
    }

    public void markNotified(String isbn) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                "UPDATE oos_requests SET status='NOTIFIED' " +
                "WHERE isbn=? AND status='PENDING'")) {
            ps.setString(1, isbn); ps.executeUpdate();
        } catch (SQLException e) { err("markNotified", e); }
    }

    // ── Logs ──────────────────────────────────────────────────────────────────

    public void addLog(String actor, String type, String msg) {
        try (Connection c = connect()) { addLog(c, actor, type, msg); }
        catch (SQLException e) { System.err.println("[LOG] " + e.getMessage()); }
    }

    private void addLog(Connection c, String actor, String type, String msg)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO app_logs (timestamp,event_type,actor,message) VALUES (?,?,?,?)")) {
            ps.setString(1, LocalDateTime.now().format(DT));
            ps.setString(2, type); ps.setString(3, actor); ps.setString(4, msg);
            ps.execute();
        }
    }

    public List<Object[]> getRecentLogs(int limit) {
        List<Object[]> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM app_logs ORDER BY log_id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Object[]{
                rs.getInt("log_id"), rs.getString("timestamp"),
                rs.getString("event_type"), rs.getString("actor"),
                rs.getString("message")});
        } catch (SQLException e) { err("getRecentLogs", e); }
        return list;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private Book map(ResultSet rs) throws SQLException {
        return new Book(
            rs.getString("isbn"), rs.getString("title"), rs.getString("author"),
            rs.getString("publisher"), rs.getString("publisher_address"),
            rs.getDouble("unit_price"), rs.getString("rack_location"),
            rs.getInt("stock_count"), rs.getInt("restock_threshold"),
            rs.getInt("request_count"), rs.getDouble("weekly_sales"),
            rs.getInt("procurement_lead_time_wks"));
    }

    private void err(String m, SQLException e) {
        System.err.println("[DB ERR] " + m + ": " + e.getMessage());
    }

    public static String newSaleId() {
        return "SALE-" + UUID.randomUUID().toString().substring(0,8).toUpperCase();
    }
    public static String newReqId() {
        return "REQ-" + UUID.randomUUID().toString().substring(0,8).toUpperCase();
    }
}

package bas.db;

import bas.config.AppConfig;
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
 * Singleton DatabaseManager — PostgreSQL / Supabase backend.
 * Uses ConnectionPool for fast connection reuse and BookCache for read performance.
 */
public class DatabaseManager {

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static DatabaseManager instance;
    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    // ── Connection (pooled) ───────────────────────────────────────────────────

    private Connection borrow() throws SQLException {
        return ConnectionPool.getInstance().borrow();
    }

    private void release(Connection c) {
        ConnectionPool.getInstance().release(c);
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    public void initializeDatabase() {
        Connection c = null;
        try {
            c = borrow();
            Statement s = c.createStatement();

            s.execute("CREATE TABLE IF NOT EXISTS users (" +
                "user_id TEXT PRIMARY KEY, name TEXT NOT NULL, " +
                "password_hash TEXT NOT NULL, role TEXT NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS books (" +
                "isbn TEXT PRIMARY KEY, title TEXT NOT NULL, " +
                "author TEXT NOT NULL, publisher TEXT NOT NULL, " +
                "publisher_address TEXT, unit_price DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "rack_location TEXT, stock_count INTEGER NOT NULL DEFAULT 0, " +
                "restock_threshold INTEGER NOT NULL DEFAULT 5, " +
                "request_count INTEGER NOT NULL DEFAULT 0, " +
                "weekly_sales DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "procurement_lead_time_wks INTEGER NOT NULL DEFAULT 2)");

            s.execute("CREATE TABLE IF NOT EXISTS sales (" +
                "sale_id TEXT PRIMARY KEY, timestamp TEXT NOT NULL, " +
                "clerk_id TEXT NOT NULL, total_amount DOUBLE PRECISION NOT NULL DEFAULT 0, " +
                "receipt_content TEXT)");
            try { s.execute("ALTER TABLE sales ADD COLUMN IF NOT EXISTS receipt_content TEXT"); }
            catch (SQLException ignored) {}

            s.execute("CREATE TABLE IF NOT EXISTS sale_items (" +
                "item_id SERIAL PRIMARY KEY, " +
                "sale_id TEXT NOT NULL REFERENCES sales(sale_id) ON DELETE CASCADE, " +
                "isbn TEXT NOT NULL, title TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, unit_price DOUBLE PRECISION NOT NULL, " +
                "subtotal DOUBLE PRECISION NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS oos_requests (" +
                "request_id TEXT PRIMARY KEY, isbn TEXT NOT NULL, " +
                "title TEXT NOT NULL, author TEXT NOT NULL, publisher TEXT NOT NULL, " +
                "email TEXT, timestamp TEXT NOT NULL, " +
                "status TEXT NOT NULL DEFAULT 'PENDING')");

            s.execute("CREATE TABLE IF NOT EXISTS app_logs (" +
                "log_id SERIAL PRIMARY KEY, " +
                "timestamp TEXT NOT NULL, event_type TEXT NOT NULL, " +
                "actor TEXT, message TEXT)");

            s.close();
            seedIfEmpty(c);

            // Pre-warm the book cache
            BookCache.getInstance().refresh();

            System.out.println("[BAS] PostgreSQL database initialised (Supabase).");
        } catch (SQLException e) {
            throw new RuntimeException("DB init failed: " + e.getMessage(), e);
        } finally {
            release(c);
        }
    }

    // ── Seed ──────────────────────────────────────────────────────────────────

    private void seedIfEmpty(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM books")) {
            ResultSet rs = p.executeQuery(); rs.next();
            if (rs.getInt(1) > 0) return;
        }

        insertUser(c, "owner1",   "Ravi Sharma (Owner)",   hash("owner123"), "OWNER");
        insertUser(c, "manager1", "Priya Mehta (Manager)", hash("mgr123"),   "MANAGER");
        insertUser(c, "clerk1",   "Arjun Gupta (Clerk)",   hash("clerk123"), "CLERK");
        insertUser(c, "clerk2",   "Sneha Rao (Clerk)",     hash("clerk123"), "CLERK");

        Object[][] books = {
            {"9780451524935","1984","George Orwell","Signet Classic","123 Publisher Row, New York NY 10001",199.0,"A-01",22,5,0,4.5,2},
            {"9780743273565","The Great Gatsby","F. Scott Fitzgerald","Scribner","456 Book Street, New York NY 10002",249.0,"A-02",15,5,0,2.5,2},
            {"9780061096525","To Kill a Mockingbird","Harper Lee","HarperCollins","195 Broadway, New York NY 10007",299.0,"A-03",18,5,0,3.0,2},
            {"9780316769174","The Catcher in the Rye","J.D. Salinger","Little Brown & Co.","100 Novel Road, Boston MA 02101",220.0,"A-04",10,5,0,2.0,3},
            {"9780679783268","Crime and Punishment","Fyodor Dostoevsky","Vintage Books","201 E 50th St, New York NY 10022",280.0,"A-05",4,5,0,2.0,3},
            {"9780140449136","Anna Karenina","Leo Tolstoy","Penguin Classics","80 Strand, London WC2R 0RL",320.0,"A-06",3,5,0,1.5,4},
            {"9780141439518","Pride and Prejudice","Jane Austen","Penguin Classics","80 Strand, London WC2R 0RL",180.0,"A-07",20,5,0,3.0,2},
            {"9780142437247","Moby-Dick","Herman Melville","Penguin Classics","80 Strand, London WC2R 0RL",250.0,"A-08",8,5,0,1.0,4},
            {"9780199535569","Hamlet","William Shakespeare","Oxford University Press","Great Clarendon St, Oxford OX2 6DP",150.0,"A-09",25,5,0,2.5,2},
            {"9780679720201","One Hundred Years of Solitude","Gabriel Garcia Marquez","Harper Perennial","195 Broadway, New York NY 10007",310.0,"A-10",12,5,0,2.0,3},
            {"9780062315007","The Alchemist","Paulo Coelho","HarperOne","195 Broadway, New York NY 10007",310.0,"B-01",25,5,0,5.0,2},
            {"9781501156700","It Ends with Us","Colleen Hoover","Atria Books","1230 Avenue of Americas, NY 10020",380.0,"B-02",8,5,0,7.0,1},
            {"9780316346627","Verity","Colleen Hoover","Grand Central Publishing","1290 Ave of Americas, NY 10104",395.0,"B-03",6,5,0,5.5,1},
            {"9781250301697","Where the Crawdads Sing","Delia Owens","G.P. Putnam's Sons","375 Hudson St, New York NY 10014",420.0,"B-04",11,5,0,4.5,2},
            {"9780385545990","The Midnight Library","Matt Haig","Viking","375 Hudson St, New York NY 10014",360.0,"B-05",9,5,0,3.5,2},
            {"9780735224292","Little Fires Everywhere","Celeste Ng","Penguin Press","375 Hudson St, New York NY 10014",340.0,"B-06",7,5,0,3.0,2},
            {"9780525559474","The Fault in Our Stars","John Green","Dutton Books","375 Hudson St, New York NY 10014",275.0,"B-07",12,5,0,3.5,2},
            {"9780062409850","The Book Thief","Markus Zusak","Picador","20 New Wharf Rd, London N1 9RR",299.0,"B-08",2,5,0,2.5,3},
            {"9780399590528","Normal People","Sally Rooney","Hogarth Press","1745 Broadway, New York NY 10019",350.0,"B-09",14,5,0,4.0,2},
            {"9780735211292","Educated","Tara Westover","Random House","1745 Broadway, New York NY 10019",399.0,"B-10",16,5,0,5.0,1},
            {"9780439023481","The Hunger Games","Suzanne Collins","Scholastic Press","557 Broadway, New York NY 10012",350.0,"C-01",14,5,0,6.0,1},
            {"9780385737951","The Maze Runner","James Dashner","Delacorte Press","1745 Broadway, New York NY 10019",330.0,"C-02",20,5,0,4.0,2},
            {"9781250178619","A Court of Thorns and Roses","Sarah J. Maas","Bloomsbury","50 Bedford Square, London WC1B 3DP",395.0,"C-03",13,5,0,5.0,2},
            {"9780547928227","The Hobbit","J.R.R. Tolkien","Mariner Books","125 High St, Boston MA 02110",299.0,"C-04",18,5,0,3.5,2},
            {"9780553573404","A Game of Thrones","George R.R. Martin","Bantam Books","1745 Broadway, New York NY 10019",425.0,"C-05",10,5,0,4.0,2},
            {"9780441013593","Dune","Frank Herbert","Ace Books","375 Hudson St, New York NY 10014",350.0,"C-06",15,5,0,3.5,2},
            {"9780060850524","Brave New World","Aldous Huxley","Harper Perennial","195 Broadway, New York NY 10007",210.0,"C-07",22,5,0,2.5,2},
            {"9780553382563","Foundation","Isaac Asimov","Bantam Spectra","1745 Broadway, New York NY 10019",280.0,"C-08",9,5,0,2.0,3},
            {"9780345391803","The Hitchhiker's Guide to the Galaxy","Douglas Adams","Del Rey","1745 Broadway, New York NY 10019",250.0,"C-09",17,5,0,3.0,2},
            {"9780765382030","The Name of the Wind","Patrick Rothfuss","DAW Books","375 Hudson St, New York NY 10014",380.0,"C-10",11,5,0,4.5,2},
            {"9781501197277","It","Stephen King","Scribner","1230 Ave of Americas, NY 10020",450.0,"D-01",0,5,5,6.0,2},
            {"9780307474278","The Girl with the Dragon Tattoo","Stieg Larsson","Vintage Crime","201 E 50th St, New York NY 10022",399.0,"D-02",0,5,3,4.0,2},
            {"9780307588371","Gone Girl","Gillian Flynn","Crown Publishing","1745 Broadway, New York NY 10019",360.0,"D-03",10,5,0,5.0,1},
            {"9781501161933","The Silent Patient","Alex Michaelides","Celadon Books","120 Broadway, New York NY 10271",380.0,"D-04",13,5,0,6.0,1},
            {"9780593321201","The Maid","Nita Prose","Ballantine Books","1745 Broadway, New York NY 10019",340.0,"D-05",8,5,0,3.5,2},
            {"9781524763138","Where the Forest Meets the Stars","Glendy Vanderah","Lake Union Publishing","PO Box 400818, Las Vegas NV 89140",299.0,"D-06",16,5,0,2.5,2},
            {"9780062834843","The Woman in the Window","A.J. Finn","William Morrow","195 Broadway, New York NY 10007",370.0,"D-07",5,5,0,4.0,2},
            {"9781982173593","Atomic Habits","James Clear","Avery Publishing","1745 Broadway, New York NY 10019",499.0,"E-01",30,8,0,8.0,1},
            {"9780743269513","The 7 Habits of Highly Effective People","Stephen R. Covey","Free Press","1230 Ave of Americas, NY 10020",399.0,"E-02",20,5,0,4.0,2},
            {"9780671027032","How to Win Friends and Influence People","Dale Carnegie","Pocket Books","1230 Ave of Americas, NY 10020",299.0,"E-03",25,5,0,3.5,2},
            {"9780307465351","The Lean Startup","Eric Ries","Currency","1745 Broadway, New York NY 10019",450.0,"E-04",12,5,0,3.0,2},
            {"9780062457714","The Subtle Art of Not Giving a F*ck","Mark Manson","Harper","195 Broadway, New York NY 10007",380.0,"E-05",18,5,0,6.0,1},
            {"9780141988511","Sapiens","Yuval Noah Harari","Vintage","20 Vauxhall Bridge Rd, London SW1V 2SA",499.0,"E-06",15,8,0,5.5,2},
            {"9780062316097","Thinking, Fast and Slow","Daniel Kahneman","Farrar Straus Giroux","175 Varick St, New York NY 10014",450.0,"E-07",10,5,0,3.0,3},
            {"9780307352149","Rich Dad Poor Dad","Robert T. Kiyosaki","Plata Publishing","4330 N Civic Center Plaza, Scottsdale AZ 85251",350.0,"E-08",22,5,0,5.0,1},
            {"9780143028628","The White Tiger","Aravind Adiga","Free Press","1230 Ave of Americas, NY 10020",299.0,"F-01",14,5,0,3.0,2},
            {"9780143442257","Train to Pakistan","Khushwant Singh","Penguin India","7th Floor, Infinity Tower C, DLF Phase 2, Gurgaon",180.0,"F-02",20,5,0,2.0,3},
            {"9788129135728","The God of Small Things","Arundhati Roy","Penguin Random House India","7th Floor, Infinity Tower C, Gurgaon",250.0,"F-03",16,5,0,2.5,2},
            {"9780143065074","A Suitable Boy","Vikram Seth","Penguin Books","80 Strand, London WC2R 0RL",550.0,"F-04",6,5,0,1.5,4},
            {"9789350291863","Five Point Someone","Chetan Bhagat","Rupa Publications","7/16 Ansari Rd, Daryaganj, New Delhi 110002",199.0,"F-05",28,5,0,6.0,1},
            {"9780143422808","The Palace of Illusions","Chitra Banerjee Divakaruni","Picador India","20 New Wharf Rd, London N1 9RR",280.0,"F-06",10,5,0,2.0,3},
            {"9780571368341","Shantaram","Gregory David Roberts","Abacus","Carmelite House, 50 Victoria Embankment, London",420.0,"F-07",7,5,0,3.0,2},
            {"9788172234980","The Immortals of Meluha","Amish Tripathi","Westland Publications","No. 38/10 Kanoor Rd, Bengaluru 560078",299.0,"F-08",19,5,0,5.0,1},
            {"9780553380163","A Brief History of Time","Stephen Hawking","Bantam Books","1745 Broadway, New York NY 10019",380.0,"G-01",13,5,0,3.0,2},
            {"9780544272996","The Elegant Universe","Brian Greene","W.W. Norton","500 Fifth Ave, New York NY 10110",420.0,"G-02",6,5,0,1.5,3},
            {"9780393355628","Astrophysics for People in a Hurry","Neil deGrasse Tyson","W.W. Norton","500 Fifth Ave, New York NY 10110",299.0,"G-03",20,5,0,4.0,1},
            {"9780544002678","The Selfish Gene","Richard Dawkins","Oxford University Press","Great Clarendon St, Oxford OX2 6DP",350.0,"G-04",8,5,0,2.0,3},
            {"9780590353427","Harry Potter and the Sorcerer's Stone","J.K. Rowling","Scholastic","557 Broadway, New York NY 10012",499.0,"H-01",35,10,0,10.0,1},
            {"9780439064873","Harry Potter and the Chamber of Secrets","J.K. Rowling","Scholastic","557 Broadway, New York NY 10012",499.0,"H-02",30,10,0,8.0,1},
            {"9780439136365","Harry Potter and the Prisoner of Azkaban","J.K. Rowling","Scholastic","557 Broadway, New York NY 10012",499.0,"H-03",28,10,0,7.5,1},
            {"9780316015844","The Lightning Thief","Rick Riordan","Disney Hyperion","77 W 66th St, New York NY 10023",330.0,"H-04",22,5,0,5.0,1},
            {"9780399501487","The Very Hungry Caterpillar","Eric Carle","World of Eric Carle","375 Hudson St, New York NY 10014",150.0,"H-05",40,10,0,6.0,1},
            {"9780064410939","Charlotte's Web","E.B. White","HarperCollins","195 Broadway, New York NY 10007",199.0,"H-06",18,5,0,3.0,2},
            {"9780140447231","Meditations","Marcus Aurelius","Penguin Classics","80 Strand, London WC2R 0RL",199.0,"G-05",15,5,0,3.5,2},
            {"9780060555665","Man's Search for Meaning","Viktor E. Frankl","Beacon Press","24 Farnsworth St, Boston MA 02210",250.0,"G-06",20,5,0,4.0,2},
            {"9780140268867","The Art of War","Sun Tzu","Penguin Classics","80 Strand, London WC2R 0RL",180.0,"G-07",25,5,0,3.0,2},
            {"9780393347777","The Republic","Plato","W.W. Norton","500 Fifth Ave, New York NY 10110",220.0,"G-08",10,5,0,1.5,3},
            {"9780553588484","A Thousand Splendid Suns","Khaled Hosseini","Riverhead Books","375 Hudson St, New York NY 10014",340.0,"F-09",3,5,0,3.5,2},
            {"9780385333481","The Kite Runner","Khaled Hosseini","Riverhead Books","375 Hudson St, New York NY 10014",320.0,"F-10",2,5,0,4.0,2},
            {"9780062797155","Circe","Madeline Miller","Little Brown & Co.","100 Novel Road, Boston MA 02101",380.0,"D-08",0,5,4,5.0,2},
            {"9780525559481","The Song of Achilles","Madeline Miller","Ecco","195 Broadway, New York NY 10007",350.0,"D-09",0,5,6,4.5,2},
        };

        String bSql = "INSERT INTO books (isbn,title,author,publisher,publisher_address,unit_price,rack_location,stock_count,restock_threshold,request_count,weekly_sales,procurement_lead_time_wks) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(bSql)) {
            for (Object[] b : books) {
                ps.setString(1,(String)b[0]); ps.setString(2,(String)b[1]);
                ps.setString(3,(String)b[2]); ps.setString(4,(String)b[3]);
                ps.setString(5,(String)b[4]); ps.setDouble(6,(Double)b[5]);
                ps.setString(7,(String)b[6]); ps.setInt(8,(Integer)b[7]);
                ps.setInt(9,(Integer)b[8]); ps.setInt(10,(Integer)b[9]);
                ps.setDouble(11,(Double)b[10]); ps.setInt(12,(Integer)b[11]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        Object[][] sales = {
            {"SALE-D001",0,"clerk1","9780451524935","1984",2,199.0},
            {"SALE-D002",0,"clerk1","9780062315007","The Alchemist",1,310.0},
            {"SALE-D003",0,"clerk2","9781982173593","Atomic Habits",3,499.0},
            {"SALE-D004",0,"clerk1","9780439023481","The Hunger Games",2,350.0},
            {"SALE-D005",0,"clerk2","9781501156700","It Ends with Us",1,380.0},
            {"SALE-D006",-1,"clerk1","9780743273565","The Great Gatsby",2,249.0},
            {"SALE-D007",-1,"clerk2","9780062315007","The Alchemist",1,310.0},
            {"SALE-D008",-2,"clerk1","9781982173593","Atomic Habits",2,499.0},
            {"SALE-D009",-3,"clerk2","9780316346627","Verity",1,395.0},
            {"SALE-D010",-5,"clerk1","9781250301697","Where the Crawdads Sing",2,420.0},
            {"SALE-D011",-7,"clerk2","9780385545990","The Midnight Library",1,360.0},
            {"SALE-D012",-7,"clerk1","9780061096525","To Kill a Mockingbird",3,299.0},
            {"SALE-D013",-10,"clerk2","9781982173593","Atomic Habits",4,499.0},
            {"SALE-D014",-14,"clerk1","9780439023481","The Hunger Games",1,350.0},
            {"SALE-D015",-21,"clerk2","9780525559474","The Fault in Our Stars",2,275.0},
            {"SALE-D016",-3,"clerk1","9781250178619","A Court of Thorns and Roses",2,395.0},
            {"SALE-D017",-6,"clerk2","9780385737951","The Maze Runner",1,330.0},
            {"SALE-D018",-9,"clerk1","9780316769174","The Catcher in the Rye",2,220.0},
            {"SALE-D019",-12,"clerk2","9780735224292","Little Fires Everywhere",1,340.0},
            {"SALE-D020",-15,"clerk1","9780743273565","The Great Gatsby",3,249.0},
            {"SALE-D021",0,"clerk1","9780590353427","Harry Potter and the Sorcerer's Stone",3,499.0},
            {"SALE-D022",0,"clerk2","9780141988511","Sapiens",2,499.0},
            {"SALE-D023",-1,"clerk1","9780547928227","The Hobbit",1,299.0},
            {"SALE-D024",-1,"clerk2","9789350291863","Five Point Someone",4,199.0},
            {"SALE-D025",-2,"clerk1","9780553380163","A Brief History of Time",1,380.0},
            {"SALE-D026",-2,"clerk2","9780307588371","Gone Girl",2,360.0},
            {"SALE-D027",-3,"clerk1","9780141439518","Pride and Prejudice",2,180.0},
            {"SALE-D028",-4,"clerk2","9780393355628","Astrophysics for People in a Hurry",3,299.0},
            {"SALE-D029",-4,"clerk1","9780062457714","The Subtle Art of Not Giving a F*ck",2,380.0},
            {"SALE-D030",-5,"clerk2","9780143028628","The White Tiger",1,299.0},
            {"SALE-D031",-6,"clerk1","9780553573404","A Game of Thrones",1,425.0},
            {"SALE-D032",-8,"clerk2","9781501161933","The Silent Patient",2,380.0},
            {"SALE-D033",-10,"clerk1","9780060555665","Man's Search for Meaning",3,250.0},
            {"SALE-D034",-11,"clerk2","9780399590528","Normal People",1,350.0},
            {"SALE-D035",-13,"clerk1","9780307352149","Rich Dad Poor Dad",2,350.0},
            {"SALE-D036",-16,"clerk2","9780439064873","Harry Potter and the Chamber of Secrets",2,499.0},
            {"SALE-D037",-18,"clerk1","9788172234980","The Immortals of Meluha",3,299.0},
            {"SALE-D038",-20,"clerk2","9780316015844","The Lightning Thief",2,330.0},
            {"SALE-D039",-22,"clerk1","9780345391803","The Hitchhiker's Guide to the Galaxy",1,250.0},
            {"SALE-D040",-25,"clerk2","9780735211292","Educated",1,399.0},
        };
        // Use SINGLE connection for all sale inserts
        String sSql = "INSERT INTO sales (sale_id,timestamp,clerk_id,total_amount,receipt_content) VALUES (?,?,?,?,?)";
        String iSql = "INSERT INTO sale_items (sale_id,isbn,title,quantity,unit_price,subtotal) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement sp = c.prepareStatement(sSql);
             PreparedStatement ip = c.prepareStatement(iSql)) {
            for (Object[] row : sales) {
                int qty = (Integer)row[5]; double price = (Double)row[6]; double total = qty * price;
                String date = LocalDate.now().plusDays((Integer)row[1]).toString() + " 10:00:00";
                String t = ((String)row[4]).length() > 22 ? ((String)row[4]).substring(0,19)+"..." : (String)row[4];
                String receipt = "========================================\n   BOOKSHOP INVENTORY & SALES SYSTEM   \n========================================\n" +
                    "Sale ID : "+(String)row[0]+"\nDate    : "+date+"\nClerk   : "+(String)row[2]+"\n" +
                    "----------------------------------------\n"+String.format("%-22s %4d %9.2f",t,qty,total)+"\n"+
                    "----------------------------------------\n"+String.format("%-26s %9.2f","TOTAL (INR):",total)+"\n========================================";
                sp.setString(1,(String)row[0]); sp.setString(2,date);
                sp.setString(3,(String)row[2]); sp.setDouble(4,total); sp.setString(5,receipt); sp.addBatch();
                ip.setString(1,(String)row[0]); ip.setString(2,(String)row[3]);
                ip.setString(3,(String)row[4]); ip.setInt(4,qty);
                ip.setDouble(5,price); ip.setDouble(6,total); ip.addBatch();
            }
            sp.executeBatch(); ip.executeBatch();
        }

        String oSql = "INSERT INTO oos_requests (request_id,isbn,title,author,publisher,email,timestamp,status) VALUES (?,?,?,?,?,?,?,?)";
        Object[][] oos = {
            {"REQ-D001","9781501197277","It","Stephen King","Scribner","rahul.k@email.com",day(-3)+" 10:00:00","PENDING"},
            {"REQ-D002","9781501197277","It","Stephen King","Scribner","priya.s@gmail.com",day(-2)+" 14:00:00","PENDING"},
            {"REQ-D003","9781501197277","It","Stephen King","Scribner",null,day(-1)+" 09:00:00","PENDING"},
            {"REQ-D004","9780307474278","The Girl with the Dragon Tattoo","Stieg Larsson","Vintage Crime","amit.sharma@yahoo.com",day(-5)+" 11:00:00","PENDING"},
            {"REQ-D005","9780307474278","The Girl with the Dragon Tattoo","Stieg Larsson","Vintage Crime",null,day(-4)+" 15:00:00","PENDING"},
            {"REQ-D006","9780062797155","Circe","Madeline Miller","Little Brown & Co.","meera.r@email.com",day(-2)+" 16:00:00","PENDING"},
            {"REQ-D007","9780062797155","Circe","Madeline Miller","Little Brown & Co.","sunita.p@gmail.com",day(-1)+" 11:30:00","PENDING"},
            {"REQ-D008","9780525559481","The Song of Achilles","Madeline Miller","Ecco","raj.kumar@email.com",day(-4)+" 09:15:00","PENDING"},
            {"REQ-D009","9780525559481","The Song of Achilles","Madeline Miller","Ecco","neha.t@yahoo.com",day(-3)+" 13:45:00","PENDING"},
            {"REQ-D010","9780525559481","The Song of Achilles","Madeline Miller","Ecco","deepak.m@gmail.com",day(-2)+" 10:00:00","PENDING"},
            {"REQ-D011","9780525559481","The Song of Achilles","Madeline Miller","Ecco",null,day(-1)+" 14:30:00","PENDING"},
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
        addLog(c, "SYSTEM","INIT","Seeded: "+books.length+" books, "+sales.length+" sales, "+oos.length+" OOS.");
        System.out.println("[BAS] Seeded: "+books.length+" books, "+sales.length+" sales, "+oos.length+" OOS.");
    }

    private String day(int offset) { return LocalDate.now().plusDays(offset).toString(); }

    private void insertUser(Connection c, String id, String name, String h, String role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (user_id,name,password_hash,role) VALUES (?,?,?,?) ON CONFLICT (user_id) DO NOTHING")) {
            ps.setString(1,id); ps.setString(2,name); ps.setString(3,h); ps.setString(4,role); ps.execute();
        }
    }

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
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE user_id=? AND password_hash=?")) {
                ps.setString(1, userId); ps.setString(2, hash(plainPwd));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return new User(rs.getString("user_id"), rs.getString("name"),
                    rs.getString("password_hash"), User.Role.valueOf(rs.getString("role")));
            }
        } catch (SQLException e) { err("authenticate", e); }
        finally { release(c); }
        return null;
    }

    // ── Book queries (CACHED — instant for reads) ────────────────────────────

    public List<Book> searchByTitle(String q)    { return BookCache.getInstance().searchByTitle(q); }
    public List<Book> searchByAuthor(String q)   { return BookCache.getInstance().searchByAuthor(q); }
    public Book getByISBN(String isbn)           { return BookCache.getInstance().getByISBN(isbn); }
    public List<Book> getAllBooks()               { return BookCache.getInstance().getAllBooks(); }
    public List<Book> getBooksNeedingRestock()    { return BookCache.getInstance().getBooksNeedingRestock(); }

    /** Raw DB fetch — called by BookCache only. */
    List<Book> fetchAllBooksFromDB() {
        List<Book> list = new ArrayList<>();
        Connection c = null;
        try {
            c = borrow();
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT * FROM books ORDER BY title");
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) { err("fetchAllBooksFromDB", e); }
        finally { release(c); }
        return list;
    }

    // ── Write operations (invalidate cache) ───────────────────────────────────

    public boolean addBook(Book b) {
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO books (isbn,title,author,publisher,publisher_address,unit_price,rack_location,stock_count,restock_threshold,request_count,weekly_sales,procurement_lead_time_wks) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1,b.getIsbn()); ps.setString(2,b.getTitle());
                ps.setString(3,b.getAuthor()); ps.setString(4,b.getPublisher());
                ps.setString(5,b.getPublisherAddress()); ps.setDouble(6,b.getUnitPrice());
                ps.setString(7,b.getRackLocation()); ps.setInt(8,b.getStockCount());
                ps.setInt(9,b.getRestockThreshold()); ps.setInt(10,0);
                ps.setDouble(11,b.getWeeklySales()); ps.setInt(12,b.getProcurementLeadTimeWeeks());
                ps.execute();
            }
            addLog(c, "MANAGER","INVENTORY","Book added: "+b.getIsbn());
            BookCache.getInstance().invalidate();
            return true;
        } catch (SQLException e) { err("addBook", e); return false; }
        finally { release(c); }
    }

    public boolean updateBook(Book b) {
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement("UPDATE books SET title=?,author=?,publisher=?,publisher_address=?,unit_price=?,rack_location=?,stock_count=?,restock_threshold=?,weekly_sales=?,procurement_lead_time_wks=? WHERE isbn=?")) {
                ps.setString(1,b.getTitle()); ps.setString(2,b.getAuthor());
                ps.setString(3,b.getPublisher()); ps.setString(4,b.getPublisherAddress());
                ps.setDouble(5,b.getUnitPrice()); ps.setString(6,b.getRackLocation());
                ps.setInt(7,b.getStockCount()); ps.setInt(8,b.getRestockThreshold());
                ps.setDouble(9,b.getWeeklySales()); ps.setInt(10,b.getProcurementLeadTimeWeeks());
                ps.setString(11,b.getIsbn()); ps.executeUpdate();
            }
            addLog(c, "MANAGER","INVENTORY","Book updated: "+b.getIsbn());
            BookCache.getInstance().invalidate();
            return true;
        } catch (SQLException e) { err("updateBook", e); return false; }
        finally { release(c); }
    }

    public boolean addStock(String isbn, int qty, String actor) {
        if (qty <= 0) return false;
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement("UPDATE books SET stock_count=stock_count+? WHERE isbn=?")) {
                ps.setInt(1, qty); ps.setString(2, isbn);
                if (ps.executeUpdate() > 0) {
                    addLog(c, actor,"INVENTORY","Stock +"+qty+" for "+isbn);
                    BookCache.getInstance().invalidate();
                    return true;
                }
            }
        } catch (SQLException e) { err("addStock", e); }
        finally { release(c); }
        return false;
    }

    // ── Atomic sale (single connection for everything) ────────────────────────

    public boolean saveSaleAtomically(SaleRecord sale, String receiptContent) {
        Connection c = null;
        try {
            c = borrow(); c.setAutoCommit(false);
            for (LineItem it : sale.getItems()) {
                try (PreparedStatement ps = c.prepareStatement("SELECT stock_count FROM books WHERE isbn=?")) {
                    ps.setString(1, it.getIsbn());
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next() || rs.getInt(1) < it.getQuantity()) { c.rollback(); return false; }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sales (sale_id,timestamp,clerk_id,total_amount,receipt_content) VALUES (?,?,?,?,?)")) {
                ps.setString(1,sale.getSaleId()); ps.setString(2,sale.getFormattedTimestamp());
                ps.setString(3,sale.getClerkId()); ps.setDouble(4,sale.getTotalAmount());
                ps.setString(5,receiptContent); ps.execute();
            }
            try (PreparedStatement ip = c.prepareStatement(
                        "INSERT INTO sale_items (sale_id,isbn,title,quantity,unit_price,subtotal) VALUES (?,?,?,?,?,?)");
                 PreparedStatement sp = c.prepareStatement(
                        "UPDATE books SET stock_count=stock_count-? WHERE isbn=?")) {
                for (LineItem it : sale.getItems()) {
                    ip.setString(1,sale.getSaleId()); ip.setString(2,it.getIsbn());
                    ip.setString(3,it.getTitle()); ip.setInt(4,it.getQuantity());
                    ip.setDouble(5,it.getUnitPrice()); ip.setDouble(6,it.getSubtotal()); ip.addBatch();
                    sp.setInt(1,it.getQuantity()); sp.setString(2,it.getIsbn()); sp.addBatch();
                }
                ip.executeBatch(); sp.executeBatch();
            }
            addLog(c, sale.getClerkId(), "SALE", "Completed: "+sale.getSaleId()+" | INR "+
                String.format("%.2f",sale.getTotalAmount())+" | "+sale.getItems().size()+" item(s)");
            c.commit();
            BookCache.getInstance().invalidate();
            return true;
        } catch (SQLException e) {
            err("saveSaleAtomically", e);
            try { if (c != null) c.rollback(); } catch (SQLException ignored) {}
            return false;
        } finally { release(c); }
    }

    public boolean saveSaleAtomically(SaleRecord sale) { return saveSaleAtomically(sale, null); }

    // ── Sales reporting ───────────────────────────────────────────────────────

    public List<Object[]> getSalesStats(String from, String to) {
        List<Object[]> list = new ArrayList<>();
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT b.isbn,b.title,b.author,b.publisher," +
                "COALESCE(SUM(si.quantity),0) AS copies,COALESCE(SUM(si.subtotal),0) AS revenue " +
                "FROM books b LEFT JOIN sale_items si ON si.isbn=b.isbn " +
                "LEFT JOIN sales s ON s.sale_id=si.sale_id AND s.timestamp BETWEEN ? AND ? " +
                "GROUP BY b.isbn,b.title,b.author,b.publisher ORDER BY revenue DESC")) {
                ps.setString(1, from+" 00:00:00"); ps.setString(2, to+" 23:59:59");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(new Object[]{
                    rs.getString("isbn"),rs.getString("title"),rs.getString("author"),
                    rs.getString("publisher"),rs.getInt("copies"),rs.getDouble("revenue")});
            }
        } catch (SQLException e) { err("getSalesStats", e); }
        finally { release(c); }
        return list;
    }

    public double getTotalRevenue(String from, String to) {
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE timestamp BETWEEN ? AND ?")) {
                ps.setString(1, from+" 00:00:00"); ps.setString(2, to+" 23:59:59");
                ResultSet rs = ps.executeQuery(); rs.next(); return rs.getDouble(1);
            }
        } catch (SQLException e) { err("getTotalRevenue", e); return 0; }
        finally { release(c); }
    }

    // ── Transaction History ───────────────────────────────────────────────────

    public List<Object[]> getTransactionHistory(int limit) {
        List<Object[]> list = new ArrayList<>();
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT s.sale_id, s.timestamp, s.clerk_id, s.total_amount, COUNT(si.item_id) AS item_count " +
                "FROM sales s LEFT JOIN sale_items si ON si.sale_id=s.sale_id " +
                "GROUP BY s.sale_id,s.timestamp,s.clerk_id,s.total_amount ORDER BY s.timestamp DESC LIMIT ?")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(new Object[]{
                    rs.getString("sale_id"),rs.getString("timestamp"),rs.getString("clerk_id"),
                    rs.getDouble("total_amount"),rs.getInt("item_count")});
            }
        } catch (SQLException e) { err("getTransactionHistory", e); }
        finally { release(c); }
        return list;
    }

    public String getReceiptContent(String saleId) {
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement("SELECT receipt_content FROM sales WHERE sale_id=?")) {
                ps.setString(1, saleId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getString("receipt_content");
            }
        } catch (SQLException e) { err("getReceiptContent", e); }
        finally { release(c); }
        return null;
    }

    public List<Object[]> getSaleItems(String saleId) {
        List<Object[]> list = new ArrayList<>();
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT isbn,title,quantity,unit_price,subtotal FROM sale_items WHERE sale_id=? ORDER BY item_id")) {
                ps.setString(1, saleId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(new Object[]{
                    rs.getString("isbn"),rs.getString("title"),rs.getInt("quantity"),
                    rs.getDouble("unit_price"),rs.getDouble("subtotal")});
            }
        } catch (SQLException e) { err("getSaleItems", e); }
        finally { release(c); }
        return list;
    }

    // ── OOS requests ──────────────────────────────────────────────────────────

    public boolean addOOSRequest(OOSRequest req) {
        Connection c = null;
        try {
            c = borrow();
            // Both operations on SAME connection
            try (PreparedStatement ps = c.prepareStatement("UPDATE books SET request_count=request_count+1 WHERE isbn=?")) {
                ps.setString(1, req.getIsbn()); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO oos_requests (request_id,isbn,title,author,publisher,email,timestamp,status) VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1,req.getRequestId()); ps.setString(2,req.getIsbn());
                ps.setString(3,req.getTitle()); ps.setString(4,req.getAuthor());
                ps.setString(5,req.getPublisher()); ps.setString(6,req.getEmail());
                ps.setString(7,req.getFormattedTimestamp()); ps.setString(8,req.getStatus().name());
                ps.execute();
            }
            addLog(c,"CUSTOMER","OOS","Request: "+req.getTitle()+" ("+req.getIsbn()+")");
            BookCache.getInstance().invalidate();
            return true;
        } catch (SQLException e) { err("addOOSRequest", e); return false; }
        finally { release(c); }
    }

    public List<OOSRequest> getAllOOSRequests() {
        List<OOSRequest> list = new ArrayList<>();
        Connection c = null;
        try {
            c = borrow();
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT * FROM oos_requests ORDER BY timestamp DESC");
                while (rs.next()) {
                    OOSRequest req = new OOSRequest(rs.getString("request_id"),rs.getString("isbn"),
                        rs.getString("title"),rs.getString("author"),rs.getString("publisher"),rs.getString("email"));
                    req.setStatus(OOSRequest.Status.valueOf(rs.getString("status")));
                    list.add(req);
                }
            }
        } catch (SQLException e) { err("getAllOOSRequests", e); }
        finally { release(c); }
        return list;
    }

    public List<String> getPendingEmails(String isbn) {
        List<String> list = new ArrayList<>();
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement("SELECT email FROM oos_requests WHERE isbn=? AND status='PENDING' AND email IS NOT NULL")) {
                ps.setString(1, isbn);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(rs.getString(1));
            }
        } catch (SQLException e) { err("getPendingEmails", e); }
        finally { release(c); }
        return list;
    }

    public void markNotified(String isbn) {
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement("UPDATE oos_requests SET status='NOTIFIED' WHERE isbn=? AND status='PENDING'")) {
                ps.setString(1, isbn); ps.executeUpdate();
            }
        } catch (SQLException e) { err("markNotified", e); }
        finally { release(c); }
    }

    // ── Logs (use single connection for internal calls) ───────────────────────

    public void addLog(String actor, String type, String msg) {
        Connection c = null;
        try { c = borrow(); addLog(c, actor, type, msg); }
        catch (SQLException e) { System.err.println("[LOG] " + e.getMessage()); }
        finally { release(c); }
    }

    private void addLog(Connection c, String actor, String type, String msg) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO app_logs (timestamp,event_type,actor,message) VALUES (?,?,?,?)")) {
            ps.setString(1, LocalDateTime.now().format(DT));
            ps.setString(2, type); ps.setString(3, actor); ps.setString(4, msg); ps.execute();
        }
    }

    public List<Object[]> getRecentLogs(int limit) {
        List<Object[]> list = new ArrayList<>();
        Connection c = null;
        try {
            c = borrow();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM app_logs ORDER BY log_id DESC LIMIT ?")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(new Object[]{
                    rs.getInt("log_id"),rs.getString("timestamp"),rs.getString("event_type"),
                    rs.getString("actor"),rs.getString("message")});
            }
        } catch (SQLException e) { err("getRecentLogs", e); }
        finally { release(c); }
        return list;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private Book map(ResultSet rs) throws SQLException {
        return new Book(rs.getString("isbn"),rs.getString("title"),rs.getString("author"),
            rs.getString("publisher"),rs.getString("publisher_address"),rs.getDouble("unit_price"),
            rs.getString("rack_location"),rs.getInt("stock_count"),rs.getInt("restock_threshold"),
            rs.getInt("request_count"),rs.getDouble("weekly_sales"),rs.getInt("procurement_lead_time_wks"));
    }

    private void err(String m, SQLException e) { System.err.println("[DB ERR] " + m + ": " + e.getMessage()); }
    public static String newSaleId() { return "SALE-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(); }
    public static String newReqId()  { return "REQ-"  + UUID.randomUUID().toString().substring(0,8).toUpperCase(); }
}

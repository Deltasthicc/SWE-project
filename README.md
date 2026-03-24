# BAS — Bookshop Inventory & Sales Management System
**Group G01 | Shiv Nadar Institution of Eminence**  
Divyam Sharma · Shashwat Rajan · Suryavedha Pradhan · Shreyas Achal

---

## Quick Start (3 Steps)

### Step 1 — Download SQLite Driver
1. Go to: https://github.com/xerial/sqlite-jdbc/releases
2. Download the latest `.jar` file (e.g. `sqlite-jdbc-3.45.3.0.jar`)
3. Place it in `BAS/lib/`

### Step 2 — Compile
**Windows:** Double-click `compile.bat`
**Linux/Mac:** `chmod +x compile_and_run.sh && ./compile_and_run.sh`

### Step 3 — Run
**Windows:** Double-click `run.bat`  
**Linux/Mac:** `java -cp "lib/*:out" bas.Main`

> On first launch `bas.db` is created automatically with 20 books + 20 demo sales
> + 5 OOS requests — everything ready for a full demo instantly.

---

## Folder Structure
```
BAS/
├── compile.bat            ← Windows compile
├── run.bat                ← Windows run
├── compile_and_run.sh     ← Linux/Mac compile+run
├── lib/
│   └── sqlite-jdbc-*.jar  ← PUT YOUR JAR HERE
├── out/                   ← created by compile script
└── src/bas/
    ├── Main.java
    ├── model/             Book, LineItem, SaleRecord, OOSRequest, User
    ├── db/                DatabaseManager (SQLite, all CRUD)
    ├── service/           EmailService (SMTP)
    ├── util/              ISBNValidator, EmailValidator, PrinterUtil
    └── ui/                LoginFrame, MainFrame, CustomerTerminalPanel,
                           POSTerminalPanel, InventoryPanel, OwnerPanel
```

---

## Login Credentials

| Role | User ID | Password | Access |
|------|---------|----------|--------|
| **Bookshop Owner** | `owner1` | `owner123` | All tabs |
| **Inventory Manager** | `manager1` | `mgr123` | Search + POS + Inventory |
| **Sales Clerk** | `clerk1` | `clerk123` | Search + POS |
| **Sales Clerk 2** | `clerk2` | `clerk123` | Search + POS |
| **Customer** | *(no login)* | *(no login)* | Click green button |

---

## Pre-loaded Demo Data

### 20 Books (across genres, varied stock levels)

| ISBN | Title | Stock | Status |
|------|-------|-------|--------|
| 9781982173593 | Atomic Habits | 30 | ✅ In Stock |
| 9780062315007 | The Alchemist | 25 | ✅ In Stock |
| 9780451524935 | 1984 | 22 | ✅ In Stock |
| 9780061096525 | To Kill a Mockingbird | 18 | ✅ In Stock |
| 9780439023481 | The Hunger Games | 14 | ✅ In Stock |
| 9780743273565 | The Great Gatsby | 15 | ✅ In Stock |
| 9780385737951 | The Maze Runner | 20 | ✅ In Stock |
| 9781250301697 | Where the Crawdads Sing | 11 | ✅ In Stock |
| 9780316769174 | The Catcher in the Rye | 10 | ✅ In Stock |
| 9780525559474 | The Fault in Our Stars | 12 | ✅ In Stock |
| 9781250178619 | A Court of Thorns and Roses | 13 | ✅ In Stock |
| 9780385545990 | The Midnight Library | 9 | ✅ In Stock |
| 9781501156700 | It Ends with Us | 8 | ✅ In Stock |
| 9780316346627 | Verity | 6 | ✅ In Stock |
| 9780735224292 | Little Fires Everywhere | 7 | ✅ In Stock |
| 9780679783268 | Crime and Punishment | 4 | 🟡 Low Stock |
| 9780140449136 | Anna Karenina | 3 | 🟡 Low Stock |
| 9780062409850 | The Book Thief | 2 | 🟡 Low Stock |
| 9781501197277 | It (Stephen King) | 0 | 🔴 Out of Stock |
| 9780307474278 | The Girl with Dragon Tattoo | 0 | 🔴 Out of Stock |

### 20 Demo Sales (last 21 days — visible in Owner's Sales Report)
### 5 OOS Requests pre-loaded (2 for "It", 3 for "Dragon Tattoo")

---

## Full Demo Walkthrough (All Roles + All Requirements)

### DEMO 1 — Customer (No Login)
**Shows: F1 (Book Search), F2 (OOS Requests), NFR-7 (minimal training needed)**

1. Launch app → Click the **green "Browse as Customer"** button
2. The Customer Terminal opens showing all 20 books
3. **Search by Title:** Type `Atomic` → click Search → see result highlighted green (in stock)
4. **Search by Author:** Select "By Author" → type `Colleen` → find "Verity" and "It Ends with Us"
5. **View OOS books:** Click "Show All" → notice 2 red rows at bottom ("It" and "Dragon Tattoo")
6. **Submit OOS request:**
   - Select the "It" row → click orange **"Request Notification / Out-of-Stock Alert"**
   - Fill: ISBN=9781501197277, Title=It, Author=Stephen King, Publisher=Scribner
   - Add your email → Submit
   - Confirms: "Request submitted!"
7. **Request unlisted book:** Click the orange button WITHOUT selecting a row
   - Fill in any book not in the system → Submit → confirms it was recorded

---

### DEMO 2 — Sales Clerk (clerk1 / clerk123)
**Shows: F3 (Billing), FR-3.1–3.5, NFR-2 (1-sec billing), Data Validation**

1. Login as `clerk1` / `clerk123`
2. Click **"🧾 POS Terminal"** tab
3. **Add item by ISBN:**
   - Type `9781982173593` (Atomic Habits) in ISBN field → press Enter
   - See it added: ₹499 × 1 = ₹499
4. **Use Quick-add buttons:**
   - Click "Atomic Habits" or "The Alchemist" or "The Hunger Games" quick-add button
   - Items instantly populate the bill
5. **Change quantity:** Set Qty spinner to `2` → type ISBN `9780062315007` → Enter
   - The Alchemist × 2 = ₹620
6. **Test ISBN validation (NFR - Data Validation):**
   - Type `1234567890123` (invalid checksum) → system rejects it with error message
7. **Remove an item:** Select a row → click "🗑 Remove"
8. **Confirm sale:**
   - Click **"✔ Confirm & Print"** → confirm dialog shows total
   - Confirm → receipt appears in the preview panel on the right
   - Choose to print or skip printer dialog
9. **Stock updates automatically** — check Inventory tab if logged in as Manager
10. **Test insufficient stock:**
    - Add ISBN `9780679783268` (Crime and Punishment, only 4 in stock) with qty = 10
    - Confirm → sale fails with clear error message

---

### DEMO 3 — Inventory Manager (manager1 / mgr123)
**Shows: F4, FR-4.1, NFR-3 (access control), NFR-9 (logs), NFR-10 (modularity)**

1. Login as `manager1` / `mgr123`
2. **POS Terminal** works same as Clerk demo above
3. Click **"📦 Inventory"** tab

**View books:**
- All 20 books loaded immediately
- Colour coding: 🔴 red = OOS, 🟡 yellow = low stock, 🟢 green = normal
- Click column headers to sort by stock level

**Filter:**
- Type `Colleen` in Filter field → only Colleen Hoover books shown
- Type `F-0` → only OOS shelf books shown
- Click Clear to reset

**Update stock on arrival (FR-4.1):**
- Select "It" (0 stock, red row) → click **"📦 Update Stock"**
- Enter `50` → OK
- System asks: "3 customers waiting — send restock emails?"
- Click Yes (if SMTP configured) or No — either way, stock updates to 50

**Add a new book:**
- Click **"+ Add Book"**
- Fill: ISBN=`9780735619425`, Title=`Shoe Dog`, Author=`Phil Knight`,
  Publisher=`Scribner`, Price=`389`, Rack=`G-01`, Stock=`15`, Threshold=`5`,
  Lead Time=`2`, Weekly Sales=`2.5`
- Save → appears in the list immediately

**Edit a book:**
- Select any book → click **"✏ Edit"**
- Change the price → Save → reflected immediately

---

### DEMO 4 — Bookshop Owner (owner1 / owner123)
**Shows: All F4 features, FR-4.2–4.4, NFR-3, NFR-9**

1. Login as `owner1` / `owner123`
2. Has access to all 4 tabs

**📊 Reports & Analytics → Sales Report:**
- Report auto-generates for last 30 days on open
- Shows all 20 books with their copies sold and revenue
- Atomic Habits should show highest revenue (8+ copies × ₹499)
- Change date range to last 7 days → see different numbers
- Total revenue shown at top right
- Click **"🖨 Print"** → printer dialog appears

**📊 → Procurement:**
- Auto-loads on open
- Shows all books at/below threshold: 5 books (Crime and Punishment, Anna Karenina,
  The Book Thief, It, Dragon Tattoo)
- **Order Qty column** (highlighted red) shows exactly how many to order:
  - Formula: max(0, ⌈Weekly Sales × Lead Time⌉ − Stock)
  - "It": (6.0 × 2) − 0 = **12 to order**
  - "Anna Karenina": (1.5 × 4) − 3 = **3 to order**
- Publisher Address shown for each → ready to place orders
- Print → full procurement report for the day

**📊 → OOS Demand Log:**
- Click Refresh → all 5 pre-loaded OOS requests visible
- Shows: Request ID, ISBN, title, email (or "no email"), status = PENDING
- After sending restock alerts, status changes to NOTIFIED

**📊 → Email Settings:**
- Fill in your SMTP details (Gmail recommended)
- Click "Send Test Email" → enter your email → verify receipt
- Once configured, restock alerts work from Inventory tab

**📊 → Activity Log:**
- Click "Load Logs" → full audit trail of all system events
- See: INIT, LOGIN, SALE, INVENTORY, OOS, CONFIG events
- Every action in the system is logged here (NFR-9)

---

## SRS Requirements Coverage

| Requirement | Where to see it |
|-------------|----------------|
| FR-1.1 Search by title | Customer Terminal → "By Title" radio |
| FR-1.2 Search by author | Customer Terminal → "By Author" radio |
| FR-1.3 Stock + rack location | Customer Terminal → Stock and Rack columns |
| FR-1.4 Results within 2 sec | SwingWorker async + SQLite indexed lookup |
| FR-2.1 OOS request form | Orange button in Customer Terminal |
| FR-2.2 Request counter | books.request_count increments on each OOS |
| FR-2.3 Optional email | Email field in OOS dialog (optional) |
| FR-2.4 Email on restock | Inventory → Update Stock → sends bulk alerts |
| FR-3.1 ISBN entry | POS Terminal → ISBN field |
| FR-3.2 Auto price fetch | Price populated from DB instantly |
| FR-3.3 Real-time total | Running total updates as you add items |
| FR-3.4 Printable receipt | Receipt preview + print dialog on confirm |
| FR-3.5 Atomic stock decrement | DatabaseManager.saveSaleAtomically() |
| FR-4.1 Stock on arrival | Inventory → Update Stock button |
| FR-4.2 Sales statistics | Owner → Sales Report tab |
| FR-4.3 Procurement formula | Book.getRequiredProcurementQty() |
| FR-4.4 Daily procurement report | Owner → Procurement tab + Print |
| NFR-1 2-sec search | SwingWorker + LIKE query |
| NFR-2 1-sec billing | SwingWorker async confirm |
| NFR-3 Access control | MainFrame shows tabs by role only |
| NFR-4 SSL/TLS email | EmailService uses STARTTLS |
| NFR-5/6 Data integrity | SQLite WAL mode + atomic transactions |
| NFR-7 Minimal training needed | Large buttons, colour coding, tooltips |
| NFR-8 Descriptive errors | All error messages are user-friendly |
| NFR-9 Application logs | Every action logged to app_logs table |
| NFR-10 Modular components | Separate packages: model/db/service/ui/util |
| NFR-11 Scalability | SQLite with schema supporting 100k+ books |
| NFR-12 Windows 10/11 | Swing + system L&F + standard peripherals |
| ISBN Validation | ISBNValidator.java — full ISBN-10 & ISBN-13 checksum |
| Email Validation | EmailValidator.java — regex pattern |
| Stock non-negativity | Pre-check in saveSaleAtomically before commit |
| Atomic transactions | setAutoCommit(false) + rollback on failure |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| App won't start | Put `sqlite-jdbc-*.jar` in `lib/` and recompile |
| "UnsupportedClassVersionError" | Install JDK 17+ |
| Books not showing | Delete `bas.db` and restart (fresh seed) |
| Sale fails unexpectedly | Check stock — items may have been sold already |
| Email not sending | Configure SMTP in Owner Panel → use Gmail App Password |
| "Invalid ISBN" error | Use ISBNs from the demo table above — all verified |

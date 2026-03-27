# BAS — Bookshop Inventory & Sales Management System

**Group G01 | Shiv Nadar Institution of Eminence**

| Name              | Roll No.    |
|-------------------|-------------|
| Divyam Sharma     | 2310110109  |
| Shashwat Rajan    | 2510111019  |
| Suryavedha Pradhan| 2310110316  |
| Shreyas Achal     | 2310110289  |

---

## Overview

BAS is a desktop-based Java Swing application that automates retail bookshop operations:
real-time inventory queries, point-of-sale billing, demand tracking, procurement planning,
and automated email notifications for out-of-stock books.

### Key Features

- **Book Search & Availability** — Search by title/author, see stock count, rack location, and publisher
- **Out-of-Stock Requests** — Customers register for email alerts when books are restocked
- **POS Billing** — ISBN-based checkout with atomic transactions and receipt generation
- **Inventory Management** — Stock updates, book CRUD, restock threshold monitoring
- **Procurement Reports** — Auto-calculated order quantities based on weekly sales × lead time
- **Sales Analytics** — Date-range revenue reports with print support
- **SMTP Email Notifications** — Working Gmail integration for restock alerts

---

## Technology Stack

| Component         | Technology                          |
|-------------------|-------------------------------------|
| Language          | Java 17+                            |
| UI Framework      | Swing + FlatLaf (modern flat L&F)   |
| Database          | PostgreSQL via Supabase (cloud)     |
| Authentication    | Custom JWT (HMAC-SHA256)            |
| Encryption        | AES-128-CBC + SHA-256 password hash |
| Email             | JavaMail API (SMTP/TLS via Gmail)   |
| Build             | javac (no Maven/Gradle required)    |

---

## Architecture

```
bas/
├── Main.java                    # Entry point
├── config/
│   └── AppConfig.java           # Centralised config (DB, JWT, SMTP)
├── crypto/
│   └── AESUtil.java             # AES encryption/decryption utility
├── auth/
│   ├── JWTUtil.java             # JWT token generation & validation
│   └── SessionManager.java      # Singleton session holder
├── model/
│   ├── Book.java                # Book entity with procurement formula
│   ├── User.java                # User entity with role enum
│   ├── SaleRecord.java          # Sale transaction record
│   ├── LineItem.java            # Individual line item in a sale
│   └── OOSRequest.java          # Out-of-stock request entity
├── db/
│   └── DatabaseManager.java     # PostgreSQL/Supabase CRUD (singleton)
├── service/
│   └── EmailService.java        # SMTP email service (Gmail)
├── ui/
│   ├── LoginFrame.java          # Login screen with JWT auth
│   ├── MainFrame.java           # Tabbed main window
│   ├── CustomerTerminalPanel.java  # Book search + OOS requests
│   ├── POSTerminalPanel.java    # Point-of-sale billing
│   ├── InventoryPanel.java      # Inventory management (Manager/Owner)
│   └── OwnerPanel.java          # Reports, procurement, email config
└── util/
    ├── ISBNValidator.java       # ISBN-10/13 checksum validation
    ├── EmailValidator.java      # Email format validation
    └── PrinterUtil.java         # Receipt & report printing
```

---

## Prerequisites

1. **JDK 17 or higher** — [Download from Oracle](https://www.oracle.com/java/technologies/downloads/) or use OpenJDK
2. **Internet connection** — Required for Supabase database and Gmail SMTP

---

## Required JARs in `lib/`

| JAR File                     | Purpose                | Download Link |
|------------------------------|------------------------|---------------|
| `postgresql-42.7.4.jar`     | PostgreSQL JDBC driver | [jdbc.postgresql.org/download](https://jdbc.postgresql.org/download/) |
| `javax.mail-1.6.2.jar`      | JavaMail API           | Already included |
| `javax.activation-1.2.0.jar`| JavaMail dependency    | Already included |
| `flatlaf-3.4.1.jar`         | Modern flat UI (optional) | [github.com/JFormDesigner/FlatLaf/releases](https://github.com/JFormDesigner/FlatLaf/releases) |

### Quick download (command line):

**PostgreSQL driver:**
```bash
curl -L -o lib/postgresql-42.7.4.jar https://jdbc.postgresql.org/download/postgresql-42.7.4.jar
```

**FlatLaf (optional):**
```bash
curl -L -o lib/flatlaf-3.4.1.jar https://repo1.maven.org/maven2/com/formdev/flatlaf/3.4.1/flatlaf-3.4.1.jar
```

---

## Setup & Run

### Windows

```batch
# 1. Place required JARs in lib\ folder
# 2. Compile
compile.bat

# 3. Run
run.bat
```

### Linux / macOS

```bash
# 1. Place required JARs in lib/ folder
# 2. Make script executable
chmod +x compile_and_run.sh

# 3. Compile and run
./compile_and_run.sh
```

### Manual (any OS)

```bash
# Compile
javac --release 17 -cp "lib/*" -d out src/bas/config/*.java src/bas/crypto/*.java src/bas/auth/*.java src/bas/model/*.java src/bas/util/*.java src/bas/db/*.java src/bas/service/*.java src/bas/ui/*.java src/bas/Main.java

# Run (Windows — semicolon separator)
java -cp "lib/*;out" bas.Main

# Run (Linux/Mac — colon separator)
java -cp "lib/*:out" bas.Main
```

---

## Default Login Credentials

| Role    | User ID    | Password  |
|---------|------------|-----------|
| Owner   | `owner1`   | `owner123`|
| Manager | `manager1` | `mgr123`  |
| Clerk   | `clerk1`   | `clerk123`|
| Clerk   | `clerk2`   | `clerk123`|
| Customer| No login required — click "Browse as Customer" |

These are seeded automatically on first run if the database is empty.

---

## JWT Authentication Flow

1. User enters credentials on the login screen
2. `DatabaseManager.authenticate()` verifies SHA-256 password hash against Supabase
3. On success, `JWTUtil.generateToken()` creates a signed JWT with:
   - `sub` (user ID), `name`, `role`, `iat` (issued at), `exp` (expiry = 8 hours)
   - Signed with HMAC-SHA256 using the secret in `AppConfig.JWT_SECRET`
4. `SessionManager` stores the token for the session
5. Protected operations (POS confirm, inventory writes) call `SessionManager.isAuthenticated()` which:
   - Validates the JWT signature
   - Checks token expiry
   - Verifies required role
6. On logout, the token is destroyed from memory

---

## Supabase Database

- **Host:** `db.hpfibvsyorccjihdyfzh.supabase.co`
- **Port:** 5432
- **SSL:** Required (enforced in connection properties)
- Tables are created automatically via `CREATE TABLE IF NOT EXISTS`
- Demo data (20 books, 20 sales, 5 OOS requests) seeded on first run

---

## SMTP Email Configuration

Pre-configured with Gmail App Password:
- **Host:** smtp.gmail.com
- **Port:** 587 (STARTTLS)
- **Sender:** shashwat.rajan2005@gmail.com

The Owner Panel → Email Settings tab allows runtime reconfiguration.
To test: click "Send Test Email" and enter a recipient address.

---

## SRS Requirement Traceability

| Requirement | Feature | Implementation |
|-------------|---------|----------------|
| FR-1.1–1.4 | Book Search | `CustomerTerminalPanel` — search by title/author, shows publisher, stock, rack |
| FR-2.1–2.4 | OOS Requests | `CustomerTerminalPanel.openOOS()` — records request, email notification on restock |
| FR-3.1–3.5 | POS Billing | `POSTerminalPanel` — ISBN entry, price fetch, atomic transaction, receipt print |
| FR-4.1 | Inventory Update | `InventoryPanel.openStockUpdate()` |
| FR-4.2 | Sales Statistics | `OwnerPanel.salesTab()` |
| FR-4.3 | Procurement Formula | `Book.getRequiredProcurementQty()` = max(0, ceil(weeklySales × leadTime) − stock) |
| FR-4.4 | Procurement Report | `OwnerPanel.procurementTab()` |
| NFR-1 | Search < 2s | PostgreSQL indexed queries |
| NFR-2 | Billing < 1s | Atomic SQL transaction |
| NFR-3 | Authorized access | JWT authentication + role-based access |
| NFR-4 | SSL/TLS encryption | Supabase SSL + SMTP STARTTLS + AES utility |
| NFR-5–6 | Data integrity | PostgreSQL ACID transactions |
| NFR-7–8 | Usability | FlatLaf UI, descriptive error messages |
| NFR-9 | Application logs | `app_logs` table, Activity Log tab |
| NFR-10 | Modularity | Separate packages: auth, config, crypto, db, model, service, ui, util |

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `ClassNotFoundException: org.postgresql.Driver` | Download `postgresql-42.7.4.jar` and place in `lib/` |
| `Connection refused` on startup | Check internet connection; verify Supabase project is active |
| `SSL error` | Ensure Java 17+ (older JDKs may lack required TLS ciphers) |
| Email test fails | Verify App Password is correct (16 chars, no spaces); check Gmail 2FA is enabled |
| FlatLaf not loading | Download `flatlaf-3.4.1.jar` to `lib/`; app falls back to system L&F gracefully |
| `FATAL: password authentication failed` | Check `AppConfig.DB_PASSWORD` matches your Supabase project password |

---

## License

Academic project — Shiv Nadar Institution of Eminence, Software Engineering Course 2026.

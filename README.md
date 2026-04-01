# BAS — Bookshop Inventory & Sales Management System

**Shiv Nadar Institution of Eminence — Software Engineering Project (Group G01)**

**Team:** Divyam Sharma, Shashwat Rajan, Suryavedha Pradhan, Shreyas Achal

---

## Overview

BAS is a Java Swing desktop application for managing a bookshop's inventory, sales, customer requests, and procurement. It connects to a PostgreSQL database hosted on Supabase (cloud) and implements role-based access control, JWT session management, AES encryption, SMTP email notifications, and a connection-pooled backend with in-memory caching.

## Architecture

```
bas/
├── Main.java                        # Entry point (FlatLaf UI + DB init)
├── config/AppConfig.java            # Environment variables with fallbacks
├── crypto/AESUtil.java              # AES-128-CBC, random IV per encryption
├── auth/
│   ├── JWTUtil.java                 # HMAC-SHA256 JWT (no external library)
│   └── SessionManager.java          # Singleton session with role enforcement
├── model/
│   ├── Book.java                    # ISBN, title, author, publisher, stock, procurement formula
│   ├── User.java                    # user_id, name, hash, role enum
│   ├── SaleRecord.java              # Sale with line items, auto-total, merge logic
│   ├── LineItem.java                # ISBN, title, qty, price, subtotal
│   └── OOSRequest.java             # Out-of-stock request with DB timestamp preservation
├── db/
│   ├── ConnectionPool.java          # 5-connection pool, 2min idle timeout, isValid() fix
│   ├── BookCache.java               # 60s TTL, stale-on-failure, concurrent refresh guard
│   └── DatabaseManager.java         # All CRUD, salted auth, FOR UPDATE locking, procurement
├── service/EmailService.java        # Gmail SMTP, selective notification tracking
├── ui/
│   ├── LoginFrame.java              # Login with JWT token generation
│   ├── MainFrame.java               # Tab container, role-based tab visibility
│   ├── CustomerTerminalPanel.java   # Book search (title/author), OOS request form
│   ├── POSTerminalPanel.java        # ISBN scan, cart, atomic sale, receipt storage
│   ├── InventoryPanel.java          # Book CRUD, stock update, regex-safe filter, alerts
│   └── OwnerPanel.java             # Sales report, transaction history, procurement, OOS log, email, activity log
└── util/
    ├── ISBNValidator.java           # ISBN-10/13 checksum validation
    ├── EmailValidator.java          # Strict regex (rejects domain..com)
    └── PrinterUtil.java             # Receipt formatting + system printer integration
```

## Requirements

- **JDK 17+** (tested with JDK 19)
- **Required JARs in `lib/`:**
  - `postgresql-42.7.4.jar` — [Download](https://jdbc.postgresql.org/download/)
  - `javax.mail-1.6.2.jar` (included)
  - `javax.activation-1.2.0.jar` (included)
- **Optional:**
  - `flatlaf-3.4.1.jar` — Modern UI look & feel (graceful fallback to system L&F if missing)
- **For testing:**
  - `junit-platform-console-standalone-1.10.2.jar` — [Download](https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar)

## Quick Start

### 1. Database Setup

The app uses Supabase (PostgreSQL). On first launch, it auto-creates all tables and seeds 71 books, 30 multi-item sales, and 11 OOS requests.

**To reset the database** (fresh seed), run this in Supabase Dashboard → SQL Editor:

```sql
DROP TABLE IF EXISTS procurement_orders, sale_items, sales, oos_requests, books, app_logs, users CASCADE;
```

### 2. Compile & Run

**Windows:**
```
compile.bat        # Compiles to out/
run.bat            # Launches the app
```

**Linux/macOS:**
```
chmod +x compile_and_run.sh
./compile_and_run.sh
```

### 3. Login Credentials

| User ID    | Password   | Role    |
|------------|------------|---------|
| `owner1`   | `owner123` | OWNER   |
| `manager1` | `mgr123`   | MANAGER |
| `clerk1`   | `clerk123` | CLERK   |
| `clerk2`   | `clerk123` | CLERK   |

## Features (Mapped to SRS)

### FR-1: Book Search & Availability
- Search by title or author (cached, instant response)
- Shows stock count, rack location, publisher
- Case-insensitive substring matching

### FR-2: Out-of-Stock Requests & Email Notification
- Customer submits request with optional email
- Request counter increments per ISBN
- Selective email notification (only marks successfully sent emails as NOTIFIED)
- Failed sends remain PENDING for retry

### FR-3: Billing & Receipt Generation
- ISBN-based checkout with real-time total
- Atomic sale: stock check → insert sale → insert items → decrement stock (single transaction)
- `SELECT ... FOR UPDATE` prevents concurrent overselling
- Receipt stored in DB for later retrieval/reprint
- Validates: positive quantities, non-negative prices, non-empty cart

### FR-4: Inventory, Reports & Procurement
- **Sales Report:** Date-range filter, per-book copies/revenue breakdown (fixed SQL: WHERE not JOIN)
- **Transaction History:** Browse all past sales, click to view line items + receipt, reprint
- **Procurement (two-step flow):**
  1. View recommendations: `Qty = max(0, ceil(WeeklySales × LeadTime) − Stock)`
  2. Click "Place Order" → creates `procurement_orders` entry (status: ORDERED)
  3. Click "Confirm Arrival" → atomically adds stock + marks ARRIVED (race-safe via `UPDATE ... WHERE status='ORDERED' RETURNING`)
- **Activity Log:** All operations logged with actor, timestamp, event type

## Security (NFR-3, NFR-4)

| Feature | Implementation |
|---------|---------------|
| Password storage | Salted SHA-256 (per-user random 16-byte salt) |
| Session management | HMAC-SHA256 JWT, 8-hour expiry, role claims |
| Encryption | AES-128-CBC with random IV per encryption |
| DB connection | SSL (sslmode=require) to Supabase |
| Email | STARTTLS on port 587 (Gmail) |
| Secrets | Read from environment variables (`BAS_DB_HOST`, `BAS_DB_PASSWORD`, `BAS_JWT_SECRET`, `BAS_SMTP_PASSWORD`, `BAS_AES_KEY`) with development fallbacks |
| Concurrency | `FOR UPDATE` row locking on sales, atomic procurement confirmation |
| Input validation | Backend rejects negative stock/price/qty; DB CHECK constraints enforce `stock_count>=0`, `unit_price>=0`, `quantity>0` |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BAS_DB_HOST` | `aws-1-ap-south-1.pooler.supabase.com` | PostgreSQL host |
| `BAS_DB_PORT` | `6543` | Session pooler port |
| `BAS_DB_USER` | `postgres.hpfibvsyorccjihdyfzh` | DB user |
| `BAS_DB_PASSWORD` | *(fallback in code)* | DB password |
| `BAS_JWT_SECRET` | *(64-char hex)* | JWT signing key |
| `BAS_SMTP_HOST` | `smtp.gmail.com` | SMTP server |
| `BAS_SMTP_PORT` | `587` | SMTP port |
| `BAS_SMTP_EMAIL` | *(fallback in code)* | Sender email |
| `BAS_SMTP_PASSWORD` | *(fallback in code)* | Gmail App Password |
| `BAS_AES_KEY` | *(fallback in code)* | AES encryption key |

## Performance Optimizations

| Layer | Technique | Impact |
|-------|-----------|--------|
| Connection Pool | 5 reusable connections, 2min idle timeout | ~200x faster than new connection per query |
| Book Cache | 60s TTL, stale-on-failure (won't show empty on DB outage) | Searches return in <1ms |
| Batch operations | Sales use batch INSERT/UPDATE (1 round-trip per batch) | Sale completes in ~3-5s (cloud) or <100ms (local) |
| Concurrent refresh guard | `AtomicBoolean` CAS prevents multiple threads refreshing simultaneously | No thundering herd |

## Database Schema

```
users              (user_id PK, name, password_hash, salt, role)
books              (isbn PK, title, author, publisher, publisher_address, unit_price CHECK>=0,
                    rack_location, stock_count CHECK>=0, restock_threshold CHECK>=0,
                    request_count, weekly_sales CHECK>=0, procurement_lead_time_wks CHECK>=0)
sales              (sale_id PK, timestamp, clerk_id, total_amount, receipt_content)
sale_items         (item_id SERIAL PK, sale_id FK, isbn, title, quantity CHECK>0,
                    unit_price CHECK>=0, subtotal)
oos_requests       (request_id PK, isbn, title, author, publisher, email, timestamp, status)
procurement_orders (order_id PK, isbn, title, publisher, publisher_address,
                    quantity CHECK>0, status, ordered_at, arrived_at)
app_logs           (log_id SERIAL PK, timestamp, event_type, actor, message)
```

## Seed Data

- **71 books** across 11 categories: Literary Classics (A), Modern Fiction (B), Sci-Fi & Fantasy (C), Mystery & Thriller (D), Self-Help & Business (E), Indian Authors (F), Science & Philosophy (G), Children's & YA (H)
- **30 multi-item sales** over 30 days with varied timestamps (morning/afternoon/evening), featuring Atomic Habits (9 copies across 3 transactions), Harry Potter bundles, Indian author combos, philosophy pairs
- **11 OOS requests** across 4 out-of-stock titles with varied timestamps spread over 10 days
- **4 users** (Owner, Manager, 2 Clerks) with salted passwords

## Test Suite

**420 tests across 15 test classes** using JUnit 5.

### Running Tests

```
# Windows
run_tests.bat

# Linux/macOS
./run_tests.sh
```

Generates:
- Console output with pass/fail tree
- Summary table with PASS/FAIL counts
- HTML report at `test-reports/BAS_Test_Report.html`

### Test Classes

| Class | Tests | Coverage |
|-------|-------|----------|
| TestISBNValidator | 23 | ISBN-10/13 validation, checksums, edge cases |
| TestEmailValidator | 17 | Email format, normalization, consecutive dots |
| TestCryptoAndAuth | 32 | AES random-IV, salted SHA-256, JWT lifecycle, SessionManager |
| TestModels | 22 | Book procurement formula, SaleRecord merge, LineItem, OOSRequest, User |
| TestDatabase | 45 | Auth, search, CRUD, atomic sales, stock, OOS, history, logs |
| TestPoolAndCache | 15 | Connection reuse, cache speed, invalidation, stale-on-failure |
| TestEdgeCases | 29 | SQL injection (4 vectors), null/empty/Unicode, boundaries |
| TestEmailAndPrinter | 13 | SMTP config, receipt generation, formatting |
| TestIntegrationWorkflow | 24 | 5 end-to-end workflows with ensureStock() idempotency |
| TestSeedDataIntegrity | 36 | Verify 71 books, genres, sales, receipts, users |
| TestAdvancedScenarios | 37 | Multi-item sales, buy-last-copy, date ranges, cache consistency |
| TestSRSCompliance | 40 | Maps to every FR-1→FR-4 and NFR-1→NFR-12 |
| TestNegativeCases | 49 | Auth failures, invalid sales, stock overflows, session edge cases |
| TestConcurrency | 18 | Parallel searches, cache storms, JWT uniqueness, pool cycles |
| TestSupplementary | 20 | ISBN normalization, precision, JWT claims, timestamp formats |
| **TOTAL** | **420** | |

### Test Idempotency

Tests use `ensureStock()` with `UPDATE books SET stock_count = GREATEST(stock_count, 20)` in `@BeforeAll`, making them safe to run unlimited times without stock depletion.

## Known Limitations (Acknowledged)

1. **Fallback secrets in source** — Development convenience; production should use environment variables exclusively
2. **Custom JWT/auth** — Not a production-grade library (bcrypt/Argon2 recommended for real systems)
3. **TEXT timestamps** — Not PostgreSQL TIMESTAMP type; lexicographic ordering works but lacks timezone support
4. **Cache-based search** — In-memory substring scan; sufficient for 71 books, not enterprise-scale
5. **Single-page printing** — PrinterUtil truncates content exceeding page height
6. **FlatLaf compile dependency** — `Main.java` references FlatLaf; runtime fallback exists but compile needs the JAR or a code comment

## Fixes Applied (from Weakness Analysis)

| # | Original Weakness | Fix Applied |
|---|-------------------|-------------|
| 1 | Hardcoded secrets | Environment variables via `System.getenv()` with fallbacks |
| 2 | Unsalted SHA-256 | Per-user random salt stored in `users.salt` column |
| 3 | Fixed-IV AES | Random IV per encryption, prepended to ciphertext |
| 4 | Reporting SQL bug | Date filter moved from LEFT JOIN to WHERE clause |
| 5 | OOS timestamp bug | New constructor `OOSRequest(..., storedTimestamp)` preserves DB time |
| 6 | Partial notification | `sendBulkAlertsSelective()` + `markNotifiedByEmail()` |
| 7 | Concurrency oversell | `SELECT ... FOR UPDATE` locks rows during stock check |
| 8 | No backend validation | Rejects negative qty/price/stock; DB CHECK constraints |
| 9 | Non-transactional OOS | `addOOSRequest()` wrapped in transaction |
| 10 | Procurement race | `UPDATE ... WHERE status='ORDERED' RETURNING` (atomic) |
| 11 | `updateBook` false success | Checks `executeUpdate()` row count |
| 12 | Hardcoded "MANAGER" in logs | `currentActor()` reads SessionManager |
| 13 | DB outage = empty cache | Stale-on-failure + `getLastError()` |
| 14 | Concurrent cache refresh | `AtomicBoolean` CAS guard |
| 15 | Regex filter crash | `Pattern.quote()` in InventoryPanel |
| 16 | Email password placeholder | Empty field with tooltip, not bullet characters |
| 17 | Singleton not thread-safe | `volatile` + `synchronized` double-check |
| 18 | Null-unsafe search | Null guards in `BookCache.searchByTitle/Author` |
| 19 | ISBN not normalized | `getByISBN` strips hyphens/spaces |
| 20 | `seedIfEmpty` only checks books | Now checks both books AND users |
| 21 | Empty sale accepted | `saveSaleAtomically` returns false for empty cart |
| 22 | `createProcurementOrder` trusts qty | Rejects non-positive quantity |

## Project Structure

```
SWE-project-main/
├── src/bas/             # 23 source files
├── src/bas/test/        # 16 test files (15 test classes + report generator)
├── lib/                 # JARs (postgresql, javax.mail, javax.activation)
├── compile.bat          # Windows compile script
├── compile_and_run.sh   # Linux/Mac compile + run
├── run.bat              # Windows run script
├── run_tests.bat        # Windows test runner (JUnit 5)
├── run_tests.sh         # Linux/Mac test runner
├── reset_database.sql   # SQL to clear all tables for fresh seed
└── README.md            # This file
```

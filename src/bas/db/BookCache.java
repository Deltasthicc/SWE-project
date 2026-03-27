package bas.db;

import bas.model.Book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory cache for the books catalogue.
 *
 * The full book list rarely changes (only on add/edit/sale/stock-update).
 * Instead of querying Supabase on every panel load or search,
 * we cache locally and invalidate on writes.
 *
 * TTL: 60 seconds — after which the next read triggers a background refresh.
 */
public final class BookCache {

    private static final long TTL_MS = 60_000;  // 60 seconds

    private static final BookCache INSTANCE = new BookCache();
    private final AtomicReference<List<Book>> cache = new AtomicReference<>(null);
    private final AtomicLong lastRefresh = new AtomicLong(0);

    private BookCache() {}
    public static BookCache getInstance() { return INSTANCE; }

    /** Get all books — returns cached copy if fresh, otherwise fetches from DB. */
    public List<Book> getAllBooks() {
        List<Book> cached = cache.get();
        if (cached != null && !isExpired()) {
            return cached;
        }
        return refresh();
    }

    /** Force a refresh from the database. */
    public List<Book> refresh() {
        List<Book> books = DatabaseManager.getInstance().fetchAllBooksFromDB();
        cache.set(Collections.unmodifiableList(books));
        lastRefresh.set(System.currentTimeMillis());
        return books;
    }

    /** Mark cache as stale — next read will refresh. */
    public void invalidate() {
        lastRefresh.set(0);
    }

    /** Search cached books by title (case-insensitive substring). */
    public List<Book> searchByTitle(String q) {
        String lq = q.toLowerCase();
        List<Book> result = new ArrayList<>();
        for (Book b : getAllBooks()) {
            if (b.getTitle().toLowerCase().contains(lq)) result.add(b);
        }
        return result;
    }

    /** Search cached books by author (case-insensitive substring). */
    public List<Book> searchByAuthor(String q) {
        String lq = q.toLowerCase();
        List<Book> result = new ArrayList<>();
        for (Book b : getAllBooks()) {
            if (b.getAuthor().toLowerCase().contains(lq)) result.add(b);
        }
        return result;
    }

    /** Get a single book by ISBN from cache. */
    public Book getByISBN(String isbn) {
        for (Book b : getAllBooks()) {
            if (b.getIsbn().equals(isbn)) return b;
        }
        return null;
    }

    /** Get books below restock threshold from cache. */
    public List<Book> getBooksNeedingRestock() {
        List<Book> result = new ArrayList<>();
        for (Book b : getAllBooks()) {
            if (b.getStockCount() <= b.getRestockThreshold()) result.add(b);
        }
        result.sort((a, c) -> Integer.compare(a.getStockCount(), c.getStockCount()));
        return result;
    }

    private boolean isExpired() {
        return System.currentTimeMillis() - lastRefresh.get() > TTL_MS;
    }
}

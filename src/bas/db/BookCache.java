package bas.db;

import bas.model.Book;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory cache for the books catalogue.
 * Keeps stale data on DB failure instead of caching empty list.
 * Guards against concurrent refresh storms.
 */
public final class BookCache {
    private static final long TTL_MS = 60_000;
    private static final BookCache INSTANCE = new BookCache();
    private final AtomicReference<List<Book>> cache = new AtomicReference<>(null);
    private final AtomicLong lastRefresh = new AtomicLong(0);
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile String lastError = null;

    private BookCache() {}
    public static BookCache getInstance() { return INSTANCE; }

    public List<Book> getAllBooks() {
        List<Book> cached = cache.get();
        if (cached != null && !isExpired()) return cached;
        return refresh();
    }

    public List<Book> refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            List<Book> existing = cache.get();
            return existing != null ? existing : Collections.emptyList();
        }
        try {
            List<Book> books = DatabaseManager.getInstance().fetchAllBooksFromDB();
            if (books != null && !books.isEmpty()) {
                cache.set(Collections.unmodifiableList(books));
                lastRefresh.set(System.currentTimeMillis());
                lastError = null;
                return books;
            } else {
                List<Book> existing = cache.get();
                if (existing != null && !existing.isEmpty()) {
                    lastError = "DB returned empty; keeping stale cache (" + existing.size() + " books)";
                    System.err.println("[BookCache] " + lastError);
                    return existing;
                }
                cache.set(Collections.emptyList());
                lastRefresh.set(System.currentTimeMillis());
                return Collections.emptyList();
            }
        } finally { refreshing.set(false); }
    }

    public void invalidate() { lastRefresh.set(0); }
    public String getLastError() { return lastError; }

    public List<Book> searchByTitle(String q) {
        if (q == null || q.isEmpty()) return getAllBooks();
        String lq = q.toLowerCase();
        List<Book> result = new ArrayList<>();
        for (Book b : getAllBooks()) if (b.getTitle().toLowerCase().contains(lq)) result.add(b);
        return result;
    }

    public List<Book> searchByAuthor(String q) {
        if (q == null || q.isEmpty()) return getAllBooks();
        String lq = q.toLowerCase();
        List<Book> result = new ArrayList<>();
        for (Book b : getAllBooks()) if (b.getAuthor().toLowerCase().contains(lq)) result.add(b);
        return result;
    }

    public Book getByISBN(String isbn) {
        if (isbn == null) return null;
        String clean = isbn.replaceAll("[\\s-]", "");
        for (Book b : getAllBooks()) if (b.getIsbn().equals(clean)) return b;
        return null;
    }

    public List<Book> getBooksNeedingRestock() {
        List<Book> result = new ArrayList<>();
        for (Book b : getAllBooks()) if (b.getStockCount() <= b.getRestockThreshold()) result.add(b);
        result.sort((a, c) -> Integer.compare(a.getStockCount(), c.getStockCount()));
        return result;
    }

    private boolean isExpired() { return System.currentTimeMillis() - lastRefresh.get() > TTL_MS; }
}

package bas.util;

import bas.model.LineItem;
import bas.model.SaleRecord;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.swing.*;
import java.awt.*;
import java.awt.print.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Receipt formatting and printing.
 * <p>
 * Windows' native print dialog enumerates every configured printer — including
 * offline or network ones that time out — which can freeze the UI for minutes
 * the first time it's shown. Three mitigations here:
 * <ol>
 *   <li>{@link #warmUpAsync()} pre-touches the print subsystem on a daemon
 *       thread at startup so the OS has already cached printer info by the
 *       time the user clicks "Yes, print".</li>
 *   <li>{@link #printReceiptAsync(SaleRecord, Component, Consumer)} runs the
 *       slow native dialog on a {@link SwingWorker} background thread — the
 *       rest of the UI stays responsive.</li>
 *   <li>{@link #saveReceiptToFile(SaleRecord, Component)} bypasses the print
 *       subsystem entirely and writes a plain-text receipt to disk in a few
 *       milliseconds — the demo-safe default.</li>
 * </ol>
 */
public class PrinterUtil {

    // ── Receipt formatting (unchanged API) ──────────────────────────────────

    public static String buildReceiptString(SaleRecord sale) {
        return String.join("\n", buildLines(sale));
    }

    public static List<String> buildLines(SaleRecord sale) {
        List<String> L = new ArrayList<>();
        L.add("========================================");
        L.add("   BOOKSHOP INVENTORY & SALES SYSTEM   ");
        L.add("========================================");
        L.add("Sale ID : " + sale.getSaleId());
        L.add("Date    : " + sale.getFormattedTimestamp());
        L.add("Clerk   : " + sale.getClerkId());
        L.add("----------------------------------------");
        L.add(String.format("%-22s %4s %9s", "Title", "Qty", "Subtotal"));
        L.add("----------------------------------------");
        for (LineItem it : sale.getItems()) {
            String t = it.getTitle().length() > 22
                    ? it.getTitle().substring(0, 19) + "..." : it.getTitle();
            L.add(String.format("%-22s %4d %9.2f", t, it.getQuantity(), it.getSubtotal()));
        }
        L.add("----------------------------------------");
        L.add(String.format("%-26s %9.2f", "TOTAL (INR):", sale.getTotalAmount()));
        L.add("========================================");
        L.add("       Thank you for shopping!          ");
        L.add("========================================");
        return L;
    }

    // ── Pre-warm (new) ──────────────────────────────────────────────────────

    private static final AtomicBoolean warmUpStarted = new AtomicBoolean(false);

    /**
     * Asynchronously pre-enumerate installed printers so the first real call
     * to {@code printDialog()} doesn't eat the OS-enumeration latency.
     * Idempotent — safe to call from multiple places (e.g.\ every panel
     * constructor). Daemon thread so it never blocks JVM shutdown.
     */
    public static void warmUpAsync() {
        if (!warmUpStarted.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
                // Also instantiate a PrinterJob once — some of its state is cached.
                PrinterJob.getPrinterJob();
                long dt = System.currentTimeMillis() - t0;
                System.out.println("[PrinterUtil] Print subsystem warmed up in "
                    + dt + "ms (" + services.length + " printer(s) detected).");
            } catch (Throwable err) {
                System.err.println("[PrinterUtil] Warm-up failed (non-fatal): " + err.getMessage());
            }
        }, "bas-printer-warmup");
        t.setDaemon(true);
        t.start();
    }

    // ── Save to file (new) — demo-friendly alternative ──────────────────────

    /**
     * Writes a plain-text receipt file to disk. Completes in ~5 ms, never hits
     * the print subsystem, so it's the best option during a live demo.
     * Returns the saved file path, or null if the user cancelled.
     */
    public static File saveReceiptToFile(SaleRecord sale, Component parent) {
        String content = buildReceiptString(sale);
        String defaultName = "Receipt-" + sale.getSaleId() + ".txt";

        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Save Receipt");
        ch.setSelectedFile(new File(System.getProperty("user.home"), defaultName));
        if (ch.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;

        File target = ch.getSelectedFile();
        try {
            Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
            return target;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                "Save failed: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /**
     * Non-interactive variant — dumps straight to {@code ~/Documents} (or the
     * working directory if that doesn't exist) without asking the user. Used
     * for fully-automated flows. Returns the Path written, or null on failure.
     */
    public static Path saveReceiptSilently(SaleRecord sale) {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), "Documents");
            if (!Files.isDirectory(dir)) dir = Paths.get(System.getProperty("user.dir"));
            Path out = dir.resolve("Receipt-" + sale.getSaleId() + ".txt");
            Files.write(out, buildReceiptString(sale).getBytes(StandardCharsets.UTF_8));
            return out;
        } catch (IOException ex) {
            System.err.println("[PrinterUtil] Silent save failed: " + ex.getMessage());
            return null;
        }
    }

    // ── Printing ────────────────────────────────────────────────────────────

    /**
     * Synchronous print (legacy API — used by OwnerPanel reprint). Blocks
     * the calling thread while the OS enumerates printers. Prefer
     * {@link #printReceiptAsync} for new call sites so the EDT stays free.
     */
    public static boolean printReceipt(SaleRecord sale) {
        return printLines(buildLines(sale), "Receipt-" + sale.getSaleId());
    }

    /**
     * Async print: runs {@link #printReceipt(SaleRecord)} on a background
     * SwingWorker and invokes {@code onDone} on the EDT with the result.
     * The rest of the UI stays responsive even if the OS takes minutes to
     * open the native print dialog.
     */
    public static void printReceiptAsync(SaleRecord sale, Component parent, Consumer<Boolean> onDone) {
        // Lightweight modeless feedback — a title-only dialog that closes itself
        // when printing is done. We deliberately don't make it modal, otherwise
        // the EDT blocks here too.
        final JDialog feedback = new JDialog(
            parent == null ? null : SwingUtilities.getWindowAncestor(parent),
            "Preparing Printer", JDialog.ModalityType.MODELESS);
        JLabel lbl = new JLabel("  Opening system print dialog — this may take a few seconds on Windows…  ");
        lbl.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        feedback.add(lbl);
        feedback.pack();
        feedback.setLocationRelativeTo(parent);
        feedback.setVisible(true);

        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() { return printReceipt(sale); }
            @Override protected void done() {
                feedback.dispose();
                try { if (onDone != null) onDone.accept(get()); }
                catch (Exception ex) { if (onDone != null) onDone.accept(false); }
            }
        }.execute();
    }

    public static boolean printTextReport(String content, String jobTitle) {
        List<String> lines = new ArrayList<>();
        for (String s : content.split("\n")) lines.add(s);
        return printLines(lines, jobTitle);
    }

    // ── Internal rendering ──────────────────────────────────────────────────

    private static boolean printLines(List<String> lines, String jobName) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(jobName);
        final List<String> fl = lines;
        job.setPrintable((g, pf, pi) -> {
            if (pi > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) g;
            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            int lh = g2.getFontMetrics().getHeight();
            double x = pf.getImageableX(), y = pf.getImageableY() + lh;
            for (String s : fl) {
                g2.drawString(s, (float) x, (float) y);
                y += lh;
                if (y > pf.getImageableY() + pf.getImageableHeight()) break;
            }
            return Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) {
            try { job.print(); return true; }
            catch (PrinterException e) { e.printStackTrace(); }
        }
        return false;
    }
}

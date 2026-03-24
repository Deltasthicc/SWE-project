package bas.util;

import bas.model.LineItem;
import bas.model.SaleRecord;

import java.awt.*;
import java.awt.print.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PrinterUtil {

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

    public static boolean printReceipt(SaleRecord sale) {
        return printLines(buildLines(sale), "Receipt-" + sale.getSaleId());
    }

    public static boolean printTextReport(String content, String jobTitle) {
        List<String> lines = new ArrayList<>();
        for (String s : content.split("\n")) lines.add(s);
        return printLines(lines, jobTitle);
    }

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

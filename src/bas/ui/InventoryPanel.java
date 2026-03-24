package bas.ui;

import bas.db.DatabaseManager;
import bas.model.Book;
import bas.service.EmailService;
import bas.util.ISBNValidator;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Inventory management panel (F4, FR-4.1) for Manager and Owner.
 */
public class InventoryPanel extends JPanel {

    private final String managerId;
    private JTable table;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField filterField;
    private JLabel statusLbl;

    private static final String[] COLS = {
        "ISBN","Title","Author","Publisher","Price ₹","Rack",
        "Stock","Threshold","Req.","Wk Sales","Lead(wk)"
    };

    public InventoryPanel(String managerId) {
        this.managerId = managerId;
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        build();
        load();
    }

    private void build() {

        // ── Toolbar ───────────────────────────────────────────────────────────
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        tb.setBackground(Color.WHITE);
        tb.setBorder(new TitledBorder("Inventory Controls"));

        filterField = new JTextField(18);
        filterField.setToolTipText("Filter by title, author or ISBN");
        JButton filterBtn = btn("Filter", new Color(60, 90, 180), Color.WHITE);
        JButton clearFilt = btn("Clear", Color.LIGHT_GRAY, Color.BLACK);

        JButton refreshBtn    = btn("↻ Refresh",       new Color(60, 120, 60),  Color.WHITE);
        JButton addBtn        = btn("+ Add Book",       new Color(30, 130, 50),  Color.WHITE);
        JButton stockBtn      = btn("📦 Update Stock",  new Color(180, 110, 20), Color.WHITE);
        JButton editBtn       = btn("✏ Edit",           new Color(50, 80, 160),  Color.WHITE);
        JButton notifBtn      = btn("📧 Send Alerts",   new Color(140, 30, 140), Color.WHITE);

        stockBtn.setEnabled(false); editBtn.setEnabled(false); notifBtn.setEnabled(false);

        tb.add(new JLabel("Filter:")); tb.add(filterField);
        tb.add(filterBtn); tb.add(clearFilt);
        tb.add(new JSeparator(SwingConstants.VERTICAL));
        tb.add(refreshBtn); tb.add(addBtn); tb.add(stockBtn); tb.add(editBtn); tb.add(notifBtn);
        add(tb, BorderLayout.NORTH);

        // Legend
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        legend.setBackground(Color.WHITE);
        legend.add(swatch(new Color(255, 200, 200))); legend.add(new JLabel("Out of Stock"));
        legend.add(swatch(new Color(255, 240, 180))); legend.add(new JLabel("Low / At Threshold"));
        legend.add(swatch(new Color(200, 240, 210))); legend.add(new JLabel("Normal Stock"));

        // ── Table ─────────────────────────────────────────────────────────────
        model = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.setGridColor(new Color(225, 225, 225));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                if (!sel) {
                    int mRow = t.convertRowIndexToModel(row);
                    int stock = intVal(model.getValueAt(mRow, 6));
                    int thresh= intVal(model.getValueAt(mRow, 7));
                    c.setBackground(stock <= 0     ? new Color(255, 200, 200)
                                  : stock <= thresh ? new Color(255, 240, 180)
                                  :                   new Color(200, 240, 210));
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(table,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(new TitledBorder("Books Catalogue"));

        JPanel centre = new JPanel(new BorderLayout(4,4));
        centre.add(legend, BorderLayout.NORTH);
        centre.add(scroll,  BorderLayout.CENTER);
        add(centre, BorderLayout.CENTER);

        // ── Status ────────────────────────────────────────────────────────────
        statusLbl = new JLabel(" ");
        statusLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
        statusLbl.setBorder(new EmptyBorder(4,4,4,4));
        add(statusLbl, BorderLayout.SOUTH);

        // ── Listeners ─────────────────────────────────────────────────────────
        refreshBtn.addActionListener(e -> load());
        addBtn.addActionListener(e -> openAddDialog());

        filterBtn.addActionListener(e -> applyFilter());
        filterField.addActionListener(e -> applyFilter());
        clearFilt.addActionListener(e -> { filterField.setText(""); sorter.setRowFilter(null); });

        stockBtn.addActionListener(e -> openStockUpdate());
        editBtn.addActionListener(e -> openEdit());
        notifBtn.addActionListener(e -> sendAlerts());

        table.getSelectionModel().addListSelectionListener(e -> {
            boolean sel = table.getSelectedRow() >= 0;
            stockBtn.setEnabled(sel); editBtn.setEnabled(sel); notifBtn.setEnabled(sel);
        });
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void load() {
        new SwingWorker<List<Book>, Void>() {
            @Override protected List<Book> doInBackground() {
                return DatabaseManager.getInstance().getAllBooks();
            }
            @Override protected void done() {
                try {
                    List<Book> books = get();
                    model.setRowCount(0);
                    for (Book b : books) model.addRow(new Object[]{
                        b.getIsbn(), b.getTitle(), b.getAuthor(), b.getPublisher(),
                        String.format("%.2f", b.getUnitPrice()), b.getRackLocation(),
                        b.getStockCount(), b.getRestockThreshold(),
                        b.getRequestCount(), String.format("%.1f", b.getWeeklySales()),
                        b.getProcurementLeadTimeWeeks()});
                    status("Loaded " + books.size() + " books.");
                } catch (Exception ex) { status("Load error: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void applyFilter() {
        String t = filterField.getText().trim();
        sorter.setRowFilter(t.isEmpty() ? null : RowFilter.regexFilter("(?i)" + t));
    }

    // ── Update stock on arrival ───────────────────────────────────────────────

    private void openStockUpdate() {
        int row = table.getSelectedRow(); if (row < 0) return;
        int mr  = table.convertRowIndexToModel(row);
        String isbn  = model.getValueAt(mr,0).toString();
        String title = model.getValueAt(mr,1).toString();
        int curr     = intVal(model.getValueAt(mr,6));

        String input = JOptionPane.showInputDialog(this,
            "<html><b>Update Stock on Arrival</b><br>" +
            title + "<br>ISBN: " + isbn + "<br>Current stock: " + curr + "<br><br>" +
            "Enter incoming quantity:</html>",
            "Update Stock", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.isBlank()) return;

        int qty;
        try { qty = Integer.parseInt(input.trim());
              if (qty <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) { status("Enter a positive number."); return; }

        if (DatabaseManager.getInstance().addStock(isbn, qty, managerId)) {
            status("Stock updated: +" + qty + " for \"" + title + "\"");
            load();

            // Offer to send restock emails if there are pending OOS requests
            List<String> emails = DatabaseManager.getInstance().getPendingEmails(isbn);
            if (!emails.isEmpty()) {
                int ans = JOptionPane.showConfirmDialog(this,
                    emails.size() + " customer(s) are waiting for \"" + title + "\".\n" +
                    "Send restock notification emails now?",
                    "Send Restock Alerts", JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    if (!EmailService.isConfigured()) {
                        JOptionPane.showMessageDialog(this,
                            "SMTP not configured.\nGo to Owner Panel → Email Settings.",
                            "Email Not Configured", JOptionPane.WARNING_MESSAGE);
                    } else {
                        int sent = EmailService.sendBulkAlerts(emails, title, isbn);
                        DatabaseManager.getInstance().markNotified(isbn);
                        status("Sent " + sent + "/" + emails.size() + " restock email(s).");
                    }
                }
            }
        } else { status("Failed to update stock."); }
    }

    // ── Add book ──────────────────────────────────────────────────────────────

    private void openAddDialog() {
        JDialog d = dialog("Add New Book");

        JTextField isbnF  = new JTextField(22), titleF = new JTextField(22),
                   authF  = new JTextField(22), pubF   = new JTextField(22),
                   paddrF = new JTextField(22), priceF = new JTextField("0.00",10),
                   rackF  = new JTextField(8),  stockF = new JTextField("0",6),
                   threshF= new JTextField("5",6), leadF = new JTextField("2",6),
                   wkSalF = new JTextField("0.0",6);

        JPanel form = formPanel(new String[]{
            "ISBN (10/13):","Title:","Author:","Publisher:",
            "Publisher Address:","Unit Price (₹):","Rack Location:",
            "Initial Stock:","Restock Threshold:","Lead Time (wks):","Weekly Sales:"
        }, new JTextField[]{
            isbnF,titleF,authF,pubF,paddrF,priceF,rackF,stockF,threshF,leadF,wkSalF
        });

        JLabel errLbl = errLabel();
        JButton save = btn("Save", new Color(30,130,50), Color.WHITE);
        JButton cancel = new JButton("Cancel");

        d.add(errLbl, BorderLayout.NORTH);
        d.add(form,   BorderLayout.CENTER);
        d.add(btnsPanel(cancel, save), BorderLayout.SOUTH);
        cancel.addActionListener(e -> d.dispose());

        save.addActionListener(e -> {
            try {
                String isbnErr = ISBNValidator.getValidationError(isbnF.getText().trim());
                if (isbnErr != null) { errLbl.setText(isbnErr); return; }
                if (titleF.getText().isBlank()) { errLbl.setText("Title required."); return; }
                if (authF.getText().isBlank())  { errLbl.setText("Author required."); return; }
                if (pubF.getText().isBlank())   { errLbl.setText("Publisher required."); return; }

                Book b = new Book(
                    ISBNValidator.clean(isbnF.getText()),
                    titleF.getText().trim(), authF.getText().trim(),
                    pubF.getText().trim(),   paddrF.getText().trim(),
                    Double.parseDouble(priceF.getText().trim()),
                    rackF.getText().trim(),
                    Integer.parseInt(stockF.getText().trim()),
                    Integer.parseInt(threshF.getText().trim()), 0,
                    Double.parseDouble(wkSalF.getText().trim()),
                    Integer.parseInt(leadF.getText().trim()));

                if (DatabaseManager.getInstance().addBook(b)) {
                    status("Book added: " + b.getTitle()); load(); d.dispose();
                } else { errLbl.setText("Failed — ISBN may already exist."); }
            } catch (NumberFormatException ex) {
                errLbl.setText("Price, stock, threshold, lead time and weekly sales must be numbers.");
            }
        });

        d.pack(); d.setMinimumSize(new Dimension(480, 400));
        d.setLocationRelativeTo(this); d.setVisible(true);
    }

    // ── Edit book ─────────────────────────────────────────────────────────────

    private void openEdit() {
        int row = table.getSelectedRow(); if (row < 0) return;
        String isbn = model.getValueAt(table.convertRowIndexToModel(row),0).toString();
        Book b = DatabaseManager.getInstance().getByISBN(isbn);
        if (b == null) { status("Could not load book."); return; }

        JDialog d = dialog("Edit Book — " + isbn);

        JTextField titleF = new JTextField(b.getTitle(),22);
        JTextField authF  = new JTextField(b.getAuthor(),22);
        JTextField pubF   = new JTextField(b.getPublisher(),22);
        JTextField paddrF = new JTextField(nullSafe(b.getPublisherAddress()),22);
        JTextField priceF = new JTextField(String.format("%.2f",b.getUnitPrice()),10);
        JTextField rackF  = new JTextField(nullSafe(b.getRackLocation()),8);
        JTextField stockF = new JTextField(String.valueOf(b.getStockCount()),6);
        JTextField threshF= new JTextField(String.valueOf(b.getRestockThreshold()),6);
        JTextField leadF  = new JTextField(String.valueOf(b.getProcurementLeadTimeWeeks()),6);
        JTextField wkSalF = new JTextField(String.format("%.1f",b.getWeeklySales()),6);

        JPanel form = formPanel(new String[]{
            "Title:","Author:","Publisher:","Publisher Address:",
            "Unit Price (₹):","Rack Location:","Stock Count:",
            "Restock Threshold:","Lead Time (wks):","Weekly Sales:"
        }, new JTextField[]{
            titleF,authF,pubF,paddrF,priceF,rackF,stockF,threshF,leadF,wkSalF
        });

        JLabel errLbl = errLabel();
        JButton save = btn("Save", new Color(30,130,50), Color.WHITE);
        JButton cancel = new JButton("Cancel");

        d.add(errLbl, BorderLayout.NORTH);
        d.add(form,   BorderLayout.CENTER);
        d.add(btnsPanel(cancel, save), BorderLayout.SOUTH);
        cancel.addActionListener(e -> d.dispose());

        save.addActionListener(e -> {
            try {
                b.setTitle(titleF.getText().trim()); b.setAuthor(authF.getText().trim());
                b.setPublisher(pubF.getText().trim()); b.setPublisherAddress(paddrF.getText().trim());
                b.setUnitPrice(Double.parseDouble(priceF.getText().trim()));
                b.setRackLocation(rackF.getText().trim());
                b.setStockCount(Integer.parseInt(stockF.getText().trim()));
                b.setRestockThreshold(Integer.parseInt(threshF.getText().trim()));
                b.setProcurementLeadTimeWeeks(Integer.parseInt(leadF.getText().trim()));
                b.setWeeklySales(Double.parseDouble(wkSalF.getText().trim()));

                if (DatabaseManager.getInstance().updateBook(b)) {
                    status("Updated: " + b.getTitle()); load(); d.dispose();
                } else { errLbl.setText("Update failed."); }
            } catch (NumberFormatException ex) {
                errLbl.setText("Numeric fields must be valid numbers.");
            }
        });

        d.pack(); d.setMinimumSize(new Dimension(480, 370));
        d.setLocationRelativeTo(this); d.setVisible(true);
    }

    // ── Send restock alerts ───────────────────────────────────────────────────

    private void sendAlerts() {
        int row = table.getSelectedRow(); if (row < 0) return;
        int mr  = table.convertRowIndexToModel(row);
        String isbn  = model.getValueAt(mr,0).toString();
        String title = model.getValueAt(mr,1).toString();
        List<String> emails = DatabaseManager.getInstance().getPendingEmails(isbn);
        if (emails.isEmpty()) {
            JOptionPane.showMessageDialog(this,"No pending requests for this book.","No Requests",
                JOptionPane.INFORMATION_MESSAGE); return;
        }
        if (!EmailService.isConfigured()) {
            JOptionPane.showMessageDialog(this,
                "SMTP not configured. Go to Owner Panel → Email Settings.",
                "Not Configured", JOptionPane.WARNING_MESSAGE); return;
        }
        int sent = EmailService.sendBulkAlerts(emails, title, isbn);
        if (sent > 0) DatabaseManager.getInstance().markNotified(isbn);
        status("Sent "+sent+"/"+emails.size()+" restock alert(s).");
    }

    // ── Form / Dialog helpers ─────────────────────────────────────────────────

    private JDialog dialog(String title) {
        JDialog d = new JDialog((Frame)SwingUtilities.getWindowAncestor(this), title, true);
        d.setLayout(new BorderLayout(8, 8)); return d;
    }

    private JPanel formPanel(String[] labels, JTextField[] fields) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(12, 16, 8, 16));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4,6,4,6); g.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < labels.length; i++) {
            g.gridx = 0; g.gridy = i; g.weightx = 0; p.add(new JLabel(labels[i]), g);
            g.gridx = 1; g.weightx = 1; p.add(fields[i], g);
        }
        return p;
    }

    private JPanel btnsPanel(JButton cancel, JButton save) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(cancel); p.add(save); return p;
    }

    private JLabel errLabel() {
        JLabel l = new JLabel(" ", SwingConstants.CENTER);
        l.setForeground(Color.RED); l.setFont(new Font("SansSerif",Font.PLAIN,11));
        return l;
    }

    private JButton btn(String txt, Color bg, Color fg) {
        JButton b = new JButton(txt);
        b.setBackground(bg); b.setForeground(fg);
        b.setFocusPainted(false); b.setOpaque(true); b.setBorderPainted(false);
        return b;
    }

    private JPanel swatch(Color c) {
        JPanel p = new JPanel(); p.setBackground(c);
        p.setPreferredSize(new Dimension(16,14));
        p.setBorder(BorderFactory.createLineBorder(Color.GRAY)); return p;
    }

    private int intVal(Object o) {
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    private String nullSafe(String s) { return s == null ? "" : s; }
    private void status(String msg) { statusLbl.setText(msg); }
}

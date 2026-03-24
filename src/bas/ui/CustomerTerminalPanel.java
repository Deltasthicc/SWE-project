package bas.ui;

import bas.db.DatabaseManager;
import bas.model.Book;
import bas.model.OOSRequest;
import bas.util.EmailValidator;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Customer search terminal (F1 + F2).
 * No login required. Search by title/author, see stock + rack, submit OOS requests.
 */
public class CustomerTerminalPanel extends JPanel {

    private JTextField    searchField;
    private JRadioButton  byTitle, byAuthor;
    private JTable        table;
    private DefaultTableModel model;
    private JLabel        statusLbl;
    private JButton       searchBtn, oosBtn;

    private static final String[] COLS =
        {"ISBN", "Title", "Author", "Price ₹", "Stock", "Rack", "Status"};

    public CustomerTerminalPanel() {
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        build();
        // Auto-load all books on open
        SwingUtilities.invokeLater(this::loadAll);
    }

    private void build() {

        // ── Search bar ────────────────────────────────────────────────────────
        JPanel top = new JPanel(new BorderLayout(8, 6));
        top.setBackground(Color.WHITE);

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        searchRow.setBackground(Color.WHITE);

        searchField = new JTextField(26);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        searchField.setToolTipText("Search by book title or author name");

        byTitle  = new JRadioButton("By Title",  true);
        byAuthor = new JRadioButton("By Author");
        byTitle.setBackground(Color.WHITE);
        byAuthor.setBackground(Color.WHITE);
        ButtonGroup bg = new ButtonGroup();
        bg.add(byTitle); bg.add(byAuthor);

        searchBtn = new JButton("🔍  Search");
        searchBtn.setBackground(new Color(30, 100, 210));
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setFocusPainted(false);
        searchBtn.setOpaque(true);
        searchBtn.setBorderPainted(false);

        JButton clearBtn = new JButton("Show All");
        clearBtn.addActionListener(e -> { searchField.setText(""); loadAll(); });

        searchRow.add(new JLabel("🔎"));
        searchRow.add(searchField);
        searchRow.add(byTitle);
        searchRow.add(byAuthor);
        searchRow.add(searchBtn);
        searchRow.add(clearBtn);

        top.add(searchRow, BorderLayout.NORTH);

        // Legend
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        legend.setBackground(Color.WHITE);
        legend.add(colorSwatch(new Color(200, 240, 210))); legend.add(new JLabel("In Stock"));
        legend.add(colorSwatch(new Color(255, 240, 180))); legend.add(new JLabel("Low Stock"));
        legend.add(colorSwatch(new Color(255, 200, 200))); legend.add(new JLabel("Out of Stock"));
        top.add(legend, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        // ── Table ─────────────────────────────────────────────────────────────
        model = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.setGridColor(new Color(230, 230, 230));
        table.setShowGrid(true);

        // Colour rows by stock status
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t,v,sel,focus,row,col);
                if (!sel) {
                    String status = safeStr(model.getValueAt(row, 6));
                    if ("OUT OF STOCK".equals(status))
                        c.setBackground(new Color(255, 200, 200));
                    else if ("LOW STOCK".equals(status))
                        c.setBackground(new Color(255, 240, 180));
                    else
                        c.setBackground(new Color(200, 240, 210));
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200,200,200)),
            "Available Books  —  click a row then use the button below",
            TitledBorder.LEFT, TitledBorder.TOP));
        add(scroll, BorderLayout.CENTER);

        // ── Bottom bar ────────────────────────────────────────────────────────
        JPanel bottom = new JPanel(new BorderLayout(10, 4));
        bottom.setBackground(Color.WHITE);
        bottom.setBorder(new EmptyBorder(6, 0, 0, 0));

        statusLbl = new JLabel("Showing all books. Click a row to select.", SwingConstants.LEFT);
        statusLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
        statusLbl.setForeground(new Color(80, 80, 80));

        oosBtn = new JButton("📩  Request Notification / Out-of-Stock Alert");
        oosBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        oosBtn.setBackground(new Color(200, 100, 20));
        oosBtn.setForeground(Color.WHITE);
        oosBtn.setFocusPainted(false);
        oosBtn.setOpaque(true);
        oosBtn.setBorderPainted(false);
        oosBtn.setToolTipText(
            "Select a book row above OR click here to request a book not in our catalogue");
        oosBtn.addActionListener(e -> openOOS());

        bottom.add(statusLbl, BorderLayout.CENTER);
        bottom.add(oosBtn, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        // ── Listeners ─────────────────────────────────────────────────────────
        searchBtn.addActionListener(e -> doSearch());
        searchField.addActionListener(e -> doSearch()); // Enter key
    }

    // ── Load all books ────────────────────────────────────────────────────────

    private void loadAll() {
        statusLbl.setText("Loading books…");
        new SwingWorker<List<Book>, Void>() {
            @Override protected List<Book> doInBackground() {
                return DatabaseManager.getInstance().getAllBooks();
            }
            @Override protected void done() {
                try {
                    fill(get());
                    statusLbl.setText("Showing all " + model.getRowCount() + " books.");
                } catch (Exception ex) {
                    statusLbl.setText("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void doSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) { loadAll(); return; }

        searchBtn.setEnabled(false);
        statusLbl.setText("Searching…");

        new SwingWorker<List<Book>, Void>() {
            @Override protected List<Book> doInBackground() {
                DatabaseManager db = DatabaseManager.getInstance();
                return byTitle.isSelected()
                    ? db.searchByTitle(q) : db.searchByAuthor(q);
            }
            @Override protected void done() {
                try {
                    List<Book> books = get();
                    fill(books);
                    statusLbl.setText(books.isEmpty()
                        ? "No results for \"" + q + "\". Use the button below to request it."
                        : books.size() + " result(s) for \"" + q + "\".");
                } catch (Exception ex) {
                    statusLbl.setText("Search error: " + ex.getMessage());
                } finally {
                    searchBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private void fill(List<Book> books) {
        model.setRowCount(0);
        for (Book b : books) {
            String status = b.getStockCount() <= 0  ? "OUT OF STOCK"
                          : b.needsRestock()        ? "LOW STOCK"
                          :                           "IN STOCK";
            model.addRow(new Object[]{
                b.getIsbn(), b.getTitle(), b.getAuthor(),
                String.format("%.0f", b.getUnitPrice()),
                b.getStockCount(), b.getRackLocation(), status
            });
        }
    }

    // ── OOS Request dialog ────────────────────────────────────────────────────

    private void openOOS() {
        // Pre-fill from selected row if any
        int row = table.getSelectedRow();
        String isbn = "", title = "", author = "", pub = "";
        if (row >= 0) {
            isbn  = safeStr(model.getValueAt(row, 0));
            title = safeStr(model.getValueAt(row, 1));
            author= safeStr(model.getValueAt(row, 2));
        }

        JDialog dlg = new JDialog((Frame)SwingUtilities.getWindowAncestor(this),
                "Request Out-of-Stock Book / Register Restock Alert", true);
        dlg.setLayout(new BorderLayout(10, 10));

        JPanel info = new JPanel();
        info.setBackground(new Color(255, 248, 220));
        info.setBorder(new EmptyBorder(8, 14, 8, 14));
        JLabel infoLbl = new JLabel(
            "<html><b>Fill in book details.</b> If the book is out of stock, " +
            "add your email to get notified when it's back!</html>");
        infoLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        info.add(infoLbl);
        dlg.add(info, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 20, 8, 20));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill   = GridBagConstraints.HORIZONTAL;

        JTextField isbnF  = row(form, g, 0, "ISBN:",          isbn);
        JTextField titleF = row(form, g, 1, "Book Title:*",   title);
        JTextField authF  = row(form, g, 2, "Author:*",       author);
        JTextField pubF   = row(form, g, 3, "Publisher:*",    pub);
        JTextField emailF = row(form, g, 4,
            "Your Email (optional — for restock alert):", "");
        dlg.add(form, BorderLayout.CENTER);

        JLabel errLbl = new JLabel(" ", SwingConstants.CENTER);
        errLbl.setForeground(Color.RED);
        errLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JPanel btns = new JPanel(new BorderLayout(8,4));
        btns.setBorder(new EmptyBorder(0,14,12,14));
        btns.add(errLbl, BorderLayout.NORTH);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        JButton submit = new JButton("Submit Request");
        submit.setBackground(new Color(200, 100, 20));
        submit.setForeground(Color.WHITE);
        submit.setFocusPainted(false);
        submit.setOpaque(true); submit.setBorderPainted(false);
        btnRow.add(cancel); btnRow.add(submit);
        btns.add(btnRow, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);

        cancel.addActionListener(e -> dlg.dispose());

        submit.addActionListener(e -> {
            String iv = isbnF.getText().trim();
            String tv = titleF.getText().trim();
            String av = authF.getText().trim();
            String pv = pubF.getText().trim();
            String ev = emailF.getText().trim();

            if (tv.isEmpty() || av.isEmpty() || pv.isEmpty()) {
                errLbl.setText("Title, Author and Publisher are required (*)."); return;
            }
            if (!ev.isEmpty() && !EmailValidator.isValid(ev)) {
                errLbl.setText(EmailValidator.getValidationError(ev)); return;
            }

            OOSRequest req = new OOSRequest(
                DatabaseManager.newReqId(),
                iv.isEmpty() ? "UNKNOWN" : iv,
                tv, av, pv,
                ev.isEmpty() ? null : EmailValidator.normalize(ev));

            if (DatabaseManager.getInstance().addOOSRequest(req)) {
                dlg.dispose();
                JOptionPane.showMessageDialog(CustomerTerminalPanel.this,
                    "<html><b>Request submitted!</b><br><br>" +
                    (ev.isEmpty() ? "We'll note your interest." :
                        "We'll email <b>" + ev + "</b> when <b>" + tv + "</b> is back in stock.") +
                    "</html>",
                    "Request Confirmed", JOptionPane.INFORMATION_MESSAGE);
                statusLbl.setText("OOS request submitted for: " + tv);
            } else {
                errLbl.setText("Save failed — please try again.");
            }
        });

        dlg.pack();
        dlg.setMinimumSize(new Dimension(460, 320));
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JTextField row(JPanel p, GridBagConstraints g, int r, String lbl, String val) {
        g.gridx = 0; g.gridy = r; g.weightx = 0;
        p.add(new JLabel(lbl), g);
        g.gridx = 1; g.weightx = 1;
        JTextField f = new JTextField(val, 24);
        p.add(f, g); return f;
    }

    private JPanel colorSwatch(Color c) {
        JPanel p = new JPanel();
        p.setBackground(c);
        p.setPreferredSize(new Dimension(16, 14));
        p.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return p;
    }

    private String safeStr(Object o) { return o == null ? "" : o.toString(); }
}

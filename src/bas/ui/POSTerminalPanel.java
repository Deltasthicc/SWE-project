package bas.ui;

import bas.auth.SessionManager;
import bas.db.DatabaseManager;
import bas.model.Book;
import bas.model.LineItem;
import bas.model.SaleRecord;
import bas.model.User;
import bas.util.ISBNValidator;
import bas.util.PrinterUtil;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;

/**
 * POS Terminal (F3) for Sales Clerk / Manager / Owner.
 * JWT session validated before confirming sales.
 */
public class POSTerminalPanel extends JPanel {

    private final String clerkId;

    private JTextField    isbnField;
    private JSpinner      qtySpinner;
    private JButton       addBtn, removeBtn, clearBtn, confirmBtn;
    private JTable        billTable;
    private DefaultTableModel billModel;
    private JLabel        totalLbl, statusLbl;
    private JTextArea     receiptArea;

    private SaleRecord    currentSale;

    private static final String[] COLS = {"#","ISBN","Title","Qty","Unit (INR)","Subtotal (INR)"};

    public POSTerminalPanel(String clerkId) {
        this.clerkId     = clerkId;
        this.currentSale = newSale();
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        build();
    }

    private SaleRecord newSale() {
        return new SaleRecord(DatabaseManager.newSaleId(), clerkId);
    }

    private void build() {
        // ── ISBN input ─────────────────────────────────────────────────────
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.setBackground(Color.WHITE);
        top.setBorder(new CompoundBorder(
            new TitledBorder("Add Item  (type or scan ISBN — press Enter)"),
            new EmptyBorder(4, 6, 4, 6)));

        isbnField = new JTextField(20);
        isbnField.setFont(new Font("Monospaced", Font.BOLD, 15));
        isbnField.setToolTipText("ISBN-10 or ISBN-13");

        qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        ((JSpinner.DefaultEditor) qtySpinner.getEditor()).getTextField().setColumns(4);

        addBtn = btn("Add", new Color(22, 163, 74), Color.WHITE);

        JButton q1 = quickBtn("Atomic Habits",   "9781982173593");
        JButton q2 = quickBtn("The Alchemist",    "9780062315007");
        JButton q3 = quickBtn("Hunger Games",     "9780439023481");

        top.add(new JLabel("ISBN:")); top.add(isbnField);
        top.add(new JLabel("Qty:")); top.add(qtySpinner);
        top.add(addBtn);
        top.add(new JSeparator(SwingConstants.VERTICAL));
        top.add(new JLabel("Quick-add:"));
        top.add(q1); top.add(q2); top.add(q3);
        add(top, BorderLayout.NORTH);

        // ── Split: bill (left) + receipt preview (right) ──────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.62);
        split.setDividerSize(5);

        billModel = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        billTable = new JTable(billModel);
        billTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        billTable.setRowHeight(28);
        billTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        billTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        billTable.setGridColor(new Color(226, 232, 240));

        DefaultTableCellRenderer ra = new DefaultTableCellRenderer();
        ra.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int col : new int[]{3,4,5}) billTable.getColumnModel().getColumn(col).setCellRenderer(ra);

        JScrollPane billScroll = new JScrollPane(billTable);
        billScroll.setBorder(new TitledBorder("Current Bill"));
        split.setLeftComponent(billScroll);

        receiptArea = new JTextArea();
        receiptArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        receiptArea.setEditable(false);
        receiptArea.setBackground(new Color(250, 250, 245));
        receiptArea.setMargin(new Insets(8,8,8,8));
        JScrollPane rScroll = new JScrollPane(receiptArea);
        rScroll.setBorder(new TitledBorder("Receipt Preview"));
        split.setRightComponent(rScroll);

        add(split, BorderLayout.CENTER);

        // ── Bottom bar ─────────────────────────────────────────────────────
        JPanel bot = new JPanel(new BorderLayout(10, 6));
        bot.setBackground(Color.WHITE);
        bot.setBorder(new EmptyBorder(8, 0, 0, 0));

        statusLbl = new JLabel(" ");
        statusLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
        statusLbl.setForeground(new Color(100, 116, 139));

        totalLbl = new JLabel("TOTAL:  INR 0.00", SwingConstants.RIGHT);
        totalLbl.setFont(new Font("SansSerif", Font.BOLD, 20));
        totalLbl.setForeground(new Color(37, 99, 235));

        JPanel btnsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnsRight.setBackground(Color.WHITE);
        removeBtn  = btn("Remove",          new Color(180, 83, 9),   Color.WHITE);
        clearBtn   = btn("Clear Bill",      new Color(220, 38, 38),  Color.WHITE);
        confirmBtn = btn("Confirm & Print", new Color(22, 163, 74),  Color.WHITE);
        confirmBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        confirmBtn.setEnabled(false);
        removeBtn.setEnabled(false);

        btnsRight.add(removeBtn); btnsRight.add(clearBtn); btnsRight.add(confirmBtn);

        bot.add(statusLbl,  BorderLayout.WEST);
        bot.add(totalLbl,   BorderLayout.CENTER);
        bot.add(btnsRight,  BorderLayout.EAST);
        add(bot, BorderLayout.SOUTH);

        // ── Listeners ──────────────────────────────────────────────────────
        addBtn.addActionListener(e -> addItem());
        isbnField.addActionListener(e -> addItem());

        removeBtn.addActionListener(e -> {
            int r = billTable.getSelectedRow();
            if (r >= 0) { currentSale.removeItem(billModel.getValueAt(r,1).toString()); refresh(); }
        });

        clearBtn.addActionListener(e -> {
            if (currentSale.getItems().isEmpty()) return;
            if (JOptionPane.showConfirmDialog(this, "Clear the bill?", "Confirm",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                currentSale = newSale(); refresh(); receiptArea.setText(""); status("Bill cleared.");
            }
        });

        confirmBtn.addActionListener(e -> confirmSale());

        billTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) removeBtn.setEnabled(billTable.getSelectedRow() >= 0);
        });
    }

    // ── Add item ──────────────────────────────────────────────────────────────

    private void addItem() {
        String raw = isbnField.getText().trim();
        if (raw.isEmpty()) { status("Please enter an ISBN."); return; }

        String err = ISBNValidator.getValidationError(raw);
        if (err != null) { status("ISBN Error: " + err); isbnField.selectAll(); return; }

        int qty = (Integer) qtySpinner.getValue();
        String isbn = ISBNValidator.clean(raw);
        addBtn.setEnabled(false);

        new SwingWorker<Book, Void>() {
            @Override protected Book doInBackground() {
                return DatabaseManager.getInstance().getByISBN(isbn);
            }
            @Override protected void done() {
                try {
                    Book b = get();
                    if (b == null) { status("ISBN " + isbn + " not found in inventory."); return; }
                    if (b.getStockCount() < qty) {
                        status("Only " + b.getStockCount() + " copies of \"" + b.getTitle() + "\" in stock.");
                        return;
                    }
                    currentSale.addItem(new LineItem(isbn, b.getTitle(), qty, b.getUnitPrice()));
                    refresh();
                    isbnField.setText(""); qtySpinner.setValue(1); isbnField.requestFocus();
                    status("Added: " + b.getTitle() + "  x" + qty);
                } catch (Exception ex) { status("Error: " + ex.getMessage()); }
                finally { addBtn.setEnabled(true); }
            }
        }.execute();
    }

    // ── Confirm with JWT validation ───────────────────────────────────────────

    private void confirmSale() {
        if (currentSale.getItems().isEmpty()) { status("Bill is empty."); return; }

        // Validate JWT session before allowing sale
        if (!SessionManager.getInstance().isAuthenticated()) {
            JOptionPane.showMessageDialog(this,
                "Session expired. Please log in again.",
                "Session Expired", JOptionPane.WARNING_MESSAGE);
            return;
        }
        SessionManager.getInstance().requireRole(User.Role.CLERK, User.Role.MANAGER, User.Role.OWNER);

        String msg = String.format(
            "Confirm sale of %d item(s)?\nTotal: INR %.2f\nSale ID: %s",
            currentSale.getItems().size(), currentSale.getTotalAmount(), currentSale.getSaleId());
        if (JOptionPane.showConfirmDialog(this, msg, "Confirm Sale",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        confirmBtn.setEnabled(false);
        final SaleRecord sale = currentSale;
        final String receipt = PrinterUtil.buildReceiptString(sale);

        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                return DatabaseManager.getInstance().saveSaleAtomically(sale, receipt);
            }
            @Override protected void done() {
                try {
                    if (get()) {
                        receiptArea.setText(receipt);
                        status("Sale " + sale.getSaleId() + " completed — INR " +
                               String.format("%.2f", sale.getTotalAmount()));
                        if (JOptionPane.showConfirmDialog(POSTerminalPanel.this,
                                "Sale complete!\n\nSend to printer?",
                                "Print Receipt", JOptionPane.YES_NO_OPTION)
                                == JOptionPane.YES_OPTION) {
                            PrinterUtil.printReceipt(sale);
                        }
                        currentSale = newSale(); refresh();
                    } else {
                        status("Sale failed — stock may be insufficient. Review the bill.");
                        confirmBtn.setEnabled(true);
                    }
                } catch (Exception ex) {
                    status("Error: " + ex.getMessage());
                    confirmBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refresh() {
        billModel.setRowCount(0);
        int i = 1;
        for (LineItem it : currentSale.getItems()) {
            billModel.addRow(new Object[]{
                i++, it.getIsbn(), it.getTitle(), it.getQuantity(),
                String.format("%.2f", it.getUnitPrice()),
                String.format("%.2f", it.getSubtotal())});
        }
        totalLbl.setText(String.format("TOTAL:  INR %.2f", currentSale.getTotalAmount()));
        confirmBtn.setEnabled(!currentSale.getItems().isEmpty());
        removeBtn.setEnabled(false);
    }

    private JButton btn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(fg);
        b.setFocusPainted(false); b.setOpaque(true); b.setBorderPainted(false);
        return b;
    }

    private JButton quickBtn(String label, String isbn) {
        JButton b = new JButton(label);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.setToolTipText("Quick-add: " + isbn);
        b.addActionListener(e -> { isbnField.setText(isbn); addItem(); });
        return b;
    }

    private void status(String msg) { statusLbl.setText(msg); }
}

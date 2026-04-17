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
    private JComboBox<BookChoice> bookPicker;
    private Runnable      resetBookPicker;

    private SaleRecord    currentSale;

    private static final String[] COLS = {"#","ISBN","Title","Qty","Unit (INR)","Subtotal (INR)"};

    public POSTerminalPanel(String clerkId) {
        this.clerkId     = clerkId;
        this.currentSale = newSale();
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        build();
        // Pre-warm the OS print subsystem on a daemon thread so the first real
        // print dialog opens instantly instead of hanging for 2-3 minutes while
        // Windows enumerates network printers.
        PrinterUtil.warmUpAsync();
    }

    private SaleRecord newSale() {
        return new SaleRecord(DatabaseManager.newSaleId(), clerkId);
    }

    private void build() {
        // ── Top row: ISBN entry + quantity + Add + book picker ──────────────
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.setBackground(Color.WHITE);
        top.setBorder(new CompoundBorder(
            new TitledBorder("Add Item  (type / scan ISBN OR pick from the list — then press Enter or Add)"),
            new EmptyBorder(4, 6, 4, 6)));

        isbnField = new JTextField(18);
        isbnField.setFont(new Font("Monospaced", Font.BOLD, 15));
        isbnField.setToolTipText("ISBN-10 or ISBN-13");

        qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        ((JSpinner.DefaultEditor) qtySpinner.getEditor()).getTextField().setColumns(4);

        addBtn = btn("Add", new Color(22, 163, 74), Color.WHITE);

        // Book picker: editable combobox with type-to-filter. Items render as
        // "Title — Author  (ISBN)". Selecting (or committing from the editor)
        // drops the chosen book's ISBN straight into isbnField.
        bookPicker = buildBookPicker();

        top.add(new JLabel("ISBN:")); top.add(isbnField);
        top.add(new JLabel("Qty:")); top.add(qtySpinner);
        top.add(addBtn);
        top.add(new JSeparator(SwingConstants.VERTICAL));
        top.add(new JLabel("Or pick book:")); top.add(bookPicker);
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
            boolean hasBillItems = !currentSale.getItems().isEmpty();
            boolean hasReceipt   = !receiptArea.getText().isBlank();
            // Nothing at all to clear — bail out silently.
            if (!hasBillItems && !hasReceipt) return;
            // Only prompt when there are cart items at risk. If the user is
            // just clearing a leftover preview from a completed sale, don't
            // interrupt them with a dialog — the button title is literally
            // "Clear Bill" and there's no bill to lose.
            if (hasBillItems) {
                if (JOptionPane.showConfirmDialog(this, "Clear the bill?", "Confirm",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            }
            currentSale = newSale();
            refresh();
            receiptArea.setText("");
            if (resetBookPicker != null) resetBookPicker.run();
            status(hasBillItems ? "Bill cleared." : "Receipt preview cleared.");
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
                        handleInsufficientStock(b, qty);
                        return;
                    }
                    currentSale.addItem(new LineItem(isbn, b.getTitle(), qty, b.getUnitPrice()));
                    refresh();
                    isbnField.setText(""); qtySpinner.setValue(1); isbnField.requestFocus();
                    if (resetBookPicker != null) resetBookPicker.run();
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

                        // Three options. Save-to-File is fast (no OS print
                        // dialog), Send-to-Printer runs async (UI stays
                        // responsive while Windows enumerates printers), Skip
                        // just closes. Save-to-File is the default.
                        String[] opts = {"Save to File", "Send to Printer", "Skip"};
                        int choice = JOptionPane.showOptionDialog(
                            POSTerminalPanel.this,
                            "<html>Sale complete!<br><br>" +
                            "<b>Save to File</b> is fastest — writes a text receipt to disk.<br>" +
                            "<b>Send to Printer</b> opens the system print dialog<br>" +
                            "(may take a few seconds the first time on Windows).</html>",
                            "Receipt", JOptionPane.DEFAULT_OPTION,
                            JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
                        if (choice == 0) {
                            java.io.File saved = PrinterUtil.saveReceiptToFile(sale, POSTerminalPanel.this);
                            if (saved != null) status("Receipt saved to: " + saved.getAbsolutePath());
                        } else if (choice == 1) {
                            PrinterUtil.printReceiptAsync(sale, POSTerminalPanel.this, ok -> {
                                if (ok) status("Receipt sent to printer.");
                                else    status("Printing cancelled or failed.");
                            });
                        }
                        // choice == 2 (Skip) or -1 (closed) → do nothing; receipt
                        // is still visible in the right-hand preview.
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

    // ── Insufficient-stock dialog ─────────────────────────────────────────────

    /**
     * Shown when the clerk tries to add {@code requested} copies of a book
     * but only {@code book.stockCount} are on hand. Three decisions possible:
     * <ul>
     *   <li>Add only the available copies to the cart</li>
     *   <li>Place a procurement order for the shortfall (only offered if no
     *       ORDERED PO for this ISBN already exists)</li>
     *   <li>Cancel — nothing happens</li>
     * </ul>
     * If a procurement order is already on the way, the dialog surfaces the
     * order id, quantity, order date, and publisher lead time instead.
     */
    private void handleInsufficientStock(final Book book, final int requested) {
        final int available = book.getStockCount();
        final int shortfall = requested - available;

        status("Short by " + shortfall + ": only " + available + " of \"" + book.getTitle() + "\" in stock.");

        // Look up any active PO on a background thread so the EDT stays responsive.
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                return DatabaseManager.getInstance().getActiveProcurementOrder(book.getIsbn());
            }
            @Override protected void done() {
                Object[] activePO;
                try { activePO = get(); }
                catch (Exception ex) { activePO = null; }
                showInsufficientStockDialog(book, requested, available, shortfall, activePO);
            }
        }.execute();
    }

    private void showInsufficientStockDialog(Book book, int requested, int available, int shortfall, Object[] activePO) {
        // Build the message
        StringBuilder msg = new StringBuilder();
        msg.append("<html><div style='width:420px;'>");
        msg.append("<b>Insufficient stock for &quot;").append(escape(book.getTitle())).append("&quot;</b><br>");
        msg.append("<br>Requested: <b>").append(requested).append("</b>")
           .append(" &nbsp;|&nbsp; Available: <b>").append(available).append("</b>")
           .append(" &nbsp;|&nbsp; Short by: <b style='color:#dc2626'>").append(shortfall).append("</b> copies.<br><br>");

        final boolean hasActivePO = activePO != null;
        if (hasActivePO) {
            String orderId   = (String) activePO[0];
            int poQty        = (Integer) activePO[3];
            String orderedAt = (String) activePO[4];
            int leadWks      = Math.max(1, book.getProcurementLeadTimeWeeks());
            msg.append("<div style='background:#fef3c7;padding:6px;border:1px solid #fbbf24;'>");
            msg.append("<b>A restock is already on the way:</b><br>");
            msg.append("Order <tt>").append(orderId).append("</tt> — <b>").append(poQty).append("</b> copies<br>");
            msg.append("Placed on ").append(orderedAt).append("<br>");
            msg.append("Expected in about <b>").append(leadWks).append(" week")
               .append(leadWks == 1 ? "" : "s").append("</b> (publisher lead time).");
            msg.append("</div>");
        } else {
            msg.append("<i>No procurement order exists for this title. You can place one now.</i>");
        }
        msg.append("</div></html>");

        // Build action buttons
        java.util.List<String> opts = new java.util.ArrayList<>();
        if (available > 0)   opts.add("Add available (" + available + ")");
        if (!hasActivePO)    opts.add("Place procurement order");
        opts.add("Cancel");
        String[] options = opts.toArray(new String[0]);

        int choice = JOptionPane.showOptionDialog(
            POSTerminalPanel.this,
            msg.toString(),
            "Insufficient Stock",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null, options, options[0]);

        if (choice < 0 || choice >= options.length) return;
        String picked = options[choice];

        if (picked.startsWith("Add available")) {
            if (available <= 0) { status("Nothing to add — stock is zero."); return; }
            currentSale.addItem(new LineItem(book.getIsbn(), book.getTitle(), available, book.getUnitPrice()));
            refresh();
            isbnField.setText(""); qtySpinner.setValue(1); isbnField.requestFocus();
            if (resetBookPicker != null) resetBookPicker.run();
            status("Added " + available + " of \"" + book.getTitle() + "\" (short by " + shortfall + ").");
        } else if (picked.startsWith("Place procurement order")) {
            placeProcurementForShortfall(book, shortfall);
        } // else: Cancel — do nothing
    }

    /**
     * Create a procurement order covering at minimum the shortfall, and
     * ideally the restock formula's recommended quantity
     * {@code max(0, ceil(weeklySales × leadTime) − stock)}. Quantity is
     * confirmable before submission so the clerk can tweak.
     */
    private void placeProcurementForShortfall(Book book, int shortfall) {
        int recommended = Math.max(shortfall, book.getRequiredProcurementQty());
        String input = JOptionPane.showInputDialog(this,
            "<html>Order quantity for <b>" + escape(book.getTitle()) + "</b><br>"
            + "Short by: <b>" + shortfall + "</b>  |  "
            + "Recommended restock: <b>" + recommended + "</b></html>",
            recommended);
        if (input == null) return;
        int qty;
        try { qty = Integer.parseInt(input.trim()); }
        catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Invalid number."); return;
        }
        if (qty <= 0) {
            JOptionPane.showMessageDialog(this, "Quantity must be positive."); return;
        }

        String actor = SessionManager.getInstance().getUserId();
        if (actor == null) actor = clerkId;
        final String actorF = actor;
        final int qtyF = qty;
        status("Placing procurement order…");

        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                return DatabaseManager.getInstance().createProcurementOrder(book.getIsbn(), qtyF, actorF);
            }
            @Override protected void done() {
                try {
                    if (get()) {
                        int wks = Math.max(1, book.getProcurementLeadTimeWeeks());
                        JOptionPane.showMessageDialog(POSTerminalPanel.this,
                            "<html>Procurement order placed for <b>" + qtyF + "</b> copies of<br>"
                            + "<b>" + escape(book.getTitle()) + "</b>.<br><br>"
                            + "Expected in about <b>" + wks + " week" + (wks==1?"":"s") + "</b>.</html>",
                            "Order Placed", JOptionPane.INFORMATION_MESSAGE);
                        status("PO created for " + book.getTitle() + " x" + qtyF + ".");
                    } else {
                        JOptionPane.showMessageDialog(POSTerminalPanel.this,
                            "Order failed — please retry from the Owner panel.",
                            "Failed", JOptionPane.ERROR_MESSAGE);
                        status("PO create failed.");
                    }
                } catch (Exception ex) {
                    status("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
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

    /**
     * Build the type-to-filter book picker combobox.
     * <p>
     * Every book from the cache is loaded into a secondary list. As the user
     * types into the combobox editor, the visible model is rebuilt to contain
     * only entries whose title, author, or ISBN contains the query substring
     * (case-insensitive). Picking an entry (via click, Enter, or arrow-down
     * + Enter) drops the chosen book's ISBN into {@code isbnField} and moves
     * focus to the quantity spinner so the clerk can finish the add.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private JComboBox<BookChoice> buildBookPicker() {
        final java.util.List<BookChoice> allBooks = new java.util.ArrayList<>();
        for (bas.model.Book b : bas.db.DatabaseManager.getInstance().getAllBooks()) {
            allBooks.add(new BookChoice(b));
        }
        allBooks.sort((a, c) -> a.toString().compareToIgnoreCase(c.toString()));

        final javax.swing.DefaultComboBoxModel<BookChoice> cbModel =
            new javax.swing.DefaultComboBoxModel<>();
        for (BookChoice bc : allBooks) cbModel.addElement(bc);

        JComboBox<BookChoice> combo = new JComboBox<>(cbModel);
        combo.setEditable(true);
        combo.setPreferredSize(new Dimension(380, combo.getPreferredSize().height));
        combo.setMaximumRowCount(12);
        combo.setToolTipText("Start typing title, author, or ISBN to filter");

        final JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
        editor.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Filtering: rebuild the combo's model on each keystroke, keeping the
        // popup open. We suppress the rebuild while Swing is firing its own
        // selection events so the user's typing isn't overwritten.
        final boolean[] suppress = {false};
        editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate (javax.swing.event.DocumentEvent e) { refilter(); }
            public void removeUpdate (javax.swing.event.DocumentEvent e) { refilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refilter(); }
            void refilter() {
                if (suppress[0]) return;
                final String q = editor.getText().trim().toLowerCase();
                SwingUtilities.invokeLater(() -> {
                    suppress[0] = true;
                    try {
                        cbModel.removeAllElements();
                        for (BookChoice bc : allBooks) {
                            if (q.isEmpty() || bc.matches(q)) cbModel.addElement(bc);
                        }
                        editor.setText(q.isEmpty() ? "" : editor.getText()); // preserve user input verbatim
                        if (combo.isDisplayable() && cbModel.getSize() > 0 && combo.hasFocus()) {
                            combo.showPopup();
                        }
                    } finally { suppress[0] = false; }
                });
            }
        });

        // After a selection commits, the combobox editor gets set to the chosen
        // item's display string ("Title — Author (ISBN)"). That write fires our
        // own DocumentListener, and the display string doesn't match any book's
        // plain haystack (title\nauthor\nisbn) — em-dash + parentheses break the
        // substring match — so the model would get filtered down to zero items
        // and the next dropdown click would show an empty popup. Fix: capture
        // a reset lambda here and run it after every successful selection, so
        // the combobox is always ready for the next pick.
        resetBookPicker = () -> {
            suppress[0] = true;
            try {
                combo.hidePopup();
                cbModel.removeAllElements();
                for (BookChoice bc : allBooks) cbModel.addElement(bc);
                editor.setText("");
                combo.setSelectedItem(null);
            } finally { suppress[0] = false; }
        };

        // Selection → auto-fill ISBN, bounce focus to qty, and reset the combobox.
        combo.addActionListener(e -> {
            if (suppress[0]) return;
            Object sel = combo.getSelectedItem();
            if (sel instanceof BookChoice) {
                BookChoice bc = (BookChoice) sel;
                isbnField.setText(bc.book.getIsbn());
                qtySpinner.requestFocusInWindow();
                status("Selected: " + bc.book.getTitle());
                // Defer so all Swing internals finish reacting to the selection first.
                SwingUtilities.invokeLater(resetBookPicker);
            }
        });

        return combo;
    }

    /** Wrapper used in the book-picker combobox so display text and match logic are together. */
    private static final class BookChoice {
        final bas.model.Book book;
        final String display;
        final String lowerHaystack;
        BookChoice(bas.model.Book b) {
            this.book    = b;
            this.display = b.getTitle() + " — " + b.getAuthor() + "  (" + b.getIsbn() + ")";
            this.lowerHaystack = (b.getTitle() + "\n" + b.getAuthor() + "\n" + b.getIsbn()).toLowerCase();
        }
        boolean matches(String lowerQuery) { return lowerHaystack.contains(lowerQuery); }
        @Override public String toString() { return display; }
    }

    private void status(String msg) { statusLbl.setText(msg); }
}

package bas.ui;

import bas.db.DatabaseManager;
import bas.model.Book;
import bas.service.EmailService;
import bas.util.PrinterUtil;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Owner admin panel (F4, FR-4.2 – FR-4.4, NFR-3).
 * Tabs: Sales Report | Procurement | OOS Demand | Email Settings | Activity Log.
 */
public class OwnerPanel extends JPanel {

    public OwnerPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tabs.addTab("📈 Sales Report",       salesTab());
        tabs.addTab("🛒 Procurement",         procurementTab());
        tabs.addTab("📋 OOS Demand Log",      oosTab());
        tabs.addTab("📧 Email Settings",      emailTab());
        tabs.addTab("🗒 Activity Log",         logsTab());
        add(tabs, BorderLayout.CENTER);
    }

    // ═══ SALES REPORT ════════════════════════════════════════════════════════

    private JPanel salesTab() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));

        // Date controls
        String today  = LocalDate.now().toString();
        String month  = LocalDate.now().minusDays(30).toString();
        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        ctrl.setBackground(Color.WHITE);
        ctrl.setBorder(new TitledBorder("Date Range  (FR-4.2: Sales Statistics)"));

        JTextField fromF = new JTextField(month, 12);
        JTextField toF   = new JTextField(today,  12);
        JButton genBtn   = btn("Generate Report", new Color(30,80,200), Color.WHITE);
        JLabel  revLbl   = new JLabel("Total Revenue: —");
        revLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        revLbl.setForeground(new Color(20,100,40));
        JButton printBtn = btn("🖨 Print", new Color(80,80,80), Color.WHITE);

        ctrl.add(new JLabel("From (yyyy-MM-dd):")); ctrl.add(fromF);
        ctrl.add(new JLabel("To (yyyy-MM-dd):")); ctrl.add(toF);
        ctrl.add(genBtn); ctrl.add(printBtn);
        ctrl.add(Box.createHorizontalStrut(20)); ctrl.add(revLbl);
        p.add(ctrl, BorderLayout.NORTH);

        // Table
        String[] cols = {"ISBN","Title","Author","Publisher","Copies Sold","Revenue (₹)"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = styledTable(tm);
        p.add(new JScrollPane(t), BorderLayout.CENTER);

        genBtn.addActionListener(e -> {
            tm.setRowCount(0);
            String from = fromF.getText().trim(), to = toF.getText().trim();
            new SwingWorker<List<Object[]>, Void>() {
                @Override protected List<Object[]> doInBackground() {
                    return DatabaseManager.getInstance().getSalesStats(from, to);
                }
                @Override protected void done() {
                    try {
                        for (Object[] row : get())
                            tm.addRow(new Object[]{
                                row[0],row[1],row[2],row[3],
                                row[4], String.format("%.2f",row[5])});
                        double rev = DatabaseManager.getInstance().getTotalRevenue(from, to);
                        revLbl.setText(String.format("Total Revenue: ₹ %.2f", rev));
                    } catch (Exception ex) { revLbl.setText("Error: " + ex.getMessage()); }
                }
            }.execute();
        });

        printBtn.addActionListener(e -> {
            if (tm.getRowCount() == 0) {
                JOptionPane.showMessageDialog(p,"Generate the report first.","Empty",
                    JOptionPane.WARNING_MESSAGE); return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("SALES REPORT\n");
            sb.append("Period: ").append(fromF.getText()).append(" to ").append(toF.getText()).append("\n\n");
            sb.append(String.format("%-14s %-25s %10s %12s%n","ISBN","Title","Copies","Revenue(₹)"));
            sb.append("-".repeat(65)).append("\n");
            for (int r = 0; r < tm.getRowCount(); r++)
                sb.append(String.format("%-14s %-25s %10s %12s%n",
                    tm.getValueAt(r,0), abbrev(tm.getValueAt(r,1).toString(),24),
                    tm.getValueAt(r,4), tm.getValueAt(r,5)));
            sb.append("-".repeat(65)).append("\n").append(revLbl.getText());
            PrinterUtil.printTextReport(sb.toString(), "Sales Report");
        });

        // Auto-generate on open
        SwingUtilities.invokeLater(genBtn::doClick);
        return p;
    }

    // ═══ PROCUREMENT ═════════════════════════════════════════════════════════

    private JPanel procurementTab() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));

        JPanel hdr = new JPanel(new FlowLayout(FlowLayout.LEFT,10,4));
        hdr.setBackground(Color.WHITE);
        hdr.setBorder(new TitledBorder(
            "FR-4.3 Formula: Qty = max(0, ⌈Weekly Sales × Lead Time⌉ − Current Stock)"));
        JButton loadBtn  = btn("Load Procurement Report", new Color(30,80,200), Color.WHITE);
        JButton printBtn = btn("🖨 Print Report",          new Color(80,80,80),  Color.WHITE);
        hdr.add(loadBtn); hdr.add(printBtn);
        p.add(hdr, BorderLayout.NORTH);

        String[] cols = {
            "ISBN","Title","Publisher","Pub. Address",
            "Stock","Threshold","Wk Sales","Lead(wk)","⚠ Order Qty"
        };
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = styledTable(tm);

        // Highlight the "Order Qty" column
        t.getColumnModel().getColumn(8).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tbl, Object v,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl,v,sel,foc,row,col);
                int qty = v == null ? 0 : Integer.parseInt(v.toString());
                if (!sel) c.setBackground(qty > 0 ? new Color(255,200,200) : new Color(200,240,210));
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                return c;
            }
        });

        p.add(new JScrollPane(t), BorderLayout.CENTER);

        loadBtn.addActionListener(e -> {
            tm.setRowCount(0);
            new SwingWorker<List<Book>, Void>() {
                @Override protected List<Book> doInBackground() {
                    return DatabaseManager.getInstance().getBooksNeedingRestock();
                }
                @Override protected void done() {
                    try {
                        List<Book> books = get();
                        for (Book b : books) tm.addRow(new Object[]{
                            b.getIsbn(), b.getTitle(), b.getPublisher(),
                            nullSafe(b.getPublisherAddress()),
                            b.getStockCount(), b.getRestockThreshold(),
                            String.format("%.1f",b.getWeeklySales()),
                            b.getProcurementLeadTimeWeeks(),
                            b.getRequiredProcurementQty()});
                        if (books.isEmpty())
                            JOptionPane.showMessageDialog(p,
                                "All books are above their restock threshold. No procurement needed! 🎉",
                                "All Good", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(p,"Error: "+ex.getMessage(),
                            "Error",JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        printBtn.addActionListener(e -> {
            if (tm.getRowCount() == 0) {
                JOptionPane.showMessageDialog(p,"Load the report first.","Empty",
                    JOptionPane.WARNING_MESSAGE); return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("DAILY PROCUREMENT REPORT\n").append(LocalDate.now()).append("\n\n");
            sb.append(String.format("%-13s %-22s %-18s %5s %8s %5s%n",
                "ISBN","Title","Publisher","Stock","WkSale","Order"));
            sb.append("-".repeat(75)).append("\n");
            for (int r = 0; r < tm.getRowCount(); r++) {
                sb.append(String.format("%-13s %-22s %-18s %5s %8s %5s%n",
                    tm.getValueAt(r,0), abbrev(tm.getValueAt(r,1).toString(),21),
                    abbrev(tm.getValueAt(r,2).toString(),17),
                    tm.getValueAt(r,4), tm.getValueAt(r,6), tm.getValueAt(r,8)));
                sb.append("  Address: ").append(tm.getValueAt(r,3)).append("\n");
            }
            PrinterUtil.printTextReport(sb.toString(),"Procurement Report");
        });

        // Auto-load on open
        SwingUtilities.invokeLater(loadBtn::doClick);
        return p;
    }

    // ═══ OOS DEMAND LOG ══════════════════════════════════════════════════════

    private JPanel oosTab() {
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));

        String[] cols = {"Req. ID","ISBN","Title","Author","Email","Date","Status"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = styledTable(tm);
        p.add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setBackground(Color.WHITE);
        JButton refreshBtn = btn("Refresh", new Color(30,80,200), Color.WHITE);
        JLabel cntLbl = new JLabel();
        top.add(refreshBtn); top.add(cntLbl);
        p.add(top, BorderLayout.NORTH);

        Runnable load = () -> {
            tm.setRowCount(0);
            var reqs = DatabaseManager.getInstance().getAllOOSRequests();
            for (var req : reqs)
                tm.addRow(new Object[]{
                    req.getRequestId(), req.getIsbn(), req.getTitle(),
                    req.getAuthor(),
                    req.getEmail() == null ? "— (no email)" : req.getEmail(),
                    req.getFormattedTimestamp(), req.getStatus().name()});
            long pending = reqs.stream().filter(r -> r.getStatus().name().equals("PENDING")).count();
            cntLbl.setText("  Total: " + reqs.size() + "  |  Pending: " + pending);
        };

        refreshBtn.addActionListener(e -> load.run());
        SwingUtilities.invokeLater(load);
        return p;
    }

    // ═══ EMAIL SETTINGS ═══════════════════════════════════════════════════════

    private JPanel emailTab() {
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(20,40,20,40));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(new TitledBorder("SMTP Configuration  (NFR-4: TLS/SSL encrypted)"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6,8,6,8); g.fill = GridBagConstraints.HORIZONTAL;

        JTextField  hostF  = fld(EmailService.getHost(),   22);
        JTextField  portF  = fld(String.valueOf(EmailService.getPort()), 6);
        JTextField  emailF = fld(EmailService.getEmail(),  22);
        JPasswordField passF = new JPasswordField(22);

        Object[][] rows = {{"SMTP Host:", hostF},{"SMTP Port:", portF},
                           {"Sender Email:", emailF},{"App Password:", passF}};
        for (int i = 0; i < rows.length; i++) {
            g.gridx=0; g.gridy=i; g.weightx=0; form.add(new JLabel((String)rows[i][0]),g);
            g.gridx=1; g.weightx=1; form.add((Component)rows[i][1],g);
        }

        g.gridx=0; g.gridy=4; g.gridwidth=2;
        JLabel hint = new JLabel(
            "<html><i>Gmail: enable 2FA → Google Account → Security → App Passwords.<br>" +
            "Use the generated 16-char password here, not your Google password.</i></html>");
        hint.setForeground(Color.GRAY);
        hint.setFont(new Font("SansSerif",Font.PLAIN,11));
        form.add(hint,g);
        p.add(form, BorderLayout.CENTER);

        JLabel statusLbl = new JLabel(" ", SwingConstants.CENTER);
        statusLbl.setFont(new Font("SansSerif",Font.ITALIC,12));
        p.add(statusLbl, BorderLayout.NORTH);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.setBackground(Color.WHITE);
        JButton save = btn("Save & Apply",   new Color(30,130,50),  Color.WHITE);
        JButton test = btn("Send Test Email", new Color(100,60,160), Color.WHITE);
        btns.add(test); btns.add(save);
        p.add(btns, BorderLayout.SOUTH);

        save.addActionListener(e -> {
            try {
                EmailService.configure(
                    hostF.getText().trim(),
                    Integer.parseInt(portF.getText().trim()),
                    emailF.getText().trim(),
                    new String(passF.getPassword()));
                statusLbl.setForeground(new Color(0,130,0));
                statusLbl.setText("✔ Email settings saved.");
                DatabaseManager.getInstance().addLog("OWNER","CONFIG",
                    "SMTP configured: " + emailF.getText().trim());
            } catch (NumberFormatException ex) {
                statusLbl.setForeground(Color.RED);
                statusLbl.setText("Invalid port number.");
            }
        });

        test.addActionListener(e -> {
            if (!EmailService.isConfigured()) {
                statusLbl.setForeground(Color.RED);
                statusLbl.setText("Save settings first."); return;
            }
            String to = JOptionPane.showInputDialog(p,"Send test email to:","Test Email",
                JOptionPane.PLAIN_MESSAGE);
            if (to == null || to.isBlank()) return;
            boolean ok = EmailService.send(to,"BAS Test Email",
                "This is a test from BAS. SMTP is configured correctly.");
            statusLbl.setForeground(ok ? new Color(0,130,0) : Color.RED);
            statusLbl.setText(ok ? "✔ Test sent to "+to : "✘ Send failed — check console.");
        });

        return p;
    }

    // ═══ ACTIVITY LOG ════════════════════════════════════════════════════════

    private JPanel logsTab() {
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));

        String[] cols = {"#","Timestamp","Type","Actor","Message"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = styledTable(tm);
        p.add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setBackground(Color.WHITE);
        JSpinner spin = new JSpinner(new SpinnerNumberModel(100, 10, 1000, 10));
        JButton loadBtn = btn("Load Logs", new Color(30,80,200), Color.WHITE);
        top.add(new JLabel("Show last:")); top.add(spin); top.add(loadBtn);
        p.add(top, BorderLayout.NORTH);

        Runnable load = () -> {
            tm.setRowCount(0);
            DatabaseManager.getInstance().getRecentLogs((int)spin.getValue())
                .forEach(r -> tm.addRow(r));
        };
        loadBtn.addActionListener(e -> load.run());
        SwingUtilities.invokeLater(load);
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JTable styledTable(DefaultTableModel tm) {
        JTable t = new JTable(tm);
        t.setFont(new Font("SansSerif",Font.PLAIN,12));
        t.setRowHeight(24);
        t.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,12));
        t.setGridColor(new Color(225,225,225));
        return t;
    }

    private JButton btn(String txt, Color bg, Color fg) {
        JButton b = new JButton(txt);
        b.setBackground(bg); b.setForeground(fg);
        b.setFocusPainted(false); b.setOpaque(true); b.setBorderPainted(false);
        return b;
    }

    private JTextField fld(String val, int cols) { return new JTextField(val, cols); }
    private String abbrev(String s, int max) {
        return s.length() > max ? s.substring(0,max-1)+"…" : s;
    }
    private String nullSafe(String s) { return s == null ? "" : s; }
}

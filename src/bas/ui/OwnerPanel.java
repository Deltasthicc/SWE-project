package bas.ui;

import bas.auth.SessionManager;
import bas.db.DatabaseManager;
import bas.db.BookCache;
import bas.model.Book;
import bas.model.User;
import bas.service.EmailService;
import bas.util.PrinterUtil;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class OwnerPanel extends JPanel {
    public OwnerPanel() {
        setLayout(new BorderLayout()); setBackground(Color.WHITE); setBorder(new EmptyBorder(10,10,10,10));
        JTabbedPane tabs = new JTabbedPane(); tabs.setFont(new Font("SansSerif",Font.PLAIN,13));
        tabs.addTab("Sales Report",salesTab()); tabs.addTab("Transaction History",transactionHistoryTab());
        tabs.addTab("Procurement",procurementTab()); tabs.addTab("OOS Demand Log",oosTab());
        tabs.addTab("Email Settings",emailTab()); tabs.addTab("Activity Log",logsTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel salesTab() {
        JPanel p=new JPanel(new BorderLayout(10,10)); p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));
        JPanel hdr=new JPanel(new FlowLayout(FlowLayout.LEFT,10,4)); hdr.setBackground(Color.WHITE);
        JTextField fromF=new JTextField(LocalDate.now().minusDays(30).toString(),10), toF=new JTextField(LocalDate.now().toString(),10);
        JButton genBtn=btn("Generate Report",new Color(37,99,235),Color.WHITE), printBtn=btn("Print Report",new Color(71,85,105),Color.WHITE);
        JLabel revLbl=new JLabel(); revLbl.setFont(new Font("SansSerif",Font.BOLD,13));
        hdr.add(new JLabel("From:")); hdr.add(fromF); hdr.add(new JLabel("To:")); hdr.add(toF); hdr.add(genBtn); hdr.add(printBtn); hdr.add(revLbl);
        p.add(hdr,BorderLayout.NORTH);
        String[] cols={"ISBN","Title","Author","Publisher","Copies Sold","Revenue (INR)"};
        DefaultTableModel tm=new DefaultTableModel(cols,0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=styledTable(tm); p.add(new JScrollPane(table),BorderLayout.CENTER);
        genBtn.addActionListener(e->{tm.setRowCount(0); var stats=DatabaseManager.getInstance().getSalesStats(fromF.getText().trim(),toF.getText().trim()); double total=0;
            for(Object[] r:stats){int cp=(Integer)r[4]; double rv=(Double)r[5]; if(cp>0){tm.addRow(new Object[]{r[0],r[1],r[2],r[3],cp,String.format("%.2f",rv)}); total+=rv;}}
            revLbl.setText("  Total Revenue: INR "+String.format("%.2f",total)+"  |  "+tm.getRowCount()+" books sold");});
        printBtn.addActionListener(e->{StringBuilder sb=new StringBuilder("SALES REPORT\n"+fromF.getText()+" to "+toF.getText()+"\n\n");
            for(int i=0;i<tm.getRowCount();i++){for(int j=0;j<tm.getColumnCount();j++) sb.append(tm.getValueAt(i,j)).append("\t"); sb.append("\n");} sb.append("\n").append(revLbl.getText());
            PrinterUtil.printTextReport(sb.toString(),"Sales-Report");});
        SwingUtilities.invokeLater(genBtn::doClick); return p;
    }

    private JPanel transactionHistoryTab() {
        JPanel p=new JPanel(new BorderLayout(10,10)); p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));
        JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT,10,4)); top.setBackground(Color.WHITE); top.setBorder(new TitledBorder("Transactions - click row to view receipt"));
        JSpinner limitSpin=new JSpinner(new SpinnerNumberModel(50,10,500,10)); JButton loadBtn=btn("Load History",new Color(37,99,235),Color.WHITE);
        JLabel cntLbl=new JLabel(); cntLbl.setFont(new Font("SansSerif",Font.BOLD,12));
        top.add(new JLabel("Show last:")); top.add(limitSpin); top.add(loadBtn); top.add(cntLbl); p.add(top,BorderLayout.NORTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT); split.setResizeWeight(0.55); split.setDividerSize(5);
        String[] cols={"Sale ID","Date & Time","Clerk","Items","Total (INR)"};
        DefaultTableModel tm=new DefaultTableModel(cols,0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable txTable=styledTable(tm); txTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        split.setLeftComponent(new JScrollPane(txTable));
        JPanel right=new JPanel(new BorderLayout(4,4)); right.setBackground(Color.WHITE);
        String[] itemCols={"ISBN","Title","Qty","Unit Price","Subtotal"};
        DefaultTableModel itemModel=new DefaultTableModel(itemCols,0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable itemTable=styledTable(itemModel); JScrollPane itemScroll=new JScrollPane(itemTable); itemScroll.setBorder(new TitledBorder("Line Items"));
        JTextArea receiptArea=new JTextArea(); receiptArea.setFont(new Font("Monospaced",Font.PLAIN,11)); receiptArea.setEditable(false); receiptArea.setBackground(new Color(250,250,245));
        JScrollPane receiptScroll=new JScrollPane(receiptArea); receiptScroll.setBorder(new TitledBorder("Receipt"));
        JSplitPane rightSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT); rightSplit.setResizeWeight(0.45); rightSplit.setTopComponent(itemScroll); rightSplit.setBottomComponent(receiptScroll);
        right.add(rightSplit,BorderLayout.CENTER);
        JButton reprintBtn=btn("Reprint",new Color(71,85,105),Color.WHITE); reprintBtn.setEnabled(false);
        JPanel rBtns=new JPanel(new FlowLayout(FlowLayout.RIGHT)); rBtns.setBackground(Color.WHITE); rBtns.add(reprintBtn); right.add(rBtns,BorderLayout.SOUTH);
        split.setRightComponent(right); p.add(split,BorderLayout.CENTER);
        Runnable load=()->{tm.setRowCount(0); var txns=DatabaseManager.getInstance().getTransactionHistory((int)limitSpin.getValue()); double tot=0;
            for(Object[] row:txns){tm.addRow(new Object[]{row[0],row[1],row[2],row[4],String.format("%.2f",(Double)row[3])}); tot+=(Double)row[3];}
            cntLbl.setText("  "+txns.size()+" transactions | Total: INR "+String.format("%.2f",tot));};
        loadBtn.addActionListener(e->load.run());
        txTable.getSelectionModel().addListSelectionListener(e->{if(e.getValueIsAdjusting()) return; int row=txTable.getSelectedRow(); if(row<0){reprintBtn.setEnabled(false);return;}
            String saleId=tm.getValueAt(row,0).toString(); itemModel.setRowCount(0);
            var items=DatabaseManager.getInstance().getSaleItems(saleId);
            for(Object[] it:items) itemModel.addRow(new Object[]{it[0],it[1],it[2],String.format("%.2f",(Double)it[3]),String.format("%.2f",(Double)it[4])});
            String receipt = DatabaseManager.getInstance().getReceiptContent(saleId);
            boolean stored = receipt != null && !receipt.isBlank() && !isPlaceholder(receipt);
            if (!stored) {
                // Reconstruct a readable receipt from the line items we just loaded.
                // This covers test-seeded sales (stored as "perf-test") and any older
                // sales saved before receipt storage was added.
                receipt = buildReceiptFromItems(saleId,
                    String.valueOf(tm.getValueAt(row,1)),
                    String.valueOf(tm.getValueAt(row,2)),
                    items, (Double) 0.0 + Double.parseDouble(tm.getValueAt(row,4).toString()));
            }
            receiptArea.setText(receipt);
            receiptArea.setCaretPosition(0);
            reprintBtn.setEnabled(!receipt.isBlank());});
        reprintBtn.addActionListener(e->{if(!receiptArea.getText().isBlank()) PrinterUtil.printTextReport(receiptArea.getText(),"Reprint");});
        SwingUtilities.invokeLater(load); return p;
    }

    /** Returns true when a DB-stored receipt is a placeholder rather than a real receipt. */
    private boolean isPlaceholder(String s) {
        String t = s.trim().toLowerCase();
        return t.equals("perf-test") || t.equals("test") || t.startsWith("placeholder")
            || t.length() < 20; // genuine receipts are always longer than this
    }

    /** Builds a plain-text receipt from line items when the stored content is missing. */
    private String buildReceiptFromItems(String saleId, String ts, String clerk, java.util.List<Object[]> items, double total) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================\n");
        sb.append("          BAS BOOKSHOP — SALES RECEIPT\n");
        sb.append("================================================\n");
        sb.append("Sale ID  : ").append(saleId).append('\n');
        sb.append("Date     : ").append(ts).append('\n');
        sb.append("Clerk    : ").append(clerk).append('\n');
        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-14s %-22s %5s %10s%n", "ISBN", "Title", "Qty", "Subtotal"));
        sb.append("------------------------------------------------\n");
        for (Object[] it : items) {
            String title = String.valueOf(it[1]);
            if (title.length() > 22) title = title.substring(0, 21) + "…";
            sb.append(String.format("%-14s %-22s %5d %10.2f%n",
                it[0], title, (Integer) it[2], (Double) it[4]));
        }
        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-43s INR %.2f%n", "TOTAL", total));
        sb.append("================================================\n");
        sb.append("  (receipt reconstructed from sale_items table)\n");
        return sb.toString();
    }

    private JPanel procurementTab() {
        JPanel p=new JPanel(new BorderLayout(10,10)); p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));

        // ── Top bar: one Refresh button reloads BOTH tables ────────────────
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT,8,4)); topBar.setBackground(Color.WHITE);
        JButton refreshBtn = btn("Refresh", new Color(37,99,235), Color.WHITE);
        refreshBtn.setToolTipText("Recompute restock recommendations and reload the orders table");
        JLabel hint = new JLabel("Recommendations update automatically after you place or confirm an order.");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 11));
        hint.setForeground(new Color(100,116,139));
        topBar.add(refreshBtn); topBar.add(hint);
        p.add(topBar, BorderLayout.NORTH);

        // ── Recommendations (upper half) ───────────────────────────────────
        JPanel topP=new JPanel(new BorderLayout(6,6)); topP.setBackground(Color.WHITE);
        topP.setBorder(new TitledBorder("FR-4.3: Books Needing Restock  (Qty = max(0, ceil(WeeklySales × LeadTime) − Stock))"));
        String[] recCols={"ISBN","Title","Publisher","Stock","Threshold","Weekly Sales","Lead Wks","Recommended Qty"};
        DefaultTableModel recModel=new DefaultTableModel(recCols,0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable recTable=styledTable(recModel); recTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JPanel recBtns=new JPanel(new FlowLayout(FlowLayout.LEFT,8,4)); recBtns.setBackground(Color.WHITE);
        JButton placeOrderBtn=btn("Place Order for Selected",new Color(22,163,74),Color.WHITE);
        placeOrderBtn.setToolTipText("Create a procurement order for the selected title (asks for quantity)");
        recBtns.add(placeOrderBtn);
        topP.add(recBtns,BorderLayout.NORTH); topP.add(new JScrollPane(recTable),BorderLayout.CENTER);

        // ── Orders (lower half) ────────────────────────────────────────────
        JPanel bottomP=new JPanel(new BorderLayout(6,6)); bottomP.setBackground(Color.WHITE); bottomP.setBorder(new TitledBorder("Procurement Orders"));
        String[] ordCols={"Order ID","ISBN","Title","Publisher","Qty","Status","Ordered","Arrived"};
        DefaultTableModel ordModel=new DefaultTableModel(ordCols,0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable ordTable=styledTable(ordModel); ordTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JPanel ordBtns=new JPanel(new FlowLayout(FlowLayout.LEFT,8,4)); ordBtns.setBackground(Color.WHITE);
        JButton confirmBtn=btn("Confirm Arrival (adds stock)",new Color(22,163,74),Color.WHITE);
        confirmBtn.setToolTipText("Mark the selected ORDERED row as arrived — stock is added to the book");
        JComboBox<String> filterBox=new JComboBox<>(new String[]{"ALL","ORDERED","ARRIVED"});
        filterBox.setToolTipText("Filter the orders table by status");
        ordBtns.add(filterBox); ordBtns.add(confirmBtn);
        bottomP.add(ordBtns,BorderLayout.NORTH); bottomP.add(new JScrollPane(ordTable),BorderLayout.CENTER);

        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,topP,bottomP); split.setResizeWeight(0.5); p.add(split,BorderLayout.CENTER);

        Runnable loadRec=()->{recModel.setRowCount(0); BookCache.getInstance().invalidate();
            for(Book b:DatabaseManager.getInstance().getBooksNeedingRestock()) recModel.addRow(new Object[]{b.getIsbn(),b.getTitle(),b.getPublisher(),b.getStockCount(),b.getRestockThreshold(),b.getWeeklySales(),b.getProcurementLeadTimeWeeks(),b.getRequiredProcurementQty()});};
        Runnable loadOrd=()->{ordModel.setRowCount(0); String f=filterBox.getSelectedItem().toString();
            for(Object[] o:DatabaseManager.getInstance().getProcurementOrders("ALL".equals(f)?null:f)) ordModel.addRow(o);};
        Runnable refreshAll = () -> { loadRec.run(); loadOrd.run(); };

        refreshBtn.addActionListener(e -> refreshAll.run());
        filterBox.addActionListener(e -> loadOrd.run());

        placeOrderBtn.addActionListener(e->{int row=recTable.getSelectedRow(); if(row<0){JOptionPane.showMessageDialog(this,"Select a book.");return;}
            String isbn=recModel.getValueAt(row,0).toString(),title=recModel.getValueAt(row,1).toString(); int recQty=Math.max(1,(Integer)recModel.getValueAt(row,7));
            String input=JOptionPane.showInputDialog(this,"Order quantity for \""+title+"\":",recQty); if(input==null) return;
            try{int qty=Integer.parseInt(input.trim()); if(qty<=0){JOptionPane.showMessageDialog(this,"Must be positive.");return;}
                String actor=SessionManager.getInstance().getUserId(); if(actor==null) actor="manager1";
                if(DatabaseManager.getInstance().createProcurementOrder(isbn,qty,actor)){JOptionPane.showMessageDialog(this,"Order placed: "+title+" x "+qty); refreshAll.run();}
                else JOptionPane.showMessageDialog(this,"Order failed.","Error",JOptionPane.ERROR_MESSAGE);
            }catch(NumberFormatException ex){JOptionPane.showMessageDialog(this,"Invalid number.");}});

        confirmBtn.addActionListener(e->{int row=ordTable.getSelectedRow(); if(row<0){JOptionPane.showMessageDialog(this,"Select an order.");return;}
            String orderId=ordModel.getValueAt(row,0).toString(),status=ordModel.getValueAt(row,5).toString();
            if(!"ORDERED".equals(status)){JOptionPane.showMessageDialog(this,"Only ORDERED items can be confirmed.");return;}
            String title=ordModel.getValueAt(row,2).toString(); int qty=(Integer)ordModel.getValueAt(row,4);
            if(JOptionPane.showConfirmDialog(this,"Confirm arrival of "+qty+" copies of \""+title+"\"?\nStock will increase by "+qty+".","Confirm",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
            String actor=SessionManager.getInstance().getUserId(); if(actor==null) actor="manager1";
            if(DatabaseManager.getInstance().confirmProcurementArrival(orderId,actor)){JOptionPane.showMessageDialog(this,"Stock +"+qty+" for \""+title+"\""); refreshAll.run();}
            else JOptionPane.showMessageDialog(this,"Failed.","Error",JOptionPane.ERROR_MESSAGE);});

        SwingUtilities.invokeLater(refreshAll);
        return p;
    }

    private JPanel oosTab() {
        JPanel p=new JPanel(new BorderLayout(10,10)); p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));
        String[] cols={"Request ID","ISBN","Title","Author","Publisher","Email","Date/Time","Status"};
        DefaultTableModel tm=new DefaultTableModel(cols,0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=styledTable(tm); p.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel btns=new JPanel(new FlowLayout(FlowLayout.LEFT,10,4)); btns.setBackground(Color.WHITE);
        JButton loadBtn=btn("Refresh",new Color(37,99,235),Color.WHITE); JLabel countLbl=new JLabel(); countLbl.setFont(new Font("SansSerif",Font.BOLD,12));
        btns.add(loadBtn); btns.add(countLbl); p.add(btns,BorderLayout.NORTH);
        Runnable load=()->{tm.setRowCount(0); var reqs=DatabaseManager.getInstance().getAllOOSRequests();
            for(var r:reqs) tm.addRow(new Object[]{r.getRequestId(),r.getIsbn(),r.getTitle(),r.getAuthor(),r.getPublisher(),r.getEmail()==null?"\u2014":r.getEmail(),r.getFormattedTimestamp(),r.getStatus()});
            countLbl.setText("  "+reqs.size()+" requests");};
        loadBtn.addActionListener(e->load.run()); SwingUtilities.invokeLater(load); return p;
    }

    private JPanel emailTab() {
        JPanel p=new JPanel(new BorderLayout(10,10)); p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));
        JPanel form=new JPanel(new GridLayout(5,2,8,8)); form.setBackground(Color.WHITE); form.setBorder(new TitledBorder("SMTP Configuration (Gmail)"));
        JTextField hostF=new JTextField(EmailService.getHost()),portF=new JTextField(String.valueOf(EmailService.getPort())),emailF=new JTextField(EmailService.getEmail());
        // Pre-populate password with the AES-decoded stored value. JPasswordField
        // renders every character as a dot, so the actual secret is never visible.
        JPasswordField passF=new JPasswordField();
        passF.setText(bas.config.AppConfig.SMTP_PASSWORD);
        passF.setToolTipText("Gmail App Password (16 chars, masked)");
        JButton saveBtn=btn("Save & Test",new Color(37,99,235),Color.WHITE);
        form.add(new JLabel("SMTP Host:")); form.add(hostF); form.add(new JLabel("Port:")); form.add(portF);
        form.add(new JLabel("Email:")); form.add(emailF); form.add(new JLabel("App Password:")); form.add(passF);
        form.add(new JLabel()); form.add(saveBtn); p.add(form,BorderLayout.NORTH);

        JLabel statusLbl=new JLabel();
        statusLbl.setFont(new Font("SansSerif",Font.BOLD,13));
        statusLbl.setBorder(new EmptyBorder(6,4,4,4));
        Runnable refreshStatus = () -> {
            if (EmailService.isConfigured()) {
                statusLbl.setText("Status: Configured");
                statusLbl.setForeground(new Color(22,163,74));
            } else {
                statusLbl.setText("Status: Not Configured  (host, port, email, and app password are all required)");
                statusLbl.setForeground(new Color(220,38,38));
            }
        };
        refreshStatus.run();
        p.add(statusLbl,BorderLayout.CENTER);

        saveBtn.addActionListener(e->{
            try {
                String pw = new String(passF.getPassword());
                // Don't silently fall back to the stored password if the user intentionally cleared the field.
                // Only fall back when the field still holds the auto-populated value (the user never touched it).
                int portV = Integer.parseInt(portF.getText().trim());
                EmailService.configure(hostF.getText().trim(), portV, emailF.getText().trim(), pw);
                refreshStatus.run();
            } catch (NumberFormatException nfe) {
                statusLbl.setText("Error: Port must be a number."); statusLbl.setForeground(new Color(220,38,38));
            } catch (Exception ex) {
                statusLbl.setText("Error: "+ex.getMessage()); statusLbl.setForeground(new Color(220,38,38));
            }
        });
        return p;
    }

    private JPanel logsTab() {
        JPanel p=new JPanel(new BorderLayout(10,10)); p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(12,12,12,12));
        String[] cols={"ID","Timestamp","Event","Actor","Message"};
        DefaultTableModel tm=new DefaultTableModel(cols,0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=styledTable(tm); p.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel btns=new JPanel(new FlowLayout(FlowLayout.LEFT,10,4)); btns.setBackground(Color.WHITE);
        JSpinner limitSpin=new JSpinner(new SpinnerNumberModel(100,10,1000,50));
        JButton loadBtn=btn("Refresh Logs",new Color(37,99,235),Color.WHITE);
        btns.add(new JLabel("Show last:")); btns.add(limitSpin); btns.add(loadBtn); p.add(btns,BorderLayout.NORTH);
        Runnable load=()->{tm.setRowCount(0); for(Object[] l:DatabaseManager.getInstance().getRecentLogs((int)limitSpin.getValue())) tm.addRow(l);};
        loadBtn.addActionListener(e->load.run()); SwingUtilities.invokeLater(load); return p;
    }

    private JButton btn(String text,Color bg,Color fg){JButton b=new JButton(text);b.setBackground(bg);b.setForeground(fg);b.setFont(new Font("SansSerif",Font.BOLD,12));b.setFocusPainted(false);b.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;}
    private JTable styledTable(DefaultTableModel m){JTable t=new JTable(m);t.setRowHeight(28);t.setFont(new Font("SansSerif",Font.PLAIN,12));t.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,12));t.getTableHeader().setBackground(new Color(241,245,249));t.setGridColor(new Color(226,232,240));return t;}
}

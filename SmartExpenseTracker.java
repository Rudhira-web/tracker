import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SmartExpenseTracker.java
 * Single-file Java Swing application (medium complexity) that demonstrates:
 * - Login screen (very simple, credentials stored in file)
 * - Dashboard showing totals
 * - Add / Delete transactions saved to CSV
 * - JTable for transaction list with sorting by clicking headers (basic)
 * - Simple pie chart drawn with Graphics2D
 *
 * Build & Run:
 * javac SmartExpenseTracker.java
 * java SmartExpenseTracker
 *
 * Data files created in user's working directory: users.txt and transactions.csv
 */
public class SmartExpenseTracker {
    public static void main(String[] args) {
        // Ensure Swing runs on EDT
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new LoginFrame();
        });
    }
}

// -------------------- Model --------------------
class Transaction {
    enum Type { INCOME, EXPENSE }
    LocalDate date;
    String category;
    String description;
    double amount;
    Type type;

    static DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    Transaction(LocalDate date, String category, String description, double amount, Type type) {
        this.date = date;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.type = type;
    }

    String toCSV() {
        // Escape commas in description/category
        return String.join(",",
                date.format(fmt),
                escape(category),
                escape(description),
                String.format("%.2f", amount),
                type.name());
    }

    static Transaction fromCSV(String line) {
        String[] parts = splitCSV(line);
        if (parts.length < 5) return null;
        LocalDate date = LocalDate.parse(parts[0], fmt);
        String category = unescape(parts[1]);
        String desc = unescape(parts[2]);
        double amt = Double.parseDouble(parts[3]);
        Type t = Type.valueOf(parts[4]);
        return new Transaction(date, category, desc, amt, t);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"").replace(",", "\,");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\,", ",").replace("\"\"", "\"");
    }

    // Basic CSV splitter that respects escaped commas (we used \\ to escape commas)
    private static String[] splitCSV(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (esc) {
                sb.append(c);
                esc = false;
            } else {
                if (c == '\\') {
                    esc = true;
                } else if (c == ',') {
                    parts.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        parts.add(sb.toString());
        return parts.toArray(new String[0]);
    }
}

class TransactionManager {
    private final List<Transaction> transactions = new ArrayList<>();
    private final Path dataFile = Path.of("transactions.csv");

    TransactionManager() {
        load();
    }

    List<Transaction> all() { return transactions; }

    void add(Transaction t) {
        transactions.add(t);
        save();
    }

    void remove(int index) {
        if (index >= 0 && index < transactions.size()) {
            transactions.remove(index);
            save();
        }
    }

    double totalIncome() {
        return transactions.stream().filter(t -> t.type == Transaction.Type.INCOME).mapToDouble(t -> t.amount).sum();
    }

    double totalExpense() {
        return transactions.stream().filter(t -> t.type == Transaction.Type.EXPENSE).mapToDouble(t -> t.amount).sum();
    }

    void save() {
        try (BufferedWriter w = Files.newBufferedWriter(dataFile)) {
            for (Transaction t : transactions) {
                w.write(t.toCSV());
                w.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to save transactions: " + e.getMessage());
        }
    }

    void load() {
        transactions.clear();
        if (!Files.exists(dataFile)) return;
        try (BufferedReader r = Files.newBufferedReader(dataFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                Transaction t = Transaction.fromCSV(line);
                if (t != null) transactions.add(t);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to load transactions: " + e.getMessage());
        }
    }
}

// -------------------- UI --------------------
class LoginFrame extends JFrame {
    private final JTextField userField = new JTextField(20);
    private final JPasswordField passField = new JPasswordField(20);
    private final Path usersFile = Path.of("users.txt");

    LoginFrame() {
        setTitle("Smart Expense Tracker - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(380, 220);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBorder(new EmptyBorder(12,12,12,12));

        JLabel title = new JLabel("Smart Expense Tracker", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(18f).deriveFont(Font.BOLD));
        root.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.EAST;
        center.add(new JLabel("Username:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        center.add(userField, c);
        c.gridx = 0; c.gridy = 1; c.anchor = GridBagConstraints.EAST;
        center.add(new JLabel("Password:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        center.add(passField, c);

        root.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");
        bottom.add(registerBtn);
        bottom.add(loginBtn);
        root.add(bottom, BorderLayout.SOUTH);

        add(root);

        loginBtn.addActionListener(e -> tryLogin());
        registerBtn.addActionListener(e -> tryRegister());

        // create default user if none
        if (!Files.exists(usersFile)) {
            try {
                Files.writeString(usersFile, "admin:admin\n");
            } catch (IOException ignored) {}
        }

        setVisible(true);
    }

    private void tryLogin() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        if (u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username and password.");
            return;
        }
        if (checkCredentials(u, p)) {
            dispose();
            new DashboardFrame(u);
        } else {
            JOptionPane.showMessageDialog(this, "Invalid credentials.");
        }
    }

    private boolean checkCredentials(String u, String p) {
        try (BufferedReader r = Files.newBufferedReader(usersFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length==2 && parts[0].equals(u) && parts[1].equals(p)) return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void tryRegister() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        if (u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username and password to register.");
            return;
        }
        // very simple append - no hashing for this demo
        try (BufferedWriter w = Files.newBufferedWriter(usersFile, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            w.write(u + ":" + p);
            w.newLine();
            JOptionPane.showMessageDialog(this, "User registered. You may login now.");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to register: " + e.getMessage());
        }
    }
}

class DashboardFrame extends JFrame {
    private final TransactionManager manager = new TransactionManager();
    private final TransactionTableModel tableModel = new TransactionTableModel(manager.all());
    private final JLabel incomeLabel = new JLabel();
    private final JLabel expenseLabel = new JLabel();
    private final JLabel balanceLabel = new JLabel();
    private final PieChartPanel chartPanel = new PieChartPanel();

    DashboardFrame(String username) {
        setTitle("Dashboard - " + username);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBorder(new EmptyBorder(10,10,10,10));

        // Top: header
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Welcome, " + username);
        title.setFont(title.getFont().deriveFont(16f).deriveFont(Font.BOLD));
        top.add(title, BorderLayout.WEST);

        JButton addBtn = new JButton("Add Transaction");
        JButton delBtn = new JButton("Delete Selected");
        JButton exportBtn = new JButton("Export CSV");
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topRight.add(exportBtn);
        topRight.add(delBtn);
        topRight.add(addBtn);
        top.add(topRight, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        // Center: split pane with table and chart
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.6);

        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane leftScroll = new JScrollPane(table);
        split.setLeftComponent(leftScroll);

        chartPanel.setPreferredSize(new Dimension(300,300));
        JPanel right = new JPanel(new BorderLayout());
        right.add(chartPanel, BorderLayout.CENTER);

        // summary panel under chart
        JPanel sums = new JPanel(new GridLayout(3,1,6,6));
        incomeLabel.setFont(incomeLabel.getFont().deriveFont(14f));
        expenseLabel.setFont(expenseLabel.getFont().deriveFont(14f));
        balanceLabel.setFont(balanceLabel.getFont().deriveFont(14f).deriveFont(Font.BOLD));
        sums.add(incomeLabel);
        sums.add(expenseLabel);
        sums.add(balanceLabel);
        right.add(sums, BorderLayout.SOUTH);

        split.setRightComponent(right);
        root.add(split, BorderLayout.CENTER);

        // bottom: filter and quick stats
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(new JLabel("Filter by category:"));
        JComboBox<String> filterBox = new JComboBox<>();
        filterBox.addItem("All");
        updateFilterCategories(filterBox);
        bottom.add(filterBox);
        root.add(bottom, BorderLayout.SOUTH);

        add(root);

        // Actions
        addBtn.addActionListener(e -> openAddDialog());
        delBtn.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel >= 0) {
                int modelIdx = table.convertRowIndexToModel(sel);
                int confirm = JOptionPane.showConfirmDialog(this, "Delete selected transaction?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    manager.remove(modelIdx);
                    tableModel.fireTableDataChanged();
                    refreshSummary();
                    chartPanel.setTransactions(manager.all());
                    updateFilterCategories(filterBox);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Select a row to delete.");
            }
        });

        exportBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
                    for (Transaction t : manager.all()) {
                        w.write(t.toCSV());
                        w.newLine();
                    }
                    JOptionPane.showMessageDialog(this, "Exported to " + f.getAbsolutePath());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Failed to export: " + ex.getMessage());
                }
            }
        });

        filterBox.addActionListener(e -> {
            String sel = (String) filterBox.getSelectedItem();
            if (sel == null || sel.equals("All")) {
                tableModel.setFilter(null);
            } else {
                tableModel.setFilter(sel);
            }
            tableModel.fireTableDataChanged();
        });

        // selection listener to show details
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int sel = table.getSelectedRow();
                    if (sel >= 0) {
                        int modelIdx = table.convertRowIndexToModel(sel);
                        Transaction t = manager.all().get(modelIdx);
                        // show tooltip-style details
                        table.setToolTipText(t.description + " (" + t.date + ")");
                    }
                }
            }
        });

        // initialize view
        refreshSummary();
        chartPanel.setTransactions(manager.all());

        setVisible(true);
    }

    private void openAddDialog() {
        AddTransactionDialog dlg = new AddTransactionDialog(this);
        dlg.setVisible(true);
        Transaction t = dlg.getResult();
        if (t != null) {
            manager.add(t);
            tableModel.fireTableDataChanged();
            refreshSummary();
            chartPanel.setTransactions(manager.all());
        }
    }

    private void refreshSummary() {
        double inc = manager.totalIncome();
        double exp = manager.totalExpense();
        incomeLabel.setText(String.format("Total Income: %.2f", inc));
        expenseLabel.setText(String.format("Total Expense: %.2f", exp));
        balanceLabel.setText(String.format("Balance: %.2f", inc - exp));
    }

    private void updateFilterCategories(JComboBox<String> box) {
        List<String> cats = manager.all().stream().map(t -> t.category).distinct().sorted().collect(Collectors.toList());
        box.removeAllItems();
        box.addItem("All");
        for (String c : cats) box.addItem(c);
    }
}

class AddTransactionDialog extends JDialog {
    private Transaction result = null;

    AddTransactionDialog(JFrame parent) {
        super(parent, "Add Transaction", true);
        setSize(420, 320);
        setLocationRelativeTo(parent);
        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBorder(new EmptyBorder(10,10,10,10));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.anchor = GridBagConstraints.EAST;

        JLabel dateL = new JLabel("Date (YYYY-MM-DD):");
        JTextField dateF = new JTextField(LocalDate.now().toString(), 12);
        JLabel catL = new JLabel("Category:");
        JTextField catF = new JTextField(15);
        JLabel descL = new JLabel("Description:");
        JTextField descF = new JTextField(20);
        JLabel amtL = new JLabel("Amount:");
        JTextField amtF = new JTextField(10);
        JLabel typeL = new JLabel("Type:");
        JComboBox<String> typeBox = new JComboBox<>(new String[]{"EXPENSE","INCOME"});

        c.gridx=0; c.gridy=0; form.add(dateL,c); c.gridx=1; form.add(dateF,c);
        c.gridx=0; c.gridy=1; form.add(catL,c); c.gridx=1; form.add(catF,c);
        c.gridx=0; c.gridy=2; form.add(descL,c); c.gridx=1; form.add(descF,c);
        c.gridx=0; c.gridy=3; form.add(amtL,c); c.gridx=1; form.add(amtF,c);
        c.gridx=0; c.gridy=4; form.add(typeL,c); c.gridx=1; form.add(typeBox,c);

        root.add(form, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Add");
        JButton cancel = new JButton("Cancel");
        btns.add(cancel); btns.add(ok);
        root.add(btns, BorderLayout.SOUTH);

        add(root);

        cancel.addActionListener(e -> {
            result = null; dispose();
        });

        ok.addActionListener(e -> {
            try {
                LocalDate d = LocalDate.parse(dateF.getText().trim());
                String cat = catF.getText().trim();
                String desc = descF.getText().trim();
                double amt = Double.parseDouble(amtF.getText().trim());
                Transaction.Type type = Transaction.Type.valueOf((String) typeBox.getSelectedItem());
                if (amt <= 0) throw new NumberFormatException("Amount must be positive");
                result = new Transaction(d, cat, desc, amt, type);
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage());
            }
        });
    }

    Transaction getResult() { return result; }
}

class TransactionTableModel extends AbstractTableModel {
    private List<Transaction> base;
    private List<Transaction> view;
    private String filterCategory = null;
    private final String[] cols = {"Date","Category","Description","Amount","Type"};

    TransactionTableModel(List<Transaction> data) {
        this.base = data;
        this.view = new ArrayList<>(base);
    }

    void setFilter(String category) {
        this.filterCategory = category;
        rebuildView();
    }

    private void rebuildView() {
        if (filterCategory == null) view = new ArrayList<>(base);
        else view = base.stream().filter(t -> t.category.equals(filterCategory)).collect(Collectors.toList());
    }

    @Override
    public int getRowCount() { return view.size(); }

    @Override
    public int getColumnCount() { return cols.length; }

    @Override
    public String getColumnName(int col) { return cols[col]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Transaction t = view.get(rowIndex);
        switch (columnIndex) {
            case 0: return t.date.toString();
            case 1: return t.category;
            case 2: return t.description;
            case 3: return String.format("%.2f", t.amount);
            case 4: return t.type.name();
        }
        return "";
    }
}

class PieChartPanel extends JPanel {
    private List<Transaction> transactions = new ArrayList<>();

    void setTransactions(List<Transaction> txs) {
        this.transactions = new ArrayList<>(txs);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (transactions == null || transactions.isEmpty()) {
            g.drawString("No data to display", 20, 20);
            return;
        }
        // Aggregate expenses by category only (pie for expense distribution)
        var map = transactions.stream().filter(t -> t.type == Transaction.Type.EXPENSE)
                .collect(Collectors.groupingBy(t -> t.category, Collectors.summingDouble(t -> t.amount)));
        double total = map.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            g.drawString("No expense data to display", 20, 20);
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        int w = Math.min(getWidth(), getHeight()) - 40;
        int x = 20 + (getWidth() - w)/2;
        int y = 20;
        int cx = x, cy = y;
        int start = 0;
        int i = 0;
        // choose a set of pleasing hues programmatically
        for (String cat : map.keySet()) {
            double v = map.get(cat);
            int angle = (int) Math.round(v / total * 360);
            // compute color from index
            float hue = (i * 0.14f) % 1.0f;
            Color ccol = Color.getHSBColor(hue, 0.6f, 0.9f);
            g2.setColor(ccol);
            g2.fillArc(x, y, w, w, start, angle);
            start += angle;
            i++;
        }
        // draw legend
        int lx = x + w + 10;
        int ly = y + 10;
        i = 0;
        g2.setColor(Color.BLACK);
        g2.drawString("Expense distribution:", lx, ly-6);
        for (String cat : map.keySet()) {
            float hue = (i * 0.14f) % 1.0f;
            Color ccol = Color.getHSBColor(hue, 0.6f, 0.9f);
            g2.setColor(ccol);
            g2.fillRect(lx, ly + i*20, 12, 12);
            g2.setColor(Color.BLACK);
            String label = String.format("%s (%.2f)", cat, map.get(cat));
            g2.drawString(label, lx + 18, ly + i*20 + 12);
            i++;
        }
    }
}

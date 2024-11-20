import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class ExpenseTracker {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/expense_tracker";
    private static final String DB_USER = "root"; // Change to your username
    private static final String DB_PASSWORD = "rajesh"; // Change to your password

    public static void main(String[] args) {
        try {
            setupDatabase();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error setting up database: " + e.getMessage());
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Expense Tracker");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);

            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.addTab("Add Transaction", createAddTransactionPanel());
            tabbedPane.addTab("View Summary", createViewSummaryPanel());
            tabbedPane.addTab("View Transactions", createViewTransactionsPanel());

            frame.add(tabbedPane);
            frame.setVisible(true);
        });
    }

    private static JPanel createAddTransactionPanel() {
        JPanel panel = new JPanel(new GridLayout(7, 2, 10, 10));
        JTextField categoryField = new JTextField();
        JTextField descriptionField = new JTextField();
        JTextField amountField = new JTextField();
        JComboBox<String> typeBox = new JComboBox<>(new String[]{"Income", "Expense"});
        JButton addButton = new JButton("Add Transaction");
        JButton clearAllButton = new JButton("Clear All Transactions");

        panel.add(new JLabel("Category:"));
        panel.add(categoryField);
        panel.add(new JLabel("Description:"));
        panel.add(descriptionField);
        panel.add(new JLabel("Amount:"));
        panel.add(amountField);
        panel.add(new JLabel("Type:"));
        panel.add(typeBox);
        panel.add(new JLabel());
        panel.add(addButton);
        panel.add(new JLabel());
        panel.add(clearAllButton);

        // Add transaction to the database
        addButton.addActionListener(e -> {
            String category = categoryField.getText();
            String description = descriptionField.getText();
            String amountText = amountField.getText();
            String type = (String) typeBox.getSelectedItem();

            try {
                double amount = Double.parseDouble(amountText);
                addTransactionToDatabase(category, description, amount, type);
                JOptionPane.showMessageDialog(panel, "Transaction added successfully!");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Please enter a valid amount.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Error adding transaction: " + ex.getMessage());
            }
        });

        // Clear all transactions from the database
        clearAllButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    panel,
                    "Are you sure you want to delete all data? This action cannot be undone.",
                    "Confirm Clear All",
                    JOptionPane.YES_NO_OPTION
            );

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    clearDatabase();
                    JOptionPane.showMessageDialog(panel, "All transactions cleared successfully!");
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Error clearing data: " + ex.getMessage());
                }
            }
        });

        return panel;
    }

    private static JPanel createViewSummaryPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel incomeLabel = new JLabel("Total Income: ");
        JLabel expenseLabel = new JLabel("Total Expenses: ");
        JLabel balanceLabel = new JLabel("Net Balance: ");

        panel.add(incomeLabel);
        panel.add(expenseLabel);
        panel.add(balanceLabel);

        JButton refreshButton = new JButton("Refresh");
        panel.add(refreshButton);

        refreshButton.addActionListener(e -> {
            try {
                double totalIncome = calculateTotal("Income");
                double totalExpense = calculateTotal("Expense");
                double netBalance = totalIncome - totalExpense;

                incomeLabel.setText("Total Income: " + totalIncome);
                expenseLabel.setText("Total Expenses: " + totalExpense);
                balanceLabel.setText("Net Balance: " + netBalance);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Error loading summary: " + ex.getMessage());
            }
        });

        return panel;
    }

    private static JPanel createViewTransactionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultTableModel model = new DefaultTableModel(new Object[]{"ID", "Date", "Category", "Description", "Amount", "Type"}, 0);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);

        JButton refreshButton = new JButton("Refresh");
        JButton deleteButton = new JButton("Delete Selected Transaction");

        // Load transactions into the table
        refreshButton.addActionListener(e -> {
            try {
                model.setRowCount(0);
                loadTransactions(model);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Error loading transactions: " + ex.getMessage());
            }
        });

        // Delete the selected transaction
        deleteButton.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(panel, "Please select a transaction to delete.");
                return;
            }

            int transactionId = (int) model.getValueAt(selectedRow, 0);
            try {
                deleteTransaction(transactionId);
                model.removeRow(selectedRow);
                JOptionPane.showMessageDialog(panel, "Transaction deleted successfully!");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Error deleting transaction: " + ex.getMessage());
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(refreshButton);
        buttonPanel.add(deleteButton);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static void setupDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS transactions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    category VARCHAR(255) NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    amount DOUBLE NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;
            try (Statement statement = connection.createStatement()) {
                statement.execute(createTableSQL);
            }
        }
    }

    private static void addTransactionToDatabase(String category, String description, double amount, String type) throws SQLException {
        String insertSQL = "INSERT INTO transactions (category, description, amount, type) VALUES (?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {
            preparedStatement.setString(1, category);
            preparedStatement.setString(2, description);
            preparedStatement.setDouble(3, amount);
            preparedStatement.setString(4, type);
            preparedStatement.executeUpdate();
        }
    }

    private static double calculateTotal(String type) throws SQLException {
        String query = "SELECT SUM(amount) FROM transactions WHERE type = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, type);
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.next() ? resultSet.getDouble(1) : 0;
        }
    }

    private static void loadTransactions(DefaultTableModel model) throws SQLException {
        String query = "SELECT id, date, category, description, amount, type FROM transactions";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                model.addRow(new Object[]{
                        resultSet.getInt("id"),
                        resultSet.getTimestamp("date"),
                        resultSet.getString("category"),
                        resultSet.getString("description"),
                        resultSet.getDouble("amount"),
                        resultSet.getString("type")
                });
            }
        }
    }

    private static void deleteTransaction(int transactionId) throws SQLException {
        String deleteSQL = "DELETE FROM transactions WHERE id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement preparedStatement = connection.prepareStatement(deleteSQL)) {
            preparedStatement.setInt(1, transactionId);
            preparedStatement.executeUpdate();
        }
    }

    private static void clearDatabase() throws SQLException {
        String deleteSQL = "DELETE FROM transactions";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(deleteSQL);
        }
    }
}

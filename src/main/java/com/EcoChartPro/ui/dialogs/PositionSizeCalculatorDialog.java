package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.ui.ChartWorkspacePanel;
import com.EcoChartPro.ui.chart.ChartPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.function.Consumer;

/**
 * A dialog window that provides a tool for calculating position size
 * based on account details, risk parameters, and trade levels.
 */
public class PositionSizeCalculatorDialog extends JDialog {

    // --- Input Fields ---
    private JFormattedTextField accountBalanceField;
    private JFormattedTextField riskPercentageField;
    private JFormattedTextField leverageField;
    private JFormattedTextField entryPriceField;
    private JFormattedTextField stopLossPriceField;

    // --- Result Labels ---
    private JLabel stopDistanceLabel;
    private JLabel riskAmountLabel;
    private JLabel positionSizeLabel;
    private JLabel notionalValueLabel;
    
    // --- Formatters for Results ---
    private final NumberFormat currencyResultFormat = NumberFormat.getCurrencyInstance();
    private final NumberFormat quantityResultFormat = NumberFormat.getNumberInstance();


    public PositionSizeCalculatorDialog(Frame owner, ChartWorkspacePanel workspacePanel) {
        super(owner, "Position Size Calculator", true); // Modal dialog

        // --- UI Setup ---
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 5, 8, 5); 
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Setup result formatters
        quantityResultFormat.setMaximumFractionDigits(8);
        quantityResultFormat.setGroupingUsed(true);

        // --- Number Formatters for Input Fields ---
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setGroupingUsed(false);
        numberFormat.setMaximumFractionDigits(10); // Allow high precision for price inputs

        // --- Account & Risk Panel ---
        JPanel accountPanel = createTitledPanel("Account & Risk");
        accountBalanceField = new JFormattedTextField(numberFormat);
        riskPercentageField = new JFormattedTextField(numberFormat);
        leverageField = new JFormattedTextField(numberFormat);

        accountBalanceField.setColumns(12);
        riskPercentageField.setColumns(12);
        leverageField.setColumns(12);
        
        // Default values
        accountBalanceField.setValue(100000);
        riskPercentageField.setValue(1);
        leverageField.setValue(1);

        // Layout within account panel
        addLabelAndComponent(accountPanel, new JLabel("Account Balance ($):"), accountBalanceField, 0);
        addLabelAndComponent(accountPanel, new JLabel("Risk (%):"), riskPercentageField, 1);
        addLabelAndComponent(accountPanel, new JLabel("Leverage (x):"), leverageField, 2);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        mainPanel.add(accountPanel, gbc);

        // --- Trade Levels Panel ---
        JPanel levelsPanel = createTitledPanel("Trade Levels");
        entryPriceField = new JFormattedTextField(numberFormat);
        stopLossPriceField = new JFormattedTextField(numberFormat);

        entryPriceField.setColumns(12);
        stopLossPriceField.setColumns(12);

        JButton selectEntryButton = new JButton("...");
        selectEntryButton.setMargin(new Insets(1, 4, 1, 4));
        selectEntryButton.setToolTipText("Select Entry Price from Chart");
        JPanel entryPricePanel = new JPanel(new BorderLayout(4, 0));
        entryPricePanel.add(entryPriceField, BorderLayout.CENTER);
        entryPricePanel.add(selectEntryButton, BorderLayout.EAST);

        JButton selectStopButton = new JButton("...");
        selectStopButton.setMargin(new Insets(1, 4, 1, 4));
        selectStopButton.setToolTipText("Select Stop-Loss Price from Chart");
        JPanel stopLossPanel = new JPanel(new BorderLayout(4, 0));
        stopLossPanel.add(stopLossPriceField, BorderLayout.CENTER);
        stopLossPanel.add(selectStopButton, BorderLayout.EAST);
        
        if (workspacePanel != null) {
            selectEntryButton.addActionListener(e -> selectPriceFromChart(workspacePanel, entryPriceField));
            selectStopButton.addActionListener(e -> selectPriceFromChart(workspacePanel, stopLossPriceField));
        } else {
            selectEntryButton.setEnabled(false);
            selectStopButton.setEnabled(false);
        }

        addLabelAndComponent(levelsPanel, new JLabel("Entry Price:"), entryPricePanel, 0);
        addLabelAndComponent(levelsPanel, new JLabel("Stop-Loss Price:"), stopLossPanel, 1);
        
        gbc.gridy = 1;
        mainPanel.add(levelsPanel, gbc);

        // --- Results Panel ---
        JPanel resultsPanel = createTitledPanel("Calculated Position");
        stopDistanceLabel = createResultLabel();
        riskAmountLabel = createResultLabel();
        positionSizeLabel = createResultLabel();
        notionalValueLabel = createResultLabel();

        addLabelAndComponent(resultsPanel, new JLabel("Stop Distance ($):"), stopDistanceLabel, 0);
        addLabelAndComponent(resultsPanel, new JLabel("Risk Amount ($):"), riskAmountLabel, 1);
        addLabelAndComponent(resultsPanel, new JLabel("Position Size (units):"), positionSizeLabel, 2);
        addLabelAndComponent(resultsPanel, new JLabel("Position Value (Notional $):"), notionalValueLabel, 3);
        
        gbc.gridy = 2;
        mainPanel.add(resultsPanel, gbc);
        
        // --- Close Button ---
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(15, 5, 0, 5);
        mainPanel.add(closeButton, gbc);

        // listener to trigger recalculation on any input change
        DocumentListener recalculateListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { recalculate(); }
            @Override public void removeUpdate(DocumentEvent e) { recalculate(); }
            @Override public void changedUpdate(DocumentEvent e) { recalculate(); }
        };

        accountBalanceField.getDocument().addDocumentListener(recalculateListener);
        riskPercentageField.getDocument().addDocumentListener(recalculateListener);
        leverageField.getDocument().addDocumentListener(recalculateListener);
        entryPriceField.getDocument().addDocumentListener(recalculateListener);
        stopLossPriceField.getDocument().addDocumentListener(recalculateListener);
        
        // Initial calculation with default values
        recalculate();

        // --- Finalize Dialog ---
        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /**
     * Public method to set the entry price field from an external source.
     * @param price The price to set.
     */
    public void setEntryPrice(BigDecimal price) {
        if (price != null) {
            entryPriceField.setValue(price);
        }
    }
    
    /**
     * Hides the dialog, enters price selection mode on the chart, and sets up a callback
     * to populate the target field with the selected price.
     * @param workspacePanel The main application workspace to find the active chart.
     * @param targetField The text field to update with the selected price.
     */
    private void selectPriceFromChart(ChartWorkspacePanel workspacePanel, JFormattedTextField targetField) {
        setVisible(false);
        ChartPanel chartPanel = workspacePanel.getActiveChartPanel();
        if (chartPanel != null) {
            chartPanel.enterPriceSelectionMode(price -> {
                // This callback runs when a price is selected from the chart
                SwingUtilities.invokeLater(() -> {
                    if (price != null) {
                        targetField.setValue(price);
                    }
                    setVisible(true);
                    toFront();
                    requestFocusInWindow();
                });
            });
        } else {
            // If no chart is available, show the dialog again with an error message.
            setVisible(true);
            JOptionPane.showMessageDialog(this, "No active chart to select a price from.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Core calculation logic.
     * Parses all inputs, calculates the results, and updates the UI.
     */
    private void recalculate() {
        try {
            // 1. Parse all inputs into BigDecimal
            BigDecimal accountBalance = new BigDecimal(accountBalanceField.getText());
            BigDecimal riskPercentage = new BigDecimal(riskPercentageField.getText());
            BigDecimal leverage = new BigDecimal(leverageField.getText());
            BigDecimal entryPrice = new BigDecimal(entryPriceField.getText());
            BigDecimal stopLossPrice = new BigDecimal(stopLossPriceField.getText());

            // 2. Perform Calculations
            BigDecimal stopDistance = entryPrice.subtract(stopLossPrice).abs();
            if (stopDistance.signum() == 0) { // Avoid division by zero
                clearResults();
                return;
            }

            BigDecimal riskAmount = accountBalance.multiply(riskPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal positionSize = riskAmount.divide(stopDistance, 8, RoundingMode.DOWN);
            BigDecimal notionalValue = positionSize.multiply(entryPrice).multiply(leverage);

            // 3. Update result labels with formatted strings
            stopDistanceLabel.setText(quantityResultFormat.format(stopDistance));
            riskAmountLabel.setText(currencyResultFormat.format(riskAmount));
            positionSizeLabel.setText(quantityResultFormat.format(positionSize));
            notionalValueLabel.setText(currencyResultFormat.format(notionalValue));

        } catch (NumberFormatException | ArithmeticException ex) {
            // If any field is empty or contains invalid text, clear the results
            clearResults();
        }
    }

    /**
     * Helper method to reset result labels to their default state.
     */
    private void clearResults() {
        stopDistanceLabel.setText("--");
        riskAmountLabel.setText("--");
        positionSizeLabel.setText("--");
        notionalValueLabel.setText("--");
    }

    /**
     * Helper method to create a panel with a titled border and a GridBagLayout.
     */
    private JPanel createTitledPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP));
        return panel;
    }

    /**
     * Helper method to add a label and a component to a panel in a two-column layout.
     */
    private void addLabelAndComponent(JPanel panel, JComponent label, JComponent component, int yPos) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 10);
        
        gbc.gridx = 0;
        gbc.gridy = yPos;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0.5;
        panel.add(label, gbc);
        
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        panel.add(component, gbc);
    }
    
    /**
     * Helper method to create a standard, bolded label for displaying results.
     */
    private JLabel createResultLabel() {
        JLabel label = new JLabel("--");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }
}
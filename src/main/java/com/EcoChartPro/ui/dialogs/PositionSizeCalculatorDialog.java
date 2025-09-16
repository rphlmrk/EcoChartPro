package com.EcoChartPro.ui.dialogs;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;

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


    public PositionSizeCalculatorDialog(Frame owner) {
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

        // MODIFICATION: Set columns to suggest a wider preferred size
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
        
        addLabelAndComponent(levelsPanel, new JLabel("Entry Price:"), entryPriceField, 0);
        addLabelAndComponent(levelsPanel, new JLabel("Stop-Loss Price:"), stopLossPriceField, 1);
        
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
        gbc.insets = new Insets(4, 5, 4, 10); // MODIFICATION: Increased padding
        
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
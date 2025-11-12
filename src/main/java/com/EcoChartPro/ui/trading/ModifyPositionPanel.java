package com.EcoChartPro.ui.trading;

import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.chart.ChartPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.function.Consumer;

/**
 * A panel for modifying the Stop Loss and Take Profit of an existing position.
 */
public class ModifyPositionPanel extends JPanel {

    private final Position position;
    private final ChartPanel chartPanel;
    private final WorkspaceContext context;
    private final JTextField entryPriceField, stopLossField, takeProfitField, trailingStopDistanceField;
    private final JCheckBox slCheckBox, tpCheckBox, trailingStopCheckBox;
    private final JButton modifyPositionButton;
    private final TradeDirection tradeDirection;
    private final Consumer<Boolean> closeCallback;

    private final JLabel pnlAtSlLabel;
    private final JLabel pnlAtTpLabel;
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$0.00;-$0.00");

    private final JLabel stopDistanceLabel;
    private final JLabel profitDistanceLabel;
    private final JLabel riskRewardLabel;
    
    private final JButton slPickerButton, tpPickerButton;
    
    private boolean isInternallyUpdating = false;

    public ModifyPositionPanel(Position position, ChartPanel chartPanel, WorkspaceContext context, Consumer<Boolean> closeCallback) {
        this.position = position;
        this.chartPanel = chartPanel;
        this.context = context;
        this.tradeDirection = position.direction();
        this.closeCallback = closeCallback;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // --- Entry Price (Read-only) ---
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        add(new JLabel("Entry Price:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        entryPriceField = new JTextField();
        entryPriceField.setEditable(false);
        entryPriceField.setBackground(UIManager.getColor("TextField.disabledBackground"));
        add(entryPriceField, gbc);
        gbc.gridwidth = 1;

        // --- Stop Loss ---
        gbc.gridx = 0; gbc.gridy = 1;
        slCheckBox = new JCheckBox("Stop Loss:");
        add(slCheckBox, gbc);
        gbc.gridx = 1;
        stopLossField = new JTextField();
        add(stopLossField, gbc);
        gbc.gridx = 2;
        slPickerButton = createPickerButton("stopLoss");
        add(slPickerButton, gbc);
        gbc.gridx = 3;
        pnlAtSlLabel = new JLabel();
        pnlAtSlLabel.setForeground(Color.GRAY);
        add(pnlAtSlLabel, gbc);

        // --- Take Profit ---
        gbc.gridx = 0; gbc.gridy = 2;
        tpCheckBox = new JCheckBox("Take Profit:");
        add(tpCheckBox, gbc);
        gbc.gridx = 1;
        takeProfitField = new JTextField();
        add(takeProfitField, gbc);
        gbc.gridx = 2;
        tpPickerButton = createPickerButton("takeProfit");
        add(tpPickerButton, gbc);
        gbc.gridx = 3;
        pnlAtTpLabel = new JLabel();
        pnlAtTpLabel.setForeground(Color.GRAY);
        add(pnlAtTpLabel, gbc);

        // --- Trailing Stop ---
        gbc.gridx = 0; gbc.gridy = 3;
        trailingStopCheckBox = new JCheckBox("Trailing:");
        add(trailingStopCheckBox, gbc);
        gbc.gridx = 1;
        trailingStopDistanceField = new JTextField();
        add(trailingStopDistanceField, gbc);
        gbc.gridx = 3;
        add(new JLabel("Distance"), gbc);

        // --- Risk/Reward Summary Panel ---
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4;
        gbc.insets = new Insets(10, 0, 4, 0);
        JPanel summaryPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        summaryPanel.setBorder(BorderFactory.createTitledBorder("Trade Summary"));
        stopDistanceLabel = createSummaryLabel("SL Distance: -");
        profitDistanceLabel = createSummaryLabel("TP Distance: -");
        riskRewardLabel = createSummaryLabel("R/R: -");
        summaryPanel.add(stopDistanceLabel);
        summaryPanel.add(profitDistanceLabel);
        summaryPanel.add(riskRewardLabel);
        add(summaryPanel, gbc);

        // --- Buttons ---
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 4; 
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE; 
        gbc.insets = new Insets(10, 4, 4, 4); 

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> closeCallback.accept(false)); // Signal close
        modifyPositionButton = new JButton("Modify Position");
        buttonPanel.add(cancelButton);
        buttonPanel.add(modifyPositionButton);
        add(buttonPanel, gbc);


        addListeners();
        prefillData();
        updateFieldStates();
        updateCalculations();
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "";
        return price.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    
    private JButton createPickerButton(String fieldName) {
        JButton button = new JButton("⌖");
        button.setMargin(new Insets(1, 4, 1, 4));
        button.setToolTipText("Select price from chart");
        button.addActionListener(e -> firePropertyChange("priceSelectionRequested", null, fieldName));
        return button;
    }

    private JLabel createSummaryLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return label;
    }

    @FunctionalInterface
    interface DocumentUpdateListener extends DocumentListener {
        void update(DocumentEvent e);
        @Override default void insertUpdate(DocumentEvent e) { update(e); }
        @Override default void removeUpdate(DocumentEvent e) { update(e); }
        @Override default void changedUpdate(DocumentEvent e) { update(e); }
    }

    private void addListeners() {
        slCheckBox.addActionListener(e -> {
            updateFieldStates();
            updateCalculations();
        });
        tpCheckBox.addActionListener(e -> {
            updateFieldStates();
            updateCalculations();
        });
        trailingStopCheckBox.addActionListener(e -> {
            updateFieldStates();
            updateCalculations();
        });
        modifyPositionButton.addActionListener(e -> modifyPosition());

        DocumentUpdateListener calculationListener = e -> updateCalculations();
        stopLossField.getDocument().addDocumentListener(calculationListener);
        takeProfitField.getDocument().addDocumentListener(calculationListener);
        trailingStopDistanceField.getDocument().addDocumentListener(calculationListener);
    }

    private void updateCalculations() {
        if (isInternallyUpdating) return;
        isInternallyUpdating = true;
        OrderPreview preview = null;
        try {
            BigDecimal entryPrice = new BigDecimal(entryPriceField.getText());
            
            if (trailingStopCheckBox.isSelected()) {
                BigDecimal stopLossPrice = new BigDecimal(stopLossField.getText());
                BigDecimal distance = entryPrice.subtract(stopLossPrice).abs();
                trailingStopDistanceField.setText(formatPrice(distance));
            }

            updatePnlLabels(position.size(), entryPrice);
            updateRiskRewardLabels(entryPrice);

            BigDecimal slPrice = slCheckBox.isSelected() ? new BigDecimal(stopLossField.getText()) : null;
            BigDecimal tpPrice = tpCheckBox.isSelected() ? new BigDecimal(takeProfitField.getText()) : null;
            preview = new OrderPreview(entryPrice, slPrice, tpPrice);

        } catch (Exception ex) {
            pnlAtSlLabel.setText("");
            pnlAtTpLabel.setText("");
            stopDistanceLabel.setText("SL Distance: -");
            profitDistanceLabel.setText("TP Distance: -");
            riskRewardLabel.setText("R/R: -");
            riskRewardLabel.setForeground(Color.BLACK);
        } finally {
            firePropertyChange("previewUpdated", null, preview);
            isInternallyUpdating = false;
        }
    }

    private void updatePnlLabels(BigDecimal assetSize, BigDecimal entryPrice) throws NumberFormatException {
        if (slCheckBox.isSelected()) {
            BigDecimal stopLossPrice = new BigDecimal(stopLossField.getText());
            BigDecimal pnl = stopLossPrice.subtract(entryPrice).multiply(assetSize);
            if (tradeDirection == TradeDirection.SHORT) pnl = pnl.negate();
            pnlAtSlLabel.setText(PNL_FORMAT.format(pnl));
            pnlAtSlLabel.setForeground(pnl.compareTo(BigDecimal.ZERO) >= 0 ? new Color(0x4CAF50) : new Color(0xF44336));
        } else {
            pnlAtSlLabel.setText("");
        }

        if (tpCheckBox.isSelected()) {
            BigDecimal takeProfitPrice = new BigDecimal(takeProfitField.getText());
            BigDecimal pnl = takeProfitPrice.subtract(entryPrice).multiply(assetSize);
            if (tradeDirection == TradeDirection.SHORT) pnl = pnl.negate();
            pnlAtTpLabel.setText(PNL_FORMAT.format(pnl));
            pnlAtTpLabel.setForeground(pnl.compareTo(BigDecimal.ZERO) >= 0 ? new Color(0x4CAF50) : new Color(0xF44336));
        } else {
            pnlAtTpLabel.setText("");
        }
    }

    private void updateRiskRewardLabels(BigDecimal entryPrice) throws NumberFormatException {
        BigDecimal stopDistance = BigDecimal.ZERO;
        BigDecimal profitDistance = BigDecimal.ZERO;

        if (slCheckBox.isSelected()) {
            BigDecimal stopLossPrice = new BigDecimal(stopLossField.getText());
            stopDistance = entryPrice.subtract(stopLossPrice).abs();
            stopDistanceLabel.setText(String.format("SL Dist: %.4f", stopDistance));
        } else {
            stopDistanceLabel.setText("SL Dist: -");
        }

        if (tpCheckBox.isSelected()) {
            BigDecimal takeProfitPrice = new BigDecimal(takeProfitField.getText());
            profitDistance = takeProfitPrice.subtract(entryPrice).abs();
            profitDistanceLabel.setText(String.format("TP Dist: %.4f", profitDistance));
        } else {
            profitDistanceLabel.setText("TP Dist: -");
        }

        if (slCheckBox.isSelected() && tpCheckBox.isSelected() && stopDistance.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = profitDistance.divide(stopDistance, 2, RoundingMode.HALF_UP);
            riskRewardLabel.setText(String.format("R/R: 1 : %.2f", ratio));
            riskRewardLabel.setForeground(ratio.compareTo(BigDecimal.ONE) >= 0 ? new Color(0x4CAF50) : new Color(0xF44336));
        } else {
            riskRewardLabel.setText("R/R: -");
            riskRewardLabel.setForeground(Color.BLACK);
        }
    }

    private void updateFieldStates() {
        boolean isTrailing = trailingStopCheckBox.isSelected();
        trailingStopDistanceField.setEditable(isTrailing);
        if (isTrailing) {
            slCheckBox.setSelected(true);
            slCheckBox.setEnabled(false);
            trailingStopDistanceField.setBackground(UIManager.getColor("TextField.background"));
        } else {
            slCheckBox.setEnabled(true);
            trailingStopDistanceField.setBackground(UIManager.getColor("TextField.disabledBackground"));
        }
        
        stopLossField.setEnabled(slCheckBox.isSelected());
        takeProfitField.setEnabled(tpCheckBox.isSelected());
        slPickerButton.setEnabled(slCheckBox.isSelected());
        tpPickerButton.setEnabled(tpCheckBox.isSelected());
    }

    private void prefillData() {
        entryPriceField.setText(formatPrice(position.entryPrice()));
        if (position.stopLoss() != null) {
            slCheckBox.setSelected(true);
            stopLossField.setText(formatPrice(position.stopLoss()));
        } else {
            slCheckBox.setSelected(false);
            stopLossField.setText("");
        }
        if (position.takeProfit() != null) {
            tpCheckBox.setSelected(true);
            takeProfitField.setText(formatPrice(position.takeProfit()));
        } else {
            tpCheckBox.setSelected(false);
            takeProfitField.setText("");
        }
        if (position.trailingStopDistance() != null) {
            trailingStopCheckBox.setSelected(true);
            trailingStopDistanceField.setText(formatPrice(position.trailingStopDistance()));
        } else {
            trailingStopCheckBox.setSelected(false);
            trailingStopDistanceField.setText("");
        }
    }

    private void modifyPosition() {
        try {
            BigDecimal entryPrice = new BigDecimal(entryPriceField.getText());
            BigDecimal stopLoss = slCheckBox.isSelected() ? new BigDecimal(stopLossField.getText()) : null;
            BigDecimal takeProfit = tpCheckBox.isSelected() ? new BigDecimal(takeProfitField.getText()) : null;
            BigDecimal trailingDistance = trailingStopCheckBox.isSelected() ? new BigDecimal(trailingStopDistanceField.getText()) : null;

            validateOrder(entryPrice, stopLoss, takeProfit);

            context.getPaperTradingService().modifyOrder(
                position.id(),
                null, // Entry price is not modifiable for an open position
                stopLoss,
                takeProfit,
                trailingDistance
            );

            closeCallback.accept(true);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number format: " + ex.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validation Error", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not modify position: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void validateOrder(BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal takeProfit) {
        if (slCheckBox.isSelected() && stopLoss != null) {
            if (tradeDirection == TradeDirection.LONG && stopLoss.compareTo(entryPrice) >= 0) {
                throw new IllegalArgumentException("For a LONG trade, Stop Loss must be below the entry price.");
            }
            if (tradeDirection == TradeDirection.SHORT && stopLoss.compareTo(entryPrice) <= 0) {
                throw new IllegalArgumentException("For a SHORT trade, Stop Loss must be above the entry price.");
            }
        }

        if (tpCheckBox.isSelected() && takeProfit != null) {
            if (tradeDirection == TradeDirection.LONG && takeProfit.compareTo(entryPrice) <= 0) {
                throw new IllegalArgumentException("For a LONG trade, Take Profit must be above the entry price.");
            }
            if (tradeDirection == TradeDirection.SHORT && takeProfit.compareTo(entryPrice) >= 0) {
                throw new IllegalArgumentException("For a SHORT trade, Take Profit must be below the entry price.");
            }
        }
    }

    public JButton getModifyPositionButton() {
        return modifyPositionButton;
    }
    
    public JTextField getStopLossField() { return stopLossField; }
    public JTextField getTakeProfitField() { return takeProfitField; }
}
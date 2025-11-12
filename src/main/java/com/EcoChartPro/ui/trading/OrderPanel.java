package com.EcoChartPro.ui.trading;

import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.settings.Checklist;
import com.EcoChartPro.core.settings.ChecklistManager;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.OrderStatus;
import com.EcoChartPro.model.trading.OrderType;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A panel for creating and submitting trade orders.
 * It contains fields for all necessary order parameters.
 */
public class OrderPanel extends JPanel {

    private final ChartPanel chartPanel;
    private final WorkspaceContext context;
    private final JTextField amountUsdField, limitPriceField, stopLossField, takeProfitField, trailingStopDistanceField;
    private final JCheckBox slCheckBox, tpCheckBox, trailingStopCheckBox;
    private final JButton placeOrderButton;
    private final TradeDirection tradeDirection;
    private final Consumer<Boolean> closeCallback;

    private final JRadioButton amountRadioButton, riskRadioButton;
    private final JTextField riskPercentField;
    private final ButtonGroup sizingGroup;
    private final JLabel pnlAtSlLabel;
    private final JLabel pnlAtTpLabel;
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$0.00;-$0.00");

    private final JLabel stopDistanceLabel;
    private final JLabel profitDistanceLabel;
    private final JLabel riskRewardLabel;

    private final JRadioButton buyLimitRadio, buyStopRadio, sellLimitRadio, sellStopRadio;
    private final ButtonGroup orderTypeGroup;

    private final JButton limitPricePickerButton, slPickerButton, tpPickerButton;

    private final JComboBox<Object> checklistComboBox;
    private final JPanel checklistItemsPanel;
    private final List<JCheckBox> activeCheckBoxes = new ArrayList<>();

    private boolean isInternallyUpdating = false;

    public OrderPanel(ChartPanel chartPanel, TradeDirection tradeDirection, WorkspaceContext context, Consumer<Boolean> closeCallback) {
        this.chartPanel = chartPanel;
        this.tradeDirection = tradeDirection;
        this.context = context;
        this.closeCallback = closeCallback;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        int yPos = 0;

        // --- Checklist ---
        gbc.gridx = 0; gbc.gridy = yPos; gbc.gridwidth = 1;
        add(new JLabel("Checklist:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        checklistComboBox = new JComboBox<>();
        add(checklistComboBox, gbc);
        yPos++;

        // Panel to hold checklist items
        gbc.gridx = 0; gbc.gridy = yPos; gbc.gridwidth = 4;
        checklistItemsPanel = new JPanel();
        checklistItemsPanel.setLayout(new BoxLayout(checklistItemsPanel, BoxLayout.Y_AXIS));
        checklistItemsPanel.setOpaque(false);
        add(checklistItemsPanel, gbc);
        yPos++;
        
        // --- Order Type ---
        gbc.gridx = 0; gbc.gridy = yPos; gbc.gridwidth = 1;
        add(new JLabel("Order Type:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        orderTypeGroup = new ButtonGroup();
        buyLimitRadio = new JRadioButton("Buy Limit");
        buyStopRadio = new JRadioButton("Buy Stop");
        sellLimitRadio = new JRadioButton("Sell Limit");
        sellStopRadio = new JRadioButton("Sell Stop");
        orderTypeGroup.add(buyLimitRadio);
        orderTypeGroup.add(buyStopRadio);
        orderTypeGroup.add(sellLimitRadio);
        orderTypeGroup.add(sellStopRadio);
        JPanel orderTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        if (tradeDirection == TradeDirection.LONG) {
            orderTypePanel.add(buyLimitRadio);
            orderTypePanel.add(buyStopRadio);
            buyLimitRadio.setSelected(true);
        } else {
            orderTypePanel.add(sellLimitRadio);
            orderTypePanel.add(sellStopRadio);
            sellLimitRadio.setSelected(true);
        }
        add(orderTypePanel, gbc);
        gbc.gridwidth = 1; yPos++;

        // --- Sizing Mode ---
        gbc.gridx = 0; gbc.gridy = yPos; gbc.gridwidth = 1;
        add(new JLabel("Sizing Mode:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        amountRadioButton = new JRadioButton("Amount", true);
        riskRadioButton = new JRadioButton("Risk %");
        sizingGroup = new ButtonGroup();
        sizingGroup.add(amountRadioButton);
        sizingGroup.add(riskRadioButton);
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        radioPanel.add(amountRadioButton);
        radioPanel.add(riskRadioButton);
        add(radioPanel, gbc);
        gbc.gridwidth = 1; yPos++;

        // --- Amount (USD) ---
        gbc.gridx = 0; gbc.gridy = yPos;
        add(new JLabel("Amount (USD):"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        amountUsdField = new JTextField("1000");
        add(amountUsdField, gbc);
        gbc.gridwidth = 1; yPos++;
        
        // --- Risk Percentage ---
        gbc.gridx = 0; gbc.gridy = yPos;
        add(new JLabel("Risk (%):"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        riskPercentField = new JTextField("1.0");
        add(riskPercentField, gbc);
        gbc.gridwidth = 1; yPos++;

        // --- Limit/Stop Price ---
        gbc.gridx = 0; gbc.gridy = yPos;
        add(new JLabel("Price:"), gbc);
        gbc.gridx = 1;
        limitPriceField = new JTextField();
        add(limitPriceField, gbc);
        gbc.gridx = 2;
        limitPricePickerButton = createPickerButton("limitPrice");
        add(limitPricePickerButton, gbc); yPos++;

        // --- Stop Loss ---
        gbc.gridx = 0; gbc.gridy = yPos;
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
        add(pnlAtSlLabel, gbc); yPos++;

        // --- Take Profit ---
        gbc.gridx = 0; gbc.gridy = yPos;
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
        add(pnlAtTpLabel, gbc); yPos++;

        // --- Trailing Stop ---
        gbc.gridx = 0; gbc.gridy = yPos;
        trailingStopCheckBox = new JCheckBox("Trailing:");
        add(trailingStopCheckBox, gbc);
        gbc.gridx = 1;
        trailingStopDistanceField = new JTextField();
        add(trailingStopDistanceField, gbc);
        gbc.gridx = 3;
        add(new JLabel("Distance"), gbc); yPos++;
        
        // --- Risk/Reward Summary Panel ---
        gbc.gridx = 0; gbc.gridy = yPos; gbc.gridwidth = 4;
        gbc.insets = new Insets(10, 0, 4, 0);
        JPanel summaryPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        summaryPanel.setBorder(BorderFactory.createTitledBorder("Trade Summary"));
        stopDistanceLabel = createSummaryLabel("SL Distance: -");
        profitDistanceLabel = createSummaryLabel("TP Distance: -");
        riskRewardLabel = createSummaryLabel("R/R: -");
        summaryPanel.add(stopDistanceLabel);
        summaryPanel.add(profitDistanceLabel);
        summaryPanel.add(riskRewardLabel);
        add(summaryPanel, gbc); yPos++;

        // --- Buttons ---
        gbc.gridx = 0; gbc.gridy = yPos; gbc.gridwidth = 4;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE; 
        gbc.insets = new Insets(10, 4, 4, 4); 

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> closeCallback.accept(false));
        placeOrderButton = new JButton("Place Order");
        buttonPanel.add(cancelButton);
        buttonPanel.add(placeOrderButton);
        add(buttonPanel, gbc);

        addListeners();
        populateChecklistComboBox();
        updateFieldStates();
        updateSizingControls();
        prefillPrices();
        validateOrderButtonState();
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
        placeOrderButton.addActionListener(e -> placeOrder());
        
        amountRadioButton.addActionListener(e -> updateSizingControls());
        riskRadioButton.addActionListener(e -> updateSizingControls());
        
        DocumentUpdateListener calculationListener = e -> updateCalculations();
        limitPriceField.getDocument().addDocumentListener(calculationListener);
        stopLossField.getDocument().addDocumentListener(calculationListener);
        takeProfitField.getDocument().addDocumentListener(calculationListener);
        riskPercentField.getDocument().addDocumentListener(calculationListener);
        amountUsdField.getDocument().addDocumentListener(calculationListener);
        trailingStopDistanceField.getDocument().addDocumentListener(calculationListener);

        checklistComboBox.addActionListener(e -> handleChecklistSelection());
    }
    
    private void updateSizingControls() {
        boolean isRiskSizing = riskRadioButton.isSelected();
        riskPercentField.setEnabled(isRiskSizing);
        amountUsdField.setEditable(!isRiskSizing);
        
        if (isRiskSizing) {
            amountUsdField.setBackground(UIManager.getColor("TextField.disabledBackground"));
        } else {
            amountUsdField.setBackground(UIManager.getColor("TextField.background"));
        }
        updateCalculations();
    }
    
    private void updateCalculations() {
        if (isInternallyUpdating) return;
        isInternallyUpdating = true;

        OrderPreview preview = null;

        try {
            BigDecimal entryPrice = new BigDecimal(limitPriceField.getText());
            
            if (trailingStopCheckBox.isSelected()) {
                BigDecimal trailingDistance = new BigDecimal(trailingStopDistanceField.getText());
                BigDecimal initialSl = (tradeDirection == TradeDirection.LONG)
                    ? entryPrice.subtract(trailingDistance)
                    : entryPrice.add(trailingDistance);
                stopLossField.setText(formatPrice(initialSl));
            }

            BigDecimal assetSize;
            PaperTradingService pts = context.getPaperTradingService();
            if (riskRadioButton.isSelected()) {
                BigDecimal accountBalance = pts.getAccountBalance();
                BigDecimal riskPercent = new BigDecimal(riskPercentField.getText()).divide(BigDecimal.valueOf(100));
                
                if (slCheckBox.isSelected()) {
                    BigDecimal stopLossPrice = new BigDecimal(stopLossField.getText());
                    BigDecimal riskAmountUsd = accountBalance.multiply(riskPercent).setScale(2, RoundingMode.HALF_UP);
                    amountUsdField.setText(riskAmountUsd.toPlainString());
                    BigDecimal priceDifference = entryPrice.subtract(stopLossPrice).abs();
                    assetSize = (priceDifference.compareTo(BigDecimal.ZERO) > 0) ?
                                riskAmountUsd.divide(priceDifference, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                } else {
                    assetSize = BigDecimal.ZERO;
                    amountUsdField.setText("");
                }
            } else {
                BigDecimal leverage = pts.getLeverage();
                BigDecimal amountUsd = new BigDecimal(amountUsdField.getText());
                BigDecimal leveragedAmount = amountUsd.multiply(leverage);
                assetSize = (entryPrice.compareTo(BigDecimal.ZERO) > 0) ?
                            leveragedAmount.divide(entryPrice, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            }
            
            updatePnlLabels(assetSize, entryPrice);
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
        
        trailingStopDistanceField.setEnabled(isTrailing);

        if (isTrailing) {
            slCheckBox.setSelected(true);
            slCheckBox.setEnabled(false);
            stopLossField.setEditable(false);
            stopLossField.setBackground(UIManager.getColor("TextField.disabledBackground"));
            slPickerButton.setEnabled(false);
        } else {
            slCheckBox.setEnabled(true);
            stopLossField.setEditable(slCheckBox.isSelected());
            stopLossField.setBackground(slCheckBox.isSelected() ? UIManager.getColor("TextField.background") : UIManager.getColor("TextField.disabledBackground"));
            slPickerButton.setEnabled(slCheckBox.isSelected());
        }

        stopLossField.setEnabled(slCheckBox.isSelected());
        takeProfitField.setEnabled(tpCheckBox.isSelected());
        tpPickerButton.setEnabled(tpCheckBox.isSelected());
    }
    
    private void prefillPrices() {
        KLine lastBar = chartPanel.getDataModel().getCurrentReplayKLine();
        if (lastBar != null) {
            String price = formatPrice(lastBar.close());
            limitPriceField.setText(price);
            stopLossField.setText(price);
            takeProfitField.setText(price);
        }
    }
    
    private void populateChecklistComboBox() {
        checklistComboBox.addItem("None");
        ChecklistManager.getInstance().getChecklists().forEach(checklistComboBox::addItem);
    }

    private void handleChecklistSelection() {
        checklistItemsPanel.removeAll();
        activeCheckBoxes.clear();

        Object selected = checklistComboBox.getSelectedItem();
        if (selected instanceof Checklist checklist) {
            checklist.items().forEach(item -> {
                JCheckBox checkBox = new JCheckBox(item.text());
                checkBox.addActionListener(e -> validateOrderButtonState());
                checklistItemsPanel.add(checkBox);
                activeCheckBoxes.add(checkBox);
            });
        }

        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.pack();
        }
        validateOrderButtonState();
        revalidate();
        repaint();
    }

    private void validateOrderButtonState() {
        boolean allChecked = activeCheckBoxes.stream().allMatch(JCheckBox::isSelected);
        placeOrderButton.setEnabled(allChecked);
    }

    public void placeOrder() {
        if (!placeOrderButton.isEnabled()) {
            return;
        }
        try {
            PaperTradingService pts = context.getPaperTradingService();
            BigDecimal entryPrice = new BigDecimal(limitPriceField.getText());
            BigDecimal leverage = pts.getLeverage();
            BigDecimal size;
            
            if (riskRadioButton.isSelected()) {
                BigDecimal accountBalance = pts.getAccountBalance();
                BigDecimal riskPercent = new BigDecimal(riskPercentField.getText()).divide(BigDecimal.valueOf(100));
                BigDecimal stopLossPrice = new BigDecimal(stopLossField.getText());
                BigDecimal riskAmountUsd = accountBalance.multiply(riskPercent);
                BigDecimal priceDifference = entryPrice.subtract(stopLossPrice).abs();
                if (priceDifference.compareTo(BigDecimal.ZERO) == 0) throw new ArithmeticException("Entry price cannot equal Stop Loss price.");
                size = riskAmountUsd.divide(priceDifference, 8, RoundingMode.HALF_UP);
            } else {
                BigDecimal amountUsd = new BigDecimal(amountUsdField.getText());
                BigDecimal leveragedAmount = amountUsd.multiply(leverage);
                if (entryPrice.compareTo(BigDecimal.ZERO) == 0) throw new ArithmeticException("Entry price cannot be zero for amount-based sizing.");
                size = leveragedAmount.divide(entryPrice, 8, RoundingMode.HALF_UP);
            }
            
            if (size.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException("Calculated size must be positive.");
            }

            Symbol symbol = new Symbol(chartPanel.getDataModel().getCurrentSymbol().symbol());
            
            OrderType type;
            if (buyLimitRadio.isSelected() || sellLimitRadio.isSelected()) {
                type = OrderType.LIMIT;
            } else { 
                type = OrderType.STOP;
            }

            BigDecimal limitPrice = entryPrice;
            BigDecimal stopLoss = slCheckBox.isSelected() ? new BigDecimal(stopLossField.getText()) : null;
            BigDecimal takeProfit = tpCheckBox.isSelected() ? new BigDecimal(takeProfitField.getText()) : null;
            BigDecimal trailingDistance = trailingStopCheckBox.isSelected() ? new BigDecimal(trailingStopDistanceField.getText()) : null;
            
            validateOrder(entryPrice, stopLoss, takeProfit);

            Object selectedChecklistObj = checklistComboBox.getSelectedItem();
            UUID checklistId = null;
            if (selectedChecklistObj instanceof Checklist) {
                checklistId = ((Checklist) selectedChecklistObj).id();
            }

            Order order = new Order(
                UUID.randomUUID(), symbol, type, OrderStatus.PENDING, tradeDirection,
                size, limitPrice, stopLoss, takeProfit, trailingDistance, Instant.now(),
                checklistId
            );

            pts.placeOrder(order, null);
            
            closeCallback.accept(true);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number format: " + ex.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validation Error", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not place order: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
    
    public JButton getPlaceOrderButton() {
        return placeOrderButton;
    }
    
    public JTextField getLimitPriceField() { return limitPriceField; }
    public JTextField getStopLossField() { return stopLossField; }
    public JTextField getTakeProfitField() { return takeProfitField; }
}
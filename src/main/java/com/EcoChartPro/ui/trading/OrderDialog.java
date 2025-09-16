package com.EcoChartPro.ui.trading;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.chart.ChartPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.List;

/**
 * A modal dialog that wraps the OrderPanel to allow users to create an order.
 */
public class OrderDialog extends JDialog implements PropertyChangeListener {

    private final ChartPanel chartPanel;
    private final OrderPanel orderPanel;
    // private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#.########");

    public OrderDialog(Frame owner, ChartPanel chartPanel, TradeDirection tradeDirection) {
        super(owner, true); // Modal, title is now handled by our custom header
        this.chartPanel = chartPanel;

        setUndecorated(true);

        // Main container with a border to define the dialog's edge
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        // Custom header panel
        String directionString = (tradeDirection == TradeDirection.LONG) ? "Buy/Long" : "Sell/Short";
        String symbolString = chartPanel.getDataModel().getCurrentSymbol().displayName();
        String titleText = directionString + " - " + symbolString;
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(titleLabel);

        this.orderPanel = new OrderPanel(chartPanel, tradeDirection, (success) -> {
            // This callback is triggered by "Cancel" button, or by our new listener.
            dispose();
        });
        
        this.orderPanel.addPropertyChangeListener(this);
        
        JButton placeOrderButton = orderPanel.getPlaceOrderButton();
        
        if(tradeDirection == TradeDirection.LONG) {
            placeOrderButton.setBackground(new Color(0x4CAF50));
        } else {
            placeOrderButton.setBackground(new Color(0xF44336));
        }
        placeOrderButton.setForeground(Color.WHITE);
        placeOrderButton.setFocusPainted(false);

        // --- START: Phase 3 Modification ---
        // Remove the default ActionListener(s) from the OrderPanel's button
        // so we can add our own logic before placing the order.
        for (ActionListener l : placeOrderButton.getActionListeners()) {
            placeOrderButton.removeActionListener(l);
        }

        // Add our new, enhanced ActionListener that includes the fatigue check.
        placeOrderButton.addActionListener(e -> {
            SettingsManager settingsManager = SettingsManager.getInstance();
            // Check only if in replay mode and the nudge setting is enabled.
            if (chartPanel.getDataModel().isInReplayMode() && settingsManager.isFatigueNudgeEnabled()) {
                KLine currentBar = ReplaySessionManager.getInstance().getCurrentBar();
                if (currentBar != null) {
                    List<Integer> peakHours = settingsManager.getPeakPerformanceHoursOverride();
                    if (peakHours.isEmpty()) {
                        peakHours = GamificationService.getInstance().getPeakPerformanceHours();
                    }

                    // If a peak performance window is defined...
                    if (!peakHours.isEmpty()) {
                        int currentHour = currentBar.timestamp().atZone(ZoneOffset.UTC).getHour();
                        // ...and the current trade is outside that window...
                        if (!peakHours.contains(currentHour)) {
                            // ...show a confirmation dialog.
                            int choice = JOptionPane.showConfirmDialog(
                                this,
                                "You are about to place a trade outside your identified peak performance hours.\nThis may indicate decision fatigue. Are you sure you want to proceed?",
                                "Outside Peak Hours",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                            );
                            // If the user chooses "No", abort the trade placement.
                            if (choice != JOptionPane.YES_OPTION) {
                                dispose(); // Close dialog without placing order
                                return;
                            }
                        }
                    }
                }
            }

            // If the check was passed or skipped, delegate the order placement to the OrderPanel.
            orderPanel.placeOrder();
        });
        // --- END: Phase 3 Modification ---

        // Assemble the dialog with the custom header
        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(orderPanel, BorderLayout.CENTER);
        setContentPane(rootPanel);

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /**
     * Overriding dispose to ensure the chart state is always cleaned up.
     * This is called when the dialog is closed via the panel's callback.
     */
    @Override
    public void dispose() {
        chartPanel.exitPriceSelectionMode(); // Explicitly exit the chart's mode
        chartPanel.setOrderPreview(null);      // Clear any lingering preview lines
        super.dispose();
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propName = evt.getPropertyName();

        if ("priceSelectionRequested".equals(propName)) {
            String targetFieldName = (String) evt.getNewValue();
            if (targetFieldName == null) return;
            
            setVisible(false);
            
            SwingUtilities.invokeLater(() -> {
                chartPanel.enterPriceSelectionMode(selectedPrice -> {
                    if (selectedPrice == null) {
                        setVisible(true); // Just make the dialog visible again
                        return;
                    }
                    String formattedPrice = selectedPrice.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                    
                    switch (targetFieldName) {
                        case "limitPrice":
                            orderPanel.getLimitPriceField().setText(formattedPrice);
                            break;
                        case "stopLoss":
                            orderPanel.getStopLossField().setText(formattedPrice);
                            break;
                        case "takeProfit":
                            orderPanel.getTakeProfitField().setText(formattedPrice);
                            break;
                    }
                    
                    setVisible(true);
                });
            });
        } else if ("previewUpdated".equals(propName)) {
            chartPanel.setOrderPreview((OrderPreview) evt.getNewValue());
        }
    }
}
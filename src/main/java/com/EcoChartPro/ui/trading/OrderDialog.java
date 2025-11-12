package com.EcoChartPro.ui.trading;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.settings.SettingsService;
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
    private final WorkspaceContext context;

    public OrderDialog(Frame owner, ChartPanel chartPanel, TradeDirection tradeDirection, WorkspaceContext context) {
        super(owner, true);
        this.chartPanel = chartPanel;
        this.context = context;

        setUndecorated(true);

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        String directionString = (tradeDirection == TradeDirection.LONG) ? "Buy/Long" : "Sell/Short";
        String symbolString = chartPanel.getDataModel().getCurrentSymbol().displayName();
        String titleText = directionString + " - " + symbolString;
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(titleLabel);

        this.orderPanel = new OrderPanel(chartPanel, tradeDirection, this.context, (success) -> dispose());
        
        this.orderPanel.addPropertyChangeListener(this);
        
        JButton placeOrderButton = orderPanel.getPlaceOrderButton();
        
        if(tradeDirection == TradeDirection.LONG) {
            placeOrderButton.setBackground(new Color(0x4CAF50));
        } else {
            placeOrderButton.setBackground(new Color(0xF44336));
        }
        placeOrderButton.setForeground(Color.WHITE);
        placeOrderButton.setFocusPainted(false);

        for (ActionListener l : placeOrderButton.getActionListeners()) {
            placeOrderButton.removeActionListener(l);
        }

        placeOrderButton.addActionListener(e -> {
            SettingsService settingsService = SettingsService.getInstance();
            if (chartPanel.getDataModel().isInReplayMode() && settingsService.isFatigueNudgeEnabled()) {
                KLine currentBar = ReplaySessionManager.getInstance().getCurrentBar();
                if (currentBar != null) {
                    List<Integer> peakHours = settingsService.getPeakPerformanceHoursOverride();
                    if (peakHours.isEmpty()) {
                        peakHours = GamificationService.getInstance().getPeakPerformanceHours();
                    }

                    if (!peakHours.isEmpty()) {
                        int currentHour = currentBar.timestamp().atZone(ZoneOffset.UTC).getHour();
                        if (!peakHours.contains(currentHour)) {
                            int choice = JOptionPane.showConfirmDialog(
                                this,
                                "You are about to place a trade outside your identified peak performance hours.\nThis may indicate decision fatigue. Are you sure you want to proceed?",
                                "Outside Peak Hours",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                            );
                            if (choice != JOptionPane.YES_OPTION) {
                                dispose();
                                return;
                            }
                        }
                    }
                }
            }
            orderPanel.placeOrder();
        });

        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(orderPanel, BorderLayout.CENTER);
        setContentPane(rootPanel);

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }
    
    @Override
    public void dispose() {
        chartPanel.exitPriceSelectionMode();
        chartPanel.setOrderPreview(null);
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
                        setVisible(true);
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
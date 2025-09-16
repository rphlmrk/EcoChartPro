package com.EcoChartPro.ui.sidebar;

import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.chart.ChartPanel;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class PositionsViewPanel extends JPanel {
    private final JPanel contentPanel;
    private ChartPanel activeChartPanel;

    private final JLabel totalPnlLabel;
    private List<Position> currentPositions = new ArrayList<>();
    private List<Order> currentOrders = new ArrayList<>();
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$0.00;-$0.00");


    public PositionsViewPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setOpaque(false);
        wrapperPanel.add(contentPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapperPanel);

        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));

        JLabel totalPnlTitleLabel = new JLabel("Total Unrealized P&L:");
        totalPnlTitleLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        totalPnlTitleLabel.setForeground(UIManager.getColor("Label.foreground"));
        footerPanel.add(totalPnlTitleLabel, BorderLayout.WEST);

        totalPnlLabel = new JLabel(PNL_FORMAT.format(BigDecimal.ZERO));
        totalPnlLabel.setFont(UIManager.getFont("app.font.widget_title"));
        totalPnlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        footerPanel.add(totalPnlLabel, BorderLayout.EAST);

        add(footerPanel, BorderLayout.SOUTH);

        rebuildView(); // Initial empty state
    }
    
    /**
     * Method to receive the active chart context from the parent sidebar.
     */
    public void setActiveChartPanel(ChartPanel activeChartPanel) {
        this.activeChartPanel = activeChartPanel;
        // Rebuild if context changes to ensure rows have the correct reference
        rebuildView();
    }

    /**
     * Rebuilds the list of position rows.
     * @param positions The new list of open positions.
     * @param chartPanel The active chart panel context, needed for actions.
     */
    public void updatePositions(List<Position> positions, ChartPanel chartPanel) {
        this.activeChartPanel = chartPanel;
        this.currentPositions = (positions != null) ? new ArrayList<>(positions) : new ArrayList<>();
        rebuildView();
    }
    
    /**
     * method to receive and store updated pending orders.
     */
    public void updateOrders(List<Order> orders) {
        this.currentOrders = (orders != null) ? new ArrayList<>(orders) : new ArrayList<>();
        rebuildView();
    }

    private void rebuildView() {
        contentPanel.removeAll();
        
        if (currentPositions.isEmpty() && currentOrders.isEmpty()) {
            JPanel emptyPanel = new JPanel(new GridBagLayout());
            emptyPanel.setOpaque(false);
            emptyPanel.setPreferredSize(new Dimension(100, 100));
            JLabel label = new JLabel("No open positions or orders.");
            label.setFont(UIManager.getFont("app.font.widget_content"));
            label.setForeground(UIManager.getColor("Label.disabledForeground"));
            emptyPanel.add(label);
            contentPanel.add(emptyPanel);

            // Reset total P&L label
            totalPnlLabel.setText(PNL_FORMAT.format(BigDecimal.ZERO));
            totalPnlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        } else {
            if (!currentPositions.isEmpty()) {
                contentPanel.add(createHeaderLabel("Open Positions"));
                for (Position pos : currentPositions) {
                    PositionRowPanel rowPanel = new PositionRowPanel(pos, this.activeChartPanel);
                    contentPanel.add(rowPanel);
                }
            }

            if (!currentOrders.isEmpty()) {
                if (!currentPositions.isEmpty()) {
                    contentPanel.add(Box.createVerticalStrut(10));
                }
                contentPanel.add(createHeaderLabel("Pending Orders"));
                for (Order order : currentOrders) {
                    contentPanel.add(createPendingOrderRow(order));
                }
            }
        }
        revalidate();
        repaint();
    }

    private JLabel createHeaderLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 0));
        return label;
    }

    /**
     * method to create a UI row for a pending order.
     */
    private JPanel createPendingOrderRow(Order order) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 2, 4);

        // Row 1
        gbc.gridy = 0;
        gbc.gridx = 0;
        JLabel symbolLabel = new JLabel(order.symbol().name());
        symbolLabel.setFont(UIManager.getFont("app.font.widget_title").deriveFont(14f));
        symbolLabel.setForeground(UIManager.getColor("Label.foreground"));
        panel.add(symbolLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 8, 2, 4);
        JLabel priceLabel = new JLabel(order.limitPrice().setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        priceLabel.setForeground(UIManager.getColor("Label.foreground"));
        priceLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        panel.add(priceLabel, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        panel.add(Box.createHorizontalGlue(), gbc);
        gbc.weightx = 0;

        gbc.gridx = 3;
        gbc.anchor = GridBagConstraints.EAST;
        JButton cancelButton = createActionButton("X");
        cancelButton.setToolTipText("Cancel Order");
        cancelButton.addActionListener(e -> PaperTradingService.getInstance().cancelOrder(order.id()));
        panel.add(cancelButton, gbc);

        // Row 2
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 0, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        String typeString = order.type().toString() + " " + order.direction().toString();
        JLabel typeLabel = new JLabel(typeString);
        typeLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        typeLabel.setForeground(order.direction() == TradeDirection.LONG ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
        panel.add(typeLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 8, 0, 4);
        JLabel sizeLabel = new JLabel(order.size().setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        sizeLabel.setForeground(UIManager.getColor("Label.foreground"));
        sizeLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        panel.add(sizeLabel, gbc);

        return panel;
    }

    private JButton createActionButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(UIManager.getColor("Label.disabledForeground"));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 5, 0, 5));
        return button;
    }


    public void updateLivePnl(KLine lastBar) {
        BigDecimal totalPnl = BigDecimal.ZERO;

        for (Component comp : contentPanel.getComponents()) {
            if (comp instanceof PositionRowPanel row) {
                BigDecimal pnl = row.updatePnl(lastBar);
                if (pnl != null) {
                    totalPnl = totalPnl.add(pnl);
                }
            }
        }

        // Update the footer label
        totalPnlLabel.setText(PNL_FORMAT.format(totalPnl));
        Color pnlColor = UIManager.getColor("Label.disabledForeground");
        if (totalPnl.compareTo(BigDecimal.ZERO) > 0) {
            pnlColor = UIManager.getColor("app.color.positive");
        } else if (totalPnl.compareTo(BigDecimal.ZERO) < 0) {
            pnlColor = UIManager.getColor("app.color.negative");
        }
        totalPnlLabel.setForeground(pnlColor);
    }
}
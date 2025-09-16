package com.EcoChartPro.ui.sidebar;

import javax.swing.*;

import com.EcoChartPro.model.Trade;

import java.awt.*;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A panel at the bottom of the sidebar to display a summary of trading performance,
 * including a small area chart of cumulative P&L over time.
 */
public class PerformanceFooterPanel extends JPanel {

    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private final JLabel pnlLabel;
    private final JLabel percentageLabel;
    private final PerformanceChart chartPanel;
    private BigDecimal initialBalance = new BigDecimal("100000"); // Standard starting balance

    /**
     * Constructs the performance footer panel.
     */
    public PerformanceFooterPanel() {
        setLayout(new BorderLayout(0, 5));
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x333333)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        // --- Header Section ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Performance Over Time");
        titleLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        titleLabel.setForeground(UIManager.getColor("Label.foreground"));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        // --- Chart Section ---
        chartPanel = new PerformanceChart();
        chartPanel.setPreferredSize(new Dimension(0, 60));

        // --- Footer Section (P&L and Percentage) ---
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setOpaque(false);
        pnlLabel = new JLabel(PNL_FORMAT.format(BigDecimal.ZERO));
        pnlLabel.setFont(UIManager.getFont("app.font.widget_title"));
        pnlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        percentageLabel = new JLabel("0.00%");
        percentageLabel.setFont(UIManager.getFont("app.font.widget_content"));
        percentageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        infoPanel.add(pnlLabel, BorderLayout.WEST);
        infoPanel.add(percentageLabel, BorderLayout.EAST);

        // --- Add components to main panel ---
        add(headerPanel, BorderLayout.NORTH);
        add(chartPanel, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.SOUTH);
    }

    /**
     * Updates the performance chart and labels based on a list of trades.
     * @param trades The complete list of trades for the session.
     */
    public void updatePerformance(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            chartPanel.setData(Collections.emptyList());
            pnlLabel.setText(PNL_FORMAT.format(BigDecimal.ZERO));
            pnlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            percentageLabel.setText("0.00%");
            percentageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            return;
        }

        // Sort trades chronologically by exit time to calculate cumulative P&L
        List<Trade> sortedTrades = new ArrayList<>(trades);
        sortedTrades.sort((t1, t2) -> t1.exitTime().compareTo(t2.exitTime()));

        List<BigDecimal> cumulativePnl = new ArrayList<>();
        BigDecimal runningPnl = BigDecimal.ZERO;
        for (Trade trade : sortedTrades) {
            runningPnl = runningPnl.add(trade.profitAndLoss());
            cumulativePnl.add(runningPnl);
        }

        // Update the chart with the calculated data
        chartPanel.setData(cumulativePnl);

        // Update the labels with the final P&L
        BigDecimal finalPnl = cumulativePnl.get(cumulativePnl.size() - 1);
        pnlLabel.setText(PNL_FORMAT.format(finalPnl));
        pnlLabel.setForeground(finalPnl.compareTo(BigDecimal.ZERO) >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
        
        // Calculate and update the percentage change
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentage = finalPnl.divide(initialBalance, 4, RoundingMode.HALF_UP)
                                           .multiply(new BigDecimal("100"));
            percentageLabel.setText(String.format("%s%.2f%%", percentage.signum() >= 0 ? "+" : "", percentage));
            percentageLabel.setForeground(finalPnl.compareTo(BigDecimal.ZERO) >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
        }
    }

    /**
     * A private inner class responsible for custom painting the area chart.
     */
    private static class PerformanceChart extends JPanel {
        private List<BigDecimal> dataPoints = new ArrayList<>();

        PerformanceChart() {
            setOpaque(false);
        }

        public void setData(List<BigDecimal> data) {
            this.dataPoints = data;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (dataPoints == null || dataPoints.size() < 2) {
                return; // Not enough data to draw a chart
            }

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            BigDecimal minVal = dataPoints.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal maxVal = dataPoints.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            minVal = minVal.min(BigDecimal.ZERO);
            maxVal = maxVal.max(BigDecimal.ZERO);
            
            BigDecimal range = maxVal.subtract(minVal);
            if (range.compareTo(BigDecimal.ZERO) == 0) range = BigDecimal.ONE;

            int width = getWidth();
            int height = getHeight();
            double xStep = (double) (width - 1) / (dataPoints.size() - 1);

            Path2D areaPath = new Path2D.Double();
            Path2D linePath = new Path2D.Double();

            areaPath.moveTo(0, height);
            linePath.moveTo(0, getY(dataPoints.get(0), minVal, range, height));

            for (int i = 0; i < dataPoints.size(); i++) {
                double x = i * xStep;
                double y = getY(dataPoints.get(i), minVal, range, height);
                areaPath.lineTo(x, y);
                linePath.lineTo(x, y);
            }

            areaPath.lineTo((dataPoints.size() - 1) * xStep, height);
            areaPath.closePath();

            Color accentColor = UIManager.getColor("app.color.neutral");
            if (accentColor == null) accentColor = new Color(33, 150, 243);

            g2d.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 80));
            g2d.fill(areaPath);

            g2d.setColor(accentColor);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.draw(linePath);

            g2d.dispose();
        }

        private double getY(BigDecimal value, BigDecimal min, BigDecimal range, int chartHeight) {
            BigDecimal offset = value.subtract(min);
            double percentage = offset.divide(range, 4, RoundingMode.HALF_UP).doubleValue();
            return chartHeight - (percentage * chartHeight);
        }
    }
}
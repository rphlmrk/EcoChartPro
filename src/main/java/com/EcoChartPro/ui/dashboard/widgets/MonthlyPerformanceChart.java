package com.EcoChartPro.ui.dashboard.widgets;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A custom panel that displays performance data as a vertical bar chart.
 * It handles both positive (profits) and negative (losses) values,
 * drawing them in different colors above and below a zero line.
 */
public class MonthlyPerformanceChart extends JPanel {

    private Map<Object, BigDecimal> data = new LinkedHashMap<>();
    private final JComboBox<String> viewModeComboBox;
    private BigDecimal maxValue = BigDecimal.ZERO;
    private BigDecimal minValue = BigDecimal.ZERO;
    private Set<Object> highlightedKeys = Collections.emptySet();

    public MonthlyPerformanceChart() {
        this.viewModeComboBox = new JComboBox<>(new String[]{"PNL", "Volume"});
        this.viewModeComboBox.setVisible(false);

        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
    }

    public void updateData(Map<Object, BigDecimal> data) {
        if (data == null) {
            this.data = Collections.emptyMap();
        } else {
            this.data = new LinkedHashMap<>(data);
        }
        calculateBounds();
        repaint();
    }

    public void setHighlightedKeys(Set<Object> keys) {
        this.highlightedKeys = (keys != null) ? new HashSet<>(keys) : Collections.emptySet();
        repaint();
    }

    public JComboBox<String> getViewModeComboBox() {
        return viewModeComboBox;
    }

    private void calculateBounds() {
        if (data.isEmpty()) {
            maxValue = BigDecimal.ZERO;
            minValue = BigDecimal.ZERO;
            return;
        }

        maxValue = data.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        minValue = data.values().stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        if (maxValue.compareTo(BigDecimal.ZERO) == 0 && minValue.compareTo(BigDecimal.ZERO) == 0) {
            maxValue = BigDecimal.ONE;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (data == null || data.isEmpty()) {
            drawEmptyState(g);
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int padding = 5;
        int labelHeight = 15;
        int chartX = padding;
        int chartY = padding;
        int chartWidth = getWidth() - 2 * padding;
        int chartHeight = getHeight() - 2 * padding - labelHeight;

        if (chartWidth <= 0 || chartHeight <= 0) {
            g2d.dispose();
            return;
        }

        BigDecimal range = maxValue.subtract(minValue);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            range = maxValue.abs().max(BigDecimal.ONE);
        }

        double zeroLineY = chartY + chartHeight;
        if (maxValue.signum() > 0 && minValue.signum() < 0) {
            BigDecimal percent = maxValue.divide(range, 4, RoundingMode.HALF_UP);
            zeroLineY = chartY + (percent.doubleValue() * chartHeight);
        } else if (minValue.signum() >= 0) {
            zeroLineY = chartY + chartHeight;
        } else if (maxValue.signum() <= 0) {
            zeroLineY = chartY;
        }
        
        g2d.setColor(UIManager.getColor("Component.borderColor"));
        g2d.drawLine(chartX, (int) zeroLineY, chartX + chartWidth, (int) zeroLineY);

        int barCount = data.size();
        double totalBarWidth = (double) chartWidth / barCount;
        double barWidth = Math.max(1, totalBarWidth * 0.7);
        double barSpacing = totalBarWidth - barWidth;

        int i = 0;
        g2d.setFont(UIManager.getFont("app.font.widget_content").deriveFont(9f));
        FontMetrics fm = g2d.getFontMetrics();

        for (Map.Entry<Object, BigDecimal> entry : data.entrySet()) {
            BigDecimal value = entry.getValue();

            double barHeight = value.abs().divide(range, 4, RoundingMode.HALF_UP).doubleValue() * chartHeight;
            double x = chartX + (i * totalBarWidth) + (barSpacing / 2);

            boolean isHighlighted = highlightedKeys.contains(entry.getKey());
            
            double y;
            if (value.signum() >= 0) {
                g2d.setColor(isHighlighted ? UIManager.getColor("Component.focusedBorderColor") : UIManager.getColor("app.color.accent"));
                y = zeroLineY - barHeight;
            } else {
                g2d.setColor(isHighlighted ? UIManager.getColor("Component.focusedBorderColor").darker() : UIManager.getColor("app.color.negative"));
                y = zeroLineY;
            }

            g2d.fillRect((int) x, (int) y, (int) barWidth, (int) barHeight);

            String label = entry.getKey().toString();
            if (!label.trim().isEmpty()) {
                int labelWidth = fm.stringWidth(label);
                g2d.setColor(UIManager.getColor("Label.disabledForeground"));
                g2d.drawString(label, (int) (x + barWidth / 2 - labelWidth / 2), chartY + chartHeight + labelHeight - 2);
            }

            i++;
        }

        g2d.dispose();
    }
    
    private void drawEmptyState(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        g2d.setFont(UIManager.getFont("app.font.widget_content"));
        String text = "No data available";
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(text, x, y);
        g2d.dispose();
    }
}
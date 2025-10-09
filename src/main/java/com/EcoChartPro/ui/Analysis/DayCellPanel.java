package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService.DailyStats;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class DayCellPanel extends JPanel {
    public enum ViewMode { PLAN, PNL }
    private final JLabel dayNumberLabel;
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0%");

    public DayCellPanel(int dayOfMonth) {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 5)); // Reduced vertical padding
        setPreferredSize(new Dimension(0, 50)); // Reduced height
        dayNumberLabel = new JLabel(String.valueOf(dayOfMonth));
        dayNumberLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        dayNumberLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        add(dayNumberLabel, BorderLayout.NORTH);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (dayNumberLabel != null) {
            // Dim the day number if the cell is disabled (for days outside the current month)
            dayNumberLabel.setForeground(enabled
                ? UIManager.getColor("Label.disabledForeground")
                : UIManager.getColor("Component.borderColor"));
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (dayNumberLabel != null) { // Guard against calls during construction
            dayNumberLabel.setForeground(isEnabled()
                ? UIManager.getColor("Label.disabledForeground")
                : UIManager.getColor("Component.borderColor"));
            repaint();
        }
    }
    
    private Color getHighContrastColor(Color background) {
        double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue()) / 255;
        return (luminance > 0.5) ? Color.BLACK : Color.WHITE;
    }

    public void updateData(DailyStats stats, ViewMode viewMode) {
        if (getComponentCount() > 1) { remove(1); }
        if (stats == null) {
            setBackground(UIManager.getColor("Component.borderColor"));
        } else {
            if (viewMode == ViewMode.PNL) {
                setBackground(getPnlColor(stats.totalPnl()));
                add(createPnlStatsPanel(stats), BorderLayout.CENTER);
            } else {
                setBackground(getPlanColor(stats.planFollowedPercentage()));
                add(createPlanStatsPanel(stats), BorderLayout.CENTER);
            }
        }
        revalidate();
        repaint();
    }

    private JPanel createPnlStatsPanel(DailyStats stats) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        JLabel pnlLabel = new JLabel(PNL_FORMAT.format(stats.totalPnl()));
        pnlLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 14f));
        pnlLabel.setForeground(getHighContrastColor(getBackground()));
        panel.add(pnlLabel);
        JLabel winRateLabel = new JLabel(PERCENT_FORMAT.format(stats.winRatio()) + " win");
        winRateLabel.setFont(UIManager.getFont("app.font.widget_content"));
        winRateLabel.setForeground(getHighContrastColor(getBackground()));
        panel.add(winRateLabel);
        return panel;
    }

    private JPanel createPlanStatsPanel(DailyStats stats) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        JLabel planLabel = new JLabel(PERCENT_FORMAT.format(stats.planFollowedPercentage()) + " plan");
        planLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 14f));
        planLabel.setForeground(getHighContrastColor(getBackground()));
        panel.add(planLabel);
        long winCount = Math.round(stats.tradeCount() * stats.winRatio());
        long lossCount = stats.tradeCount() - winCount;
        JLabel wlLabel = new JLabel(String.format("W/L: %d/%d", winCount, lossCount));
        wlLabel.setFont(UIManager.getFont("app.font.widget_content"));
        wlLabel.setForeground(getHighContrastColor(getBackground()));
        panel.add(wlLabel);
        return panel;
    }

    private Color getPnlColor(BigDecimal pnl) {
        if (pnl.compareTo(BigDecimal.ZERO) > 0) return UIManager.getColor("app.journal.profit");
        if (pnl.compareTo(BigDecimal.ZERO) < 0) return UIManager.getColor("app.journal.loss");
        return UIManager.getColor("app.journal.breakeven");
    }

    private Color getPlanColor(double planPercentage) {
        if (planPercentage >= 0.7) return UIManager.getColor("app.journal.plan.good");
        if (planPercentage >= 0.5) return UIManager.getColor("app.journal.plan.ok");
        return UIManager.getColor("app.journal.plan.bad");
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(getBackground());
        g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));
        g2d.dispose();
        super.paintComponent(g);
    }
}
package com.EcoChartPro.ui.chart;

import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.model.KLine;
import javax.swing.*;
import java.awt.*;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A custom panel that displays detailed information (OHLC, indicator values) for a specific KLine.
 * It's designed to be rendered as an overlay on the ChartPanel.
 */
public class InfoPanel extends JPanel {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font VALUE_FONT = new Font("SansSerif", Font.BOLD, 12);

    public InfoPanel() {
        setOpaque(false);
        setLayout(new GridBagLayout()); // Use GridBagLayout directly on this panel
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10)); // Adjusted padding
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bgColor = UIManager.getColor("Panel.background");
        g2d.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 220));
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

        g2d.setColor(UIManager.getColor("Component.borderColor"));
        g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

        g2d.dispose();
    }

    /**
     * Updates the panel's content with data from a new KLine and associated indicators.
     * @param kline The KLine to display data for. Can be null.
     * @param indicators A list of active indicators to query for values.
     * @param displayZone The time zone for formatting the timestamp.
     */
    public void updateData(KLine kline, List<Indicator> indicators, ZoneId displayZone) {
        removeAll(); // Remove components directly from this panel

        if (kline == null) {
            JLabel noDataLabel = new JLabel("No data at cursor");
            noDataLabel.setFont(LABEL_FONT);
            add(noDataLabel);
        } else {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 5, 2, 5);
            gbc.anchor = GridBagConstraints.WEST;
            int gridY = 0;

            // Date
            addLabelValueRow(this, gbc, gridY++, "Time", kline.timestamp().atZone(displayZone).format(DATE_FORMATTER));

            // OHLC
            int scale = kline.close().scale() > 4 ? 8 : 4;
            addLabelValueRow(this, gbc, gridY++, "Open", kline.open().setScale(scale, RoundingMode.HALF_UP).toPlainString());
            addLabelValueRow(this, gbc, gridY++, "High", kline.high().setScale(scale, RoundingMode.HALF_UP).toPlainString());
            addLabelValueRow(this, gbc, gridY++, "Low", kline.low().setScale(scale, RoundingMode.HALF_UP).toPlainString());
            addLabelValueRow(this, gbc, gridY++, "Close", kline.close().setScale(scale, RoundingMode.HALF_UP).toPlainString());
            addLabelValueRow(this, gbc, gridY++, "Volume", kline.volume().toPlainString());

            // Indicators
            if (indicators != null) {
                for (Indicator indicator : indicators) {
                    String value = indicator.getValueAsStringAt(kline.timestamp());
                    if (value != null) {
                        addLabelValueRow(this, gbc, gridY++, indicator.getName(), value);
                    }
                }
            }
        }

        // For a manually rendered component not in a visible hierarchy, we must
        // force the layout manager to calculate its preferred size and then synchronously
        // lay out the child components within those new bounds.
        Dimension prefSize = getLayout().preferredLayoutSize(this);
        setSize(prefSize);
        doLayout(); // This synchronously lays out the child components.
    }

    private void addLabelValueRow(JPanel panel, GridBagConstraints gbc, int gridY, String labelText, String valueText) {
        gbc.gridx = 0;
        gbc.gridy = gridY;
        gbc.weightx = 0;
        JLabel label = new JLabel(labelText + ":");
        label.setFont(LABEL_FONT);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JLabel value = new JLabel(valueText);
        value.setFont(VALUE_FONT);
        panel.add(value, gbc);
    }
}
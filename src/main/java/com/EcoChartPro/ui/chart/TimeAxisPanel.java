package com.EcoChartPro.ui.chart;

import com.EcoChartPro.core.manager.CrosshairManager;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class TimeAxisPanel extends JPanel implements PropertyChangeListener {

    private static final int PANEL_HEIGHT = 20;
    private static final Font AXIS_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM");

    private final ChartDataModel dataModel;
    private final ChartAxis chartAxis;
    private DrawingObjectPoint crosshairPoint;

    public TimeAxisPanel(ChartDataModel dataModel, ChartAxis chartAxis) {
        this.dataModel = dataModel;
        this.chartAxis = chartAxis;
        setPreferredSize(new Dimension(0, PANEL_HEIGHT));
        updateUI(); // Set initial theme-aware background
        
        SettingsManager sm = SettingsManager.getInstance();
        sm.addPropertyChangeListener(this); // Generic listener for all properties
        CrosshairManager.getInstance().addPropertyChangeListener("crosshairMoved", this);
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
        if (UIManager.getColor("Panel.background") != null) {
            setBackground(UIManager.getColor("Panel.background"));
        }
    }

    public void cleanup() {
        SettingsManager.getInstance().removePropertyChangeListener(this);
        CrosshairManager.getInstance().removePropertyChangeListener("crosshairMoved", this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propName = evt.getPropertyName();
        // Add peakHoursSettingsChanged and sessionHighlightingChanged to trigger repaint
        if ("displayZoneId".equals(propName) || "chartColorsChanged".equals(propName) || "crosshairLabelColorChanged".equals(propName) || "peakHoursSettingsChanged".equals(propName) || "sessionHighlightingChanged".equals(propName)) {
            repaint();
        }
        if ("crosshairMoved".equals(propName)) {
            if (evt.getNewValue() instanceof CrosshairManager.CrosshairUpdate) {
                CrosshairManager.CrosshairUpdate update = (CrosshairManager.CrosshairUpdate) evt.getNewValue();
                this.crosshairPoint = update.point();
            } else {
                this.crosshairPoint = null;
            }
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(AXIS_FONT);

        if (!chartAxis.isConfigured()) return;

        List<KLine> klines = dataModel.getVisibleKLines();
        if (klines.isEmpty()) return;

        SettingsManager settings = SettingsManager.getInstance();
        ZoneId displayZone = settings.getDisplayZoneId();
        Timeframe tf = dataModel.getCurrentDisplayTimeframe();

        // Check if the session highlighting feature is enabled before drawing
        if (settings.isSessionHighlightingEnabled()) {
            Color axisTextColor = settings.getAxisTextColor();
            Color openingRangeColor = new Color(axisTextColor.getRed(), axisTextColor.getGreen(), axisTextColor.getBlue(), 50);

            if (tf != null && tf.getDuration().compareTo(Duration.ofMinutes(15)) <= 0) {
                double barWidth = chartAxis.getBarWidth();
                for (int i = 0; i < klines.size(); i++) {
                    KLine kline = klines.get(i);
                    LocalTime barTime = kline.timestamp().atZone(displayZone).toLocalTime();
                    for (SettingsManager.TradingSession session : SettingsManager.TradingSession.values()) {
                        if (settings.getSessionEnabled().get(session)) {
                            LocalTime start = settings.getSessionStartTimes().get(session);
                            LocalTime end = settings.getSessionEndTimes().get(session);
                            LocalTime openingEnd = start.plusMinutes(15);
                            boolean isInOpeningRange;
                            if (openingEnd.isBefore(start)) {
                                isInOpeningRange = !barTime.isBefore(start) || barTime.isBefore(openingEnd);
                            } else {
                                isInOpeningRange = !barTime.isBefore(start) && barTime.isBefore(openingEnd);
                            }
                            if (isInOpeningRange) {
                                g2d.setColor(openingRangeColor);
                                int x = chartAxis.slotToX(i) - (int) (barWidth / 2);
                                g2d.fillRect(x, 0, (int) Math.ceil(barWidth), getHeight());
                                break;
                            }
                            boolean isInSession;
                            if (start.isAfter(end)) {
                                isInSession = !barTime.isBefore(start) || barTime.isBefore(end);
                            } else {
                                isInSession = !barTime.isBefore(start) && barTime.isBefore(end);
                            }
                            if (isInSession) {
                                g2d.setColor(settings.getSessionColors().get(session));
                                int x = chartAxis.slotToX(i) - (int) (barWidth / 2);
                                g2d.fillRect(x, 0, (int) Math.ceil(barWidth), getHeight());
                                break;
                            }
                        }
                    }
                }
            }
        }

        g2d.setColor(settings.getAxisTextColor());
        FontMetrics fm = g2d.getFontMetrics();
        Instant firstVisibleTime = klines.get(0).timestamp();
        Instant lastVisibleTime = klines.get(klines.size() - 1).timestamp();
        Duration visibleDuration = Duration.between(firstVisibleTime, lastVisibleTime);

        Duration labelInterval;
        DateTimeFormatter formatter;
        if (visibleDuration.toDays() > 2) {
            labelInterval = Duration.ofDays(1); formatter = DATE_FORMATTER;
        } else if (visibleDuration.toHours() > 6) {
            labelInterval = Duration.ofHours(1); formatter = TIME_FORMATTER;
        } else if (visibleDuration.toHours() > 2) {
            labelInterval = Duration.ofMinutes(30); formatter = TIME_FORMATTER;
        } else {
            labelInterval = Duration.ofMinutes(15); formatter = TIME_FORMATTER;
        }

        long intervalSeconds = labelInterval.toSeconds();
        if (intervalSeconds == 0) return;
        long firstVisibleEpochSecond = firstVisibleTime.getEpochSecond();
        long startEpochSecond = (long) (Math.ceil((double) firstVisibleEpochSecond / intervalSeconds) * intervalSeconds);
        Instant currentLabelTime = Instant.ofEpochSecond(startEpochSecond);
        int lastDrawnX = -100;

        while (currentLabelTime.isBefore(lastVisibleTime)) {
            int x = chartAxis.timeToX(currentLabelTime, klines, tf);
            String timeLabel = currentLabelTime.atZone(displayZone).format(formatter);
            int labelWidth = fm.stringWidth(timeLabel);
            if (x != -1 && (x - lastDrawnX) > (labelWidth + 10)) {
                g2d.drawString(timeLabel, x - labelWidth / 2, 15);
                lastDrawnX = x;
            }
            currentLabelTime = currentLabelTime.plus(labelInterval);
        }

        if (crosshairPoint != null) {
            Instant time = crosshairPoint.timestamp();
            int x = chartAxis.timeToX(time, klines, tf);
            String timeLabel = time.atZone(displayZone).format(DateTimeFormatter.ofPattern("dd MMM HH:mm"));
            int labelWidth = fm.stringWidth(timeLabel) + 10;
            int labelX = x - labelWidth / 2;
            
            // Use colors from settings instead of UIManager
            g2d.setColor(settings.getCrosshairLabelBackgroundColor());
            g2d.fillRect(labelX, 0, labelWidth, getHeight());
            g2d.setColor(settings.getCrosshairLabelForegroundColor());
            g2d.drawString(timeLabel, x - (labelWidth - 10) / 2, 15);
        }
    }
}
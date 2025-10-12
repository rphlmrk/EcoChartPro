package com.EcoChartPro.ui.chart;

import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.manager.CrosshairManager;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TimeAxisPanel extends JPanel implements PropertyChangeListener {

    private final ChartDataModel dataModel;
    private final ChartAxis chartAxis;
    private DrawingObjectPoint crosshairPoint;
    private final ChartInteractionManager interactionManager;
    private JButton scaleModeButton;
    private JButton invertButton;


    public TimeAxisPanel(ChartDataModel dataModel, ChartAxis chartAxis, ChartInteractionManager interactionManager) {
        this.dataModel = dataModel;
        this.chartAxis = chartAxis;
        this.interactionManager = interactionManager;
        setPreferredSize(new Dimension(0, 25));
        setLayout(new BorderLayout());
        updateUI(); 
        
        TimeScaleDrawer drawer = new TimeScaleDrawer();
        add(drawer, BorderLayout.CENTER);
        setupControls();

        SettingsManager sm = SettingsManager.getInstance();
        sm.addPropertyChangeListener(this); 
        CrosshairManager.getInstance().addPropertyChangeListener("crosshairMoved", this);
        if (this.interactionManager != null) {
            this.interactionManager.addPropertyChangeListener("axisConfigChanged", this);
        }
    }
    
    private void setupControls() {
        if (interactionManager == null) return;
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5)); // Add right margin
        Font buttonFont = new Font("SansSerif", Font.BOLD, 10);
        Dimension buttonSize = new Dimension(30, 18);
        Insets buttonMargin = new Insets(0, 0, 0, 0);

        // --- Invert Button ---
        invertButton = new JButton("I");
        invertButton.setFont(buttonFont);
        invertButton.setMargin(buttonMargin);
        invertButton.setPreferredSize(buttonSize);
        invertButton.setFocusPainted(false);
        invertButton.addActionListener(e -> interactionManager.toggleInvertY());

        // --- Scale Mode Toggle Button ---
        scaleModeButton = new JButton();
        scaleModeButton.setFont(buttonFont);
        scaleModeButton.setMargin(buttonMargin);
        scaleModeButton.setPreferredSize(buttonSize);
        scaleModeButton.setOpaque(false);
        scaleModeButton.setContentAreaFilled(false);
        scaleModeButton.setFocusPainted(false);
        scaleModeButton.addActionListener(e -> interactionManager.setAutoScalingY(!interactionManager.isAutoScalingY()));
        
        buttonPanel.add(invertButton);
        buttonPanel.add(scaleModeButton);
        
        add(buttonPanel, BorderLayout.EAST);
        updateControlState();
    }
    
    private void updateControlState() {
        if (interactionManager == null || scaleModeButton == null || invertButton == null) {
            return;
        }

        // --- State Colors ---
        Color activeColor = new Color(33, 150, 243); // Blue for active states
        Color autoColor = new Color(38, 166, 154);   // Teal for auto state

        // --- Update Invert Button ---
        boolean isInverted = interactionManager.isInvertedY();
        if (isInverted) {
            invertButton.setToolTipText("Y-Axis Inverted (Click to Restore)");
            invertButton.setBackground(activeColor);
            invertButton.setForeground(Color.WHITE);
            invertButton.setOpaque(true);
            invertButton.setBorderPainted(false);
        } else {
            invertButton.setToolTipText("Invert Y-Axis");
            invertButton.setBackground(UIManager.getColor("Button.background"));
            invertButton.setForeground(UIManager.getColor("Button.foreground"));
            invertButton.setOpaque(true); // Let LookAndFeel handle it
            invertButton.setBorderPainted(true);
        }

        // --- Update Scale Mode Button ---
        boolean isAuto = interactionManager.isAutoScalingY();
        if (isAuto) {
            scaleModeButton.setText("A");
            scaleModeButton.setToolTipText("Y-Axis Auto Scaling (Click to switch to Manual)");
            scaleModeButton.setForeground(autoColor);
            scaleModeButton.setBorder(BorderFactory.createLineBorder(autoColor));
        } else {
            scaleModeButton.setText("M");
            scaleModeButton.setToolTipText("Y-Axis Manual Scaling (Click to switch to Auto)");
            scaleModeButton.setForeground(activeColor);
            scaleModeButton.setBorder(BorderFactory.createLineBorder(activeColor));
        }
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
        if (interactionManager != null) {
            interactionManager.removePropertyChangeListener("axisConfigChanged", this);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propName = evt.getPropertyName();
        if ("displayZoneId".equals(propName) || "chartColorsChanged".equals(propName) || "crosshairLabelColorChanged".equals(propName) || "peakHoursSettingsChanged".equals(propName) || "sessionHighlightingChanged".equals(propName)) {
            repaint();
        } else if ("axisConfigChanged".equals(propName)) {
            updateControlState();
        } else if ("crosshairMoved".equals(propName)) {
            if (evt.getNewValue() instanceof CrosshairManager.CrosshairUpdate) {
                CrosshairManager.CrosshairUpdate update = (CrosshairManager.CrosshairUpdate) evt.getNewValue();
                this.crosshairPoint = update.point();
            } else {
                this.crosshairPoint = null;
            }
            repaint();
        }
    }

    private class TimeScaleDrawer extends JComponent {
        private static final Font AXIS_FONT = new Font("SansSerif", Font.PLAIN, 12);
        private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM");
        private Point lastMousePoint = null;

        TimeScaleDrawer() {
            addMouseListeners();
        }
        
        private void addMouseListeners() {
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (interactionManager != null && SwingUtilities.isLeftMouseButton(e)) {
                        lastMousePoint = e.getPoint();
                        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    lastMousePoint = null;
                    setCursor(Cursor.getDefaultCursor());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (lastMousePoint == null || interactionManager == null || getWidth() <= 0) return;
                    
                    int dx = e.getX() - lastMousePoint.x;
                    lastMousePoint = e.getPoint();

                    if (dx == 0) return;
                    
                    // A negative dx (drag left) results in zoomFactor > 1 (zoom in).
                    // A positive dx (drag right) results in zoomFactor < 1 (zoom out).
                    double zoomFactor = Math.pow(1.005, -dx);
                    
                    double cursorXRatio = (double) e.getX() / getWidth();
                    cursorXRatio = Math.max(0.0, Math.min(1.0, cursorXRatio));
                    
                    interactionManager.zoom(zoomFactor, cursorXRatio);
                }
            };
            
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
            
            addMouseWheelListener((MouseWheelEvent e) -> {
                if (interactionManager == null || getWidth() <= 0) return;
                
                double zoomFactor = e.getWheelRotation() < 0 ? 1.25 : 0.8;
                double cursorXRatio = (double) e.getX() / getWidth();
                interactionManager.zoom(zoomFactor, cursorXRatio);
            });
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
                
                g2d.setColor(settings.getCrosshairLabelBackgroundColor());
                g2d.fillRect(labelX, 0, labelWidth, getHeight());
                g2d.setColor(settings.getCrosshairLabelForegroundColor());
                g2d.drawString(timeLabel, x - (labelWidth - 10) / 2, 15);
            }
        }
    }
}
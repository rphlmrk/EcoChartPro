package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.data.DataResampler; 
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class TradeReplayChartPanel extends JPanel {

    private List<KLine> klines = Collections.emptyList(); // This holds the currently DISPLAYED (resampled) klines
    private List<KLine> rawOneMinuteKlines = Collections.emptyList(); // This will store the original 1m data
    private Trade trade;
    private int currentIndex = 0;
    private final Timer replayTimer;
    private final JPanel timeframePanel;
    private ButtonGroup timeframeGroup = new ButtonGroup();
    private Timeframe selectedTimeframe = Timeframe.M1; // Default

    private final JSlider progressSlider;
    private final JButton playPauseButton;
    private final JComboBox<String> speedComboBox;

    private BigDecimal minPrice, maxPrice;
    private long minTimestamp, maxTimestamp;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final Color ENTRY_MARKER_COLOR = UIManager.getColor("app.color.accent"); // A theme-based blue
    private static final Color EXIT_MARKER_COLOR = new Color(255, 165, 0); // Orange

    public TradeReplayChartPanel() {
        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        setLayout(new BorderLayout());

        JPanel bottomControlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        bottomControlsPanel.setOpaque(false);

        playPauseButton = createControlButton(UITheme.Icons.PLAY);
        progressSlider = new JSlider(0, 100, 0);
        speedComboBox = new JComboBox<>(new String[]{"0.5x", "1x", "2x", "4x", "8x"});
        speedComboBox.setSelectedItem("1x");
        JButton endButton = createControlButton(UITheme.Icons.FAST_FORWARD);
        
        bottomControlsPanel.add(playPauseButton);
        bottomControlsPanel.add(speedComboBox);
        bottomControlsPanel.add(progressSlider);
        bottomControlsPanel.add(endButton);
        add(bottomControlsPanel, BorderLayout.SOUTH);

        this.timeframePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        this.timeframePanel.setOpaque(false);
        add(this.timeframePanel, BorderLayout.NORTH);

        replayTimer = new Timer(1000, e -> tick());
        replayTimer.setRepeats(true);

        playPauseButton.addActionListener(e -> toggleReplay());
        speedComboBox.addActionListener(e -> updateTimerSpeed());
        progressSlider.addChangeListener(e -> {
            if (!replayTimer.isRunning() && progressSlider.getValueIsAdjusting()) {
                setCurrentIndex(progressSlider.getValue());
            }
        });
        endButton.addActionListener(e -> {
            replayTimer.stop();
            setCurrentIndex(klines.size() - 1);
            updatePlayButtonIcon();
        });

        // [FIX] Corrected the listener registration to match SettingsService's method signature.
        // The lambda now filters for the specific property change event.
        SettingsService.getInstance().addPropertyChangeListener(evt -> {
            if ("tradeReplayTimeframesChanged".equals(evt.getPropertyName())) {
                SwingUtilities.invokeLater(this::rebuildTimeframeButtons);
            }
        });
        rebuildTimeframeButtons();
    }

    private void rebuildTimeframeButtons() {
        timeframePanel.removeAll();
        timeframeGroup = new ButtonGroup();

        List<String> availableTfs = SettingsService.getInstance().getTradeReplayAvailableTimeframes();
        if (availableTfs.isEmpty()) availableTfs.add("1m");

        for (String tfString : availableTfs) {
            Timeframe tf = Timeframe.fromString(tfString);
            if (tf != null) {
                JToggleButton button = new JToggleButton(tf.displayName());
                styleTimeframeButton(button);
                button.setActionCommand(tf.displayName());
                timeframeGroup.add(button);
                timeframePanel.add(button);

                button.addActionListener(e -> {
                    Timeframe newTimeframe = Timeframe.fromString(e.getActionCommand());
                    if (newTimeframe != null && this.selectedTimeframe != newTimeframe) {
                        this.selectedTimeframe = newTimeframe;
                        resampleAndRedraw();
                    }
                });
            }
        }
        updateButtonSelection();
        timeframePanel.revalidate();
        timeframePanel.repaint();
    }
    
    private void resampleAndRedraw() {
        if (rawOneMinuteKlines.isEmpty()) {
            this.klines = Collections.emptyList();
        } else {
            this.klines = DataResampler.resample(this.rawOneMinuteKlines, this.selectedTimeframe);
        }

        progressSlider.setMaximum(this.klines.isEmpty() ? 0 : this.klines.size() - 1);
        if (!this.klines.isEmpty()) {
            calculateBounds();
        }
        setCurrentIndex(0); // Reset replay to the beginning
        updatePlayButtonIcon();
        repaint();
    }

    private void updateButtonSelection() {
        if (selectedTimeframe == null && timeframePanel.getComponentCount() > 0) {
            JToggleButton firstButton = (JToggleButton) timeframePanel.getComponent(0);
            this.selectedTimeframe = Timeframe.fromString(firstButton.getActionCommand());
        }
        
        for (Component comp : timeframePanel.getComponents()) {
            if (comp instanceof JToggleButton) {
                JToggleButton button = (JToggleButton) comp;
                boolean shouldBeSelected = selectedTimeframe != null && button.getActionCommand().equals(selectedTimeframe.displayName());
                if (button.isSelected() != shouldBeSelected) {
                    button.setSelected(shouldBeSelected);
                }
            }
        }
    }

    private void tick() {
        if (currentIndex < klines.size() - 1) {
            setCurrentIndex(currentIndex + 1);
        } else {
            replayTimer.stop();
            updatePlayButtonIcon();
        }
    }

    private void toggleReplay() {
        if (replayTimer.isRunning()) {
            replayTimer.stop();
        } else {
            if (currentIndex >= klines.size() - 1) {
                setCurrentIndex(0);
            }
            replayTimer.start();
        }
        updatePlayButtonIcon();
    }
    
    private void updatePlayButtonIcon() {
        playPauseButton.setIcon(UITheme.getIcon(replayTimer.isRunning() ? UITheme.Icons.PAUSE : UITheme.Icons.PLAY, 16, 16, UIManager.getColor("Label.foreground")));
    }

    private void updateTimerSpeed() {
        String speed = (String) speedComboBox.getSelectedItem();
        if (speed == null) speed = "1x";
        double factor = Double.parseDouble(speed.replace("x", ""));
        replayTimer.setDelay((int) (1000 / factor));
    }
    
    private void setCurrentIndex(int index) {
        this.currentIndex = Math.max(0, Math.min(index, klines.size() - 1));
        progressSlider.setValue(this.currentIndex);
        repaint();
    }

    public void setData(Trade newTrade, List<KLine> klines) {
        replayTimer.stop();
        this.trade = newTrade;
        this.rawOneMinuteKlines = (klines != null) ? klines : Collections.emptyList();
        this.selectedTimeframe = Timeframe.M1; // Always reset to 1m for a new trade
        updateButtonSelection();

        if (this.rawOneMinuteKlines.isEmpty()) {
            this.klines = Collections.emptyList();
            progressSlider.setEnabled(false);
            playPauseButton.setEnabled(false);
            speedComboBox.setEnabled(false);
        } else {
            progressSlider.setEnabled(true);
            playPauseButton.setEnabled(true);
            speedComboBox.setEnabled(true);
            resampleAndRedraw();
        }
    }

    private void calculateBounds() {
        minPrice = klines.get(0).low();
        maxPrice = klines.get(0).high();
        for (KLine k : klines) {
            if (k.low().compareTo(minPrice) < 0) minPrice = k.low();
            if (k.high().compareTo(maxPrice) > 0) maxPrice = k.high();
        }
        BigDecimal yPadding = maxPrice.subtract(minPrice).multiply(BigDecimal.valueOf(0.1));
        minPrice = minPrice.subtract(yPadding);
        maxPrice = maxPrice.add(yPadding);
        
        long firstKLineTimestamp = klines.get(0).timestamp().toEpochMilli();
        long lastKLineTimestamp = klines.get(klines.size() - 1).timestamp().toEpochMilli();
        long tradeExitTimestamp = trade.exitTime().toEpochMilli();

        long effectiveStartTime = firstKLineTimestamp;
        long effectiveEndTime = Math.max(lastKLineTimestamp, tradeExitTimestamp);
        
        long duration = effectiveEndTime - effectiveStartTime;
        
        // Use record accessor duration() instead of getDuration()
        long fixedPadding = 30 * selectedTimeframe.duration().toMillis();
        long dynamicPadding = (long)(duration * 0.30);
        long xPadding = dynamicPadding + fixedPadding;

        this.minTimestamp = effectiveStartTime - xPadding;
        this.maxTimestamp = effectiveEndTime + xPadding;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (klines.isEmpty()) {
            g2d.setColor(UIManager.getColor("Label.disabledForeground"));
            g2d.drawString("No replay data available for this trade.", getWidth() / 2 - 80, getHeight() / 2);
            g2d.dispose();
            return;
        }

        int chartWidth = getWidth() - 60;
        int chartHeight = getHeight() - 75;
        int chartX = 5;
        int chartY = 25;
        
        drawGrid(g2d, chartX, chartY, chartWidth, chartHeight);
        drawPriceAxis(g2d, chartX, chartY, chartWidth, chartHeight);
        drawTimeAxis(g2d, chartX, chartY, chartWidth, chartHeight);

        long timeRange = maxTimestamp - minTimestamp;
        if (timeRange <= 0) return;

        for (int i = 0; i <= currentIndex; i++) {
            KLine k = klines.get(i);
            double x_double = chartX + ((double)(k.timestamp().toEpochMilli() - minTimestamp) / timeRange * chartWidth);
            int x = (int) x_double;
            // Use record accessor duration() instead of getDuration()
            double barWidth = (double) selectedTimeframe.duration().toMillis() / timeRange * chartWidth;
            drawCandle(g2d, k, x, chartY, (int) Math.max(1, barWidth * 0.8), chartHeight);
        }
        
        if (trade != null) {
            int entryCandleIndex = findCandleIndexForTimestamp(trade.entryTime());
            if (entryCandleIndex != -1 && entryCandleIndex <= currentIndex) {
                drawMarkerForEvent(g2d, trade.entryTime(), trade.direction(), true, entryCandleIndex,
                                   chartX, chartY, chartWidth, chartHeight);
            }

            int exitCandleIndex = findCandleIndexForTimestamp(trade.exitTime());
            if (exitCandleIndex != -1 && exitCandleIndex <= currentIndex) {
                drawMarkerForEvent(g2d, trade.exitTime(), (trade.direction() == TradeDirection.LONG) ? TradeDirection.SHORT : TradeDirection.LONG,
                                   false, exitCandleIndex, chartX, chartY, chartWidth, chartHeight);
            }
        }
        g2d.dispose();
    }

    private int findCandleIndexForTimestamp(Instant timestamp) {
        if (klines.isEmpty()) return -1;
        long targetMillis = timestamp.toEpochMilli();
        // Use record accessor duration() instead of getDuration()
        long candleDuration = selectedTimeframe.duration().toMillis();
        if (candleDuration <= 0) return -1;

        for (int i = 0; i < klines.size(); i++) {
            long candleStart = klines.get(i).timestamp().toEpochMilli();
            if (targetMillis >= candleStart && targetMillis < (candleStart + candleDuration)) {
                return i;
            }
        }
        return -1;
    }

    private void drawMarkerForEvent(Graphics2D g2d, Instant timestamp, TradeDirection direction, boolean isEntry,
                                    int candleIndex, int chartX, int chartY, int chartWidth, int chartHeight) {
        
        KLine candle = klines.get(candleIndex);
        long candleStart = candle.timestamp().toEpochMilli();
        // Use record accessor duration() instead of getDuration()
        long candleDuration = selectedTimeframe.duration().toMillis();
        if (candleDuration <= 0) return;
        long timeRange = maxTimestamp - minTimestamp;
        if (timeRange <= 0) return;

        double timeRatioInCandle = (double)(timestamp.toEpochMilli() - candleStart) / candleDuration;
        long candleStartOnAxis = candleStart - minTimestamp;
        
        int markerX = chartX + (int) (((double)candleStartOnAxis / timeRange * chartWidth) + 
                                      ((double)candleDuration / timeRange * chartWidth * timeRatioInCandle));

        drawMarker(g2d, markerX, candle, direction, isEntry, chartY, chartHeight);
    }

    private void styleTimeframeButton(JToggleButton button) {
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        button.setForeground(UIManager.getColor("Label.disabledForeground"));
        button.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                button.setForeground(UIManager.getColor("Label.foreground"));
            } else {
                button.setForeground(UIManager.getColor("Label.disabledForeground"));
            }
        });
    }
    
    private JButton createControlButton(String iconPath) {
        JButton button = new JButton(UITheme.getIcon(iconPath, 16, 16, UIManager.getColor("Label.foreground")));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        return button;
    }

    private void drawGrid(Graphics2D g2d, int x, int y, int w, int h) {
        g2d.setColor(UIManager.getColor("Component.borderColor"));
        for (int i = 1; i <= 4; i++) {
            g2d.drawLine(x, y + (i * h / 5), x + w, y + (i * h / 5));
        }
    }

    private void drawPriceAxis(Graphics2D g2d, int x, int y, int w, int h) {
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        g2d.setFont(UIManager.getFont("app.font.widget_content").deriveFont(10f));
        for (int i = 0; i <= 5; i++) {
            BigDecimal price = minPrice.add(maxPrice.subtract(minPrice).multiply(BigDecimal.valueOf(i)).divide(BigDecimal.valueOf(5), 5, RoundingMode.HALF_UP));
            int labelY = y + h - (int)((double)i / 5 * h);
            g2d.drawString(price.setScale(2, RoundingMode.HALF_UP).toPlainString(), x + w + 5, labelY);
        }
    }

    private void drawTimeAxis(Graphics2D g2d, int x, int y, int w, int h) {
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        g2d.setFont(UIManager.getFont("app.font.widget_content").deriveFont(10f));
        long timeRange = maxTimestamp - minTimestamp;
        if (timeRange <= 0) return;

        for (int i = 0; i <= 4; i++) {
            long timestamp = minTimestamp + (long)((double)i / 4 * timeRange);
            int labelX = x + (int)((double)i / 4 * w);
            g2d.drawString(TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp)), labelX, y + h + 15);
        }
    }
    
    private void drawCandle(Graphics2D g2d, KLine k, int x, int y, int w, int h) {
        int openY = priceToY(k.open(), y, h);
        int closeY = priceToY(k.close(), y, h);
        int highY = priceToY(k.high(), y, h);
        int lowY = priceToY(k.low(), y, h);
        
        Color candleColor = k.close().compareTo(k.open()) >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative");
        g2d.setColor(candleColor);
        
        g2d.drawLine(x + w/2, highY, x + w/2, lowY);
        g2d.fillRect(x, Math.min(openY, closeY), w, Math.abs(openY - closeY));
    }

    private void drawMarker(Graphics2D g2d, int x, KLine candle, TradeDirection direction, boolean isEntry, int chartY, int chartHeight) {
        g2d.setColor(isEntry ? ENTRY_MARKER_COLOR : EXIT_MARKER_COLOR);

        if (direction == TradeDirection.LONG) { // "buy" action
            int markerY = priceToY(candle.low(), chartY, chartHeight);
            g2d.fillPolygon(new int[]{x - 5, x + 5, x}, new int[]{markerY + 15, markerY + 15, markerY + 5}, 3);
        } else { // "sell" action
            int markerY = priceToY(candle.high(), chartY, chartHeight);
            g2d.fillPolygon(new int[]{x - 5, x + 5, x}, new int[]{markerY - 15, markerY - 15, markerY - 5}, 3);
        }
    }

    private int priceToY(BigDecimal price, int chartY, int chartHeight) {
        BigDecimal range = maxPrice.subtract(minPrice);
        if (range.signum() == 0) return chartY + chartHeight / 2;
        BigDecimal relativePrice = price.subtract(minPrice);
        double percentage = relativePrice.divide(range, 5, RoundingMode.HALF_UP).doubleValue();
        return chartY + (int) ((1.0 - percentage) * chartHeight);
    }
}
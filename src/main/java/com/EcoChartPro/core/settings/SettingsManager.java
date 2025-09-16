package com.EcoChartPro.core.settings;

import com.EcoChartPro.core.theme.ThemeManager;
import com.EcoChartPro.core.theme.ThemeManager.Theme;
import com.EcoChartPro.model.drawing.FibonacciRetracementObject.FibLevelProperties;
import com.EcoChartPro.model.drawing.TextProperties;
import com.EcoChartPro.utils.AppDataManager;
import com.EcoChartPro.utils.SessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import java.math.BigDecimal;

public final class SettingsManager {

    private static final Logger logger = LoggerFactory.getLogger(SettingsManager.class);
    private static volatile SettingsManager instance;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final Properties properties = new Properties();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Color.class, new SessionManager.ColorSerializer());
        module.addDeserializer(Color.class, new SessionManager.ColorDeserializer());
        jsonMapper.registerModule(module);
    }


    public enum TradingSession { ASIA, SYDNEY, LONDON, NEW_YORK }
    public enum ToolbarPosition { LEFT, RIGHT }
    public enum CrosshairFPS {
        FPS_22(45, "22 FPS (Power Saving)"),
        FPS_30(33, "30 FPS (Balanced)"),
        FPS_45(22, "45 FPS (Smooth)"),
        FPS_60(16, "60 FPS (Default)"),
        FPS_120(8, "120 FPS (High Performance)");

        private final int delayMs;
        private final String displayName;
        CrosshairFPS(int delayMs, String displayName) { this.delayMs = delayMs; this.displayName = displayName; }
        public int getDelayMs() { return delayMs; }
        @Override public String toString() { return displayName; }
    }
    public enum ImageSource { LIVE, LOCAL }
    public enum PriceAxisLabelPosition { LEFT, RIGHT }

    // New enum for peak hours display style
    public enum PeakHoursDisplayStyle {
        SHADE_AREA("Shade Background Area"),
        INDICATOR_LINES("Start/End Indicator Lines"),
        BOTTOM_BAR("Bar Along Time Axis");
        private final String displayName; PeakHoursDisplayStyle(String s) { this.displayName = s; } @Override public String toString() { return displayName; }
    }


    // --- Settings Fields ---
    private Theme currentTheme;
    private float uiScale;
    private Color bullColor, bearColor, gridColor, chartBackground, crosshairColor, axisTextColor;
    private Color livePriceLabelBullTextColor, livePriceLabelBearTextColor;
    private int livePriceLabelFontSize;
    private Color crosshairLabelBackgroundColor, crosshairLabelForegroundColor;
    private ZoneId displayZoneId;
    private boolean daySeparatorsEnabled;
    private ToolbarPosition drawingToolbarPosition;
    private int drawingToolbarWidth, drawingToolbarHeight;
    private int snapRadius;
    private int drawingHitThreshold;
    private int drawingHandleSize;
    private int autoSaveInterval;
    private CrosshairFPS crosshairFps;
    private final Map<TradingSession, LocalTime> sessionStartTimes = new EnumMap<>(TradingSession.class);
    private final Map<TradingSession, LocalTime> sessionEndTimes = new EnumMap<>(TradingSession.class);
    private final Map<TradingSession, Color> sessionColors = new EnumMap<>(TradingSession.class);
    private final Map<TradingSession, Boolean> sessionEnabled = new EnumMap<>(TradingSession.class);
    private ImageSource imageSource;
    private boolean priceAxisLabelsEnabled;
    private boolean priceAxisLabelsShowOrders;
    private boolean priceAxisLabelsShowDrawings;
    private boolean priceAxisLabelsShowFibonaccis;
    private PriceAxisLabelPosition priceAxisLabelPosition;
    private final Map<String, Properties> toolDefaults = new ConcurrentHashMap<>();
    private List<String> tradeReplayAvailableTimeframes;
    private boolean disciplineCoachEnabled;
    private int optimalTradeCountOverride;
    private boolean overtrainingNudgeEnabled;
    private boolean fatigueNudgeEnabled;
    private boolean winStreakNudgeEnabled;
    private boolean lossStreakNudgeEnabled;
    private LocalTime fastForwardTime;
    private boolean showPeakHoursLines;
    private PeakHoursDisplayStyle peakHoursDisplayStyle;
    private Color peakHoursColorShade;
    private Color peakHoursColorStart;
    private int peakHoursBottomBarHeight;
    private Color peakHoursColorEnd;
    private List<Integer> peakPerformanceHoursOverride;
    private BigDecimal commissionPerTrade;
    private BigDecimal simulatedSpreadPoints;
    private boolean autoJournalOnTradeClose;
    private boolean sessionHighlightingEnabled;
    private List<TradingSession> preferredTradingSessions;


    private SettingsManager() {
        initializeDefaultSessionValues();
        loadSettings(); // Load saved settings, which will override defaults
    }

    private void initializeDefaultSessionValues() {
        sessionStartTimes.put(TradingSession.ASIA, LocalTime.of(0, 0));
        sessionEndTimes.put(TradingSession.ASIA, LocalTime.of(9, 0));
        sessionColors.put(TradingSession.ASIA, new Color(0, 150, 136, 40));
        sessionEnabled.put(TradingSession.ASIA, true);

        sessionStartTimes.put(TradingSession.SYDNEY, LocalTime.of(22, 0));
        sessionEndTimes.put(TradingSession.SYDNEY, LocalTime.of(7, 0));
        sessionColors.put(TradingSession.SYDNEY, new Color(255, 82, 82, 40));
        sessionEnabled.put(TradingSession.SYDNEY, true);

        sessionStartTimes.put(TradingSession.LONDON, LocalTime.of(8, 0));
        sessionEndTimes.put(TradingSession.LONDON, LocalTime.of(17, 0));
        sessionColors.put(TradingSession.LONDON, new Color(33, 150, 243, 40));
        sessionEnabled.put(TradingSession.LONDON, true);

        sessionStartTimes.put(TradingSession.NEW_YORK, LocalTime.of(13, 0));
        sessionEndTimes.put(TradingSession.NEW_YORK, LocalTime.of(22, 0));
        sessionColors.put(TradingSession.NEW_YORK, new Color(255, 193, 7, 40));
        sessionEnabled.put(TradingSession.NEW_YORK, true);
    }
    
    public static SettingsManager getInstance() {
        if (instance == null) {
            synchronized (SettingsManager.class) {
                if (instance == null) {
                    instance = new SettingsManager();
                }
            }
        }
        return instance;
    }
    
    private void loadSettings() {
        Optional<Path> configPathOpt = AppDataManager.getAppConfigPath();
        if (configPathOpt.isPresent() && configPathOpt.get().toFile().exists()) {
            try (FileInputStream in = new FileInputStream(configPathOpt.get().toFile())) {
                properties.load(in);
            } catch (IOException e) {
                logger.error("Failed to load settings file, using defaults.", e);
            }
        }

        // Load UI Scale first as it's a general app setting
        this.uiScale = Float.parseFloat(properties.getProperty("app.uiScale", "1.0f"));

        // Load theme and update chart colors based on it BEFORE loading custom colors
        this.currentTheme = Theme.valueOf(properties.getProperty("app.theme", "DARK"));
        updateChartColorsForTheme(this.currentTheme, false); // Load defaults for the theme

        // Now load custom colors, which will override the theme defaults if present
        this.bullColor = parseColor(properties.getProperty("chart.bullColor"), this.bullColor);
        this.bearColor = parseColor(properties.getProperty("chart.bearColor"), this.bearColor);
        this.gridColor = parseColor(properties.getProperty("chart.gridColor"), this.gridColor);
        this.chartBackground = parseColor(properties.getProperty("chart.backgroundColor"), this.chartBackground);
        this.crosshairColor = parseColor(properties.getProperty("chart.crosshairColor"), this.crosshairColor);
        this.axisTextColor = parseColor(properties.getProperty("chart.axisTextColor"), this.axisTextColor);
        this.livePriceLabelBullTextColor = parseColor(properties.getProperty("chart.livePriceLabel.bullTextColor"), this.livePriceLabelBullTextColor);
        this.livePriceLabelBearTextColor = parseColor(properties.getProperty("chart.livePriceLabel.bearTextColor"), this.livePriceLabelBearTextColor);
        this.livePriceLabelFontSize = Integer.parseInt(properties.getProperty("chart.livePriceLabel.fontSize", "12"));
        this.crosshairLabelBackgroundColor = parseColor(properties.getProperty("crosshair.label.backgroundColor"), this.crosshairLabelBackgroundColor);
        this.crosshairLabelForegroundColor = parseColor(properties.getProperty("crosshair.label.foregroundColor"), this.crosshairLabelForegroundColor);
        
        // Load other settings
        this.displayZoneId = ZoneId.of(properties.getProperty("chart.zoneId", ZoneId.systemDefault().getId()));
        this.daySeparatorsEnabled = Boolean.parseBoolean(properties.getProperty("chart.daySeparators", "true"));
        this.drawingToolbarPosition = ToolbarPosition.valueOf(properties.getProperty("toolbar.position", "LEFT"));
        this.drawingToolbarWidth = Integer.parseInt(properties.getProperty("toolbar.width", "35"));
        this.drawingToolbarHeight = Integer.parseInt(properties.getProperty("toolbar.height", "300"));
        this.snapRadius = Integer.parseInt(properties.getProperty("chart.snapRadius", "10"));
        this.drawingHitThreshold = Integer.parseInt(properties.getProperty("drawing.hitThreshold", "8"));
        this.drawingHandleSize = Integer.parseInt(properties.getProperty("drawing.handleSize", "8"));
        this.autoSaveInterval = Integer.parseInt(properties.getProperty("replay.autoSaveInterval", "100"));
        this.crosshairFps = CrosshairFPS.valueOf(properties.getProperty("chart.crosshairFps", "FPS_45"));
        this.imageSource = ImageSource.valueOf(properties.getProperty("dashboard.imageSource", "LOCAL"));

        this.priceAxisLabelsEnabled = Boolean.parseBoolean(properties.getProperty("chart.priceAxisLabels.enabled", "true"));
        this.priceAxisLabelsShowOrders = Boolean.parseBoolean(properties.getProperty("chart.priceAxisLabels.showOrders", "true"));
        this.priceAxisLabelsShowDrawings = Boolean.parseBoolean(properties.getProperty("chart.priceAxisLabels.showDrawings", "true"));
        this.priceAxisLabelsShowFibonaccis = Boolean.parseBoolean(properties.getProperty("chart.priceAxisLabels.showFibonaccis", "false"));
        this.priceAxisLabelPosition = PriceAxisLabelPosition.valueOf(properties.getProperty("chart.priceAxisLabels.position", "RIGHT"));

        this.disciplineCoachEnabled = Boolean.parseBoolean(properties.getProperty("discipline.enabled", "true"));
        this.optimalTradeCountOverride = Integer.parseInt(properties.getProperty("discipline.tradeCountOverride", "-1"));
        this.overtrainingNudgeEnabled = Boolean.parseBoolean(properties.getProperty("discipline.nudge.overtraining", "true"));
        this.fatigueNudgeEnabled = Boolean.parseBoolean(properties.getProperty("discipline.nudge.fatigue", "true"));
        this.winStreakNudgeEnabled = Boolean.parseBoolean(properties.getProperty("discipline.nudge.winStreak", "true"));
        this.lossStreakNudgeEnabled = Boolean.parseBoolean(properties.getProperty("discipline.nudge.lossStreak", "true"));
        this.fastForwardTime = LocalTime.parse(properties.getProperty("discipline.nudge.fastForwardTime", "00:00"));
        this.showPeakHoursLines = Boolean.parseBoolean(properties.getProperty("discipline.showPeakHoursLines", "true"));
        
        this.peakHoursDisplayStyle = PeakHoursDisplayStyle.valueOf(properties.getProperty("discipline.peakHours.style", "INDICATOR_LINES"));
        this.peakHoursColorShade = parseColor(properties.getProperty("discipline.peakHours.color.shade"), new Color(76, 175, 80, 20));
        this.peakHoursColorStart = parseColor(properties.getProperty("discipline.peakHours.color.start"), new Color(76, 175, 80, 150));
        this.peakHoursColorEnd = parseColor(properties.getProperty("discipline.peakHours.color.end"), new Color(0, 150, 136, 150));
        this.peakHoursBottomBarHeight = Integer.parseInt(properties.getProperty("discipline.peakHours.bottomBarHeight", "4"));

        String overrideHoursStr = properties.getProperty("discipline.peakHoursOverride", "");
        this.peakPerformanceHoursOverride = new ArrayList<>();
        if (!overrideHoursStr.isBlank()) {
            try {
                this.peakPerformanceHoursOverride = Arrays.stream(overrideHoursStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            } catch (NumberFormatException e) {
                logger.warn("Could not parse peakPerformanceHoursOverride setting: '{}'. Using default empty list.", overrideHoursStr);
                this.peakPerformanceHoursOverride = new ArrayList<>();
            }
        }

        // --- NEW: Load preferred trading sessions ---
        String preferredSessionsStr = properties.getProperty("discipline.preferredTradingSessions", "LONDON,NEW_YORK");
        this.preferredTradingSessions = new ArrayList<>();
        if (!preferredSessionsStr.isBlank()) {
            try {
                this.preferredTradingSessions = Arrays.stream(preferredSessionsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(TradingSession::valueOf)
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                logger.warn("Could not parse preferredTradingSessions setting: '{}'. Using defaults.", preferredSessionsStr);
                this.preferredTradingSessions = new ArrayList<>(Arrays.asList(TradingSession.LONDON, TradingSession.NEW_YORK));
            }
        }

        // Load simulation settings
        this.commissionPerTrade = new BigDecimal(properties.getProperty("simulation.commissionPerTrade", "0.0"));
        this.simulatedSpreadPoints = new BigDecimal(properties.getProperty("simulation.simulatedSpreadPoints", "0.0"));
        this.autoJournalOnTradeClose = Boolean.parseBoolean(properties.getProperty("simulation.autoJournalOnTradeClose", "true"));

        // Load session highlighting setting
        this.sessionHighlightingEnabled = Boolean.parseBoolean(properties.getProperty("chart.sessionHighlighting.enabled", "true"));

        // Load trade replay timeframes
        String timeframesStr = properties.getProperty("tradeReplay.availableTimeframes", "1m,5m,15m");
        this.tradeReplayAvailableTimeframes = new ArrayList<>(Arrays.asList(timeframesStr.split(",")));

        for (TradingSession session : TradingSession.values()) {
            sessionEnabled.put(session, Boolean.parseBoolean(properties.getProperty("session." + session.name() + ".enabled", "true")));
        }

        properties.stringPropertyNames().forEach(key -> {
            if (key.startsWith("tool.")) {
                String[] parts = key.split("\\.", 3);
                if (parts.length == 3) {
                    String toolName = parts[1];
                    String propName = parts[2];
                    String value = properties.getProperty(key);
                    toolDefaults.computeIfAbsent(toolName, k -> new Properties()).setProperty(propName, value);
                }
            }
        });
    }
    
    private synchronized void saveSettings() {
        Optional<Path> configPathOpt = AppDataManager.getAppConfigPath();
        if (configPathOpt.isPresent()) {
            try (FileOutputStream out = new FileOutputStream(configPathOpt.get().toFile())) {
                properties.setProperty("app.theme", currentTheme.name());
                properties.setProperty("app.uiScale", String.valueOf(this.uiScale)); // MODIFICATION
                properties.setProperty("chart.bullColor", formatColor(bullColor));
                properties.setProperty("chart.bearColor", formatColor(bearColor));
                properties.setProperty("chart.gridColor", formatColor(gridColor));
                properties.setProperty("chart.backgroundColor", formatColor(chartBackground));
                properties.setProperty("chart.crosshairColor", formatColor(crosshairColor));
                properties.setProperty("chart.axisTextColor", formatColor(axisTextColor));
                properties.setProperty("chart.livePriceLabel.bullTextColor", formatColor(livePriceLabelBullTextColor));
                properties.setProperty("chart.livePriceLabel.bearTextColor", formatColor(livePriceLabelBearTextColor));
                properties.setProperty("chart.livePriceLabel.fontSize", String.valueOf(livePriceLabelFontSize));
                properties.setProperty("crosshair.label.backgroundColor", formatColor(crosshairLabelBackgroundColor));
                properties.setProperty("crosshair.label.foregroundColor", formatColor(crosshairLabelForegroundColor));
                properties.setProperty("chart.zoneId", displayZoneId.getId());
                properties.setProperty("chart.daySeparators", String.valueOf(daySeparatorsEnabled));
                properties.setProperty("toolbar.position", drawingToolbarPosition.name());
                properties.setProperty("toolbar.width", String.valueOf(drawingToolbarWidth));
                properties.setProperty("toolbar.height", String.valueOf(drawingToolbarHeight));
                properties.setProperty("chart.snapRadius", String.valueOf(snapRadius));
                properties.setProperty("drawing.hitThreshold", String.valueOf(drawingHitThreshold));
                properties.setProperty("drawing.handleSize", String.valueOf(drawingHandleSize));
                properties.setProperty("replay.autoSaveInterval", String.valueOf(autoSaveInterval));
                properties.setProperty("chart.crosshairFps", crosshairFps.name());
                properties.setProperty("dashboard.imageSource", imageSource.name());

                properties.setProperty("chart.priceAxisLabels.enabled", String.valueOf(priceAxisLabelsEnabled));
                properties.setProperty("chart.priceAxisLabels.showOrders", String.valueOf(priceAxisLabelsShowOrders));
                properties.setProperty("chart.priceAxisLabels.showDrawings", String.valueOf(priceAxisLabelsShowDrawings));
                properties.setProperty("chart.priceAxisLabels.showFibonaccis", String.valueOf(priceAxisLabelsShowFibonaccis));
                properties.setProperty("chart.priceAxisLabels.position", priceAxisLabelPosition.name());

                properties.setProperty("discipline.enabled", String.valueOf(disciplineCoachEnabled));
                properties.setProperty("discipline.tradeCountOverride", String.valueOf(optimalTradeCountOverride));
                properties.setProperty("discipline.nudge.overtraining", String.valueOf(overtrainingNudgeEnabled));
                properties.setProperty("discipline.nudge.fatigue", String.valueOf(fatigueNudgeEnabled));
                properties.setProperty("discipline.nudge.winStreak", String.valueOf(winStreakNudgeEnabled));
                properties.setProperty("discipline.nudge.lossStreak", String.valueOf(lossStreakNudgeEnabled));
                properties.setProperty("discipline.nudge.fastForwardTime", fastForwardTime.toString());
                properties.setProperty("discipline.showPeakHoursLines", String.valueOf(showPeakHoursLines));

                properties.setProperty("discipline.peakHours.style", peakHoursDisplayStyle.name());
                properties.setProperty("discipline.peakHours.color.shade", formatColor(peakHoursColorShade));
                properties.setProperty("discipline.peakHours.color.start", formatColor(peakHoursColorStart));
                properties.setProperty("discipline.peakHours.color.end", formatColor(peakHoursColorEnd));
                properties.setProperty("discipline.peakHours.bottomBarHeight", String.valueOf(peakHoursBottomBarHeight));

                String overrideHoursStr = this.peakPerformanceHoursOverride.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
                properties.setProperty("discipline.peakHoursOverride", overrideHoursStr);

                // --- NEW: Save preferred trading sessions ---
                String preferredSessionsStr = this.preferredTradingSessions.stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
                properties.setProperty("discipline.preferredTradingSessions", preferredSessionsStr);

                // Save simulation settings
                properties.setProperty("simulation.commissionPerTrade", commissionPerTrade.toPlainString());
                properties.setProperty("simulation.simulatedSpreadPoints", simulatedSpreadPoints.toPlainString());
                properties.setProperty("simulation.autoJournalOnTradeClose", String.valueOf(autoJournalOnTradeClose));

                // Save session highlighting setting
                properties.setProperty("chart.sessionHighlighting.enabled", String.valueOf(sessionHighlightingEnabled));

                // Save trade replay timeframes
                properties.setProperty("tradeReplay.availableTimeframes", String.join(",", this.tradeReplayAvailableTimeframes));

                for (TradingSession session : TradingSession.values()) {
                    properties.setProperty("session." + session.name() + ".enabled", String.valueOf(sessionEnabled.get(session)));
                }

                toolDefaults.forEach((toolName, props) -> {
                    props.forEach((propName, propValue) -> {
                        String key = "tool." + toolName + "." + propName;
                        properties.setProperty(key, (String) propValue);
                    });
                });

                properties.store(out, "Eco Chart Pro Application Settings");
            } catch (IOException e) {
                logger.error("Failed to save settings.", e);
            }
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) { pcs.addPropertyChangeListener(listener); }
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) { pcs.addPropertyChangeListener(propertyName, listener); }
    public void removePropertyChangeListener(PropertyChangeListener listener) { pcs.removePropertyChangeListener(listener); }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void setCurrentTheme(Theme newTheme) {
        if (newTheme != null && this.currentTheme != newTheme) {
            Theme oldVal = this.currentTheme;
            this.currentTheme = newTheme;
            ThemeManager.applyTheme(newTheme);
            updateChartColorsForTheme(newTheme, true);
            saveSettings(); // Save the change
            pcs.firePropertyChange("themeChanged", oldVal, newTheme);
        }
    }

    // Add getter and setter for UI scale
    public float getUiScale() {
        return uiScale;
    }
    public void setUiScale(float newScale) {
        if (this.uiScale != newScale) {
            float oldVal = this.uiScale;
            this.uiScale = newScale;
            saveSettings();
            pcs.firePropertyChange("uiScaleChanged", oldVal, newScale);
        }
    }

    private void updateChartColorsForTheme(Theme theme, boolean fireEvent) {
        if (theme == Theme.LIGHT) {
            this.bullColor = new Color(0, 150, 136);
            this.bearColor = new Color(211, 47, 47);
            this.gridColor = new Color(224, 224, 224);
            this.chartBackground = Color.WHITE;
            this.crosshairColor = new Color(100, 100, 100);
            this.axisTextColor = new Color(50, 50, 50);
            this.livePriceLabelBullTextColor = Color.WHITE;
            this.livePriceLabelBearTextColor = Color.WHITE;
            this.crosshairLabelBackgroundColor = new Color(242, 242, 242);
            this.crosshairLabelForegroundColor = Color.BLACK;
        } else { // DARK theme
            this.bullColor = new Color(255, 140, 40);
            this.bearColor = Color.WHITE;
            this.gridColor = new Color(40, 42, 45); // Darker grid for the darker theme
            this.chartBackground = new Color(0x181A1B); // Darker background
            this.crosshairColor = Color.LIGHT_GRAY;
            this.axisTextColor = new Color(224, 224, 224);
            this.livePriceLabelBullTextColor = Color.BLACK;
            this.livePriceLabelBearTextColor = Color.BLACK;
            this.crosshairLabelBackgroundColor = new Color(60, 63, 65);
            this.crosshairLabelForegroundColor = Color.WHITE;
        }
        if (fireEvent) {
            pcs.firePropertyChange("chartColorsChanged", null, null);
        }
    }

    public Color getBullColor() { return bullColor; }
    public void setBullColor(Color c) { this.bullColor = c; saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }

    public Color getBearColor() { return bearColor; }
    public void setBearColor(Color c) { this.bearColor = c; saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }

    public Color getGridColor() { return gridColor; }
    public void setGridColor(Color c) { this.gridColor = c; saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }

    public Color getChartBackground() { return chartBackground; }
    public void setChartBackground(Color c) { this.chartBackground = c; saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }

    public Color getCrosshairColor() { return crosshairColor; }
    public void setCrosshairColor(Color c) { this.crosshairColor = c; saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    
    public Color getAxisTextColor() { return axisTextColor; }
    public void setAxisTextColor(Color c) { this.axisTextColor = c; saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    
    public Color getLivePriceLabelBullTextColor() { return livePriceLabelBullTextColor; }
    public void setLivePriceLabelBullTextColor(Color c) { this.livePriceLabelBullTextColor = c; saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    
    public Color getLivePriceLabelBearTextColor() { return livePriceLabelBearTextColor; }
    public void setLivePriceLabelBearTextColor(Color c) { this.livePriceLabelBearTextColor = c; saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }

    public int getLivePriceLabelFontSize() { return livePriceLabelFontSize; }
    public void setLivePriceLabelFontSize(int size) {
        if (this.livePriceLabelFontSize != size) {
            int oldVal = this.livePriceLabelFontSize;
            this.livePriceLabelFontSize = size;
            saveSettings();
            pcs.firePropertyChange("livePriceLabelFontSizeChanged", oldVal, this.livePriceLabelFontSize);
        }
    }

    public Color getCrosshairLabelBackgroundColor() { return crosshairLabelBackgroundColor; }
    public void setCrosshairLabelBackgroundColor(Color c) { this.crosshairLabelBackgroundColor = c; saveSettings(); pcs.firePropertyChange("crosshairLabelColorChanged", null, null); }
    
    public Color getCrosshairLabelForegroundColor() { return crosshairLabelForegroundColor; }
    public void setCrosshairLabelForegroundColor(Color c) { this.crosshairLabelForegroundColor = c; saveSettings(); pcs.firePropertyChange("crosshairLabelColorChanged", null, null); }

    public boolean isDaySeparatorsEnabled() { return daySeparatorsEnabled; }
    public void setDaySeparatorsEnabled(boolean enabled) {
        boolean oldVal = this.daySeparatorsEnabled;
        this.daySeparatorsEnabled = enabled;
        saveSettings();
        pcs.firePropertyChange("daySeparatorsEnabledChanged", oldVal, this.daySeparatorsEnabled);
    }
    
    public ToolbarPosition getDrawingToolbarPosition() { return drawingToolbarPosition; }
    public void setDrawingToolbarPosition(ToolbarPosition pos) {
        ToolbarPosition oldVal = this.drawingToolbarPosition;
        this.drawingToolbarPosition = pos;
        saveSettings();
        pcs.firePropertyChange("drawingToolbarPositionChanged", oldVal, pos);
    }
    
    public int getDrawingToolbarWidth() { return drawingToolbarWidth; }
    public void setDrawingToolbarWidth(int width) {
        int oldVal = this.drawingToolbarWidth;
        this.drawingToolbarWidth = width;
        saveSettings();
        pcs.firePropertyChange("drawingToolbarSizeChanged", oldVal, width);
    }
    
    public int getDrawingToolbarHeight() { return drawingToolbarHeight; }
    public void setDrawingToolbarHeight(int height) {
        int oldVal = this.drawingToolbarHeight;
        this.drawingToolbarHeight = height;
        saveSettings();
        pcs.firePropertyChange("drawingToolbarSizeChanged", oldVal, height);
    }

    public CrosshairFPS getCrosshairFps() { return crosshairFps; }
    public void setCrosshairFps(CrosshairFPS fps) {
        CrosshairFPS oldVal = this.crosshairFps;
        this.crosshairFps = fps;
        saveSettings();
        pcs.firePropertyChange("crosshairFpsChanged", oldVal, this.crosshairFps);
    }
    
    public int getSnapRadius() { return snapRadius; }
    public void setSnapRadius(int radius) {
        int oldVal = this.snapRadius;
        this.snapRadius = radius;
        saveSettings();
        pcs.firePropertyChange("snapRadiusChanged", oldVal, this.snapRadius);
    }
    public int getDrawingHitThreshold() { return drawingHitThreshold; }
    public void setDrawingHitThreshold(int threshold) {
        int oldVal = this.drawingHitThreshold;
        this.drawingHitThreshold = threshold;
        saveSettings();
        pcs.firePropertyChange("drawingHitThresholdChanged", oldVal, this.drawingHitThreshold);
    }
    public int getDrawingHandleSize() { return drawingHandleSize; }
    public void setDrawingHandleSize(int size) {
        int oldVal = this.drawingHandleSize;
        this.drawingHandleSize = size;
        saveSettings();
        pcs.firePropertyChange("drawingHandleSizeChanged", oldVal, this.drawingHandleSize);
    }
    public int getAutoSaveInterval() { return autoSaveInterval; }
    public void setAutoSaveInterval(int interval) {
        int oldVal = this.autoSaveInterval;
        this.autoSaveInterval = interval;
        saveSettings();
        pcs.firePropertyChange("autoSaveIntervalChanged", oldVal, this.autoSaveInterval);
    }

    public ImageSource getImageSource() { return imageSource; }
    public void setImageSource(ImageSource newSource) {
        if (this.imageSource != newSource) {
            ImageSource oldSource = this.imageSource;
            this.imageSource = newSource;
            saveSettings();
            pcs.firePropertyChange("imageSource", oldSource, newSource);
        }
    }

    public ZoneId getDisplayZoneId() { return displayZoneId; }
    public void setDisplayZoneId(ZoneId newZoneId) {
        if (newZoneId != null && !this.displayZoneId.equals(newZoneId)) {
            ZoneId oldZoneId = this.displayZoneId;
            this.displayZoneId = newZoneId;
            saveSettings();
            pcs.firePropertyChange("displayZoneId", oldZoneId, newZoneId);
        }
    }

    public Map<TradingSession, Boolean> getSessionEnabled() { return sessionEnabled; }
    public void setSessionEnabled(TradingSession s, boolean isEnabled) {
        sessionEnabled.put(s, isEnabled); saveSettings(); pcs.firePropertyChange("sessionSettingsChanged", null, s);
    }

    public Map<TradingSession, LocalTime> getSessionStartTimes() { return sessionStartTimes; }
    public void setSessionStartTime(TradingSession s, LocalTime startTime) {
        sessionStartTimes.put(s, startTime); saveSettings(); pcs.firePropertyChange("sessionSettingsChanged", null, s);
    }

    public Map<TradingSession, LocalTime> getSessionEndTimes() { return sessionEndTimes; }
    public void setSessionEndTime(TradingSession s, LocalTime endTime) {
        sessionEndTimes.put(s, endTime); saveSettings(); pcs.firePropertyChange("sessionSettingsChanged", null, s);
    }

    public Map<TradingSession, Color> getSessionColors() { return sessionColors; }
    public void setSessionColor(TradingSession s, Color color) {
        sessionColors.put(s, color); saveSettings(); pcs.firePropertyChange("sessionSettingsChanged", null, s);
    }

    public boolean isPriceAxisLabelsEnabled() { return priceAxisLabelsEnabled; }
    public void setPriceAxisLabelsEnabled(boolean enabled) {
        boolean oldVal = this.priceAxisLabelsEnabled;
        this.priceAxisLabelsEnabled = enabled;
        saveSettings();
        pcs.firePropertyChange("priceAxisLabelsEnabledChanged", oldVal, this.priceAxisLabelsEnabled);
    }

    public boolean isPriceAxisLabelsShowOrders() { return priceAxisLabelsShowOrders; }
    public void setPriceAxisLabelsShowOrders(boolean show) {
        if (this.priceAxisLabelsShowOrders != show) {
            this.priceAxisLabelsShowOrders = show;
            saveSettings();
            pcs.firePropertyChange("priceAxisLabelsVisibilityChanged", !show, show);
        }
    }

    public boolean isPriceAxisLabelsShowDrawings() { return priceAxisLabelsShowDrawings; }
    public void setPriceAxisLabelsShowDrawings(boolean show) {
        if (this.priceAxisLabelsShowDrawings != show) {
            this.priceAxisLabelsShowDrawings = show;
            saveSettings();
            pcs.firePropertyChange("priceAxisLabelsVisibilityChanged", !show, show);
        }
    }

    public boolean isPriceAxisLabelsShowFibonaccis() { return priceAxisLabelsShowFibonaccis; }
    public void setPriceAxisLabelsShowFibonaccis(boolean show) {
        if (this.priceAxisLabelsShowFibonaccis != show) {
            this.priceAxisLabelsShowFibonaccis = show;
            saveSettings();
            pcs.firePropertyChange("priceAxisLabelsVisibilityChanged", !show, show);
        }
    }

    public PriceAxisLabelPosition getPriceAxisLabelPosition() { return priceAxisLabelPosition; }
    public void setPriceAxisLabelPosition(PriceAxisLabelPosition position) {
        if (this.priceAxisLabelPosition != position) {
            PriceAxisLabelPosition oldVal = this.priceAxisLabelPosition;
            this.priceAxisLabelPosition = position;
            saveSettings();
            pcs.firePropertyChange("priceAxisLabelPositionChanged", oldVal, position);
        }
    }

    public List<String> getTradeReplayAvailableTimeframes() {
        return tradeReplayAvailableTimeframes;
    }

    public void setTradeReplayAvailableTimeframes(List<String> timeframes) {
        if (!this.tradeReplayAvailableTimeframes.equals(timeframes)) {
            List<String> oldVal = new ArrayList<>(this.tradeReplayAvailableTimeframes);
            this.tradeReplayAvailableTimeframes = new ArrayList<>(timeframes);
            saveSettings();
            pcs.firePropertyChange("tradeReplayTimeframesChanged", oldVal, this.tradeReplayAvailableTimeframes);
        }
    }

    public boolean isDisciplineCoachEnabled() { return disciplineCoachEnabled; }
    public void setDisciplineCoachEnabled(boolean enabled) {
        if (this.disciplineCoachEnabled != enabled) {
            boolean oldVal = this.disciplineCoachEnabled;
            this.disciplineCoachEnabled = enabled;
            saveSettings();
            pcs.firePropertyChange("disciplineSettingsChanged", oldVal, this.disciplineCoachEnabled);
        }
    }

    public int getOptimalTradeCountOverride() { return optimalTradeCountOverride; }
    public void setOptimalTradeCountOverride(int count) {
        if (this.optimalTradeCountOverride != count) {
            int oldVal = this.optimalTradeCountOverride;
            this.optimalTradeCountOverride = count;
            saveSettings();
            pcs.firePropertyChange("disciplineSettingsChanged", oldVal, this.optimalTradeCountOverride);
        }
    }

    public boolean isOvertrainingNudgeEnabled() { return overtrainingNudgeEnabled; }
    public void setOvertrainingNudgeEnabled(boolean enabled) {
        if (this.overtrainingNudgeEnabled != enabled) {
            this.overtrainingNudgeEnabled = enabled;
            saveSettings();
        }
    }

    public boolean isFatigueNudgeEnabled() { return fatigueNudgeEnabled; }
    public void setFatigueNudgeEnabled(boolean enabled) {
        if (this.fatigueNudgeEnabled != enabled) {
            this.fatigueNudgeEnabled = enabled;
            saveSettings();
        }
    }

    public boolean isWinStreakNudgeEnabled() { return winStreakNudgeEnabled; }
    public void setWinStreakNudgeEnabled(boolean enabled) {
        if (this.winStreakNudgeEnabled != enabled) {
            this.winStreakNudgeEnabled = enabled;
            saveSettings();
        }
    }

    public boolean isLossStreakNudgeEnabled() { return lossStreakNudgeEnabled; }
    public void setLossStreakNudgeEnabled(boolean enabled) {
        if (this.lossStreakNudgeEnabled != enabled) {
            this.lossStreakNudgeEnabled = enabled;
            saveSettings();
        }
    }

    public LocalTime getFastForwardTime() { return fastForwardTime; }
    public void setFastForwardTime(LocalTime time) {
        if (!this.fastForwardTime.equals(time)) {
            this.fastForwardTime = time;
            saveSettings();
        }
    }

    public boolean isShowPeakHoursLines() { return showPeakHoursLines; }
    public void setShowPeakHoursLines(boolean enabled) {
        if (this.showPeakHoursLines != enabled) {
            this.showPeakHoursLines = enabled;
            saveSettings();
            pcs.firePropertyChange("peakHoursLinesVisibilityChanged", !enabled, enabled);
        }
    }

    public PeakHoursDisplayStyle getPeakHoursDisplayStyle() { return peakHoursDisplayStyle; }
    public void setPeakHoursDisplayStyle(PeakHoursDisplayStyle style) {
        if (this.peakHoursDisplayStyle != style) {
            this.peakHoursDisplayStyle = style;
            saveSettings();
            pcs.firePropertyChange("peakHoursSettingsChanged", null, null);
        }
    }

    public Color getPeakHoursColorShade() { return peakHoursColorShade; }
    public void setPeakHoursColorShade(Color color) {
        if (!this.peakHoursColorShade.equals(color)) {
            this.peakHoursColorShade = color;
            saveSettings();
            pcs.firePropertyChange("peakHoursSettingsChanged", null, null);
        }
    }

    public Color getPeakHoursColorStart() { return peakHoursColorStart; }
    public void setPeakHoursColorStart(Color color) {
        if (!this.peakHoursColorStart.equals(color)) {
            this.peakHoursColorStart = color;
            saveSettings();
            pcs.firePropertyChange("peakHoursSettingsChanged", null, null);
        }
    }

    public Color getPeakHoursColorEnd() { return peakHoursColorEnd; }
    public void setPeakHoursColorEnd(Color color) {
        if (!this.peakHoursColorEnd.equals(color)) {
            this.peakHoursColorEnd = color;
            saveSettings();
            pcs.firePropertyChange("peakHoursSettingsChanged", null, null);
        }
    }

    public int getPeakHoursBottomBarHeight() { return peakHoursBottomBarHeight; }
    public void setPeakHoursBottomBarHeight(int height) {
        if (this.peakHoursBottomBarHeight != height) {
            this.peakHoursBottomBarHeight = height;
            saveSettings();
            pcs.firePropertyChange("peakHoursSettingsChanged", null, null);
        }
    }

    public List<Integer> getPeakPerformanceHoursOverride() {
        return peakPerformanceHoursOverride;
    }

    public void setPeakPerformanceHoursOverride(List<Integer> hours) {
        List<Integer> sortedNewHours = hours.stream().sorted().collect(Collectors.toList());
        List<Integer> sortedOldHours = this.peakPerformanceHoursOverride.stream().sorted().collect(Collectors.toList());
        if (!sortedOldHours.equals(sortedNewHours)) {
            List<Integer> oldVal = new ArrayList<>(this.peakPerformanceHoursOverride);
            this.peakPerformanceHoursOverride = new ArrayList<>(sortedNewHours);
            saveSettings();
            pcs.firePropertyChange("peakHoursOverrideChanged", oldVal, this.peakPerformanceHoursOverride);
        }
    }

    // --- NEW: Getter and Setter for preferred trading sessions ---
    public List<TradingSession> getPreferredTradingSessions() {
        return preferredTradingSessions;
    }
    public void setPreferredTradingSessions(List<TradingSession> sessions) {
        if (!this.preferredTradingSessions.equals(sessions)) {
            List<TradingSession> oldVal = new ArrayList<>(this.preferredTradingSessions);
            this.preferredTradingSessions = new ArrayList<>(sessions);
            saveSettings();
            pcs.firePropertyChange("preferredTradingSessionsChanged", oldVal, this.preferredTradingSessions);
        }
    }

    public BigDecimal getCommissionPerTrade() {
        return commissionPerTrade;
    }

    public void setCommissionPerTrade(BigDecimal commission) {
        if (this.commissionPerTrade == null || this.commissionPerTrade.compareTo(commission) != 0) {
            this.commissionPerTrade = commission;
            saveSettings();
            pcs.firePropertyChange("simulationSettingsChanged", null, null);
        }
    }

    public BigDecimal getSimulatedSpreadPoints() {
        return simulatedSpreadPoints;
    }

    public void setSimulatedSpreadPoints(BigDecimal spread) {
        if (this.simulatedSpreadPoints == null || this.simulatedSpreadPoints.compareTo(spread) != 0) {
            this.simulatedSpreadPoints = spread;
            saveSettings();
            pcs.firePropertyChange("simulationSettingsChanged", null, null);
        }
    }
    
    public boolean isAutoJournalOnTradeClose() { return autoJournalOnTradeClose; }
    public void setAutoJournalOnTradeClose(boolean enabled) {
        if (this.autoJournalOnTradeClose != enabled) {
            this.autoJournalOnTradeClose = enabled;
            saveSettings();
            pcs.firePropertyChange("autoJournalOnTradeCloseChanged", !enabled, enabled);
        }
    }

    public boolean isSessionHighlightingEnabled() { return sessionHighlightingEnabled; }
    public void setSessionHighlightingEnabled(boolean enabled) {
        if (this.sessionHighlightingEnabled != enabled) {
            boolean oldVal = this.sessionHighlightingEnabled;
            this.sessionHighlightingEnabled = enabled;
            saveSettings();
            pcs.firePropertyChange("sessionHighlightingChanged", oldVal, this.sessionHighlightingEnabled);
        }
    }

    private String formatColor(Color c) {
        return c.getRed() + "," + c.getGreen() + "," + c.getBlue() + "," + c.getAlpha();
    }

    private Color parseColor(String s, Color defaultColor) {
        if (s == null || s.isBlank()) return defaultColor;
        try {
            String[] parts = s.split(",");
            return new Color(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (Exception e) {
            logger.warn("Could not parse color string '{}', using default.", s);
            return defaultColor;
        }
    }

    public Color getToolDefaultColor(String toolName, Color fallback) {
        Properties props = toolDefaults.get(toolName);
        if (props != null) {
            return parseColor(props.getProperty("color", formatColor(fallback)), fallback);
        }
        return fallback;
    }

    public BasicStroke getToolDefaultStroke(String toolName, BasicStroke fallback) {
        Properties props = toolDefaults.get(toolName);
        if (props == null) return fallback;
        try {
            float width = Float.parseFloat(props.getProperty("stroke.width", String.valueOf(fallback.getLineWidth())));
            return new BasicStroke(width, fallback.getEndCap(), fallback.getLineJoin());
        } catch (Exception e) {
            return fallback;
        }
    }

    public void setToolDefaultColor(String toolName, Color color) {
        toolDefaults.computeIfAbsent(toolName, k -> new Properties()).setProperty("color", formatColor(color));
        saveSettings();
        pcs.firePropertyChange("toolDefaultsChanged", null, toolName);
    }

    public void setToolDefaultStroke(String toolName, BasicStroke stroke) {
        Properties props = toolDefaults.computeIfAbsent(toolName, k -> new Properties());
        props.setProperty("stroke.width", String.valueOf(stroke.getLineWidth()));
        saveSettings();
        pcs.firePropertyChange("toolDefaultsChanged", null, toolName);
    }
    
    public boolean getToolDefaultShowPriceLabel(String toolName, boolean fallback) {
        Properties props = toolDefaults.get(toolName);
        if (props != null) {
            return Boolean.parseBoolean(props.getProperty("showPriceLabel", String.valueOf(fallback)));
        }
        return fallback;
    }

    public void setToolDefaultShowPriceLabel(String toolName, boolean show) {
        toolDefaults.computeIfAbsent(toolName, k -> new Properties()).setProperty("showPriceLabel", String.valueOf(show));
        saveSettings();
        pcs.firePropertyChange("toolDefaultsChanged", null, toolName);
    }

    private Map<Double, FibLevelProperties> createDefaultFibRetracementLevels() {
        Map<Double, FibLevelProperties> levels = new TreeMap<>();
        Color defaultColor = new Color(0, 150, 136, 200);
        levels.put(-0.618, new FibLevelProperties(true, defaultColor.darker()));
        levels.put(-0.272, new FibLevelProperties(true, defaultColor.darker()));
        levels.put(0.0, new FibLevelProperties(true, defaultColor));
        levels.put(0.236, new FibLevelProperties(true, defaultColor));
        levels.put(0.382, new FibLevelProperties(true, defaultColor));
        levels.put(0.5, new FibLevelProperties(true, defaultColor));
        levels.put(0.618, new FibLevelProperties(true, defaultColor));
        levels.put(0.786, new FibLevelProperties(true, defaultColor));
        levels.put(1.0, new FibLevelProperties(true, defaultColor));
        return levels;
    }

    private Map<Double, FibLevelProperties> createDefaultFibExtensionLevels() {
        Map<Double, FibLevelProperties> levels = new TreeMap<>();
        Color defaultColor = new Color(76, 175, 80, 200);
        levels.put(0.0, new FibLevelProperties(true, defaultColor));
        levels.put(0.236, new FibLevelProperties(true, defaultColor));
        levels.put(0.382, new FibLevelProperties(true, defaultColor));
        levels.put(0.5, new FibLevelProperties(true, defaultColor));
        levels.put(0.618, new FibLevelProperties(true, defaultColor));
        levels.put(0.786, new FibLevelProperties(true, defaultColor));
        levels.put(1.0, new FibLevelProperties(true, Color.CYAN));
        levels.put(1.618, new FibLevelProperties(true, defaultColor.darker()));
        levels.put(2.618, new FibLevelProperties(true, defaultColor.darker()));
        return levels;
    }

    public Map<Double, FibLevelProperties> getFibRetracementDefaultLevels() {
        Properties props = toolDefaults.get("FibonacciRetracementObject");
        if (props != null && props.containsKey("levels")) {
            try {
                return jsonMapper.readValue(props.getProperty("levels"), new TypeReference<>() {});
            } catch (IOException e) {
                logger.error("Failed to parse Fibonacci Retracement default levels from settings.", e);
            }
        }
        return createDefaultFibRetracementLevels();
    }
    
    public void setFibRetracementDefaultLevels(Map<Double, FibLevelProperties> levels) {
        try {
            String json = jsonMapper.writeValueAsString(levels);
            toolDefaults.computeIfAbsent("FibonacciRetracementObject", k -> new Properties()).setProperty("levels", json);
            saveSettings();
            pcs.firePropertyChange("toolDefaultsChanged", null, "FibonacciRetracementObject");
        } catch (IOException e) {
            logger.error("Failed to serialize Fibonacci Retracement default levels to settings.", e);
        }
    }
    
    public Map<Double, FibLevelProperties> getFibExtensionDefaultLevels() {
        Properties props = toolDefaults.get("FibonacciExtensionObject");
        if (props != null && props.containsKey("levels")) {
            try {
                return jsonMapper.readValue(props.getProperty("levels"), new TypeReference<>() {});
            } catch (IOException e) {
                logger.error("Failed to parse Fibonacci Extension default levels from settings.", e);
            }
        }
        return createDefaultFibExtensionLevels();
    }

    public void setFibExtensionDefaultLevels(Map<Double, FibLevelProperties> levels) {
        try {
            String json = jsonMapper.writeValueAsString(levels);
            toolDefaults.computeIfAbsent("FibonacciExtensionObject", k -> new Properties()).setProperty("levels", json);
            saveSettings();
            pcs.firePropertyChange("toolDefaultsChanged", null, "FibonacciExtensionObject");
        } catch (IOException e) {
            logger.error("Failed to serialize Fibonacci Extension default levels to settings.", e);
        }
    }
    
    public Font getToolDefaultFont(String toolName, Font fallback) {
        Properties props = toolDefaults.get(toolName);
        if (props == null) return fallback;
        try {
            String name = props.getProperty("font.name", fallback.getName());
            int style = Integer.parseInt(props.getProperty("font.style", String.valueOf(fallback.getStyle())));
            int size = Integer.parseInt(props.getProperty("font.size", String.valueOf(fallback.getSize())));
            return new Font(name, style, size);
        } catch (Exception e) {
            return fallback;
        }
    }
    
    public void setToolDefaultFont(String toolName, Font font) {
        Properties props = toolDefaults.computeIfAbsent(toolName, k -> new Properties());
        props.setProperty("font.name", font.getName());
        props.setProperty("font.style", String.valueOf(font.getStyle()));
        props.setProperty("font.size", String.valueOf(font.getSize()));
        saveSettings();
        pcs.firePropertyChange("toolDefaultsChanged", null, toolName);
    }
    
    public TextProperties getToolDefaultTextProperties(String toolName, TextProperties fallback) {
        Properties props = toolDefaults.get(toolName);
        if (props != null && props.containsKey("text.properties")) {
            try {
                return jsonMapper.readValue(props.getProperty("text.properties"), TextProperties.class);
            } catch (IOException e) {
                logger.error("Failed to parse TextProperties for {} from settings.", toolName, e);
            }
        }
        return fallback;
    }
    
    public void setToolDefaultTextProperties(String toolName, TextProperties textProps) {
        try {
            String json = jsonMapper.writeValueAsString(textProps);
            toolDefaults.computeIfAbsent(toolName, k -> new Properties()).setProperty("text.properties", json);
            saveSettings();
            pcs.firePropertyChange("toolDefaultsChanged", null, toolName);
        } catch (IOException e) {
            logger.error("Failed to serialize TextProperties for {} to settings.", toolName, e);
        }
    }
}
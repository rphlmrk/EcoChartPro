package com.EcoChartPro.core.settings;

import com.EcoChartPro.core.settings.config.*;
import com.EcoChartPro.core.theme.ThemeManager;
import com.EcoChartPro.core.theme.ThemeManager.Theme;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.model.drawing.FibonacciRetracementObject.FibLevelProperties;
import com.EcoChartPro.model.drawing.TextProperties;
import com.EcoChartPro.utils.AppDataManager;
import com.EcoChartPro.utils.SessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * [REFACTORED] A singleton service that manages all application settings using a .properties file
 * for robust startup, while maintaining a modern internal structure with config objects.
 */
public final class SettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);
    private static volatile SettingsService instance;
    private transient final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final Properties properties = new Properties();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // Configuration Data Objects
    private GeneralConfig generalConfig = new GeneralConfig();
    private ChartConfig chartConfig = new ChartConfig();
    private VolumeProfileConfig volumeProfileConfig = new VolumeProfileConfig();
    private DrawingConfig drawingConfig = new DrawingConfig();
    private TradingConfig tradingConfig = new TradingConfig();
    private DisciplineCoachConfig disciplineCoachConfig = new DisciplineCoachConfig();

    static {
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Color.class, new SessionManager.ColorSerializer());
        module.addDeserializer(Color.class, new SessionManager.ColorDeserializer());
        module.addSerializer(BasicStroke.class, new SessionManager.BasicStrokeSerializer());
        module.addDeserializer(BasicStroke.class, new SessionManager.BasicStrokeDeserializer());
        module.addSerializer(Font.class, new SessionManager.FontSerializer());
        module.addDeserializer(Font.class, new SessionManager.FontDeserializer());
        jsonMapper.registerModule(module);
    }

    private SettingsService() {
        loadSettings();
    }

    public static SettingsService getInstance() {
        if (instance == null) {
            synchronized (SettingsService.class) {
                if (instance == null) {
                    instance = new SettingsService();
                }
            }
        }
        return instance;
    }

    private void loadSettings() {
        Optional<Path> configPathOpt = AppDataManager.getAppConfigPath();
        if (configPathOpt.isPresent() && Files.exists(configPathOpt.get())) {
            try (FileInputStream in = new FileInputStream(configPathOpt.get().toFile())) {
                properties.load(in);
                logger.info("Successfully loaded settings from app_state.properties");
            } catch (IOException e) {
                logger.error("Failed to load settings file, using defaults.", e);
            }
        } else {
            logger.info("No app_state.properties file found. Initializing with default values.");
        }

        // --- General Config ---
        generalConfig.setUiScale(Float.parseFloat(properties.getProperty("app.uiScale", "1.0f")));
        generalConfig.setCurrentTheme(Theme.valueOf(properties.getProperty("app.theme", "DARK")));
        generalConfig.setDisplayZoneId(ZoneId.of(properties.getProperty("chart.zoneId", ZoneId.systemDefault().getId())));
        generalConfig.setImageSource(GeneralConfig.ImageSource.valueOf(properties.getProperty("dashboard.imageSource", "LOCAL")));

        // --- Chart Config ---
        chartConfig.initializeDefaultColors(generalConfig.getCurrentTheme() == Theme.DARK);
        chartConfig.setCurrentChartType(ChartType.valueOf(properties.getProperty("chart.type", "CANDLES")));
        chartConfig.setBullColor(parseColor(properties.getProperty("chart.bullColor"), chartConfig.getBullColor()));
        chartConfig.setBearColor(parseColor(properties.getProperty("chart.bearColor"), chartConfig.getBearColor()));
        chartConfig.setGridColor(parseColor(properties.getProperty("chart.gridColor"), chartConfig.getGridColor()));
        chartConfig.setChartBackground(parseColor(properties.getProperty("chart.backgroundColor"), chartConfig.getChartBackground()));
        chartConfig.setCrosshairColor(parseColor(properties.getProperty("chart.crosshairColor"), chartConfig.getCrosshairColor()));
        chartConfig.setAxisTextColor(parseColor(properties.getProperty("chart.axisTextColor"), chartConfig.getAxisTextColor()));
        chartConfig.setLivePriceLabelBullTextColor(parseColor(properties.getProperty("chart.livePriceLabel.bullTextColor"), chartConfig.getLivePriceLabelBullTextColor()));
        chartConfig.setLivePriceLabelBearTextColor(parseColor(properties.getProperty("chart.livePriceLabel.bearTextColor"), chartConfig.getLivePriceLabelBearTextColor()));
        chartConfig.setLivePriceLabelFontSize(Integer.parseInt(properties.getProperty("chart.livePriceLabel.fontSize", "12")));
        chartConfig.setCrosshairLabelBackgroundColor(parseColor(properties.getProperty("crosshair.label.backgroundColor"), chartConfig.getCrosshairLabelBackgroundColor()));
        chartConfig.setCrosshairLabelForegroundColor(parseColor(properties.getProperty("crosshair.label.foregroundColor"), chartConfig.getCrosshairLabelForegroundColor()));
        chartConfig.setDaySeparatorsEnabled(Boolean.parseBoolean(properties.getProperty("chart.daySeparators.enabled", "true")));
        chartConfig.setDaySeparatorStartTime(LocalTime.parse(properties.getProperty("chart.daySeparators.startTimeUTC", "00:00")));
        chartConfig.setDaySeparatorColor(parseColor(properties.getProperty("chart.daySeparators.color"), chartConfig.getDaySeparatorColor()));
        chartConfig.setCrosshairFps(ChartConfig.CrosshairFPS.valueOf(properties.getProperty("chart.crosshairFps", "FPS_45")));
        chartConfig.setPriceAxisLabelsEnabled(Boolean.parseBoolean(properties.getProperty("chart.priceAxisLabels.enabled", "true")));
        chartConfig.setPriceAxisLabelsShowOrders(Boolean.parseBoolean(properties.getProperty("chart.priceAxisLabels.showOrders", "true")));
        chartConfig.setPriceAxisLabelsShowDrawings(Boolean.parseBoolean(properties.getProperty("chart.priceAxisLabels.showDrawings", "true")));
        chartConfig.setPriceAxisLabelsShowFibonaccis(Boolean.parseBoolean(properties.getProperty("chart.priceAxisLabels.showFibonaccis", "false")));
        chartConfig.setFootprintCandleOpacity(Integer.parseInt(properties.getProperty("footprint.candleOpacity", "20")));

        // --- Volume Profile Config ---
        volumeProfileConfig.setVrvpVisible(Boolean.parseBoolean(properties.getProperty("chart.vrvpVisible", "false")));
        volumeProfileConfig.setSvpVisible(Boolean.parseBoolean(properties.getProperty("chart.svpVisible", "false")));
        volumeProfileConfig.setVrvpUpVolumeColor(parseColor(properties.getProperty("vrvp.color.upVolume"), volumeProfileConfig.getVrvpUpVolumeColor()));
        volumeProfileConfig.setVrvpDownVolumeColor(parseColor(properties.getProperty("vrvp.color.downVolume"), volumeProfileConfig.getVrvpDownVolumeColor()));
        volumeProfileConfig.setVrvpPocColor(parseColor(properties.getProperty("vrvp.color.poc"), volumeProfileConfig.getVrvpPocColor()));
        volumeProfileConfig.setVrvpValueAreaUpColor(parseColor(properties.getProperty("vrvp.color.valueAreaUp"), volumeProfileConfig.getVrvpValueAreaUpColor()));
        volumeProfileConfig.setVrvpValueAreaDownColor(parseColor(properties.getProperty("vrvp.color.valueAreaDown"), volumeProfileConfig.getVrvpValueAreaDownColor()));
        volumeProfileConfig.setVrvpRowHeight(Integer.parseInt(properties.getProperty("vrvp.rowHeight", "1")));
        volumeProfileConfig.setVrvpPocLineStroke(new BasicStroke(Float.parseFloat(properties.getProperty("vrvp.pocLineWidth", "2.0"))));

        // --- Drawing Config ---
        drawingConfig.setDrawingToolbarPosition(DrawingConfig.ToolbarPosition.valueOf(properties.getProperty("toolbar.position", "LEFT")));
        drawingConfig.setSnapRadius(Integer.parseInt(properties.getProperty("chart.snapRadius", "10")));
        drawingConfig.setDrawingHitThreshold(Integer.parseInt(properties.getProperty("drawing.hitThreshold", "8")));
        drawingConfig.setDrawingHandleSize(Integer.parseInt(properties.getProperty("drawing.handleSize", "8")));
        loadComplexProperty("tool.templates.v2.json", new TypeReference<Map<String, List<DrawingConfig.DrawingToolTemplate>>>() {}, drawingConfig::setToolTemplates);
        loadComplexProperty("tool.activeTemplates.v2.json", new TypeReference<Map<String, UUID>>() {}, drawingConfig::setActiveToolTemplates);
        
        // --- Trading Config ---
        tradingConfig.setAutoSaveInterval(Integer.parseInt(properties.getProperty("replay.autoSaveInterval", "100")));
        tradingConfig.setCommissionPerTrade(new BigDecimal(properties.getProperty("simulation.commissionPerTrade", "0.0")));
        tradingConfig.setSimulatedSpreadPoints(new BigDecimal(properties.getProperty("simulation.simulatedSpreadPoints", "0.0")));
        tradingConfig.setAutoJournalOnTradeClose(Boolean.parseBoolean(properties.getProperty("simulation.autoJournalOnTradeClose", "true")));
        tradingConfig.setSessionHighlightingEnabled(Boolean.parseBoolean(properties.getProperty("chart.sessionHighlighting.enabled", "true")));
        tradingConfig.setTradeCandleRetentionMonths(Integer.parseInt(properties.getProperty("trading.candleRetentionMonths", "12")));
        tradingConfig.setTradeReplayAvailableTimeframes(new ArrayList<>(Arrays.asList(properties.getProperty("tradeReplay.availableTimeframes", "1m,5m,15m").split(","))));
        String favSymbols = properties.getProperty("favorite.symbols", "");
        if (!favSymbols.isEmpty()) {
            tradingConfig.setFavoriteSymbols(new ArrayList<>(Arrays.asList(favSymbols.split(","))));
        }
        for (TradingConfig.TradingSession session : TradingConfig.TradingSession.values()) {
            tradingConfig.getSessionEnabled().put(session, Boolean.parseBoolean(properties.getProperty("session." + session.name() + ".enabled", "true")));
        }
        
        // --- Discipline Coach Config ---
        disciplineCoachConfig.setDisciplineCoachEnabled(Boolean.parseBoolean(properties.getProperty("discipline.enabled", "true")));
        disciplineCoachConfig.setOptimalTradeCountOverride(Integer.parseInt(properties.getProperty("discipline.tradeCountOverride", "-1")));
        disciplineCoachConfig.setOvertrainingNudgeEnabled(Boolean.parseBoolean(properties.getProperty("discipline.nudge.overtraining", "true")));
        disciplineCoachConfig.setFatigueNudgeEnabled(Boolean.parseBoolean(properties.getProperty("discipline.nudge.fatigue", "true")));
        disciplineCoachConfig.setWinStreakNudgeEnabled(Boolean.parseBoolean(properties.getProperty("discipline.nudge.winStreak", "true")));
        disciplineCoachConfig.setLossStreakNudgeEnabled(Boolean.parseBoolean(properties.getProperty("discipline.nudge.lossStreak", "true")));
        disciplineCoachConfig.setFastForwardTime(LocalTime.parse(properties.getProperty("discipline.nudge.fastForwardTime", "00:00")));
        disciplineCoachConfig.setShowPeakHoursLines(Boolean.parseBoolean(properties.getProperty("discipline.showPeakHoursLines", "true")));
        disciplineCoachConfig.setPeakHoursDisplayStyle(DisciplineCoachConfig.PeakHoursDisplayStyle.valueOf(properties.getProperty("discipline.peakHours.style", "INDICATOR_LINES")));
        disciplineCoachConfig.setPeakHoursColorShade(parseColor(properties.getProperty("discipline.peakHours.color.shade"), new Color(76, 175, 80, 20)));
        disciplineCoachConfig.setPeakHoursColorStart(parseColor(properties.getProperty("discipline.peakHours.color.start"), new Color(76, 175, 80, 150)));
        disciplineCoachConfig.setPeakHoursColorEnd(parseColor(properties.getProperty("discipline.peakHours.color.end"), new Color(0, 150, 136, 150)));
        disciplineCoachConfig.setPeakHoursBottomBarHeight(Integer.parseInt(properties.getProperty("discipline.peakHours.bottomBarHeight", "4")));
        String overrideHoursStr = properties.getProperty("discipline.peakHoursOverride", "");
        if (!overrideHoursStr.isEmpty()) {
            disciplineCoachConfig.setPeakPerformanceHoursOverride(Arrays.stream(overrideHoursStr.split(",")).map(String::trim).map(Integer::parseInt).collect(Collectors.toList()));
        }
        String preferredSessionsStr = properties.getProperty("discipline.preferredTradingSessions", "LONDON,NEW_YORK");
        if (!preferredSessionsStr.isEmpty()) {
             disciplineCoachConfig.setPreferredTradingSessions(Arrays.stream(preferredSessionsStr.split(",")).map(String::trim).map(TradingConfig.TradingSession::valueOf).collect(Collectors.toList()));
        }

        saveSettings(); // Ensure any new default properties are written to the file
    }

    private <T> void loadComplexProperty(String key, TypeReference<T> type, java.util.function.Consumer<T> setter) {
        String json = properties.getProperty(key);
        if (json != null && !json.isBlank()) {
            try {
                setter.accept(jsonMapper.readValue(json, type));
            } catch (IOException e) {
                logger.error("Failed to parse complex property '{}' from settings.", key, e);
            }
        }
    }

    private synchronized void saveSettings() {
        // General
        properties.setProperty("app.uiScale", String.valueOf(generalConfig.getUiScale()));
        properties.setProperty("app.theme", generalConfig.getCurrentTheme().name());
        properties.setProperty("chart.zoneId", generalConfig.getDisplayZoneId().getId());
        properties.setProperty("dashboard.imageSource", generalConfig.getImageSource().name());

        // Chart
        properties.setProperty("chart.type", chartConfig.getCurrentChartType().name());
        properties.setProperty("chart.bullColor", formatColor(chartConfig.getBullColor()));
        properties.setProperty("chart.bearColor", formatColor(chartConfig.getBearColor()));
        properties.setProperty("chart.gridColor", formatColor(chartConfig.getGridColor()));
        properties.setProperty("chart.backgroundColor", formatColor(chartConfig.getChartBackground()));
        properties.setProperty("chart.crosshairColor", formatColor(chartConfig.getCrosshairColor()));
        properties.setProperty("chart.axisTextColor", formatColor(chartConfig.getAxisTextColor()));
        properties.setProperty("chart.livePriceLabel.bullTextColor", formatColor(chartConfig.getLivePriceLabelBullTextColor()));
        properties.setProperty("chart.livePriceLabel.bearTextColor", formatColor(chartConfig.getLivePriceLabelBearTextColor()));
        properties.setProperty("chart.livePriceLabel.fontSize", String.valueOf(chartConfig.getLivePriceLabelFontSize()));
        properties.setProperty("crosshair.label.backgroundColor", formatColor(chartConfig.getCrosshairLabelBackgroundColor()));
        properties.setProperty("crosshair.label.foregroundColor", formatColor(chartConfig.getCrosshairLabelForegroundColor()));
        properties.setProperty("chart.daySeparators.enabled", String.valueOf(chartConfig.isDaySeparatorsEnabled()));
        properties.setProperty("chart.daySeparators.startTimeUTC", chartConfig.getDaySeparatorStartTime().toString());
        properties.setProperty("chart.daySeparators.color", formatColor(chartConfig.getDaySeparatorColor()));
        properties.setProperty("chart.crosshairFps", chartConfig.getCrosshairFps().name());
        properties.setProperty("chart.priceAxisLabels.enabled", String.valueOf(chartConfig.isPriceAxisLabelsEnabled()));
        properties.setProperty("chart.priceAxisLabels.showOrders", String.valueOf(chartConfig.isPriceAxisLabelsShowOrders()));
        properties.setProperty("chart.priceAxisLabels.showDrawings", String.valueOf(chartConfig.isPriceAxisLabelsShowDrawings()));
        properties.setProperty("chart.priceAxisLabels.showFibonaccis", String.valueOf(chartConfig.isPriceAxisLabelsShowFibonaccis()));
        properties.setProperty("footprint.candleOpacity", String.valueOf(chartConfig.getFootprintCandleOpacity()));

        // Volume Profile
        properties.setProperty("chart.vrvpVisible", String.valueOf(volumeProfileConfig.isVrvpVisible()));
        properties.setProperty("chart.svpVisible", String.valueOf(volumeProfileConfig.isSvpVisible()));
        properties.setProperty("vrvp.color.upVolume", formatColor(volumeProfileConfig.getVrvpUpVolumeColor()));
        properties.setProperty("vrvp.color.downVolume", formatColor(volumeProfileConfig.getVrvpDownVolumeColor()));
        properties.setProperty("vrvp.color.poc", formatColor(volumeProfileConfig.getVrvpPocColor()));
        properties.setProperty("vrvp.color.valueAreaUp", formatColor(volumeProfileConfig.getVrvpValueAreaUpColor()));
        properties.setProperty("vrvp.color.valueAreaDown", formatColor(volumeProfileConfig.getVrvpValueAreaDownColor()));
        properties.setProperty("vrvp.rowHeight", String.valueOf(volumeProfileConfig.getVrvpRowHeight()));
        properties.setProperty("vrvp.pocLineWidth", String.valueOf(volumeProfileConfig.getVrvpPocLineStroke().getLineWidth()));

        // Drawing
        properties.setProperty("toolbar.position", drawingConfig.getDrawingToolbarPosition().name());
        properties.setProperty("chart.snapRadius", String.valueOf(drawingConfig.getSnapRadius()));
        properties.setProperty("drawing.hitThreshold", String.valueOf(drawingConfig.getDrawingHitThreshold()));
        properties.setProperty("drawing.handleSize", String.valueOf(drawingConfig.getDrawingHandleSize()));
        saveComplexProperty("tool.templates.v2.json", drawingConfig.getToolTemplates());
        saveComplexProperty("tool.activeTemplates.v2.json", drawingConfig.getActiveToolTemplates());

        // Trading
        properties.setProperty("replay.autoSaveInterval", String.valueOf(tradingConfig.getAutoSaveInterval()));
        properties.setProperty("simulation.commissionPerTrade", tradingConfig.getCommissionPerTrade().toPlainString());
        properties.setProperty("simulation.simulatedSpreadPoints", tradingConfig.getSimulatedSpreadPoints().toPlainString());
        properties.setProperty("simulation.autoJournalOnTradeClose", String.valueOf(tradingConfig.isAutoJournalOnTradeClose()));
        properties.setProperty("chart.sessionHighlighting.enabled", String.valueOf(tradingConfig.isSessionHighlightingEnabled()));
        properties.setProperty("trading.candleRetentionMonths", String.valueOf(tradingConfig.getTradeCandleRetentionMonths()));
        properties.setProperty("tradeReplay.availableTimeframes", String.join(",", tradingConfig.getTradeReplayAvailableTimeframes()));
        properties.setProperty("favorite.symbols", String.join(",", tradingConfig.getFavoriteSymbols()));
        for (TradingConfig.TradingSession session : TradingConfig.TradingSession.values()) {
            properties.setProperty("session." + session.name() + ".enabled", String.valueOf(tradingConfig.getSessionEnabled().get(session)));
        }

        // Discipline Coach
        properties.setProperty("discipline.enabled", String.valueOf(disciplineCoachConfig.isDisciplineCoachEnabled()));
        properties.setProperty("discipline.tradeCountOverride", String.valueOf(disciplineCoachConfig.getOptimalTradeCountOverride()));
        properties.setProperty("discipline.nudge.overtraining", String.valueOf(disciplineCoachConfig.isOvertrainingNudgeEnabled()));
        properties.setProperty("discipline.nudge.fatigue", String.valueOf(disciplineCoachConfig.isFatigueNudgeEnabled()));
        properties.setProperty("discipline.nudge.winStreak", String.valueOf(disciplineCoachConfig.isWinStreakNudgeEnabled()));
        properties.setProperty("discipline.nudge.lossStreak", String.valueOf(disciplineCoachConfig.isLossStreakNudgeEnabled()));
        properties.setProperty("discipline.nudge.fastForwardTime", disciplineCoachConfig.getFastForwardTime().toString());
        properties.setProperty("discipline.showPeakHoursLines", String.valueOf(disciplineCoachConfig.isShowPeakHoursLines()));
        properties.setProperty("discipline.peakHours.style", disciplineCoachConfig.getPeakHoursDisplayStyle().name());
        properties.setProperty("discipline.peakHours.color.shade", formatColor(disciplineCoachConfig.getPeakHoursColorShade()));
        properties.setProperty("discipline.peakHours.color.start", formatColor(disciplineCoachConfig.getPeakHoursColorStart()));
        properties.setProperty("discipline.peakHours.color.end", formatColor(disciplineCoachConfig.getPeakHoursColorEnd()));
        properties.setProperty("discipline.peakHours.bottomBarHeight", String.valueOf(disciplineCoachConfig.getPeakHoursBottomBarHeight()));
        properties.setProperty("discipline.peakHoursOverride", disciplineCoachConfig.getPeakPerformanceHoursOverride().stream().map(String::valueOf).collect(Collectors.joining(",")));
        properties.setProperty("discipline.preferredTradingSessions", disciplineCoachConfig.getPreferredTradingSessions().stream().map(Enum::name).collect(Collectors.joining(",")));

        // Persist to file
        Optional<Path> configPathOpt = AppDataManager.getAppConfigPath();
        if (configPathOpt.isPresent()) {
            try (FileOutputStream out = new FileOutputStream(configPathOpt.get().toFile())) {
                properties.store(out, "Eco Chart Pro Application State");
            } catch (IOException e) {
                logger.error("Failed to save settings.", e);
            }
        }
    }

    private void saveComplexProperty(String key, Object value) {
        try {
            properties.setProperty(key, jsonMapper.writeValueAsString(value));
        } catch (IOException e) {
            logger.error("Failed to serialize complex property '{}' to settings.", key, e);
        }
    }

    private String formatColor(Color c) {
        if (c == null) return "0,0,0,255";
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

    // --- The rest of the getters and setters are the same as before ---
    // --- They just delegate to the internal config objects ---
    
    // Property Change Listeners
    public void addPropertyChangeListener(PropertyChangeListener listener) { pcs.addPropertyChangeListener(listener); }
    public void removePropertyChangeListener(PropertyChangeListener listener) { pcs.removePropertyChangeListener(listener); }

    // GeneralConfig
    public Theme getCurrentTheme() { return generalConfig.getCurrentTheme(); }
    public void setCurrentTheme(Theme newTheme) {
        if (newTheme != null && this.generalConfig.getCurrentTheme() != newTheme) {
            Theme oldVal = this.generalConfig.getCurrentTheme();
            this.generalConfig.setCurrentTheme(newTheme);
            ThemeManager.applyTheme(newTheme);
            chartConfig.initializeDefaultColors(newTheme == Theme.DARK);
            volumeProfileConfig.initializeDefaultColors(newTheme == Theme.DARK);
            saveSettings();
            pcs.firePropertyChange("themeChanged", oldVal, newTheme);
            pcs.firePropertyChange("chartColorsChanged", null, null);
        }
    }
    public float getUiScale() { return generalConfig.getUiScale(); }
    public void setUiScale(float newScale) {
        float oldVal = this.generalConfig.getUiScale();
        if (oldVal != newScale) {
            this.generalConfig.setUiScale(newScale);
            saveSettings();
            pcs.firePropertyChange("uiScaleChanged", oldVal, newScale);
        }
    }
    public GeneralConfig.ImageSource getImageSource() { return generalConfig.getImageSource(); }
    public void setImageSource(GeneralConfig.ImageSource newSource) {
        GeneralConfig.ImageSource oldSource = this.generalConfig.getImageSource();
        if (oldSource != newSource) {
            this.generalConfig.setImageSource(newSource);
            saveSettings();
            pcs.firePropertyChange("imageSource", oldSource, newSource);
        }
    }
    public ZoneId getDisplayZoneId() { return generalConfig.getDisplayZoneId(); }
    public void setDisplayZoneId(ZoneId newZoneId) {
        if (newZoneId != null && !this.generalConfig.getDisplayZoneId().equals(newZoneId)) {
            ZoneId oldZoneId = this.generalConfig.getDisplayZoneId();
            this.generalConfig.setDisplayZoneId(newZoneId);
            saveSettings();
            pcs.firePropertyChange("displayZoneId", oldZoneId, newZoneId);
        }
    }

    // ChartConfig
    public ChartType getCurrentChartType() { return chartConfig.getCurrentChartType(); }
    public void setCurrentChartType(ChartType type) {
        ChartType oldVal = this.chartConfig.getCurrentChartType();
        if (oldVal != type) {
            this.chartConfig.setCurrentChartType(type);
            saveSettings();
            pcs.firePropertyChange("chartTypeChanged", oldVal, type);
        }
    }
    public Color getBullColor() { return chartConfig.getBullColor(); }
    public void setBullColor(Color c) { this.chartConfig.setBullColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getBearColor() { return chartConfig.getBearColor(); }
    public void setBearColor(Color c) { this.chartConfig.setBearColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getGridColor() { return chartConfig.getGridColor(); }
    public void setGridColor(Color c) { this.chartConfig.setGridColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getChartBackground() { return chartConfig.getChartBackground(); }
    public void setChartBackground(Color c) { this.chartConfig.setChartBackground(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getCrosshairColor() { return chartConfig.getCrosshairColor(); }
    public void setCrosshairColor(Color c) { this.chartConfig.setCrosshairColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getAxisTextColor() { return chartConfig.getAxisTextColor(); }
    public void setAxisTextColor(Color c) { this.chartConfig.setAxisTextColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getLivePriceLabelBullTextColor() { return chartConfig.getLivePriceLabelBullTextColor(); }
    public void setLivePriceLabelBullTextColor(Color c) { this.chartConfig.setLivePriceLabelBullTextColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getLivePriceLabelBearTextColor() { return chartConfig.getLivePriceLabelBearTextColor(); }
    public void setLivePriceLabelBearTextColor(Color c) { this.chartConfig.setLivePriceLabelBearTextColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public int getLivePriceLabelFontSize() { return chartConfig.getLivePriceLabelFontSize(); }
    public void setLivePriceLabelFontSize(int size) {
        int oldVal = this.chartConfig.getLivePriceLabelFontSize();
        if (oldVal != size) {
            this.chartConfig.setLivePriceLabelFontSize(size);
            saveSettings();
            pcs.firePropertyChange("livePriceLabelFontSizeChanged", oldVal, size);
        }
    }
    public Color getCrosshairLabelBackgroundColor() { return chartConfig.getCrosshairLabelBackgroundColor(); }
    public void setCrosshairLabelBackgroundColor(Color c) { this.chartConfig.setCrosshairLabelBackgroundColor(c); saveSettings(); pcs.firePropertyChange("crosshairLabelColorChanged", null, null); }
    public Color getCrosshairLabelForegroundColor() { return chartConfig.getCrosshairLabelForegroundColor(); }
    public void setCrosshairLabelForegroundColor(Color c) { this.chartConfig.setCrosshairLabelForegroundColor(c); saveSettings(); pcs.firePropertyChange("crosshairLabelColorChanged", null, null); }
    public boolean isDaySeparatorsEnabled() { return chartConfig.isDaySeparatorsEnabled(); }
    public void setDaySeparatorsEnabled(boolean enabled) {
        boolean oldVal = this.chartConfig.isDaySeparatorsEnabled();
        this.chartConfig.setDaySeparatorsEnabled(enabled);
        saveSettings();
        pcs.firePropertyChange("daySeparatorsEnabledChanged", oldVal, enabled);
    }
    public LocalTime getDaySeparatorStartTime() { return chartConfig.getDaySeparatorStartTime(); }
    public void setDaySeparatorStartTime(LocalTime time) {
        LocalTime oldVal = this.chartConfig.getDaySeparatorStartTime();
        if (!oldVal.equals(time)) {
            this.chartConfig.setDaySeparatorStartTime(time);
            saveSettings();
            pcs.firePropertyChange("daySeparatorsEnabledChanged", oldVal, time);
        }
    }
    public Color getDaySeparatorColor() { return chartConfig.getDaySeparatorColor(); }
    public void setDaySeparatorColor(Color color) {
        if (!this.chartConfig.getDaySeparatorColor().equals(color)) {
            this.chartConfig.setDaySeparatorColor(color);
            saveSettings();
            pcs.firePropertyChange("chartColorsChanged", null, null);
        }
    }
    public ChartConfig.CrosshairFPS getCrosshairFps() { return chartConfig.getCrosshairFps(); }
    public void setCrosshairFps(ChartConfig.CrosshairFPS fps) {
        ChartConfig.CrosshairFPS oldVal = this.chartConfig.getCrosshairFps();
        this.chartConfig.setCrosshairFps(fps);
        saveSettings();
        pcs.firePropertyChange("crosshairFpsChanged", oldVal, fps);
    }
    public boolean isPriceAxisLabelsEnabled() { return chartConfig.isPriceAxisLabelsEnabled(); }
    public void setPriceAxisLabelsEnabled(boolean enabled) {
        boolean oldVal = this.chartConfig.isPriceAxisLabelsEnabled();
        this.chartConfig.setPriceAxisLabelsEnabled(enabled);
        saveSettings();
        pcs.firePropertyChange("priceAxisLabelsEnabledChanged", oldVal, enabled);
    }
    public boolean isPriceAxisLabelsShowOrders() { return chartConfig.isPriceAxisLabelsShowOrders(); }
    public void setPriceAxisLabelsShowOrders(boolean show) {
        if (this.chartConfig.isPriceAxisLabelsShowOrders() != show) {
            this.chartConfig.setPriceAxisLabelsShowOrders(show);
            saveSettings();
            pcs.firePropertyChange("priceAxisLabelsVisibilityChanged", !show, show);
        }
    }
    public boolean isPriceAxisLabelsShowDrawings() { return chartConfig.isPriceAxisLabelsShowDrawings(); }
    public void setPriceAxisLabelsShowDrawings(boolean show) {
        if (this.chartConfig.isPriceAxisLabelsShowDrawings() != show) {
            this.chartConfig.setPriceAxisLabelsShowDrawings(show);
            saveSettings();
            pcs.firePropertyChange("priceAxisLabelsVisibilityChanged", !show, show);
        }
    }
    public boolean isPriceAxisLabelsShowFibonaccis() { return chartConfig.isPriceAxisLabelsShowFibonaccis(); }
    public void setPriceAxisLabelsShowFibonaccis(boolean show) {
        if (this.chartConfig.isPriceAxisLabelsShowFibonaccis() != show) {
            this.chartConfig.setPriceAxisLabelsShowFibonaccis(show);
            saveSettings();
            pcs.firePropertyChange("priceAxisLabelsVisibilityChanged", !show, show);
        }
    }
    public int getFootprintCandleOpacity() { return chartConfig.getFootprintCandleOpacity(); }
    public void setFootprintCandleOpacity(int opacity) {
        if (this.chartConfig.getFootprintCandleOpacity() != opacity) {
            this.chartConfig.setFootprintCandleOpacity(opacity);
            saveSettings();
            pcs.firePropertyChange("chartColorsChanged", null, null);
        }
    }

    // VolumeProfileConfig
    public boolean isVrvpVisible() { return volumeProfileConfig.isVrvpVisible(); }
    public void setVrvpVisible(boolean visible) {
        if (this.volumeProfileConfig.isVrvpVisible() != visible) {
            this.volumeProfileConfig.setVrvpVisible(visible);
            saveSettings();
            pcs.firePropertyChange("volumeProfileVisibilityChanged", !visible, visible);
        }
    }
    public boolean isSvpVisible() { return volumeProfileConfig.isSvpVisible(); }
    public void setSvpVisible(boolean visible) {
        if (this.volumeProfileConfig.isSvpVisible() != visible) {
            this.volumeProfileConfig.setSvpVisible(visible);
            saveSettings();
            pcs.firePropertyChange("volumeProfileVisibilityChanged", !visible, visible);
        }
    }
    public Color getVrvpUpVolumeColor() { return volumeProfileConfig.getVrvpUpVolumeColor(); }
    public void setVrvpUpVolumeColor(Color c) { this.volumeProfileConfig.setVrvpUpVolumeColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getVrvpDownVolumeColor() { return volumeProfileConfig.getVrvpDownVolumeColor(); }
    public void setVrvpDownVolumeColor(Color c) { this.volumeProfileConfig.setVrvpDownVolumeColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getVrvpPocColor() { return volumeProfileConfig.getVrvpPocColor(); }
    public void setVrvpPocColor(Color c) { this.volumeProfileConfig.setVrvpPocColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getVrvpValueAreaUpColor() { return volumeProfileConfig.getVrvpValueAreaUpColor(); }
    public void setVrvpValueAreaUpColor(Color c) { this.volumeProfileConfig.setVrvpValueAreaUpColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public Color getVrvpValueAreaDownColor() { return volumeProfileConfig.getVrvpValueAreaDownColor(); }
    public void setVrvpValueAreaDownColor(Color c) { this.volumeProfileConfig.setVrvpValueAreaDownColor(c); saveSettings(); pcs.firePropertyChange("chartColorsChanged", null, null); }
    public int getVrvpRowHeight() { return volumeProfileConfig.getVrvpRowHeight(); }
    public void setVrvpRowHeight(int height) {
        if (this.volumeProfileConfig.getVrvpRowHeight() != height) {
            this.volumeProfileConfig.setVrvpRowHeight(height);
            saveSettings();
            pcs.firePropertyChange("volumeProfileVisibilityChanged", null, null);
        }
    }
    public BasicStroke getVrvpPocLineStroke() { return volumeProfileConfig.getVrvpPocLineStroke(); }
    public void setVrvpPocLineStroke(BasicStroke stroke) {
        if (!this.volumeProfileConfig.getVrvpPocLineStroke().equals(stroke)) {
            this.volumeProfileConfig.setVrvpPocLineStroke(stroke);
            saveSettings();
            pcs.firePropertyChange("volumeProfileVisibilityChanged", null, null);
        }
    }

    // DrawingConfig
    public DrawingConfig.ToolbarPosition getDrawingToolbarPosition() { return drawingConfig.getDrawingToolbarPosition(); }
    public void setDrawingToolbarPosition(DrawingConfig.ToolbarPosition pos) {
        DrawingConfig.ToolbarPosition oldVal = this.drawingConfig.getDrawingToolbarPosition();
        this.drawingConfig.setDrawingToolbarPosition(pos);
        saveSettings();
        pcs.firePropertyChange("drawingToolbarPositionChanged", oldVal, pos);
    }
    public int getSnapRadius() { return drawingConfig.getSnapRadius(); }
    public void setSnapRadius(int radius) {
        int oldVal = this.drawingConfig.getSnapRadius();
        this.drawingConfig.setSnapRadius(radius);
        saveSettings();
        pcs.firePropertyChange("snapRadiusChanged", oldVal, radius);
    }
    public int getDrawingHitThreshold() { return drawingConfig.getDrawingHitThreshold(); }
    public void setDrawingHitThreshold(int threshold) {
        int oldVal = this.drawingConfig.getDrawingHitThreshold();
        this.drawingConfig.setDrawingHitThreshold(threshold);
        saveSettings();
        pcs.firePropertyChange("drawingHitThresholdChanged", oldVal, threshold);
    }
    public int getDrawingHandleSize() { return drawingConfig.getDrawingHandleSize(); }
    public void setDrawingHandleSize(int size) {
        int oldVal = this.drawingConfig.getDrawingHandleSize();
        this.drawingConfig.setDrawingHandleSize(size);
        saveSettings();
        pcs.firePropertyChange("drawingHandleSizeChanged", oldVal, size);
    }
    public List<DrawingConfig.DrawingToolTemplate> getTemplatesForTool(String toolName) { return drawingConfig.getTemplatesForTool(toolName); }
    public UUID getActiveTemplateId(String toolName) { return drawingConfig.getActiveTemplateId(toolName); }
    public DrawingConfig.DrawingToolTemplate getActiveTemplateForTool(String toolName) { return drawingConfig.getActiveTemplateForTool(toolName); }
    public void addTemplate(String toolName, DrawingConfig.DrawingToolTemplate template) { drawingConfig.addTemplate(toolName, template); saveSettings(); pcs.firePropertyChange("toolDefaultsChanged", null, toolName); }
    public void updateTemplate(String toolName, DrawingConfig.DrawingToolTemplate template) { drawingConfig.updateTemplate(toolName, template); saveSettings(); pcs.firePropertyChange("toolDefaultsChanged", null, toolName); }
    public void deleteTemplate(String toolName, UUID templateId) { drawingConfig.deleteTemplate(toolName, templateId); saveSettings(); pcs.firePropertyChange("toolDefaultsChanged", null, toolName); }
    public void setActiveTemplate(String toolName, UUID templateId) { drawingConfig.setActiveTemplate(toolName, templateId); saveSettings(); pcs.firePropertyChange("toolDefaultsChanged", null, toolName); }
    public Map<Double, FibLevelProperties> getFibRetracementDefaultLevels() { return drawingConfig.getFibRetracementDefaultLevels(); }
    public Map<Double, FibLevelProperties> getFibExtensionDefaultLevels() { return drawingConfig.getFibExtensionDefaultLevels(); }
    public Font getToolDefaultFont(String toolName, Font fallback) { return drawingConfig.getToolDefaultFont(toolName, fallback); }
    public TextProperties getToolDefaultTextProperties(String toolName, TextProperties fallback) { return drawingConfig.getToolDefaultTextProperties(toolName, fallback); }

    // TradingConfig
    public int getAutoSaveInterval() { return tradingConfig.getAutoSaveInterval(); }
    public void setAutoSaveInterval(int interval) {
        int oldVal = this.tradingConfig.getAutoSaveInterval();
        this.tradingConfig.setAutoSaveInterval(interval);
        saveSettings();
        pcs.firePropertyChange("autoSaveIntervalChanged", oldVal, interval);
    }
    public BigDecimal getCommissionPerTrade() { return tradingConfig.getCommissionPerTrade(); }
    public void setCommissionPerTrade(BigDecimal commission) {
        if (this.tradingConfig.getCommissionPerTrade() == null || this.tradingConfig.getCommissionPerTrade().compareTo(commission) != 0) {
            this.tradingConfig.setCommissionPerTrade(commission);
            saveSettings();
            pcs.firePropertyChange("simulationSettingsChanged", null, null);
        }
    }
    public BigDecimal getSimulatedSpreadPoints() { return tradingConfig.getSimulatedSpreadPoints(); }
    public void setSimulatedSpreadPoints(BigDecimal spread) {
        if (this.tradingConfig.getSimulatedSpreadPoints() == null || this.tradingConfig.getSimulatedSpreadPoints().compareTo(spread) != 0) {
            this.tradingConfig.setSimulatedSpreadPoints(spread);
            saveSettings();
            pcs.firePropertyChange("simulationSettingsChanged", null, null);
        }
    }
    public boolean isAutoJournalOnTradeClose() { return tradingConfig.isAutoJournalOnTradeClose(); }
    public void setAutoJournalOnTradeClose(boolean enabled) {
        if (this.tradingConfig.isAutoJournalOnTradeClose() != enabled) {
            this.tradingConfig.setAutoJournalOnTradeClose(enabled);
            saveSettings();
            pcs.firePropertyChange("autoJournalOnTradeCloseChanged", !enabled, enabled);
        }
    }
    public boolean isSessionHighlightingEnabled() { return tradingConfig.isSessionHighlightingEnabled(); }
    public void setSessionHighlightingEnabled(boolean enabled) {
        if (this.tradingConfig.isSessionHighlightingEnabled() != enabled) {
            boolean oldVal = this.tradingConfig.isSessionHighlightingEnabled();
            this.tradingConfig.setSessionHighlightingEnabled(enabled);
            saveSettings();
            pcs.firePropertyChange("sessionHighlightingChanged", oldVal, enabled);
        }
    }
    public int getTradeCandleRetentionMonths() { return tradingConfig.getTradeCandleRetentionMonths(); }
    public void setTradeCandleRetentionMonths(int months) {
        if (this.tradingConfig.getTradeCandleRetentionMonths() != months) {
            this.tradingConfig.setTradeCandleRetentionMonths(months);
            saveSettings();
        }
    }
    public List<String> getTradeReplayAvailableTimeframes() { return tradingConfig.getTradeReplayAvailableTimeframes(); }
    public void setTradeReplayAvailableTimeframes(List<String> timeframes) {
        if (!this.tradingConfig.getTradeReplayAvailableTimeframes().equals(timeframes)) {
            List<String> oldVal = this.tradingConfig.getTradeReplayAvailableTimeframes();
            this.tradingConfig.setTradeReplayAvailableTimeframes(timeframes);
            saveSettings();
            pcs.firePropertyChange("tradeReplayTimeframesChanged", oldVal, timeframes);
        }
    }
    public List<String> getFavoriteSymbols() { return tradingConfig.getFavoriteSymbols(); }
    public boolean isFavoriteSymbol(String symbol) { return tradingConfig.isFavoriteSymbol(symbol); }
    public void addFavoriteSymbol(String symbol) { if (tradingConfig.addFavoriteSymbol(symbol)) { saveSettings(); pcs.firePropertyChange("favoritesChanged", null, null); } }
    public void removeFavoriteSymbol(String symbol) { if (tradingConfig.removeFavoriteSymbol(symbol)) { saveSettings(); pcs.firePropertyChange("favoritesChanged", null, null); } }
    public Map<TradingConfig.TradingSession, Boolean> getSessionEnabled() { return tradingConfig.getSessionEnabled(); }
    public void setSessionEnabled(TradingConfig.TradingSession s, boolean isEnabled) { tradingConfig.getSessionEnabled().put(s, isEnabled); saveSettings(); pcs.firePropertyChange("sessionSettingsChanged", null, s); }
    public Map<TradingConfig.TradingSession, LocalTime> getSessionStartTimes() { return tradingConfig.getSessionStartTimes(); }
    public void setSessionStartTime(TradingConfig.TradingSession s, LocalTime startTime) { tradingConfig.getSessionStartTimes().put(s, startTime); saveSettings(); pcs.firePropertyChange("sessionSettingsChanged", null, s); }
    public Map<TradingConfig.TradingSession, LocalTime> getSessionEndTimes() { return tradingConfig.getSessionEndTimes(); }
    public void setSessionEndTime(TradingConfig.TradingSession s, LocalTime endTime) { tradingConfig.getSessionEndTimes().put(s, endTime); saveSettings(); pcs.firePropertyChange("sessionSettingsChanged", null, s); }
    public Map<TradingConfig.TradingSession, Color> getSessionColors() { return tradingConfig.getSessionColors(); }
    public void setSessionColor(TradingConfig.TradingSession s, Color color) { tradingConfig.getSessionColors().put(s, color); saveSettings(); pcs.firePropertyChange("sessionSettingsChanged", null, s); }

    // DisciplineCoachConfig
    public boolean isDisciplineCoachEnabled() { return disciplineCoachConfig.isDisciplineCoachEnabled(); }
    public void setDisciplineCoachEnabled(boolean enabled) {
        if (this.disciplineCoachConfig.isDisciplineCoachEnabled() != enabled) {
            this.disciplineCoachConfig.setDisciplineCoachEnabled(enabled);
            saveSettings();
            pcs.firePropertyChange("disciplineSettingsChanged", !enabled, enabled);
        }
    }
    public int getOptimalTradeCountOverride() { return disciplineCoachConfig.getOptimalTradeCountOverride(); }
    public void setOptimalTradeCountOverride(int count) {
        if (this.disciplineCoachConfig.getOptimalTradeCountOverride() != count) {
            this.disciplineCoachConfig.setOptimalTradeCountOverride(count);
            saveSettings();
            pcs.firePropertyChange("disciplineSettingsChanged", null, null);
        }
    }
    public boolean isOvertrainingNudgeEnabled() { return disciplineCoachConfig.isOvertrainingNudgeEnabled(); }
    public void setOvertrainingNudgeEnabled(boolean enabled) { if (this.disciplineCoachConfig.isOvertrainingNudgeEnabled() != enabled) { this.disciplineCoachConfig.setOvertrainingNudgeEnabled(enabled); saveSettings(); } }
    public boolean isFatigueNudgeEnabled() { return disciplineCoachConfig.isFatigueNudgeEnabled(); }
    public void setFatigueNudgeEnabled(boolean enabled) { if (this.disciplineCoachConfig.isFatigueNudgeEnabled() != enabled) { this.disciplineCoachConfig.setFatigueNudgeEnabled(enabled); saveSettings(); } }
    public boolean isWinStreakNudgeEnabled() { return disciplineCoachConfig.isWinStreakNudgeEnabled(); }
    public void setWinStreakNudgeEnabled(boolean enabled) { if (this.disciplineCoachConfig.isWinStreakNudgeEnabled() != enabled) { this.disciplineCoachConfig.setWinStreakNudgeEnabled(enabled); saveSettings(); } }
    public boolean isLossStreakNudgeEnabled() { return disciplineCoachConfig.isLossStreakNudgeEnabled(); }
    public void setLossStreakNudgeEnabled(boolean enabled) { if (this.disciplineCoachConfig.isLossStreakNudgeEnabled() != enabled) { this.disciplineCoachConfig.setLossStreakNudgeEnabled(enabled); saveSettings(); } }
    public LocalTime getFastForwardTime() { return disciplineCoachConfig.getFastForwardTime(); }
    public void setFastForwardTime(LocalTime time) { if (!this.disciplineCoachConfig.getFastForwardTime().equals(time)) { this.disciplineCoachConfig.setFastForwardTime(time); saveSettings(); } }
    public boolean isShowPeakHoursLines() { return disciplineCoachConfig.isShowPeakHoursLines(); }
    public void setShowPeakHoursLines(boolean enabled) { if (this.disciplineCoachConfig.isShowPeakHoursLines() != enabled) { this.disciplineCoachConfig.setShowPeakHoursLines(enabled); saveSettings(); pcs.firePropertyChange("peakHoursLinesVisibilityChanged", !enabled, enabled); } }
    public DisciplineCoachConfig.PeakHoursDisplayStyle getPeakHoursDisplayStyle() { return disciplineCoachConfig.getPeakHoursDisplayStyle(); }
    public void setPeakHoursDisplayStyle(DisciplineCoachConfig.PeakHoursDisplayStyle style) { if (this.disciplineCoachConfig.getPeakHoursDisplayStyle() != style) { this.disciplineCoachConfig.setPeakHoursDisplayStyle(style); saveSettings(); pcs.firePropertyChange("peakHoursSettingsChanged", null, null); } }
    public Color getPeakHoursColorShade() { return disciplineCoachConfig.getPeakHoursColorShade(); }
    public void setPeakHoursColorShade(Color color) { if (!this.disciplineCoachConfig.getPeakHoursColorShade().equals(color)) { this.disciplineCoachConfig.setPeakHoursColorShade(color); saveSettings(); pcs.firePropertyChange("peakHoursSettingsChanged", null, null); } }
    public Color getPeakHoursColorStart() { return disciplineCoachConfig.getPeakHoursColorStart(); }
    public void setPeakHoursColorStart(Color color) { if (!this.disciplineCoachConfig.getPeakHoursColorStart().equals(color)) { this.disciplineCoachConfig.setPeakHoursColorStart(color); saveSettings(); pcs.firePropertyChange("peakHoursSettingsChanged", null, null); } }
    public Color getPeakHoursColorEnd() { return disciplineCoachConfig.getPeakHoursColorEnd(); }
    public void setPeakHoursColorEnd(Color color) { if (!this.disciplineCoachConfig.getPeakHoursColorEnd().equals(color)) { this.disciplineCoachConfig.setPeakHoursColorEnd(color); saveSettings(); pcs.firePropertyChange("peakHoursSettingsChanged", null, null); } }
    public int getPeakHoursBottomBarHeight() { return disciplineCoachConfig.getPeakHoursBottomBarHeight(); }
    public void setPeakHoursBottomBarHeight(int height) { if (this.disciplineCoachConfig.getPeakHoursBottomBarHeight() != height) { this.disciplineCoachConfig.setPeakHoursBottomBarHeight(height); saveSettings(); pcs.firePropertyChange("peakHoursSettingsChanged", null, null); } }
    public List<Integer> getPeakPerformanceHoursOverride() { return disciplineCoachConfig.getPeakPerformanceHoursOverride(); }
    public void setPeakPerformanceHoursOverride(List<Integer> hours) {
        this.disciplineCoachConfig.setPeakPerformanceHoursOverride(hours);
        saveSettings();
        pcs.firePropertyChange("peakHoursOverrideChanged", null, hours);
    }
    public List<TradingConfig.TradingSession> getPreferredTradingSessions() { return disciplineCoachConfig.getPreferredTradingSessions(); }
    public void setPreferredTradingSessions(List<TradingConfig.TradingSession> sessions) {
        this.disciplineCoachConfig.setPreferredTradingSessions(sessions);
        saveSettings();
        pcs.firePropertyChange("preferredTradingSessionsChanged", null, sessions);
    }
}
package com.EcoChartPro.core.settings;

import com.EcoChartPro.core.settings.config.*;
import com.EcoChartPro.core.theme.ThemeManager;
import com.EcoChartPro.core.theme.ThemeManager.Theme;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.model.drawing.FibonacciRetracementObject.FibLevelProperties;
import com.EcoChartPro.model.drawing.TextProperties;
import com.EcoChartPro.utils.AppDataManager;
import com.EcoChartPro.utils.SessionManager;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A singleton service that manages all application settings.
 * It governs domain-specific configuration objects, handles persistence,
 * and notifies listeners of changes. This replaces the old monolithic SettingsManager.
 */
public final class SettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);
    private static volatile SettingsService instance;
    // [FIX] Mark the PropertyChangeSupport as transient to prevent serialization
    private transient final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // --- Configuration Data Objects ---
    private GeneralConfig generalConfig = new GeneralConfig();
    private ChartConfig chartConfig = new ChartConfig();
    private VolumeProfileConfig volumeProfileConfig = new VolumeProfileConfig();
    private DrawingConfig drawingConfig = new DrawingConfig();
    private TradingConfig tradingConfig = new TradingConfig();
    private DisciplineCoachConfig disciplineCoachConfig = new DisciplineCoachConfig();

    static {
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        jsonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

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
        Optional<Path> configPathOpt = AppDataManager.getConfigFilePath("settings.json");
        if (configPathOpt.isPresent() && Files.exists(configPathOpt.get())) {
            try {
                // Read the top-level service object from JSON
                SettingsService loadedService = jsonMapper.readValue(configPathOpt.get().toFile(), SettingsService.class);
                // Assign the loaded config objects to this instance
                this.generalConfig = loadedService.generalConfig;
                this.chartConfig = loadedService.chartConfig;
                this.volumeProfileConfig = loadedService.volumeProfileConfig;
                this.drawingConfig = loadedService.drawingConfig;
                this.tradingConfig = loadedService.tradingConfig;
                this.disciplineCoachConfig = loadedService.disciplineCoachConfig;
                logger.info("Successfully loaded settings from settings.json");
            } catch (IOException e) {
                logger.error("Failed to load settings from settings.json, using defaults.", e);
                initializeDefaultColors(); // Fallback to defaults
            }
        } else {
            logger.info("No settings.json file found. Initializing with default values.");
            initializeDefaultColors();
            saveSettings(); // Create the file for the first time
        }
    }

    private synchronized void saveSettings() {
        Optional<Path> configPathOpt = AppDataManager.getConfigFilePath("settings.json");
        if (configPathOpt.isPresent()) {
            try {
                jsonMapper.writeValue(configPathOpt.get().toFile(), this);
            } catch (IOException e) {
                logger.error("Failed to save settings to settings.json.", e);
            }
        }
    }
    
    private void initializeDefaultColors() {
        boolean isDark = generalConfig.getCurrentTheme() == Theme.DARK;
        chartConfig.initializeDefaultColors(isDark);
        volumeProfileConfig.initializeDefaultColors(isDark);
    }

    // --- Property Change Listeners ---
    public void addPropertyChangeListener(PropertyChangeListener listener) { pcs.addPropertyChangeListener(listener); }
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) { pcs.addPropertyChangeListener(propertyName, listener); }
    public void removePropertyChangeListener(PropertyChangeListener listener) { pcs.removePropertyChangeListener(listener); }
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) { pcs.removePropertyChangeListener(propertyName, listener); }

    // --- Delegating Getters & Setters ---

    // GeneralConfig
    public Theme getCurrentTheme() { return generalConfig.getCurrentTheme(); }
    public void setCurrentTheme(Theme newTheme) {
        if (newTheme != null && this.generalConfig.getCurrentTheme() != newTheme) {
            Theme oldVal = this.generalConfig.getCurrentTheme();
            this.generalConfig.setCurrentTheme(newTheme);
            ThemeManager.applyTheme(newTheme);
            // Update all theme-dependent colors
            initializeDefaultColors();
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
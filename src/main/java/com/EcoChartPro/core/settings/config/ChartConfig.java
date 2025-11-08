package com.EcoChartPro.core.settings.config;

import com.EcoChartPro.model.chart.ChartType;

import java.awt.*;
import java.time.LocalTime;
import java.io.Serializable;

public class ChartConfig implements Serializable {

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

    private ChartType currentChartType = ChartType.CANDLES;
    private Color bullColor;
    private Color bearColor;
    private Color gridColor;
    private Color chartBackground;
    private Color crosshairColor;
    private Color axisTextColor;
    private Color livePriceLabelBullTextColor;
    private Color livePriceLabelBearTextColor;
    private int livePriceLabelFontSize = 12;
    private Color crosshairLabelBackgroundColor;
    private Color crosshairLabelForegroundColor;
    private boolean daySeparatorsEnabled = true;
    private LocalTime daySeparatorStartTime = LocalTime.of(0, 0);
    private Color daySeparatorColor;
    private CrosshairFPS crosshairFps = CrosshairFPS.FPS_45;
    private boolean priceAxisLabelsEnabled = true;
    private boolean priceAxisLabelsShowOrders = true;
    private boolean priceAxisLabelsShowDrawings = true;
    private boolean priceAxisLabelsShowFibonaccis = false;
    private int footprintCandleOpacity = 20;

    public ChartConfig() {
        // Initialize with default dark theme colors
        initializeDefaultColors(true);
    }

    public void initializeDefaultColors(boolean isDarkTheme) {
        if (isDarkTheme) {
            this.bullColor = new Color(255, 140, 40);
            this.bearColor = Color.WHITE;
            this.gridColor = new Color(40, 42, 45);
            this.chartBackground = new Color(0x181A1B);
            this.crosshairColor = Color.LIGHT_GRAY;
            this.axisTextColor = new Color(224, 224, 224);
            this.livePriceLabelBullTextColor = Color.BLACK;
            this.livePriceLabelBearTextColor = Color.BLACK;
            this.crosshairLabelBackgroundColor = new Color(60, 63, 65);
            this.crosshairLabelForegroundColor = Color.WHITE;
            this.daySeparatorColor = new Color(192, 192, 192, 60);
        } else { // Light Theme
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
            this.daySeparatorColor = new Color(100, 100, 100, 60);
        }
    }

    // --- Getters and Setters ---

    public ChartType getCurrentChartType() { return currentChartType; }
    public void setCurrentChartType(ChartType currentChartType) { this.currentChartType = currentChartType; }

    public Color getBullColor() { return bullColor; }
    public void setBullColor(Color bullColor) { this.bullColor = bullColor; }

    public Color getBearColor() { return bearColor; }
    public void setBearColor(Color bearColor) { this.bearColor = bearColor; }

    public Color getGridColor() { return gridColor; }
    public void setGridColor(Color gridColor) { this.gridColor = gridColor; }

    public Color getChartBackground() { return chartBackground; }
    public void setChartBackground(Color chartBackground) { this.chartBackground = chartBackground; }

    public Color getCrosshairColor() { return crosshairColor; }
    public void setCrosshairColor(Color crosshairColor) { this.crosshairColor = crosshairColor; }

    public Color getAxisTextColor() { return axisTextColor; }
    public void setAxisTextColor(Color axisTextColor) { this.axisTextColor = axisTextColor; }

    public Color getLivePriceLabelBullTextColor() { return livePriceLabelBullTextColor; }
    public void setLivePriceLabelBullTextColor(Color livePriceLabelBullTextColor) { this.livePriceLabelBullTextColor = livePriceLabelBullTextColor; }

    public Color getLivePriceLabelBearTextColor() { return livePriceLabelBearTextColor; }
    public void setLivePriceLabelBearTextColor(Color livePriceLabelBearTextColor) { this.livePriceLabelBearTextColor = livePriceLabelBearTextColor; }

    public int getLivePriceLabelFontSize() { return livePriceLabelFontSize; }
    public void setLivePriceLabelFontSize(int livePriceLabelFontSize) { this.livePriceLabelFontSize = livePriceLabelFontSize; }

    public Color getCrosshairLabelBackgroundColor() { return crosshairLabelBackgroundColor; }
    public void setCrosshairLabelBackgroundColor(Color crosshairLabelBackgroundColor) { this.crosshairLabelBackgroundColor = crosshairLabelBackgroundColor; }

    public Color getCrosshairLabelForegroundColor() { return crosshairLabelForegroundColor; }
    public void setCrosshairLabelForegroundColor(Color crosshairLabelForegroundColor) { this.crosshairLabelForegroundColor = crosshairLabelForegroundColor; }

    public boolean isDaySeparatorsEnabled() { return daySeparatorsEnabled; }
    public void setDaySeparatorsEnabled(boolean daySeparatorsEnabled) { this.daySeparatorsEnabled = daySeparatorsEnabled; }

    public LocalTime getDaySeparatorStartTime() { return daySeparatorStartTime; }
    public void setDaySeparatorStartTime(LocalTime daySeparatorStartTime) { this.daySeparatorStartTime = daySeparatorStartTime; }

    public Color getDaySeparatorColor() { return daySeparatorColor; }
    public void setDaySeparatorColor(Color daySeparatorColor) { this.daySeparatorColor = daySeparatorColor; }

    public CrosshairFPS getCrosshairFps() { return crosshairFps; }
    public void setCrosshairFps(CrosshairFPS crosshairFps) { this.crosshairFps = crosshairFps; }

    public boolean isPriceAxisLabelsEnabled() { return priceAxisLabelsEnabled; }
    public void setPriceAxisLabelsEnabled(boolean priceAxisLabelsEnabled) { this.priceAxisLabelsEnabled = priceAxisLabelsEnabled; }

    public boolean isPriceAxisLabelsShowOrders() { return priceAxisLabelsShowOrders; }
    public void setPriceAxisLabelsShowOrders(boolean priceAxisLabelsShowOrders) { this.priceAxisLabelsShowOrders = priceAxisLabelsShowOrders; }

    public boolean isPriceAxisLabelsShowDrawings() { return priceAxisLabelsShowDrawings; }
    public void setPriceAxisLabelsShowDrawings(boolean priceAxisLabelsShowDrawings) { this.priceAxisLabelsShowDrawings = priceAxisLabelsShowDrawings; }

    public boolean isPriceAxisLabelsShowFibonaccis() { return priceAxisLabelsShowFibonaccis; }
    public void setPriceAxisLabelsShowFibonaccis(boolean priceAxisLabelsShowFibonaccis) { this.priceAxisLabelsShowFibonaccis = priceAxisLabelsShowFibonaccis; }

    public int getFootprintCandleOpacity() { return footprintCandleOpacity; }
    public void setFootprintCandleOpacity(int footprintCandleOpacity) { this.footprintCandleOpacity = footprintCandleOpacity; }
}
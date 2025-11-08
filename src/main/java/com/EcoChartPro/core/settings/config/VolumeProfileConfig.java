package com.EcoChartPro.core.settings.config;

import java.awt.*;
import java.io.Serializable;

public class VolumeProfileConfig implements Serializable {

    private boolean vrvpVisible = false;
    private boolean svpVisible = false;
    private Color vrvpUpVolumeColor;
    private Color vrvpDownVolumeColor;
    private Color vrvpPocColor;
    private Color vrvpValueAreaUpColor;
    private Color vrvpValueAreaDownColor;
    private int vrvpRowHeight = 1;
    private BasicStroke vrvpPocLineStroke = new BasicStroke(2.0f);

    public VolumeProfileConfig() {
        // Initialize with default dark theme colors
        initializeDefaultColors(true);
    }

    public void initializeDefaultColors(boolean isDarkTheme) {
        if (isDarkTheme) {
            this.vrvpUpVolumeColor = new Color(255, 140, 40, 60);
            this.vrvpDownVolumeColor = new Color(200, 200, 200, 60);
            this.vrvpPocColor = new Color(255, 255, 255, 100);
            this.vrvpValueAreaUpColor = new Color(255, 140, 40, 30);
            this.vrvpValueAreaDownColor = new Color(200, 200, 200, 30);
        } else { // Light Theme
            this.vrvpUpVolumeColor = new Color(0, 150, 136, 50);
            this.vrvpDownVolumeColor = new Color(211, 47, 47, 50);
            this.vrvpPocColor = new Color(0, 105, 217, 180);
            this.vrvpValueAreaUpColor = new Color(0, 150, 136, 30);
            this.vrvpValueAreaDownColor = new Color(211, 47, 47, 30);
        }
    }

    // --- Getters and Setters ---

    public boolean isVrvpVisible() { return vrvpVisible; }
    public void setVrvpVisible(boolean vrvpVisible) { this.vrvpVisible = vrvpVisible; }

    public boolean isSvpVisible() { return svpVisible; }
    public void setSvpVisible(boolean svpVisible) { this.svpVisible = svpVisible; }

    public Color getVrvpUpVolumeColor() { return vrvpUpVolumeColor; }
    public void setVrvpUpVolumeColor(Color vrvpUpVolumeColor) { this.vrvpUpVolumeColor = vrvpUpVolumeColor; }

    public Color getVrvpDownVolumeColor() { return vrvpDownVolumeColor; }
    public void setVrvpDownVolumeColor(Color vrvpDownVolumeColor) { this.vrvpDownVolumeColor = vrvpDownVolumeColor; }

    public Color getVrvpPocColor() { return vrvpPocColor; }
    public void setVrvpPocColor(Color vrvpPocColor) { this.vrvpPocColor = vrvpPocColor; }

    public Color getVrvpValueAreaUpColor() { return vrvpValueAreaUpColor; }
    public void setVrvpValueAreaUpColor(Color vrvpValueAreaUpColor) { this.vrvpValueAreaUpColor = vrvpValueAreaUpColor; }

    public Color getVrvpValueAreaDownColor() { return vrvpValueAreaDownColor; }
    public void setVrvpValueAreaDownColor(Color vrvpValueAreaDownColor) { this.vrvpValueAreaDownColor = vrvpValueAreaDownColor; }

    public int getVrvpRowHeight() { return vrvpRowHeight; }
    public void setVrvpRowHeight(int vrvpRowHeight) { this.vrvpRowHeight = vrvpRowHeight; }

    public BasicStroke getVrvpPocLineStroke() { return vrvpPocLineStroke; }
    public void setVrvpPocLineStroke(BasicStroke vrvpPocLineStroke) { this.vrvpPocLineStroke = vrvpPocLineStroke; }
}
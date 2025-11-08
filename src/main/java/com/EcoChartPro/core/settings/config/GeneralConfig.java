package com.EcoChartPro.core.settings.config;

import com.EcoChartPro.core.theme.ThemeManager.Theme;
import java.io.Serializable;
import java.time.ZoneId;

public class GeneralConfig implements Serializable {

    public enum ImageSource { LIVE, LOCAL }

    private Theme currentTheme = Theme.DARK;
    private float uiScale = 1.0f;
    private ZoneId displayZoneId = ZoneId.systemDefault();
    private ImageSource imageSource = ImageSource.LOCAL;

    // --- Getters and Setters ---

    public Theme getCurrentTheme() { return currentTheme; }
    public void setCurrentTheme(Theme currentTheme) { this.currentTheme = currentTheme; }

    public float getUiScale() { return uiScale; }
    public void setUiScale(float uiScale) { this.uiScale = uiScale; }

    public ZoneId getDisplayZoneId() { return displayZoneId; }
    public void setDisplayZoneId(ZoneId displayZoneId) { this.displayZoneId = displayZoneId; }

    public ImageSource getImageSource() { return imageSource; }
    public void setImageSource(ImageSource imageSource) { this.imageSource = imageSource; }
}
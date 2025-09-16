package com.EcoChartPro.plugins.inapp;

import com.EcoChartPro.api.indicator.*;
import com.EcoChartPro.api.indicator.drawing.*;
import com.EcoChartPro.core.indicator.IndicatorContext;
import com.EcoChartPro.model.KLine;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ForexSessionsIndicator implements CustomIndicator {

    @Override
    public String getName() { return "Forex Sessions"; }

    @Override
    public IndicatorType getType() { return IndicatorType.OVERLAY; }

    @Override
    public List<Parameter> getParameters() {
        return List.of(
            new Parameter("showAsia", ParameterType.BOOLEAN, true),
            new Parameter("asiaColor", ParameterType.COLOR, new Color(0, 150, 136, 15)),
            new Parameter("showLondon", ParameterType.BOOLEAN, true),
            new Parameter("londonColor", ParameterType.COLOR, new Color(255, 82, 82, 15)),
            new Parameter("showNewYork", ParameterType.BOOLEAN, true),
            new Parameter("newYorkColor", ParameterType.COLOR, new Color(33, 150, 243, 15))
        );
    }
    
    // Define session times in UTC for consistency
    private static final LocalTime ASIA_START = LocalTime.of(0, 0); // Tokyo open
    private static final LocalTime ASIA_END = LocalTime.of(9, 0);
    private static final LocalTime LONDON_START = LocalTime.of(8, 0);
    private static final LocalTime LONDON_END = LocalTime.of(17, 0);
    private static final LocalTime NY_START = LocalTime.of(13, 0);
    private static final LocalTime NY_END = LocalTime.of(22, 0);

    @Override
    public List<DrawableObject> calculate(IndicatorContext context) {
        List<KLine> data = context.klineData();
        if (data.isEmpty()) return Collections.emptyList();
        
        // --- Get view boundaries to draw boxes across the full screen height ---
        BigDecimal viewHigh = data.get(0).high();
        BigDecimal viewLow = data.get(0).low();
        for (KLine k : data) {
            if (k.high().compareTo(viewHigh) > 0) viewHigh = k.high();
            if (k.low().compareTo(viewLow) < 0) viewLow = k.low();
        }
        // Add some padding
        BigDecimal padding = viewHigh.subtract(viewLow).multiply(new BigDecimal("0.05"));
        viewHigh = viewHigh.add(padding);
        viewLow = viewLow.subtract(padding);
        
        List<DrawableObject> drawables = new ArrayList<>();

        for (KLine kline : data) {
            ZonedDateTime barTime = ZonedDateTime.ofInstant(kline.timestamp(), ZoneId.of("UTC"));
            LocalTime localBarTime = barTime.toLocalTime();
            
            Color sessionColor = getSessionColor(localBarTime, context.settings());
            
            if (sessionColor != null) {
                drawables.add(new DrawableBox(
                    new DataPoint(kline.timestamp(), viewHigh),
                    new DataPoint(kline.timestamp(), viewLow),
                    sessionColor, null, 0
                ));
            }
        }
        return drawables;
    }

    private Color getSessionColor(LocalTime time, Map<String, Object> settings) {
        if ((Boolean) settings.get("showAsia") && !time.isBefore(ASIA_START) && time.isBefore(ASIA_END)) {
            return (Color) settings.get("asiaColor");
        }
        if ((Boolean) settings.get("showLondon") && !time.isBefore(LONDON_START) && time.isBefore(LONDON_END)) {
            return (Color) settings.get("londonColor");
        }
        if ((Boolean) settings.get("showNewYork") && !time.isBefore(NY_START) && time.isBefore(NY_END)) {
            return (Color) settings.get("newYorkColor");
        }
        return null;
    }
}
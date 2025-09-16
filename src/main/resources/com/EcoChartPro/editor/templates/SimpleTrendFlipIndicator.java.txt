package com.EcoChartPro.plugins.inapp;

import com.EcoChartPro.api.indicator.*;
import com.EcoChartPro.api.indicator.drawing.*;
import com.EcoChartPro.core.indicator.IndicatorContext;
import com.EcoChartPro.model.KLine;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SimpleTrendFlipIndicator implements CustomIndicator {

    @Override
    public String getName() {
        return "Simple Trend Flip";
    }

    @Override
    public IndicatorType getType() {
        return IndicatorType.OVERLAY;
    }

    @Override
    public List<Parameter> getParameters() {
        return List.of(
            new Parameter("bullColor", ParameterType.COLOR, new Color(255, 235, 59, 20)), // Yellow
            new Parameter("bearColor", ParameterType.COLOR, new Color(33, 150, 243, 20))  // Blue
        );
    }

    @Override
    public List<DrawableObject> calculate(IndicatorContext context) {
        List<KLine> data = context.klineData();
        if (data.size() < 2) {
            return Collections.emptyList();
        }

        Color bullColor = (Color) context.settings().get("bullColor");
        Color bearColor = (Color) context.settings().get("bearColor");

        List<DrawableObject> drawables = new ArrayList<>();
        
        // --- State Management ---
        boolean isUptrend = true; // Start with an initial assumption

        // Loop from the second bar since we need to look back at the previous bar.
        for (int i = 1; i < data.size(); i++) {
            KLine currentBar = data.get(i);
            KLine prevBar = data.get(i - 1);
            boolean prevTrend = isUptrend;

            // --- Core Logic ---
            if (currentBar.close().compareTo(prevBar.high()) > 0) {
                isUptrend = true; // Bullish flip
            } else if (currentBar.close().compareTo(prevBar.low()) < 0) {
                isUptrend = false; // Bearish flip
            }
            // If neither condition is met, the trend continues.

            // --- Drawing Logic ---
            
            // 1. Draw the background for the current bar's trend
            Color bgColor = isUptrend ? bullColor : bearColor;
            drawables.add(new DrawableBox(
                new DataPoint(currentBar.timestamp(), currentBar.high()),
                new DataPoint(currentBar.timestamp(), currentBar.low()),
                bgColor, null, 0
            ));

            // 2. If the trend flipped on THIS bar, draw a signal
            if (isUptrend != prevTrend) {
                addSignal(drawables, currentBar, isUptrend);
            }
        }
        return drawables;
    }
    
    private void addSignal(List<DrawableObject> drawables, KLine kline, boolean isBullSignal) {
        BigDecimal candleHeight = kline.high().subtract(kline.low());
        BigDecimal offset = candleHeight.multiply(new BigDecimal("0.2"));
        BigDecimal triangleHeight = candleHeight.multiply(new BigDecimal("0.8"));

        if (isBullSignal) {
            BigDecimal anchorY = kline.low().subtract(offset);
            DataPoint p1 = new DataPoint(kline.timestamp(), anchorY);
            DataPoint p2 = new DataPoint(kline.timestamp(), anchorY.add(triangleHeight));
            DataPoint p3 = new DataPoint(kline.timestamp(), anchorY);
            drawables.add(new DrawablePolygon(List.of(p1, p2, p3), new Color(30, 200, 100, 220), Color.WHITE, 1.0f));
        } else { // Bear Signal
            BigDecimal anchorY = kline.high().add(offset);
            DataPoint p1 = new DataPoint(kline.timestamp(), anchorY);
            DataPoint p2 = new DataPoint(kline.timestamp(), anchorY.subtract(triangleHeight));
            DataPoint p3 = new DataPoint(kline.timestamp(), anchorY);
            drawables.add(new DrawablePolygon(List.of(p1, p2, p3), new Color(255, 82, 82, 220), Color.WHITE, 1.0f));
        }
    }
}
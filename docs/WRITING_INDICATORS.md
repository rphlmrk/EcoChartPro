# Writing Custom Indicators for Eco Chart Pro

The Eco Chart Pro indicator API allows you to create powerful, custom technical indicators using Java. This guide covers the core concepts and provides a practical example.

## The `CustomIndicator` Interface

Every indicator must implement the `CustomIndicator` interface. This is the central contract between your code and the charting engine.

```java
public interface CustomIndicator {
    // Returns the display name of your indicator.
    String getName();

    // Specifies if the indicator draws on the main chart or in a separate pane.
    IndicatorType getType();

    // Defines user-configurable settings for the indicator.
    List<Parameter> getParameters();

    // The core calculation logic.
    List<DrawableObject> calculate(IndicatorContext context);
}

1. getName()
Return a user-friendly name for your indicator, e.g., "Simple Moving Average".

2. getType()
Return either IndicatorType.OVERLAY or IndicatorType.PANE.
OVERLAY: Draws directly on the main price chart (e.g., a Moving Average).
PANE: Draws in a new, separate panel below the main chart (e.g., RSI or MACD).

3. getParameters()
This method defines the settings that users can change in the indicator dialog. A Parameter consists of a key, type, default value, and optional choices.
Parameter Types:
INTEGER: A whole number (e.g., for a period length).
DECIMAL: A number with a decimal point.
BOOLEAN: A true/false checkbox.
COLOR: A color picker.
CHOICE: A dropdown list of string options.
Example:
code
Java
@Override
public List<Parameter> getParameters() {
    return List.of(
        new Parameter("period", ParameterType.INTEGER, 20),
        new Parameter("source", ParameterType.CHOICE, "Close", "Open", "High", "Low", "Close"),
        new Parameter("lineColor", ParameterType.COLOR, new Color(255, 140, 40))
    );
}

4. calculate(IndicatorContext context)
This is where your indicator's logic lives. It's called by the charting engine whenever new data is available or the view changes. The IndicatorContext object provides everything you need.
context.klineData(): A List<ApiKLine> containing the visible chart data plus a lookback buffer for your calculations.
context.settings(): A Map<String, Object> containing the current user-configured settings for your indicator.
Your method must return a List<DrawableObject>. These are the shapes, lines, and text that will be rendered on the chart.
Drawable Objects:
DrawablePolyline: For drawing lines like an SMA or RSI.
DrawableBox: For drawing rectangles (e.g., zones).
DrawablePolygon: For drawing custom filled shapes (e.g., buy/sell arrows).
DrawableText: For drawing text labels on the chart.
...and more.




Example: A Simple Moving Average (SMA)
This example shows how to implement a complete SMA indicator.
code
Java
package com.EcoChartPro.plugins.inapp;

import com.EcoChartPro.api.indicator.*;
import com.EcoChartPro.api.indicator.drawing.DataPoint;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.api.indicator.drawing.DrawablePolyline;
import com.EcoChartPro.core.indicator.IndicatorContext;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MySimpleMovingAverage implements CustomIndicator {

    @Override
    public String getName() {
        return "Simple Moving Average (SMA)";
    }

    @Override
    public IndicatorType getType() {
        return IndicatorType.OVERLAY;
    }

    @Override
    public List<Parameter> getParameters() {
        return List.of(
            new Parameter("period", ParameterType.INTEGER, 20),
            new Parameter("lineColor", ParameterType.COLOR, new Color(255, 140, 40))
        );
    }

    @Override
    public List<DrawableObject> calculate(IndicatorContext context) {
        // 1. Get user settings from the context
        int period = (int) context.settings().get("period");
        Color lineColor = (Color) context.settings().get("lineColor");
        List<ApiKLine> klineData = context.klineData();

        if (klineData.size() < period) {
            return Collections.emptyList(); // Not enough data to calculate
        }

        // 2. Extract the source data (close prices)
        List<BigDecimal> sourceData = IndicatorUtils.extractSourceData(klineData, ApiKLine::close);

        // 3. Perform the calculation using the built-in utility
        List<BigDecimal> smaValues = IndicatorUtils.calculateSMA(sourceData, period);

        // 4. Map the results back to DataPoints with correct timestamps
        // The first SMA value corresponds to the kline at index (period - 1)
        List<DataPoint> smaPoints = IntStream.range(0, smaValues.size())
            .mapToObj(i -> new DataPoint(
                klineData.get(i + period - 1).timestamp(),
                smaValues.get(i)
            ))
            .collect(Collectors.toList());

        // 5. Create a drawable object and return it
        DrawablePolyline smaLine = new DrawablePolyline(smaPoints, lineColor, 2.0f);
        return List.of(smaLine);
    }
}
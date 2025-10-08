package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.ApiKLine;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A context object passed to an indicator's `calculate` method.
 * It provides a "bag of goodies" containing everything an indicator needs to
 * perform its calculation for the current chart view.
 *
 * @param klineData The slice of K-line data for the visible chart area, plus a lookback buffer.
 *                  This data is provided as a list of the public {@link ApiKLine} records.
 * @param settings The user-configured settings for this indicator instance.
 * @param getHigherTimeframeData A function to request and receive data for another timeframe.
 *                               Note: This function still returns the internal {@code model.KLine}
 *                               for performance reasons, as it's intended for internal resampling.
 * @param debugLogger A consumer to send debug data to the Data Inspector.
 * @param state A mutable map for the indicator to store and retrieve state between calculation calls.
 *              This map is persistent for the lifetime of the indicator instance on the chart.
 *              It is managed by the core engine and should be cleared in the onSettingsChanged()
 *              hook if the state depends on user settings.
 */
public record IndicatorContext(
    List<ApiKLine> klineData,
    Map<String, Object> settings,
    Function<Timeframe, List<KLine>> getHigherTimeframeData,
    Consumer<IndicatorContext.DebugLogEntry> debugLogger,
    Map<String, Object> state
) {
    /**
     * A record to hold a single piece of debug information from an indicator calculation.
     */
    public record DebugLogEntry(int barIndex, String key, Object value) {}

    /**
     * Logs a key-value pair for a specific bar index for debugging in the Data Inspector.
     * @param barIndex The index of the bar in the `klineData` list this value corresponds to.
     * @param key A descriptive name for the value being logged (e.g., "SMA(20)", "RSI").
     * @param value The calculated value to log.
     */
    public void log(int barIndex, String key, Object value) {
        if (debugLogger != null) {
            debugLogger.accept(new DebugLogEntry(barIndex, key, value));
        }
    }
}
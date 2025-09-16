package com.EcoChartPro.core.indicator;

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
 * @param settings The user-configured settings for this indicator instance.
 * @param getHigherTimeframeData A function to request and receive data for another timeframe.
 * @param debugLogger A consumer to send debug data to the Data Inspector.
 */
public record IndicatorContext(
    List<KLine> klineData,
    Map<String, Object> settings,
    Function<Timeframe, List<KLine>> getHigherTimeframeData,
    Consumer<IndicatorContext.DebugLogEntry> debugLogger
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
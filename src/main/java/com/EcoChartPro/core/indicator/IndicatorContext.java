package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.ApiKLine;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A context object passed to an indicator's `calculate` method.
 * It provides a "bag of goodies" containing everything an indicator needs to
 * perform its calculation for the current chart view.
 *
 * @param klineData The slice of K-line data for the visible chart area, plus a lookback buffer.
 * @param settings The user-configured settings for this indicator instance.
 * @param getHigherTimeframeData A function to request and receive data for another timeframe.
 * @param debugLogger A consumer to send debug data to the Data Inspector.
 * @param state A mutable map for the indicator to store and retrieve state between calculation calls.
 * @param isReset A flag indicating that this is the first calculation run or that
 *                settings have changed, signaling that the indicator should re-initialize its state.
 */
public record IndicatorContext(
    List<ApiKLine> klineData,
    Map<String, Object> settings,
    Function<Timeframe, List<KLine>> getHigherTimeframeData,
    Consumer<IndicatorContext.DebugLogEntry> debugLogger,
    Map<String, Object> state,
    boolean isReset
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

    /**
     * [NEW] A convenience method for indicators to request resampled data for a different timeframe.
     * This converts the internal model.KLine to the public api.indicator.ApiKLine.
     * @param timeframeString The string representation of the timeframe (e.g., "H1", "15m").
     * @return A list of ApiKLine objects for the requested timeframe, or an empty list if unavailable.
     */
    public List<ApiKLine> resampledKlineData(String timeframeString) {
        Timeframe tf = Timeframe.fromString(timeframeString);
        if (tf == null || getHigherTimeframeData == null) {
            return List.of();
        }
        List<KLine> internalData = getHigherTimeframeData.apply(tf);
        if (internalData == null) {
            return List.of();
        }
        return internalData.stream()
            .map(k -> new ApiKLine(k.timestamp(), k.open(), k.high(), k.low(), k.close(), k.volume()))
            .collect(Collectors.toList());
    }
}
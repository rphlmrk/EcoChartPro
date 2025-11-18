package com.EcoChartPro.core.model.providers;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Defines the contract for providing chartable candle data,
 * abstracting away the source (Replay vs. Live). This allows ChartDataModel
 * to handle data polymorphically.
 */
public interface IHistoryProvider {

    /**
     * Gets the list of all finalized (closed) candles available from the provider.
     * @return A list of finalized K-lines.
     */
    List<KLine> getFinalizedCandles();

    /**
     * Gets the currently forming candle that updates with each tick.
     * This will be null if the provider is not processing live ticks.
     * @return The latest, forming K-line, or null.
     */
    KLine getFormingCandle();

    /**
     * Gets the total number of candles in the entire dataset managed by the provider.
     * @return The total candle count.
     */
    int getTotalCandleCount();

    /**
     * Gets the starting index of the current data window.
     * This is primarily for windowed providers like Replay mode. For live providers
     * that load all history, this may always return 0.
     * @return The start index of the current data window.
     */
    int getDataWindowStartIndex();

    /**
     * Initializes or reloads the provider's data for a new timeframe.
     * This is a key method for handling timeframe changes.
     * @param newTimeframe The timeframe to load data for.
     * @param forceReload If true, bypasses checks and forces a data reload.
     */
    void setTimeframe(Timeframe newTimeframe, boolean forceReload);
    
    /**
     * Convenience method for setTimeframe without forcing a reload.
     * @param newTimeframe The timeframe to load data for.
     */
    default void setTimeframe(Timeframe newTimeframe) {
        setTimeframe(newTimeframe, false);
    }

    /**
     * Cleans up any resources held by the provider, such as listeners or timers.
     * This should be called when the provider is no longer needed.
     */
    void cleanup();

    /**
     * [NEW] A hook for providers to react to the start of a new replay session.
     * Default implementation does nothing.
     */
    default void onReplaySessionStart() {}

    /**
     * Adds a property change listener for events like 'historyRebuilt'.
     * Default implementation does nothing, for providers that don't emit such events.
     */
    default void addPropertyChangeListener(PropertyChangeListener listener) {}

    /**
     * Removes a property change listener.
     * Default implementation does nothing.
     */
    default void removePropertyChangeListener(PropertyChangeListener listener) {}
}
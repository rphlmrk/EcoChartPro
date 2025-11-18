package com.EcoChartPro.core.model.providers;

import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.ReplayStateListener;
import com.EcoChartPro.data.DataResampler;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.ChartPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Manages the state and data fetching for a chart in Replay mode.
 * It listens to the ReplaySessionManager for ticks and handles data windowing,
 * resampling, and pre-fetching of historical data.
 */
public class ReplayHistoryProvider implements IHistoryProvider, ReplayStateListener, PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ReplayHistoryProvider.class);

    // --- Configuration Constants ---
    private static final int INDICATOR_LOOKBACK_BUFFER = 500;
    private static final int DATA_WINDOW_SIZE = 20000;
    private static final int DATA_WINDOW_TRIGGER_BUFFER = DATA_WINDOW_SIZE / 3;

    // --- Core Dependencies ---
    private final ChartInteractionManager interactionManager;
    private final ReplaySessionManager replaySessionManager;
    private final ChartPanel chartPanel; // To show loading state
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // --- State Fields ---
    private Timeframe currentDisplayTimeframe;
    private List<KLine> finalizedCandles = new ArrayList<>();
    private KLine currentlyFormingCandle;
    private List<KLine> baseDataWindow = new ArrayList<>(); // M1 data for resampling
    private List<KLine> m1BufferForResampling = new ArrayList<>();
    private int totalCandleCount = 0;
    private int dataWindowStartIndex = 0;

    // --- Asynchronous Loading Fields ---
    private record RebuildResult(List<KLine> resampledCandles, KLine formingCandle, int newWindowStart, List<KLine> rawM1Slice) {}
    private SwingWorker<RebuildResult, Void> activeRebuildWorker = null;
    private SwingWorker<RebuildResult, Void> preFetchWorker = null;
    private RebuildResult preFetchedResult = null;
    private volatile boolean isPreFetching = false;


    public ReplayHistoryProvider(ChartInteractionManager interactionManager, ChartPanel chartPanel, Timeframe initialTimeframe) {
        this.interactionManager = interactionManager;
        this.chartPanel = chartPanel;
        this.replaySessionManager = ReplaySessionManager.getInstance();
        this.currentDisplayTimeframe = initialTimeframe;

        // Register listeners to receive updates
        this.replaySessionManager.addListener(this);
        this.interactionManager.addPropertyChangeListener("viewStateChanged", this);
    }

    // --- IHistoryProvider Implementation ---

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public List<KLine> getFinalizedCandles() {
        if (currentDisplayTimeframe == Timeframe.M1) {
            // For M1, the "finalized" candles are the entire visible window from ReplaySessionManager
            // This provider doesn't hold them directly to avoid data duplication.
            return baseDataWindow;
        }
        return finalizedCandles;
    }

    @Override
    public KLine getFormingCandle() {
        return (currentDisplayTimeframe == Timeframe.M1) ? null : currentlyFormingCandle;
    }

    @Override
    public int getTotalCandleCount() {
        return totalCandleCount;
    }

    @Override
    public int getDataWindowStartIndex() {
        return dataWindowStartIndex;
    }

    @Override
    public void setTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null) return;
        if (!forceReload && newTimeframe.equals(currentDisplayTimeframe)) return;

        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            activeRebuildWorker.cancel(true);
        }
        
        // Reset all internal state for the new timeframe
        this.finalizedCandles.clear();
        this.currentlyFormingCandle = null;
        this.baseDataWindow.clear();
        this.m1BufferForResampling.clear();
        this.totalCandleCount = 0;
        this.dataWindowStartIndex = 0;
        
        this.currentDisplayTimeframe = newTimeframe;
        int m1HeadIndex = replaySessionManager.getReplayHeadIndex();
        if (m1HeadIndex < 0) return; // Not started yet

        if (currentDisplayTimeframe != Timeframe.M1 && !currentDisplayTimeframe.duration().isZero()) {
            totalCandleCount = (m1HeadIndex + 1) / (int) currentDisplayTimeframe.duration().toMinutes();
        } else {
            totalCandleCount = m1HeadIndex + 1;
        }

        int dataBarsOnScreen = (int) (interactionManager.getBarsPerScreen() * (1.0 - interactionManager.getRightMarginRatio()));
        int targetStartIndex = Math.max(0, totalCandleCount - dataBarsOnScreen);
        rebuildHistoryAsync(targetStartIndex, false);
    }

    @Override
    public void cleanup() {
        replaySessionManager.removeListener(this);
        interactionManager.removePropertyChangeListener("viewStateChanged", this);
        pcs.removePropertyChangeListener(this);
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            activeRebuildWorker.cancel(true);
        }
    }

    // --- ReplayStateListener Implementation ---

    @Override
    public void onReplayTick(KLine newM1Bar) {
        if (baseDataWindow != null) baseDataWindow.add(newM1Bar);

        if (currentDisplayTimeframe == Timeframe.M1) {
            totalCandleCount++;
            // For M1, every tick is treated as a finalized candle in terms of UI updates.
            chartPanel.getDataModel().fireLiveCandleAdded(newM1Bar);
        } else {
            processNewM1BarForResampling(newM1Bar);
        }
    }

    @Override
    public void onReplaySessionStart() {
        this.m1BufferForResampling.clear();
        // Trigger initial data load when a new session starts
        setTimeframe(this.currentDisplayTimeframe, true);
    }

    @Override
    public void onReplayStateChanged() {
        // Not used by this provider
    }

    // --- PropertyChangeListener Implementation (for InteractionManager) ---
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("viewStateChanged".equals(evt.getPropertyName())) {
            if (handleDataWindowLoading()) {
                // A reload was triggered, no need for further action here.
                return;
            }
            // If no reload, check for pre-fetch opportunity
            checkForPreFetchTrigger();
        }
    }

    // --- Internal Data Management Logic (Moved from ChartDataModel) ---

    private boolean handleDataWindowLoading() {
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            return true; // Already loading
        }

        List<KLine> sourceData = getFinalizedCandles();
        int currentWindowEnd = dataWindowStartIndex + sourceData.size();
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();

        boolean needsReload = sourceData.isEmpty() ||
                (currentStartIndex < dataWindowStartIndex + DATA_WINDOW_TRIGGER_BUFFER && dataWindowStartIndex > 0) ||
                (currentStartIndex + currentBarsPerScreen > currentWindowEnd - DATA_WINDOW_TRIGGER_BUFFER && currentWindowEnd < totalCandleCount);

        if (needsReload) {
            int newWindowCenter = currentStartIndex + (currentBarsPerScreen / 2);
            int newWindowStart = Math.max(0, newWindowCenter - (DATA_WINDOW_SIZE / 2));
            newWindowStart = Math.min(newWindowStart, Math.max(0, totalCandleCount - DATA_WINDOW_SIZE));

            if (preFetchedResult != null && preFetchedResult.newWindowStart() == newWindowStart) {
                logger.debug("Pre-fetch cache HIT. Applying cached data window.");
                applyRebuildResult(preFetchedResult);
                pcs.firePropertyChange("historyRebuilt", null, null);
                preFetchedResult = null;
                return false; // Reload happened instantly from cache
            } else {
                rebuildHistoryAsync(currentStartIndex, false);
                return true; // Reload was triggered
            }
        }
        return false;
    }

    private void rebuildHistoryAsync(final int targetStartIndex, boolean isPreFetch) {
        if (!isPreFetch) {
            if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) activeRebuildWorker.cancel(true);
            if (chartPanel != null) chartPanel.setLoading(true, "Building " + currentDisplayTimeframe.displayName() + " history...");
        }

        SwingWorker<RebuildResult, Void> worker = new SwingWorker<>() {
            @Override
            protected RebuildResult doInBackground() throws Exception {
                int newWindowCenter = targetStartIndex + (interactionManager.getBarsPerScreen() / 2);
                int newWindowStart = Math.max(0, newWindowCenter - (DATA_WINDOW_SIZE / 2));
                newWindowStart = Math.min(newWindowStart, Math.max(0, totalCandleCount - DATA_WINDOW_SIZE));
                return fetchReplayData(newWindowStart);
            }

            @Override
            protected void done() {
                try {
                    RebuildResult result = get();
                    if (isPreFetch) {
                        preFetchedResult = result;
                        isPreFetching = false;
                        logger.debug("Pre-fetch complete for window starting at {}", result.newWindowStart());
                    } else {
                        applyRebuildResult(result);
                        pcs.firePropertyChange("historyRebuilt", null, null); // Fire event after data is ready
                        
                        // Defer the final update to prevent a race condition with UI layout.
                        SwingUtilities.invokeLater(() -> {
                            if (interactionManager != null) {
                                interactionManager.setStartIndex(targetStartIndex);
                            }
                        });
                    }
                } catch (InterruptedException | java.util.concurrent.CancellationException e) {
                    logger.warn("Data loading task was cancelled by a newer request.");
                } catch (ExecutionException e) {
                    logger.error("Failed to load chart data in background", e.getCause());
                } finally {
                    if (isPreFetch) isPreFetching = false;
                    else activeRebuildWorker = null;
                    if (chartPanel != null && !isPreFetch) chartPanel.setLoading(false, null);
                }
            }
        };

        if (isPreFetch) this.preFetchWorker = worker;
        else this.activeRebuildWorker = worker;
        worker.execute();
    }
    
    private RebuildResult fetchReplayData(int newWindowStartInFinalData) {
        if (currentDisplayTimeframe == Timeframe.M1) {
            int fetchCount = Math.min(DATA_WINDOW_SIZE, replaySessionManager.getReplayHeadIndex() + 1 - newWindowStartInFinalData);
            List<KLine> m1HistorySlice = replaySessionManager.getOneMinuteBars(newWindowStartInFinalData, fetchCount);
            return new RebuildResult(m1HistorySlice, null, newWindowStartInFinalData, m1HistorySlice);
        } else {
            long m1BarsPerTargetCandle = currentDisplayTimeframe.duration().toMinutes();
            if (m1BarsPerTargetCandle <= 0) m1BarsPerTargetCandle = 1;

            int m1LookbackForWindow = (int) ((DATA_WINDOW_SIZE + INDICATOR_LOOKBACK_BUFFER) * m1BarsPerTargetCandle);
            int m1FetchStartIndex = (int) (newWindowStartInFinalData * m1BarsPerTargetCandle);
            int availableM1Bars = replaySessionManager.getReplayHeadIndex() + 1 - m1FetchStartIndex;
            int m1FetchCount = Math.min(m1LookbackForWindow, availableM1Bars);

            List<KLine> m1HistorySlice = replaySessionManager.getOneMinuteBars(m1FetchStartIndex, m1FetchCount);
            List<KLine> resampledData = DataResampler.resample(m1HistorySlice, currentDisplayTimeframe);

            KLine formingCandle = null;
            if (!resampledData.isEmpty() && !m1HistorySlice.isEmpty()) {
                Instant lastM1TsInSlice = m1HistorySlice.get(m1HistorySlice.size() - 1).timestamp();
                Instant lastResampledCandleTs = resampledData.get(resampledData.size() - 1).timestamp();
                if (getIntervalStart(lastM1TsInSlice, currentDisplayTimeframe).equals(lastResampledCandleTs)) {
                    formingCandle = resampledData.remove(resampledData.size() - 1);
                }
            }
            return new RebuildResult(resampledData, formingCandle, newWindowStartInFinalData, m1HistorySlice);
        }
    }

    private void applyRebuildResult(RebuildResult result) {
        if (currentDisplayTimeframe == Timeframe.M1) {
            baseDataWindow = result.rawM1Slice() != null ? new ArrayList<>(result.rawM1Slice()) : new ArrayList<>();
            finalizedCandles = new ArrayList<>(); // M1 doesn't use this list
        } else {
            finalizedCandles = result.resampledCandles() != null ? new ArrayList<>(result.resampledCandles()) : new ArrayList<>();
            baseDataWindow = result.rawM1Slice() != null ? new ArrayList<>(result.rawM1Slice()) : new ArrayList<>();
        }
        currentlyFormingCandle = result.formingCandle();
        dataWindowStartIndex = result.newWindowStart();

        // Update total count based on the latest replay head
        int m1HeadIndex = replaySessionManager.getReplayHeadIndex();
        if (currentDisplayTimeframe != Timeframe.M1 && !currentDisplayTimeframe.duration().isZero()) {
            totalCandleCount = (m1HeadIndex + 1) / (int)currentDisplayTimeframe.duration().toMinutes();
        } else {
            totalCandleCount = m1HeadIndex + 1;
        }
    }

    private void checkForPreFetchTrigger() {
        if (isPreFetching || (activeRebuildWorker != null && !activeRebuildWorker.isDone())) return;

        int preFetchTriggerBufferSize = DATA_WINDOW_SIZE / 4;
        List<KLine> sourceData = getFinalizedCandles();
        int currentWindowEnd = dataWindowStartIndex + sourceData.size();
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();

        int nextWindowStart = -1;
        if (currentStartIndex < dataWindowStartIndex + preFetchTriggerBufferSize && dataWindowStartIndex > 0) {
            nextWindowStart = Math.max(0, dataWindowStartIndex - (DATA_WINDOW_SIZE / 2));
        } else if (currentStartIndex + currentBarsPerScreen > currentWindowEnd - preFetchTriggerBufferSize && currentWindowEnd < totalCandleCount) {
            nextWindowStart = dataWindowStartIndex + (DATA_WINDOW_SIZE / 2);
        }

        if (nextWindowStart != -1 && (preFetchedResult == null || preFetchedResult.newWindowStart() != nextWindowStart)) {
            isPreFetching = true;
            logger.debug("Starting pre-fetch for data window starting at index {}", nextWindowStart);
            rebuildHistoryAsync(nextWindowStart, true);
        }
    }
    
    private void processNewM1BarForResampling(KLine newM1Bar) {
        long intervalMillis = currentDisplayTimeframe.duration().toMillis();
        long newBarIntervalStart = newM1Bar.timestamp().toEpochMilli() / intervalMillis * intervalMillis;

        boolean isNewInterval = true;
        if (!m1BufferForResampling.isEmpty()) {
            long currentIntervalStart = getIntervalStart(m1BufferForResampling.get(0).timestamp(), currentDisplayTimeframe).toEpochMilli();
            isNewInterval = (newBarIntervalStart != currentIntervalStart);
        }

        if (isNewInterval) {
            if (!m1BufferForResampling.isEmpty()) {
                List<KLine> resampled = DataResampler.resample(m1BufferForResampling, currentDisplayTimeframe);
                if (!resampled.isEmpty()) {
                    KLine finalizedCandle = resampled.get(0);
                    finalizedCandles.add(finalizedCandle);
                    totalCandleCount++;
                    chartPanel.getDataModel().fireLiveCandleAdded(finalizedCandle);
                }
            }
            m1BufferForResampling.clear();
        }

        m1BufferForResampling.add(newM1Bar);
        List<KLine> resampledForming = DataResampler.resample(m1BufferForResampling, currentDisplayTimeframe);
        this.currentlyFormingCandle = resampledForming.isEmpty() ? null : resampledForming.get(0);

        chartPanel.getDataModel().fireLiveTickReceived(this.currentlyFormingCandle);
    }


    private static Instant getIntervalStart(Instant timestamp, Timeframe timeframe) {
        long durationMillis = timeframe.duration().toMillis();
        if (durationMillis == 0) return timestamp;
        long epochMillis = timestamp.toEpochMilli();
        return Instant.ofEpochMilli(epochMillis - (epochMillis % durationMillis));
    }
}
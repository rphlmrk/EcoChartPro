package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.utils.AppDataManager;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Timer;
import java.io.File;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class ReplaySessionManager {

    private static final Logger logger = LoggerFactory.getLogger(ReplaySessionManager.class);
    private static volatile ReplaySessionManager instance;

    private int totalBarCount = 0;
    private List<KLine> baseOneMinuteDataWindow = new ArrayList<>();
    private int dataWindowStartIndex = 0;
    private static final int DATA_WINDOW_SIZE = 10000;

    private int replayHeadIndex = -1;
    private boolean isPlaying = false;
    private int speedInMs = 1000;
    private ChartDataSource currentSource;
    private DatabaseManager tempDbManager;

    private final Timer playbackTimer;
    private final List<ReplayStateListener> listeners = new CopyOnWriteArrayList<>();

    // Fields for auto-save functionality ---
    private int barsSinceLastAutoSave = 0;

    private ReplaySessionManager() {
        this.playbackTimer = new Timer(speedInMs, e -> nextBar());
        this.playbackTimer.setCoalesce(false);
    }

    public static ReplaySessionManager getInstance() {
        if (instance == null) {
            synchronized (ReplaySessionManager.class) {
                if (instance == null) {
                    instance = new ReplaySessionManager();
                }
            }
        }
        return instance;
    }

    public void startSessionFromState(ReplaySessionState state) {
        logger.info("Starting replay session from saved state for symbol: {}", state.dataSourceSymbol());
        this.isPlaying = false;
        this.playbackTimer.stop();

        Optional<ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.dataSourceSymbol()))
                .findFirst();

        if (sourceOpt.isEmpty()) {
            logger.error("Could not find a matching data source for symbol '{}'.", state.dataSourceSymbol());
            cleanupPreviousSession();
            return;
        }

        this.currentSource = sourceOpt.get();
        loadBaseDataInfo(this.currentSource);

        if (this.totalBarCount > 0) {
            this.replayHeadIndex = Math.max(0, Math.min(state.replayHeadIndex(), totalBarCount - 1));
            loadDataWindowForCurrentHead();
        }

        logger.info("Session state restored. Resuming at index {}.", this.replayHeadIndex);
        notifySessionStart();
    }


    public void startSession(ChartDataSource source, int startIndex) {
        logger.info("Starting new replay session for {} at index {}", source.displayName(), startIndex);
        this.currentSource = source;
        this.isPlaying = false;
        this.playbackTimer.stop();
        
        loadBaseDataInfo(source);
        
        if (totalBarCount > 0) {
            int validatedStartIndex = Math.max(0, Math.min(startIndex, totalBarCount - 1));
            this.replayHeadIndex = validatedStartIndex;
            loadDataWindowForCurrentHead();
        }
        
        notifySessionStart();
    }

    private void cleanupPreviousSession() {
        if (tempDbManager != null) {
            tempDbManager.close();
            tempDbManager = null;
        }
        this.baseOneMinuteDataWindow.clear();
        this.totalBarCount = 0;
        this.dataWindowStartIndex = 0;
        this.replayHeadIndex = -1;
    }

    private void loadBaseDataInfo(ChartDataSource source) {
        cleanupPreviousSession();
        try {
            String jdbcUrl = "jdbc:sqlite:" + source.dbPath().toAbsolutePath();
            this.tempDbManager = new DatabaseManager(jdbcUrl);
            this.totalBarCount = tempDbManager.getTotalKLineCount(new Symbol(source.symbol()), "1m");
            logger.info("Found {} total 1-minute bars for {}", totalBarCount, source.displayName());
        } catch (Exception e) {
            logger.error("Failed to load base data info for replay session", e);
            this.totalBarCount = 0;
        }
    }
    
    private void loadDataWindowForCurrentHead() {
        if (tempDbManager == null || totalBarCount == 0 || replayHeadIndex < 0) {
            baseOneMinuteDataWindow = Collections.emptyList();
            return;
        }

        int newWindowStart = Math.max(0, replayHeadIndex - (DATA_WINDOW_SIZE / 2));
        newWindowStart = Math.min(newWindowStart, totalBarCount - DATA_WINDOW_SIZE);
        newWindowStart = Math.max(0, newWindowStart);

        int newWindowSize = Math.min(DATA_WINDOW_SIZE, totalBarCount - newWindowStart);
        if (newWindowSize <= 0) {
            baseOneMinuteDataWindow = Collections.emptyList();
            return;
        }

        this.dataWindowStartIndex = newWindowStart;
        this.baseOneMinuteDataWindow = tempDbManager.getKLinesByIndex(new Symbol(currentSource.symbol()), "1m", newWindowStart, newWindowSize);
    }

    public void play() {
        if (isReplayFinished()) return;
        if (!isPlaying) {
            isPlaying = true;
            playbackTimer.start();
            notifyStateChanged();
        }
    }

    public void pause() {
        if (isPlaying) {
            isPlaying = false;
            playbackTimer.stop();
            notifyStateChanged();
        }
    }

    public void togglePlayPause() {
        if (isPlaying) pause(); else play();
    }

    public void nextBar() {
        if (isReplayFinished()) {
            pause();
            return;
        }
        
        replayHeadIndex++;

        int currentWindowEnd = dataWindowStartIndex + baseOneMinuteDataWindow.size();
        if (replayHeadIndex >= currentWindowEnd && !isReplayFinished()) {
            loadDataWindowForCurrentHead();
        }

        notifyTick();

        // Trigger auto-save logic ---
        barsSinceLastAutoSave++;
        if (barsSinceLastAutoSave >= SettingsManager.getInstance().getAutoSaveInterval()) {
            performAutoSave();
            barsSinceLastAutoSave = 0;
        }

        if (isReplayFinished()) {
            logger.info("Replay session finished.");
            pause();
        }
    }

    public void setSpeed(int delayMs) {
        this.speedInMs = Math.max(1, delayMs);
        playbackTimer.setInitialDelay(this.speedInMs);
        playbackTimer.setDelay(this.speedInMs);
    }

    public void addListener(ReplayStateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            if (replayHeadIndex >= 0) {
                 listener.onReplaySessionStart();
            }
        }
    }

    public void removeListener(ReplayStateListener listener) {
        listeners.remove(listener);
    }

    private void notifySessionStart() {
        if (replayHeadIndex < 0) return;
        for (ReplayStateListener listener : listeners) {
            listener.onReplaySessionStart();
        }
    }

    private void notifyTick() {
        KLine newBar = getCurrentBar();
        if (newBar != null) {
            for (ReplayStateListener listener : listeners) {
                listener.onReplayTick(newBar);
            }
        }
    }

    private void notifyStateChanged() {
        for (ReplayStateListener listener : listeners) {
            listener.onReplayStateChanged();
        }
    }

    public KLine getCurrentBar() {
        if (replayHeadIndex < 0 || replayHeadIndex >= totalBarCount) return null;
        int relativeIndex = replayHeadIndex - dataWindowStartIndex;
        if(relativeIndex < 0 || relativeIndex >= baseOneMinuteDataWindow.size()){
            logger.warn("Data for replay head index {} is not in the current window. Reloading.", replayHeadIndex);
            loadDataWindowForCurrentHead();
            relativeIndex = replayHeadIndex - dataWindowStartIndex;
            if (relativeIndex < 0 || relativeIndex >= baseOneMinuteDataWindow.size()) {
                logger.error("FATAL: Failed to load correct data window for replay head {}.", replayHeadIndex);
                pause();
                return null;
            }
        }
        return baseOneMinuteDataWindow.get(relativeIndex);
    }
    
    public List<KLine> getOneMinuteBars(int fromIndex, int count) {
        if (tempDbManager == null || totalBarCount == 0 || fromIndex < 0 || count <= 0) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + count, totalBarCount);
        if (fromIndex >= toIndex) {
            return Collections.emptyList();
        }
        int actualCount = toIndex - fromIndex;
        return tempDbManager.getKLinesByIndex(new Symbol(currentSource.symbol()), "1m", fromIndex, actualCount);
    }
    
    public int findClosestM1IndexForTimestamp(Instant time) {
        if (tempDbManager == null || currentSource == null) {
            return -1;
        }
        return tempDbManager.findClosestTimestampIndex(new Symbol(currentSource.symbol()), "1m", time);
    }

    public void jumpToNextDay() {
        KLine currentBar = getCurrentBar();
        if (currentBar == null || isReplayFinished()) {
            return;
        }

        if (isPlaying) {
            pause();
        }

        LocalTime fastForwardTime = SettingsManager.getInstance().getFastForwardTime();
        // FIX: Explicitly specify the UTC zone when creating the ZonedDateTime
        ZonedDateTime nextDayTarget = currentBar.timestamp().atZone(ZoneOffset.UTC)
                .toLocalDate().plusDays(1).atTime(fastForwardTime).atZone(ZoneOffset.UTC);

        Instant nextDayInstant = nextDayTarget.toInstant();
        int nextDayIndex = findClosestM1IndexForTimestamp(nextDayInstant);

        if (nextDayIndex > this.replayHeadIndex) {
            this.replayHeadIndex = nextDayIndex;

            int currentWindowEnd = dataWindowStartIndex + baseOneMinuteDataWindow.size();
            if (replayHeadIndex < dataWindowStartIndex || replayHeadIndex >= currentWindowEnd) {
                loadDataWindowForCurrentHead();
            }

            notifyTick();
            logger.info("Jumped replay forward to next day at {}. New index: {}", fastForwardTime, replayHeadIndex);
        } else {
            logger.warn("Could not jump to the next day. No further data available or index not found.");
        }
    }

    // Performs the silent auto-save ---
    private void performAutoSave() {
        if (currentSource == null) return;

        Optional<File> autoSaveFile = AppDataManager.getAutoSaveFilePath().map(java.nio.file.Path::toFile);
        if (autoSaveFile.isEmpty()) {
            logger.error("Could not determine auto-save file path. Auto-save skipped.");
            return;
        }
        
        // --- VITAL FIX: Get the current bar to ensure the timestamp is up-to-date ---
        KLine currentBar = getCurrentBar();
        Instant lastUpdatedTimestamp = (currentBar != null) ? currentBar.timestamp() : Instant.EPOCH;

        ReplaySessionState state = new ReplaySessionState(
                currentSource.symbol(),
                replayHeadIndex,
                PaperTradingService.getInstance().getAccountBalance(),
                PaperTradingService.getInstance().getOpenPositions(),
                PaperTradingService.getInstance().getPendingOrders(),
                PaperTradingService.getInstance().getTradeHistory(),
                DrawingManager.getInstance().getAllDrawings(),
                lastUpdatedTimestamp // Use the up-to-date timestamp here
        );

        try {
            SessionManager.getInstance().saveSession(state, autoSaveFile.get());
            // This log is very frequent, so keep it at DEBUG level.
            logger.debug("Auto-save completed for session {}", currentSource.symbol());
        } catch (Exception e) {
            logger.error("Auto-save failed for session {}", currentSource.symbol(), e);
        }
    }

    public boolean isPlaying() { return isPlaying; }
    
    public boolean isReplayFinished() {
        return totalBarCount == 0 || replayHeadIndex >= totalBarCount - 1;
    }
    
    public ChartDataSource getCurrentSource() { return currentSource; }
    
    public int getReplayHeadIndex() { return replayHeadIndex; }
    
    public int getTotalBarCount() { return totalBarCount; }
}
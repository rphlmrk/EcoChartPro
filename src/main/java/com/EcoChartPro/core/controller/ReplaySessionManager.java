package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.utils.AppDataManager;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.File;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ReplaySessionManager {

    private static final Logger logger = LoggerFactory.getLogger(ReplaySessionManager.class);
    private static volatile ReplaySessionManager instance;

    // --- State is now managed per symbol ---
    private record SymbolReplayContext(
        ChartDataSource source,
        int totalBarCount,
        int replayHeadIndex,
        List<KLine> baseOneMinuteDataWindow,
        int dataWindowStartIndex,
        DatabaseManager dbManager
    ) {}

    private final Map<String, SymbolReplayContext> contextsBySymbol = new ConcurrentHashMap<>();
    private String activeSymbol;

    private static final int DATA_WINDOW_SIZE = 10000;

    private boolean isPlaying = false;
    private int speedInMs = 1000;
    
    private final ScheduledExecutorService playbackExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> playbackTask;
    private final List<ReplayStateListener> listeners = new CopyOnWriteArrayList<>();
    private int barsSinceLastAutoSave = 0;

    private ReplaySessionManager() {
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
        logger.info("Starting multi-symbol replay session from saved state.");
        cleanupPreviousSession();
        this.isPlaying = false;
        if (playbackTask != null) playbackTask.cancel(false);

        if (state.symbolStates() != null) {
            for (Map.Entry<String, SymbolSessionState> entry : state.symbolStates().entrySet()) {
                String symbol = entry.getKey();
                SymbolSessionState symbolState = entry.getValue();
                
                DataSourceManager.getInstance().getAvailableSources().stream()
                    .filter(s -> s.symbol().equalsIgnoreCase(symbol)).findFirst()
                    .ifPresent(source -> {
                        SymbolReplayContext newContext = createInitialContext(source, symbolState.replayHeadIndex());
                        contextsBySymbol.put(symbol, newContext);
                        logger.info("Restored context for symbol: {}. Resuming at index {}.", symbol, symbolState.replayHeadIndex());
                    });
            }
        }
        
        // Switch to the last active symbol and load its data window
        switchActiveSymbol(state.lastActiveSymbol());
    }

    public void startSession(ChartDataSource source, int startIndex) {
        logger.info("Starting new multi-symbol replay session for {} at index {}", source.displayName(), startIndex);
        cleanupPreviousSession();
        this.isPlaying = false;
        if (playbackTask != null) playbackTask.cancel(false);
        
        SymbolReplayContext newContext = createInitialContext(source, startIndex);
        contextsBySymbol.put(source.symbol(), newContext);
        
        switchActiveSymbol(source.symbol());
    }
    
    /**
     * [NEW] The central method for changing the active symbol during a replay session.
     * @param newSymbol The symbol identifier to switch to.
     */
    public void switchActiveSymbol(String newSymbol) {
        if (newSymbol == null || newSymbol.equals(activeSymbol)) {
            return;
        }
        logger.info("Switching active replay symbol to: {}", newSymbol);
        pause();
        this.activeSymbol = newSymbol;
        
        // Ensure context exists, creating if it's the first time we see this symbol
        contextsBySymbol.computeIfAbsent(newSymbol, k -> {
            Optional<ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(k)).findFirst();
            if (sourceOpt.isPresent()) {
                return createInitialContext(sourceOpt.get(), 0);
            }
            logger.error("Could not find data source for symbol {} during switch.", k);
            return null;
        });

        // Load data for the newly active symbol
        loadDataWindowForSymbol(newSymbol);

        // Coordinate the switch with other services
        PaperTradingService.getInstance().switchActiveSymbol(newSymbol);
        DrawingManager.getInstance().setActiveSymbol(newSymbol);
        
        // Notify UI elements (like ChartPanel) to reload data for the new symbol
        notifySessionStart();
    }
    
    private SymbolReplayContext createInitialContext(ChartDataSource source, int startIndex) {
        try {
            String jdbcUrl = "jdbc:sqlite:" + source.dbPath().toAbsolutePath();
            DatabaseManager dbManager = new DatabaseManager(jdbcUrl);
            int totalBarCount = dbManager.getTotalKLineCount(new Symbol(source.symbol()), "1m");
            int validatedStartIndex = Math.max(0, Math.min(startIndex, totalBarCount - 1));

            return new SymbolReplayContext(
                source, totalBarCount, validatedStartIndex, 
                new ArrayList<>(), 0, dbManager
            );
        } catch (Exception e) {
            logger.error("Failed to create replay context for {}", source.symbol(), e);
            return null;
        }
    }

    private void cleanupPreviousSession() {
        for (SymbolReplayContext context : contextsBySymbol.values()) {
            if (context.dbManager() != null) {
                context.dbManager().close();
            }
        }
        contextsBySymbol.clear();
        activeSymbol = null;
    }
    
    private void loadDataWindowForSymbol(String symbol) {
        SymbolReplayContext context = contextsBySymbol.get(symbol);
        if (context == null || context.totalBarCount() == 0 || context.replayHeadIndex() < 0) {
            return;
        }

        int newWindowStart = Math.max(0, context.replayHeadIndex() - (DATA_WINDOW_SIZE / 2));
        newWindowStart = Math.min(newWindowStart, context.totalBarCount() - DATA_WINDOW_SIZE);
        newWindowStart = Math.max(0, newWindowStart);

        int newWindowSize = Math.min(DATA_WINDOW_SIZE, context.totalBarCount() - newWindowStart);
        if (newWindowSize <= 0) {
            SymbolReplayContext updatedContext = new SymbolReplayContext(context.source(), context.totalBarCount(), context.replayHeadIndex(), Collections.emptyList(), newWindowStart, context.dbManager());
            contextsBySymbol.put(symbol, updatedContext);
            return;
        }

        List<KLine> newWindow = context.dbManager().getKLinesByIndex(new Symbol(symbol), "1m", newWindowStart, newWindowSize);
        SymbolReplayContext updatedContext = new SymbolReplayContext(context.source(), context.totalBarCount(), context.replayHeadIndex(), newWindow, newWindowStart, context.dbManager());
        contextsBySymbol.put(symbol, updatedContext);
    }

    public void play() {
        if (isReplayFinished() || isPlaying) return;
        isPlaying = true;
        playbackTask = playbackExecutor.scheduleAtFixedRate(this::nextBar, 0, speedInMs, TimeUnit.MILLISECONDS);
        notifyStateChanged();
    }

    public void pause() {
        if (isPlaying) {
            isPlaying = false;
            if (playbackTask != null) {
                playbackTask.cancel(false);
            }
            notifyStateChanged();
        }
    }

    public void togglePlayPause() {
        if (isPlaying) pause(); else play();
    }

    public void nextBar() {
        if (isReplayFinished() || activeSymbol == null) {
            pause();
            return;
        }
        
        SymbolReplayContext currentContext = contextsBySymbol.get(activeSymbol);
        int newHeadIndex = currentContext.replayHeadIndex() + 1;

        int currentWindowEnd = currentContext.dataWindowStartIndex() + currentContext.baseOneMinuteDataWindow().size();
        if (newHeadIndex >= currentWindowEnd && !isReplayFinished()) {
            loadDataWindowForSymbol(activeSymbol);
            currentContext = contextsBySymbol.get(activeSymbol); // Refresh context after reload
        }
        
        SymbolReplayContext updatedContext = new SymbolReplayContext(
            currentContext.source(), currentContext.totalBarCount(), newHeadIndex,
            currentContext.baseOneMinuteDataWindow(), currentContext.dataWindowStartIndex(), currentContext.dbManager()
        );
        contextsBySymbol.put(activeSymbol, updatedContext);

        notifyTick();

        barsSinceLastAutoSave++;
        if (barsSinceLastAutoSave >= SettingsManager.getInstance().getAutoSaveInterval()) {
            performAutoSave();
            barsSinceLastAutoSave = 0;
        }

        if (isReplayFinished()) {
            logger.info("Replay session finished for symbol {}.", activeSymbol);
            pause();
        }
    }
    
    private void performAutoSave() {
        Optional<File> autoSaveFile = AppDataManager.getAutoSaveFilePath().map(java.nio.file.Path::toFile);
        if (autoSaveFile.isEmpty()) {
            logger.error("Could not determine auto-save file path. Auto-save skipped.");
            return;
        }
        
        ReplaySessionState state = PaperTradingService.getInstance().getCurrentSessionState();

        try {
            SessionManager.getInstance().saveSession(state, autoSaveFile.get(), true);
            logger.debug("Auto-save completed for multi-symbol session.");
        } catch (Exception e) {
            logger.error("Auto-save failed for multi-symbol session.", e);
        }
    }
    
    // --- Getters now operate on the active symbol's context ---

    public KLine getCurrentBar() {
        if (activeSymbol == null) return null;
        SymbolReplayContext context = contextsBySymbol.get(activeSymbol);
        if (context == null || context.replayHeadIndex() < 0 || context.replayHeadIndex() >= context.totalBarCount()) return null;
        
        int relativeIndex = context.replayHeadIndex() - context.dataWindowStartIndex();
        if (relativeIndex < 0 || relativeIndex >= context.baseOneMinuteDataWindow().size()) {
            logger.warn("Data for replay head index {} is not in the current window for {}. Reloading.", context.replayHeadIndex(), activeSymbol);
            loadDataWindowForSymbol(activeSymbol);
            context = contextsBySymbol.get(activeSymbol); // Refresh context
            relativeIndex = context.replayHeadIndex() - context.dataWindowStartIndex();
            if (relativeIndex < 0 || relativeIndex >= context.baseOneMinuteDataWindow().size()) {
                logger.error("FATAL: Failed to load correct data window for replay head {} on {}", context.replayHeadIndex(), activeSymbol);
                pause();
                return null;
            }
        }
        return context.baseOneMinuteDataWindow().get(relativeIndex);
    }

    public List<KLine> getOneMinuteBars(int fromIndex, int count) {
        if (activeSymbol == null) return Collections.emptyList();
        SymbolReplayContext context = contextsBySymbol.get(activeSymbol);
        if (context == null || context.dbManager() == null || context.totalBarCount() == 0 || fromIndex < 0 || count <= 0) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + count, context.totalBarCount());
        if (fromIndex >= toIndex) return Collections.emptyList();
        int actualCount = toIndex - fromIndex;
        return context.dbManager().getKLinesByIndex(new Symbol(activeSymbol), "1m", fromIndex, actualCount);
    }

    public int findClosestM1IndexForTimestamp(Instant time) {
        if (activeSymbol == null) return -1;
        SymbolReplayContext context = contextsBySymbol.get(activeSymbol);
        if (context == null || context.dbManager() == null) return -1;
        return context.dbManager().findClosestTimestampIndex(new Symbol(activeSymbol), "1m", time);
    }
    
    public String getActiveSymbol() { return activeSymbol; }
    public ChartDataSource getCurrentSource() { return activeSymbol != null ? contextsBySymbol.get(activeSymbol).source() : null; }
    public boolean isPlaying() { return isPlaying; }
    public boolean isReplayFinished() {
        if (activeSymbol == null) return true;
        SymbolReplayContext context = contextsBySymbol.get(activeSymbol);
        return context == null || context.totalBarCount() == 0 || context.replayHeadIndex() >= context.totalBarCount() - 1;
    }
    public int getReplayHeadIndex() {
        if (activeSymbol == null) return -1;
        SymbolReplayContext context = contextsBySymbol.get(activeSymbol);
        return context != null ? context.replayHeadIndex() : -1;
    }
    public int getTotalBarCount() {
        if (activeSymbol == null) return 0;
        SymbolReplayContext context = contextsBySymbol.get(activeSymbol);
        return context != null ? context.totalBarCount() : 0;
    }

    // --- NEW Getters for multi-symbol state building ---
    public int getReplayHeadIndex(String symbol) {
        SymbolReplayContext context = contextsBySymbol.get(symbol);
        return (context != null) ? context.replayHeadIndex() : -1;
    }
    public Instant getLastTimestamp(String symbol) {
        SymbolReplayContext context = contextsBySymbol.get(symbol);
        if (context == null || context.replayHeadIndex() < 0) return null;
        int relativeIndex = context.replayHeadIndex() - context.dataWindowStartIndex();
        if (relativeIndex >= 0 && relativeIndex < context.baseOneMinuteDataWindow().size()) {
            return context.baseOneMinuteDataWindow().get(relativeIndex).timestamp();
        }
        return null;
    }
    public Set<String> getAllKnownSymbols() {
        return contextsBySymbol.keySet();
    }
    
    // --- Boilerplate and other methods ---
    public void setSpeed(int delayMs) {
        this.speedInMs = Math.max(1, delayMs);
        if (isPlaying) {
            pause();
            play();
        }
    }
    public void addListener(ReplayStateListener listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    public void removeListener(ReplayStateListener listener) { listeners.remove(listener); }
    private void notifySessionStart() { SwingUtilities.invokeLater(() -> { for (ReplayStateListener listener : listeners) listener.onReplaySessionStart(); }); }
    private void notifyTick() { KLine newBar = getCurrentBar(); if (newBar != null) SwingUtilities.invokeLater(() -> { for (ReplayStateListener listener : listeners) listener.onReplayTick(newBar); }); }
    private void notifyStateChanged() { SwingUtilities.invokeLater(() -> { for (ReplayStateListener listener : listeners) listener.onReplayStateChanged(); }); }

    public void jumpToNextDay() {
        if (activeSymbol == null) return;
        KLine currentBar = getCurrentBar();
        if (currentBar == null || isReplayFinished()) return;
        if (isPlaying) pause();
        LocalTime fastForwardTime = SettingsManager.getInstance().getFastForwardTime();
        ZonedDateTime nextDayTarget = currentBar.timestamp().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atTime(fastForwardTime).atZone(ZoneOffset.UTC);
        int nextDayIndex = findClosestM1IndexForTimestamp(nextDayTarget.toInstant());

        SymbolReplayContext currentContext = contextsBySymbol.get(activeSymbol);
        if (nextDayIndex > currentContext.replayHeadIndex()) {
            SymbolReplayContext updatedContext = new SymbolReplayContext(currentContext.source(), currentContext.totalBarCount(), nextDayIndex, currentContext.baseOneMinuteDataWindow(), currentContext.dataWindowStartIndex(), currentContext.dbManager());
            contextsBySymbol.put(activeSymbol, updatedContext);
            loadDataWindowForSymbol(activeSymbol);
            notifyTick();
            logger.info("Jumped replay for {} forward to next day at {}. New index: {}", activeSymbol, fastForwardTime, nextDayIndex);
        } else {
            logger.warn("Could not jump {} to the next day. No further data available or index not found.", activeSymbol);
        }
    }

    public void shutdown() {
        playbackExecutor.shutdownNow();
    }
}
package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.core.settings.SettingsService;
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
    private final java.beans.PropertyChangeSupport pcs = new java.beans.PropertyChangeSupport(this);

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
                        if (source.dbPath() != null) {
                            SymbolReplayContext newContext = createInitialContext(source, symbolState.replayHeadIndex());
                            if (newContext != null) {
                                contextsBySymbol.put(symbol, newContext);
                                logger.info("Restored context for symbol: {}. Resuming at index {}.", symbol, symbolState.replayHeadIndex());
                            }
                        } else {
                            logger.warn("Skipping restoration of symbol '{}' from session state because it has no local data file for replay.", symbol);
                        }
                    });
            }
        }
        
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
    
    public void switchActiveSymbol(String newSymbol) {
        if (newSymbol == null || newSymbol.equals(activeSymbol)) {
            return;
        }
        logger.info("Switching active replay symbol to: {}", newSymbol);
        pause();
        this.activeSymbol = newSymbol;
        
        contextsBySymbol.computeIfAbsent(newSymbol, k -> {
            Optional<ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(k)).findFirst();
            if (sourceOpt.isPresent()) {
                ChartDataSource source = sourceOpt.get();
                if (source.dbPath() != null) {
                    return createInitialContext(source, 0);
                } else {
                    logger.error("Cannot switch to symbol '{}' in replay mode as it has no local data file.", k);
                    return null;
                }
            }
            logger.error("Could not find data source for symbol {} during switch.", k);
            return null;
        });
        
        if (contextsBySymbol.get(newSymbol) == null) {
            Optional<String> fallbackSymbol = contextsBySymbol.keySet().stream().findFirst();
            if (fallbackSymbol.isPresent()) {
                logger.warn("Falling back to symbol '{}' as the requested symbol was not valid for replay.", fallbackSymbol.get());
                switchActiveSymbol(fallbackSymbol.get());
            } else {
                logger.error("No valid replayable symbols available in the current session.");
                this.activeSymbol = null;
            }
            return;
        }

        loadDataWindowForSymbol(newSymbol);

        // The responsibility of updating context-specific services is now
        // on the listeners (e.g., ChartWorkspacePanel). This manager just signals the change.
        notifySessionStart();
    }
    
    private SymbolReplayContext createInitialContext(ChartDataSource source, int startIndex) {
        if (source.dbPath() == null) {
            logger.error("Attempted to create replay context for source '{}' which has no dbPath.", source.symbol());
            return null;
        }
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
            if (context != null && context.dbManager() != null) {
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
        
        // [REMOVED] Auto-save logic is moved to ReplayController
        
        if (isReplayFinished()) {
            logger.info("Replay session finished for symbol {}.", activeSymbol);
            pause();
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
    public ChartDataSource getCurrentSource() { 
        if (activeSymbol == null) return null;
        SymbolReplayContext context = contextsBySymbol.get(activeSymbol);
        return context != null ? context.source() : null; 
    }
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
        LocalTime fastForwardTime = SettingsService.getInstance().getFastForwardTime();
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
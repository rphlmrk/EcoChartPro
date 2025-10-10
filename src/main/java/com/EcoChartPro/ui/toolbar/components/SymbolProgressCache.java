package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A singleton cache for storing the backtesting progress of each symbol.
 * This data is loaded once at startup to ensure the symbol selection UI is responsive.
 */
public final class SymbolProgressCache {

    private static final Logger logger = LoggerFactory.getLogger(SymbolProgressCache.class);
    private static volatile SymbolProgressCache instance;

    /**
     * A record to hold the combined information for a symbol.
     * @param source The ChartDataSource object.
     * @param progressPercent The calculated backtesting progress (0-100).
     */
    public record SymbolProgressInfo(ChartDataSource source, int progressPercent) {}

    private final Map<String, SymbolProgressInfo> progressCache = new ConcurrentHashMap<>();

    private SymbolProgressCache() {
        // Private constructor
    }

    public static SymbolProgressCache getInstance() {
        if (instance == null) {
            synchronized (SymbolProgressCache.class) {
                if (instance == null) {
                    instance = new SymbolProgressCache();
                }
            }
        }
        return instance;
    }

    /**
     * Loads or reloads the progress data for all available symbols.
     * This should be called on a background thread during application startup.
     */
    public void buildCache() {
        logger.info("Building symbol progress cache...");
        progressCache.clear();
        List<ChartDataSource> allSources = DataSourceManager.getInstance().getAvailableSources();

        // [MODIFIED] Logic to handle new multi-symbol session state
        Optional<ReplaySessionState> latestSessionOpt = SessionManager.getInstance().getLatestSessionState();

        for (ChartDataSource source : allSources) {
            int progress = 0;
            if (latestSessionOpt.isPresent()) {
                ReplaySessionState session = latestSessionOpt.get();
                if (session.symbolStates() != null) {
                    SymbolSessionState symbolState = session.symbolStates().get(source.symbol());
                    if (symbolState != null) {
                        progress = calculateProgress(source, symbolState.replayHeadIndex());
                    }
                }
            }
            progressCache.put(source.symbol(), new SymbolProgressInfo(source, progress));
        }
        logger.info("Symbol progress cache built successfully with {} entries.", progressCache.size());
    }

    /**
     * [DEPRECATED] Efficiently updates the progress for a single symbol in the cache from a legacy state.
     * This will be removed once the old ReplaySessionState is fully phased out from all call sites.
     */
    @Deprecated
    public void updateProgressForSymbol(String symbol, ReplaySessionState state) {
        // This is a temporary method to handle calls that might still use the old state object.
        // The old state object no longer has a replayHeadIndex, so this method is now a no-op.
        // The correct update path is the overload that takes a SymbolSessionState.
        logger.warn("Called deprecated updateProgressForSymbol with ReplaySessionState. Update logic has been moved.");
    }
    
    /**
     * [NEW OVERLOAD] Efficiently updates the progress for a single symbol in the cache.
     * @param symbol The symbol identifier (e.g., "btcusdt").
     * @param state  The symbol-specific session state that was just saved.
     */
    public void updateProgressForSymbol(String symbol, SymbolSessionState state) {
        if (symbol == null || state == null) return;
        updateProgress(symbol, state.replayHeadIndex());
    }

    private void updateProgress(String symbol, int replayHeadIndex) {
        // Find the corresponding ChartDataSource for this symbol
        Optional<ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(symbol)).findFirst();

        if (sourceOpt.isEmpty()) {
            logger.warn("Cannot update progress cache for symbol '{}' because its data source was not found.", symbol);
            return;
        }
        
        int progress = calculateProgress(sourceOpt.get(), replayHeadIndex);
        progressCache.put(symbol, new SymbolProgressInfo(sourceOpt.get(), progress));
        logger.debug("Live-updated progress cache for {}: {}%", symbol, progress);
    }

    private int calculateProgress(ChartDataSource source, int replayHeadIndex) {
        int progress = 0;
        try (DatabaseManager tempDb = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
            int totalBars = tempDb.getTotalKLineCount(new Symbol(source.symbol()), "1m");
            if (totalBars > 0) {
                progress = (int) Math.min(100L, Math.round(((double) replayHeadIndex / totalBars) * 100.0));
            }
        } catch (Exception e) {
            logger.warn("Could not read total bar count for progress calculation on {}", source.symbol(), e);
        }
        return progress;
    }

    /**
     * Retrieves the progress information for all symbols from the cache.
     * @return An unmodifiable list of SymbolProgressInfo objects.
     */
    public List<SymbolProgressInfo> getAllProgressInfo() {
        return Collections.unmodifiableList(new ArrayList<>(progressCache.values()));
    }
}
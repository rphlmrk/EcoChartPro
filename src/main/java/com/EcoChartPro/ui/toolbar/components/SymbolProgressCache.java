package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        SessionManager sessionManager = SessionManager.getInstance();

        for (ChartDataSource source : allSources) {
            int progress = 0;
            Optional<ReplaySessionState> latestStateOpt = sessionManager.getLatestSessionStateForSymbol(source.symbol());
            if (latestStateOpt.isPresent()) {
                ReplaySessionState state = latestStateOpt.get();
                // Use a temporary DB manager to get total bar count
                try (DatabaseManager tempDb = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
                    int totalBars = tempDb.getTotalKLineCount(new Symbol(source.symbol()), "1m");
                    if (totalBars > 0) {
                        progress = (int) Math.min(100L, Math.round(((double) state.replayHeadIndex() / totalBars) * 100.0));
                    }
                } catch (Exception e) {
                    logger.warn("Could not read total bar count for progress calculation on {}", source.symbol(), e);
                }
            }
            progressCache.put(source.symbol(), new SymbolProgressInfo(source, progress));
        }
        logger.info("Symbol progress cache built successfully with {} entries.", progressCache.size());
    }

    /**
     * [NEW] Efficiently updates the progress for a single symbol in the cache.
     * This is called whenever a session is saved.
     *
     * @param symbol The symbol identifier (e.g., "btcusdt").
     * @param state  The session state that was just saved.
     */
    public void updateProgressForSymbol(String symbol, ReplaySessionState state) {
        if (symbol == null || state == null) return;

        // Find the corresponding ChartDataSource for this symbol
        Optional<ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(symbol)).findFirst();

        if (sourceOpt.isEmpty()) {
            logger.warn("Cannot update progress cache for symbol '{}' because its data source was not found.", symbol);
            return;
        }
        ChartDataSource source = sourceOpt.get();

        int progress = 0;
        try (DatabaseManager tempDb = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
            int totalBars = tempDb.getTotalKLineCount(new Symbol(symbol), "1m");
            if (totalBars > 0) {
                progress = (int) Math.min(100L, Math.round(((double) state.replayHeadIndex() / totalBars) * 100.0));
            }
        } catch (Exception e) {
            logger.warn("Could not read total bar count for progress update on {}", symbol, e);
        }

        progressCache.put(symbol, new SymbolProgressInfo(source, progress));
        logger.debug("Live-updated progress cache for {}: {}%", symbol, progress);
    }

    /**
     * Retrieves the progress information for all symbols from the cache.
     * @return An unmodifiable list of SymbolProgressInfo objects.
     */
    public List<SymbolProgressInfo> getAllProgressInfo() {
        return Collections.unmodifiableList(progressCache.values().stream().collect(Collectors.toList()));
    }
}
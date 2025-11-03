package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.core.settings.SettingsManager;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
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

        // Logic to handle new multi-symbol session state
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

    @Deprecated
    public void updateProgressForSymbol(String symbol, ReplaySessionState state) {
        logger.warn("Called deprecated updateProgressForSymbol with ReplaySessionState. Update logic has been moved.");
    }
    
    /**
     * Efficiently updates the progress for a single symbol in the cache.
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
        // Ensure source is a local file before proceeding
        if (source.dbPath() == null) {
            return 0;
        }
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

    /**
     * Retrieves a filtered list of symbol information based on various criteria.
     * This is the centralized filtering logic for any UI component that needs to search for symbols.
     * The results are sorted with favorite symbols appearing first.
     * @param query The search text to filter by display name (case-insensitive).
     * @param selectedProvider The provider/exchange name to filter by. Use "All" to ignore this filter.
     * @param showOnlyFavorites If true, only returns symbols marked as favorites.
     * @param isReplayMode If true, returns only local file-based data sources. If false, returns only live/remote sources.
     * @return A new list of SymbolProgressInfo objects that match the criteria, sorted by favorite status then display name.
     */
    public List<SymbolProgressInfo> getFilteredProgressInfo(String query, String selectedProvider, boolean showOnlyFavorites, boolean isReplayMode) {
        String lowerCaseQuery = (query != null) ? query.toLowerCase().trim() : "";
        SettingsManager sm = SettingsManager.getInstance();

        return progressCache.values().stream()
                .filter(info -> {
                    // Filter 1: Match the mode (Replay vs. Live)
                    boolean isLocalData = info.source().dbPath() != null;
                    return isReplayMode == isLocalData;
                })
                .filter(info -> {
                    // Filter 2: Match favorites if the filter is active
                    return !showOnlyFavorites || sm.isFavoriteSymbol(info.source().symbol());
                })
                .filter(info -> {
                    // Filter 3: Match the provider/exchange if not "All"
                    return "All".equalsIgnoreCase(selectedProvider) || selectedProvider.equals(info.source().providerName());
                })
                .filter(info -> {
                    // Filter 4: Match the search text
                    return lowerCaseQuery.isEmpty() || info.source().displayName().toLowerCase().contains(lowerCaseQuery);
                })
                .sorted(Comparator.comparing((SymbolProgressInfo info) -> !sm.isFavoriteSymbol(info.source().symbol()))
                                  .thenComparing(info -> info.source().displayName()))
                .collect(Collectors.toList());
    }
}
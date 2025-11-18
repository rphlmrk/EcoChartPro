package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.core.trading.SessionType;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SymbolProgressCache {

    private static final Logger logger = LoggerFactory.getLogger(SymbolProgressCache.class);
    private static volatile SymbolProgressCache instance;
    private final Map<String, SymbolProgress> progressMap = new ConcurrentHashMap<>();

    public record SymbolProgress(
            String symbol,
            String displayName,
            String providerName,
            int totalBars,
            int headIndex,
            double progressPercentage,
            Instant lastTimestamp,
            ChartDataSource source // Keep a reference to the full source object
    ) {}

    private SymbolProgressCache() {
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

    public void buildCache() {
        logger.info("Building symbol progress cache...");
        progressMap.clear();

        List<ChartDataSource> allSources = DataSourceManager.getInstance().getAvailableSources();
        for (ChartDataSource source : allSources) {
            // Initialize all symbols with 0 progress. Fetch total bars now.
            int totalBars = getTotalBarCount(source);
            SymbolProgress initialProgress = new SymbolProgress(
                source.symbol(), source.displayName(), source.providerName(), totalBars, -1, 0.0, null, source
            );
            progressMap.put(source.symbol(), initialProgress);
        }

        // Use the new, robust getLatestSession() method.
        Optional<SessionManager.LatestSessionResult> latestSessionOpt = SessionManager.getInstance().getLatestSession();

        if (latestSessionOpt.isPresent()) {
            ReplaySessionState state = latestSessionOpt.get().state();
            if (state != null && state.symbolStates() != null) {
                state.symbolStates().forEach(this::updateProgressForSymbol);
            }
        }

        logger.info("Symbol progress cache built successfully with {} entries.", progressMap.size());
    }
    
    private int getTotalBarCount(ChartDataSource source) {
        if (source.dbPath() == null) {
            return 0; // Live sources don't have a fixed total bar count for progress
        }
        try (DatabaseManager tempDb = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
            return tempDb.getTotalKLineCount(new Symbol(source.symbol()), "1m");
        } catch (Exception e) {
            logger.warn("Could not read total bar count for progress calculation on {}", source.symbol(), e);
            return 0;
        }
    }

    public Optional<SymbolProgress> getProgressForSymbol(String symbol) {
        return Optional.ofNullable(progressMap.get(symbol));
    }

    public List<SymbolProgress> getProgressForAllSymbols() {
        return progressMap.values().stream()
                .sorted(Comparator.comparing(SymbolProgress::displayName))
                .collect(Collectors.toList());
    }

    public void updateProgressForSymbol(String symbol, SymbolSessionState symbolState) {
        if (symbol == null || symbolState == null) return;
        
        SymbolProgress existingProgress = progressMap.get(symbol);
        if (existingProgress == null) {
            logger.warn("Attempted to update progress for an unknown symbol: {}", symbol);
            return;
        }
        
        int totalBars = existingProgress.totalBars();
        int headIndex = symbolState.replayHeadIndex();
        double percentage = (totalBars > 0) ? ((double) (headIndex + 1) / totalBars) * 100 : 0.0;
        
        SymbolProgress updatedProgress = new SymbolProgress(
            symbol,
            existingProgress.displayName(),
            existingProgress.providerName(),
            totalBars,
            headIndex,
            percentage,
            symbolState.lastTimestamp(),
            existingProgress.source()
        );
        progressMap.put(symbol, updatedProgress);
        logger.debug("Live-updated progress cache for {}: {}%", symbol, (int)percentage);
    }
}
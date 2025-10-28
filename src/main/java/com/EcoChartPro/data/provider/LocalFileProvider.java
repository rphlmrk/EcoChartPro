package com.EcoChartPro.data.provider;

import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.TradeTick;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import com.EcoChartPro.utils.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class LocalFileProvider implements DataProvider {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileProvider.class);

    @Override
    public String getProviderName() {
        return "Local Files";
    }

    @Override
    public List<ChartDataSource> getAvailableSymbols() {
        List<ChartDataSource> sources = new ArrayList<>();
        try {
            Path dataDirectoryPath = DataSourceManager.getProjectDataDirectory();
            try (Stream<Path> subdirectories = Files.list(dataDirectoryPath)) {
                subdirectories
                        .filter(Files::isDirectory)
                        .forEach(symbolDir -> processSymbolDirectory(symbolDir, sources));
            }
        } catch (IOException e) {
            logger.error("Failed to scan the local data directory. No local charts will be available.", e);
        }
        return sources;
    }

    @Override
    public List<KLine> getHistoricalData(String symbol, String timeframe, int limit) {
        logger.warn("getHistoricalData called on LocalFileProvider. This is not the primary data loading path for local files.");
        return Collections.emptyList();
    }

    @Override
    public List<TradeTick> getHistoricalTrades(String symbol, long startTimeMillis, int limit) {
        logger.warn("getHistoricalTrades not yet implemented for LocalFileProvider. Tick data must be imported into the local DB first.");
        return Collections.emptyList();
    }

    @Override
    public void connectToLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        logger.debug("connectToLiveStream called on LocalFileProvider, which does not support live data. Ignoring.");
    }

    @Override
    public void disconnectFromLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        logger.debug("disconnectFromLiveStream called on LocalFileProvider. Nothing to disconnect.");
    }

    @Override
    public void connectToTradeStream(String symbol, Consumer<TradeTick> onTradeUpdate) {
        logger.debug("connectToTradeStream not supported by LocalFileProvider.");
    }

    @Override
    public void disconnectFromTradeStream(String symbol, Consumer<TradeTick> onTradeUpdate) {
        // Nothing to do
    }

    private void processSymbolDirectory(Path symbolDir, List<ChartDataSource> sources) {
        String symbolName = symbolDir.getFileName().toString().toLowerCase();
        Optional<Path> dbPathOpt;
        try (Stream<Path> files = Files.list(symbolDir)) {
            dbPathOpt = files.filter(f -> f.toString().toLowerCase().endsWith(".db")).findFirst();
        } catch (IOException e) {
            logger.error("Could not read files in symbol directory: {}", symbolDir, e);
            return;
        }

        if (dbPathOpt.isPresent()) {
            Path dbPath = dbPathOpt.get();
            String displayName = formatDisplayName(symbolName);
            List<String> timeframes = getTimeframesFromDb(dbPath);

            ChartDataSource source = new ChartDataSource(getProviderName(), symbolName, displayName, dbPath.toAbsolutePath(), timeframes);
            sources.add(source);
            logger.info("LocalFileProvider discovered data source: {}", source.displayName() + " (" + String.join(", ", timeframes) + ")");
        } else {
            logger.warn("LocalFileProvider: Directory '{}' does not contain a database (.db) file. Skipping.", symbolName);
        }
    }

    private List<String> getTimeframesFromDb(Path dbPath) {
        try (DatabaseManager tempDbManager = new DatabaseManager("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            return tempDbManager.getDistinctTimeframes();
        } catch (Exception e) {
            logger.error("Could not read timeframes from database: {}. Reason: {}", dbPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String formatDisplayName(String rawSymbol) {
        return rawSymbol.toUpperCase().replace("_", "/");
    }
}
package com.EcoChartPro.utils;

import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.provider.LocalFileProvider;
import com.EcoChartPro.data.provider.BinanceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manages the discovery and state of data sources by delegating to registered DataProviders.
 * This class is responsible for scanning for available chart data for the application.
 * It follows a Singleton pattern.
 */
public class DataSourceManager {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceManager.class);
    private static final String DATA_DIR_NAME = "data";
    
    private static volatile DataSourceManager instance;

    private final List<DataProvider> dataProviders = new ArrayList<>();
    private final List<ChartDataSource> availableSources = new ArrayList<>();

    /**
     * A record representing a discoverable data source for a specific symbol.
     * @param symbol The unique, lowercase identifier (e.g., "btcusdt").
     * @param displayName A user-friendly name (e.g., "BTC/USDT").
     * @param dbPath The absolute path to the symbol's SQLite database file.
     * @param timeframes A list of available timeframes found within the database.
     */
    public record ChartDataSource(String symbol, String displayName, Path dbPath, List<String> timeframes) {
        @Override
        public String toString() {
            if (timeframes.isEmpty()) {
                return displayName + " (No timeframes found)";
            }
            return displayName + " (" + String.join(", ", timeframes) + ")";
        }
    }

    private DataSourceManager() {
        // Register all available data providers here
        dataProviders.add(new LocalFileProvider());
        dataProviders.add(new BinanceProvider());
        // Future providers like `new BinanceProvider()` would be added here.
    }

    public static DataSourceManager getInstance() {
        if (instance == null) {
            synchronized (DataSourceManager.class) {
                if (instance == null) {
                    instance = new DataSourceManager();
                }
            }
        }
        return instance;
    }

    /**
     * Scans all registered DataProviders to find and register all available symbol data sources.
     * This method should be called at startup.
     */
    public void scanDataDirectory() {
        logger.info("Starting data source scan through all registered providers...");
        this.availableSources.clear();

        for (DataProvider provider : dataProviders) {
            logger.debug("Scanning for symbols from provider: {}", provider.getProviderName());
            availableSources.addAll(provider.getAvailableSymbols());
        }
        
        // Sort the combined list for a consistent UI
        availableSources.sort(Comparator.comparing(ChartDataSource::displayName));

        logger.info("Data source scan complete. Found {} total available symbol(s) across {} provider(s).",
                availableSources.size(), dataProviders.size());
    }

    /**
     * Returns an unmodifiable list of all data sources found during the last scan.
     * @return A list of available ChartDataSource objects.
     */
    public List<ChartDataSource> getAvailableSources() {
        return Collections.unmodifiableList(availableSources);
    }

    /**
     * Gets the path to the main data directory where symbol subdirectories are stored.
     * @return The Path to the data directory.
     * @throws IOException If the directory doesn't exist.
     */
    public static Path getProjectDataDirectory() throws IOException {
        String projectRoot = System.getProperty("user.dir");
        Path dataDirectoryPath = Paths.get(projectRoot, DATA_DIR_NAME);
        if (Files.notExists(dataDirectoryPath) || !Files.isDirectory(dataDirectoryPath)) {
            throw new IOException("Data directory not found at the expected location: " + dataDirectoryPath.toAbsolutePath() +
                    "\nPlease ensure a '" + DATA_DIR_NAME + "' folder exists at the root of the application.");
        }
        return dataDirectoryPath;
    }
}
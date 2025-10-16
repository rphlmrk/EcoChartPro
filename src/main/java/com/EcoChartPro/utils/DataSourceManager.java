package com.EcoChartPro.utils;

import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.provider.LocalFileProvider;
import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.data.provider.OkxProvider;
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

public class DataSourceManager {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceManager.class);
    private static final String DATA_DIR_NAME = "data";
    
    private static volatile DataSourceManager instance;

    private final List<DataProvider> dataProviders = new ArrayList<>();
    private final List<ChartDataSource> availableSources = new ArrayList<>();

    /**
     * [MODIFIED] A record representing a discoverable data source for a specific symbol.
     * @param providerName The name of the DataProvider (e.g., "Binance", "OKX").
     * @param symbol The unique, lowercase identifier (e.g., "btcusdt", "btc-usdt-swap").
     * @param displayName A user-friendly name (e.g., "BTC/USDT", "BTC/USD-SWAP").
     * @param dbPath The absolute path to the symbol's SQLite database file.
     * @param timeframes A list of available timeframes found within the database.
     */
    public record ChartDataSource(String providerName, String symbol, String displayName, Path dbPath, List<String> timeframes) {
        @Override
        public String toString() {
            // [FIX] Always use the displayName for the JComboBox representation.
            return displayName;
        }
    }

    private DataSourceManager() {
        dataProviders.add(new LocalFileProvider());
        dataProviders.add(new BinanceProvider());
        dataProviders.add(new OkxProvider());
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
    
    public void scanDataDirectory() {
        logger.info("Starting data source scan through all registered providers...");
        this.availableSources.clear();

        for (DataProvider provider : dataProviders) {
            logger.debug("Scanning for symbols from provider: {}", provider.getProviderName());
            availableSources.addAll(provider.getAvailableSymbols());
        }
        
        availableSources.sort(Comparator.comparing(ChartDataSource::displayName));

        logger.info("Data source scan complete. Found {} total available symbol(s) across {} provider(s).",
                availableSources.size(), dataProviders.size());
    }
    
    public List<ChartDataSource> getAvailableSources() {
        return Collections.unmodifiableList(availableSources);
    }
    
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
package com.EcoChartPro.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Manages the discovery and state of local data sources.
 * This class is responsible for scanning the file system to find available
 * chart data for the application. It follows a Singleton pattern.
 */
public class DataSourceManager {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceManager.class);
    private static final String DATA_DIR_NAME = "data";
    
    private static volatile DataSourceManager instance;

    private final List<ChartDataSource> availableSources = new ArrayList<>();

    public record ChartDataSource(String symbol, String displayName, Path dbPath, List<String> timeframes) {
        @Override
        public String toString() {
            if (timeframes.isEmpty()) {
                return displayName + " (No timeframes found)";
            }
            return displayName + " (" + String.join(", ", timeframes) + ")";
        }
    }

    private DataSourceManager() {}

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
        this.availableSources.clear();
        logger.info("Starting data source scan...");

        try {
            Path dataDirectoryPath = getProjectDataDirectory();
            try (Stream<Path> subdirectories = Files.list(dataDirectoryPath)) {
                subdirectories
                    .filter(Files::isDirectory)
                    .forEach(this::processSymbolDirectory);
            }
            logger.info("Data source scan complete. Found {} available symbol(s).", availableSources.size());
        } catch (IOException e) {
            logger.error("Failed to scan the data directory. No local charts will be available.", e);
        }
    }

    private void processSymbolDirectory(Path symbolDir) {
        String symbolName = symbolDir.getFileName().toString().toLowerCase();
        
        // Scan for *any* .db file instead of a hardcoded name ---
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

            ChartDataSource source = new ChartDataSource(symbolName, displayName, dbPath.toAbsolutePath(), timeframes);
            availableSources.add(source);
            logger.info("Discovered data source: {}", source);
        } else {
            logger.warn("Directory '{}' does not contain a database (.db) file. Skipping.", symbolName);
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
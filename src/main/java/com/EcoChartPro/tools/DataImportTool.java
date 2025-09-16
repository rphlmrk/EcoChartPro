package com.EcoChartPro.tools;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.utils.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A powerful command-line tool to automate the import of raw CSV data into the
 * application's required SQLite database format. This tool can also be used as a
 * service by other parts of the application.
 */
public class DataImportTool {

    private static final Logger logger = LoggerFactory.getLogger(DataImportTool.class);
    private static final int BATCH_SIZE = 10000;
    private static final String IMPORT_DIR_NAME = "import_data";
    private static final String PROCESSED_DIR_NAME = "processed";
    private static final String DATA_DIR_NAME = "data";

    // NEW: A record to pass progress updates from the tool to the caller.
    public record ProgressUpdate(String status, int percentage) {}
    private final Consumer<ProgressUpdate> progressConsumer;

    public static void main(String[] args) {
        System.out.println("--- EcoChartPro Data Import Tool ---");
        try {
            new DataImportTool(null).run(); // No progress consumer for CLI
            System.out.println("\n--- Import process completed successfully! ---");
        } catch (Exception e) {
            System.err.println("\n--- An error occurred during the import process ---");
            e.printStackTrace();
        }
    }

    /**
     * Constructs the DataImportTool.
     * @param progressConsumer A consumer to receive real-time progress updates. Can be null.
     */
    public DataImportTool(Consumer<ProgressUpdate> progressConsumer) {
        this.progressConsumer = progressConsumer != null ? progressConsumer : (p) -> {}; // No-op default
    }

    public void run() throws IOException {
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path importDir = projectRoot.resolve(IMPORT_DIR_NAME);
        Path dataDir = projectRoot.resolve(DATA_DIR_NAME);
        Path processedDir = importDir.resolve(PROCESSED_DIR_NAME);

        if (Files.notExists(importDir)) return;
        Files.createDirectories(dataDir);
        Files.createDirectories(processedDir);

        Map<String, List<Path>> groupedFiles = findAndGroupCsvFiles(importDir);

        if (groupedFiles.isEmpty()) return;

        for (Map.Entry<String, List<Path>> entry : groupedFiles.entrySet()) {
            String baseSymbol = entry.getKey();
            List<Path> filesToImport = entry.getValue();
            
            Path symbolDataDir = dataDir.resolve(baseSymbol);
            Files.createDirectories(symbolDataDir);
            String dbFileName = baseSymbol + ".db";
            Path dbPath = symbolDataDir.resolve(dbFileName);
            String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            
            try (DatabaseManager dbManager = new DatabaseManager(jdbcUrl)) {
                for (Path csvFile : filesToImport) {
                    importCsvFile(csvFile, baseSymbol, dbManager);
                    moveFileToProcessed(csvFile, processedDir);
                }
            }
        }
    }

    private void importCsvFile(Path csvFile, String symbolId, DatabaseManager dbManager) throws IOException {
        progressConsumer.accept(new ProgressUpdate("Processing: " + csvFile.getFileName(), 0));
        Symbol symbol = new Symbol(symbolId);
        long totalLines = Files.lines(csvFile).count();
        if (totalLines <= 1) return;
        
        List<KLine> klineBatch = new ArrayList<>(BATCH_SIZE);
        long lineCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile.toFile()))) {
            String header = reader.readLine();
            CsvFormatDetector detector = new CsvFormatDetector(header);
            
            if (!detector.isValid()) {
                logger.error("    -> SKIPPING file: Could not detect a valid column format in {}. Check headers (e.g., open, high, low, close).", csvFile.getFileName());
                return;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                KLine kline = detector.parseLine(line);
                if (kline != null) klineBatch.add(kline);
                
                if (klineBatch.size() >= BATCH_SIZE) {
                    dbManager.saveKLines(klineBatch, symbol, "1m");
                    klineBatch.clear();
                }
                int percentage = (int) (((double) lineCount / (totalLines - 1)) * 100);
                progressConsumer.accept(new ProgressUpdate("Processing: " + csvFile.getFileName(), percentage));
            }

            if (!klineBatch.isEmpty()) dbManager.saveKLines(klineBatch, symbol, "1m");
            progressConsumer.accept(new ProgressUpdate("Finished: " + csvFile.getFileName(), 100));
        }
    }
    
    private Map<String, List<Path>> findAndGroupCsvFiles(Path importDir) throws IOException {
        try (Stream<Path> stream = Files.walk(importDir, 1)) {
            return stream
                .filter(path -> path.toString().toLowerCase().endsWith(".csv"))
                .collect(Collectors.groupingBy(this::extractBaseSymbol));
        }
    }

    private String extractBaseSymbol(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.replaceAll("_\\d{4}\\.csv", ".csv").replace(".csv", "");
    }

    private void moveFileToProcessed(Path sourceFile, Path processedDir) throws IOException {
        Path targetFile = processedDir.resolve(sourceFile.getFileName());
        Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        logger.info("  -> Moved {} to processed directory.", sourceFile.getFileName());
    }

    private static class CsvFormatDetector {
        private final Map<String, Integer> columnIndexMap = new HashMap<>();
        private final TimeParser timeParser;
        private enum TimeFormat { UNIX, DATETIME_SINGLE_COLUMN, DATETIME_SPLIT_COLUMN, UNKNOWN }
        private final TimeFormat detectedTimeFormat;
        private static final DateTimeFormatter FORMAT_DD_MM_YYYY_SSS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS");
        private static final DateTimeFormatter FORMAT_YYYY_MM_DD_HH_MM_SS = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
        private static final DateTimeFormatter FORMAT_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy.MM.dd");

        CsvFormatDetector(String headerLine) {
            String[] headers = headerLine.toLowerCase().split("[,;]");
            for (int i = 0; i < headers.length; i++) {
                columnIndexMap.put(headers[i].trim().replace("\"", ""), i);
            }

            if (columnIndexMap.containsKey("gmt time") || columnIndexMap.containsKey("timestamp")) {
                this.timeParser = this::parseFromGenericTimestampColumn;
                this.detectedTimeFormat = TimeFormat.DATETIME_SINGLE_COLUMN;
            } else if (columnIndexMap.containsKey("date_time")) {
                 this.timeParser = p -> LocalDateTime.parse(p[columnIndexMap.get("date_time")].trim(), FORMAT_YYYY_MM_DD_HH_MM_SS).toInstant(ZoneOffset.UTC);
                 this.detectedTimeFormat = TimeFormat.DATETIME_SINGLE_COLUMN;
            } else if (columnIndexMap.containsKey("date") && columnIndexMap.containsKey("time")) {
                 this.timeParser = this::parseFromSplitDateTimeColumns;
                 this.detectedTimeFormat = TimeFormat.DATETIME_SPLIT_COLUMN;
            } else {
                this.timeParser = p -> Instant.EPOCH;
                this.detectedTimeFormat = TimeFormat.UNKNOWN;
            }
        }

        public boolean isValid() {
            return detectedTimeFormat != TimeFormat.UNKNOWN &&
                   columnIndexMap.containsKey("open") &&
                   columnIndexMap.containsKey("high") &&
                   columnIndexMap.containsKey("low") &&
                   columnIndexMap.containsKey("close");
        }

        public KLine parseLine(String line) {
            String[] parts = line.split("[,;]");
            if (!isValid() || parts.length < columnIndexMap.size()) return null;

            try {
                Instant timestamp = timeParser.parse(parts);
                BigDecimal open = new BigDecimal(parts[columnIndexMap.get("open")].trim());
                BigDecimal high = new BigDecimal(parts[columnIndexMap.get("high")].trim());
                BigDecimal low = new BigDecimal(parts[columnIndexMap.get("low")].trim());
                BigDecimal close = new BigDecimal(parts[columnIndexMap.get("close")].trim());
                BigDecimal volume = columnIndexMap.containsKey("volume") ? new BigDecimal(parts[columnIndexMap.get("volume")].trim()) : BigDecimal.ZERO;
                return new KLine(timestamp, open, high, low, close, volume);
            } catch (Exception e) {
                return null;
            }
        }

        private Instant parseFromGenericTimestampColumn(String[] parts) {
            Integer index = columnIndexMap.getOrDefault("gmt time", columnIndexMap.get("timestamp"));
            String timeStr = parts[index].trim();
            try {
                long epochSeconds = (long) Double.parseDouble(timeStr);
                return Instant.ofEpochSecond(epochSeconds);
            } catch (NumberFormatException e) {
                return LocalDateTime.parse(timeStr, FORMAT_DD_MM_YYYY_SSS).toInstant(ZoneOffset.UTC);
            }
        }

        private Instant parseFromSplitDateTimeColumns(String[] parts) {
            String dateStr = parts[columnIndexMap.get("date")].trim();
            String timeStr = parts[columnIndexMap.get("time")].trim();
            if (!timeStr.contains(":")) timeStr = timeStr.substring(0, 2) + ":" + timeStr.substring(2);
            LocalDate date = LocalDate.parse(dateStr, FORMAT_YYYY_MM_DD);
            java.time.LocalTime time = java.time.LocalTime.parse(timeStr);
            return LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC);
        }

        @FunctionalInterface
        private interface TimeParser { Instant parse(String[] parts); }
    }
}
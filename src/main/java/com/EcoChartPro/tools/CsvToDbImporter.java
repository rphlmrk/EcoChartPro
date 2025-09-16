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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility to import K-line data from a CSV file into the SQLite database.
 * The symbol is inferred from the CSV file's parent directory name.
 *
 * @deprecated This class is replaced by the more powerful and automated
 *             {@link DataImportTool}. Use that tool instead for importing
 *             data from CSV files.
 */
@Deprecated
public class CsvToDbImporter {

    private static final Logger logger = LoggerFactory.getLogger(CsvToDbImporter.class);
    private static final int BATCH_SIZE = 5000;
    private static final DateTimeFormatter CSV_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS");

    public static void main(String[] args) throws IOException {
        System.err.println("WARNING: This tool (CsvToDbImporter) is deprecated.");
        System.err.println("Please use the new 'com.EcoChartPro.tools.DataImportTool' instead.");
        System.err.println("The new tool automatically scans the 'import_data' directory.");
        if (args.length != 2) {
            // Improved cross-platform usage instructions ---
            System.err.println("Usage: java com.EcoChartPro.tools.CsvToDbImporter <filePath> <timeframe>");
            System.err.println("\nExamples:");
            System.err.println("  Windows: java ... CsvToDbImporter \"C:\\data\\btcusdt\\BTCUSDT-1h.csv\" 1h");
            System.err.println("  macOS/Linux: java ... CsvToDbImporter \"/data/btcusdt/BTCUSDT-1h.csv\" 1h");
            System.err.println("\nNote: The symbol 'btcusdt' is inferred from the parent directory of the file.");
            return;
        }
        String filePath = args[0];
        String timeframe = args[1];
        new CsvToDbImporter().importData(filePath, timeframe);
    }

    /**
     * Imports data from a CSV file into a database. The symbol is inferred from the
     * file path and the database is created in the same directory as the CSV.
     * This method is platform-agnostic thanks to java.nio.file.Path.
     * @param filePath The full path to the CSV file.
     * @param timeframe The timeframe of the data in the file (e.g., "1h").
     * @throws IOException If the file cannot be read.
     */
    public void importData(String filePath, String timeframe) throws IOException {
        Path csvPath = Paths.get(filePath);
        Path parentDir = csvPath.getParent();
        if (parentDir == null) {
            throw new IOException("Cannot determine parent directory for file: " + filePath);
        }

        // The symbol is the name of the parent directory (e.g., "btcusdt").
        Symbol symbol = new Symbol(parentDir.getFileName().toString().toLowerCase());
        Path dbPath = parentDir.resolve("trading_data.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        
        logger.info("Starting CSV import for symbol '{}' from file: {}", symbol.name(), filePath);
        logger.info("Target database location: {}", dbPath.toAbsolutePath());
        
        DatabaseManager dbManager = null;
        try {
            dbManager = new DatabaseManager(jdbcUrl);
            List<KLine> klineBatch = new ArrayList<>(BATCH_SIZE);
            long totalLines = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                reader.readLine(); // Skip header line
                String line;
                while ((line = reader.readLine()) != null) {
                    totalLines++;
                    try {
                        KLine kline = parseLine(line);
                        klineBatch.add(kline);

                        if (klineBatch.size() >= BATCH_SIZE) {
                            dbManager.saveKLines(klineBatch, symbol, timeframe);
                            logger.info("Saved batch of {} records...", klineBatch.size());
                            klineBatch.clear();
                        }
                    } catch (Exception e) {
                        logger.warn("Skipping malformed line #{}: '{}'. Reason: {}", totalLines, line, e.getMessage());
                    }
                }

                if (!klineBatch.isEmpty()) {
                    dbManager.saveKLines(klineBatch, symbol, timeframe);
                    logger.info("Saving final batch of {} records...", klineBatch.size());
                }
            }
            logger.info("Import completed successfully. Total lines processed: {}", totalLines);
        } finally {
            if (dbManager != null) {
                dbManager.close();
            }
        }
    }

    private KLine parseLine(String line) {
        String[] parts = line.split(",");
        LocalDateTime ldt = LocalDateTime.parse(parts[0].trim(), CSV_DATE_TIME_FORMATTER);
        Instant timestamp = ldt.toInstant(ZoneOffset.UTC);

        return new KLine(
            timestamp,
            new BigDecimal(parts[1].trim()), // open
            new BigDecimal(parts[2].trim()), // high
            new BigDecimal(parts[3].trim()), // low
            new BigDecimal(parts[4].trim()), // close
            new BigDecimal(parts[5].trim())  // volume
        );
    }
}
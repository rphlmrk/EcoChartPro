package com.EcoChartPro.utils;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class DatabaseManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_FILE_NAME = "trading_data.db";
    private static final String DB_URL = getDatabaseUrl();
    private static volatile DatabaseManager instance;

    private Connection connection;

    public record DatasetInfo(String symbol, String timeframe) {
        @Override
        public String toString() {
            return symbol + " (" + timeframe + ")";
        }
    }

    public record DataRange(Instant start, Instant end) {}

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS kline_data (
            symbol TEXT NOT NULL,
            timeframe TEXT NOT NULL,
            timestamp_sec INTEGER NOT NULL,
            open TEXT NOT NULL,
            high TEXT NOT NULL,
            low TEXT NOT NULL,
            close TEXT NOT NULL,
            volume TEXT NOT NULL,
            PRIMARY KEY (symbol, timeframe, timestamp_sec)
        );
    """;
    
    private static final String CREATE_INDEX_SQL = """
        CREATE INDEX IF NOT EXISTS idx_kline_query
        ON kline_data (symbol, timeframe, timestamp_sec);
    """;

    private DatabaseManager() {
        init(DB_URL);
    }
    
    public DatabaseManager(String dbUrl) {
        init(dbUrl);
    }
    
    private void init(String dbUrl) {
        try {
            connection = DriverManager.getConnection(dbUrl);
            logger.info("Database connection established for URL: {}", dbUrl);
            setConnectionPragmas();
            initializeSchema();
        } catch (SQLException e) {
            logger.error("Failed to connect to the database at " + dbUrl, e);
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private static String getDatabaseUrl() {
        try {
            Path appDataPath = AppDataManager.getAppDataDirectory();
            Path dbPath = appDataPath.resolve(DB_FILE_NAME);
            logger.info("Main application using database at: {}", dbPath.toAbsolutePath());
            return "jdbc:sqlite:" + dbPath.toAbsolutePath();
        } catch (Exception e) {
            logger.error("Could not create or access the application data directory. Falling back to relative path.", e);
            return "jdbc:sqlite:" + DB_FILE_NAME;
        }
    }
    
    private void setConnectionPragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA mmap_size = 268435456;");
            stmt.execute("PRAGMA cache_size = -200000;");
            logger.info("Performance PRAGMAs set manually (WAL, NORMAL, MMAP, Cache).");
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
            stmt.execute(CREATE_INDEX_SQL);
        }
    }

    public List<DatasetInfo> getAvailableDatasets() {
        List<DatasetInfo> datasets = new ArrayList<>();
        String sql = "SELECT DISTINCT symbol, timeframe FROM kline_data ORDER BY symbol, timeframe";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                datasets.add(new DatasetInfo(rs.getString("symbol"), rs.getString("timeframe")));
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve available datasets.", e);
        }
        return datasets;
    }

    public Optional<DataRange> getDataRange(Symbol symbol, String timeframe) {
        String sql = "SELECT MIN(timestamp_sec), MAX(timestamp_sec) FROM kline_data WHERE symbol = ? AND timeframe = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long minTimestamp = rs.getLong(1);
                    long maxTimestamp = rs.getLong(2);
                    if (rs.wasNull()) {
                        return Optional.empty();
                    }
                    return Optional.of(new DataRange(Instant.ofEpochSecond(minTimestamp), Instant.ofEpochSecond(maxTimestamp)));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve data range for {} ({}).", symbol.name(), timeframe, e);
        }
        return Optional.empty();
    }

    public List<String> getDistinctTimeframes() {
        List<String> timeframes = new ArrayList<>();
        String sql = "SELECT DISTINCT timeframe FROM kline_data ORDER BY timeframe";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                timeframes.add(rs.getString("timeframe"));
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve distinct timeframes.", e);
        }
        return timeframes;
    }

    /**
     * [DEPRECATED] This method loads all timestamps into memory and should not be used for large datasets.
     * @see #findClosestTimestampIndex(Symbol, String, Instant) for the efficient alternative.
     */
    @Deprecated
    public List<Long> getAllTimestamps(Symbol symbol, String timeframe) {
        List<Long> timestamps = new ArrayList<>();
        String sql = "SELECT timestamp_sec FROM kline_data WHERE symbol = ? AND timeframe = ? ORDER BY timestamp_sec ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    timestamps.add(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve all timestamps.", e);
        }
        logger.info("Loaded {} total timestamps for {}({}) into memory.", timestamps.size(), symbol.name(), timeframe);
        return timestamps;
    }

    /**
     * An extremely efficient method to find the index of a timestamp without loading data into memory.
     * It performs two fast, indexed queries.
     */
    public int findClosestTimestampIndex(Symbol symbol, String timeframe, Instant targetTime) {
        long targetTimestampSec = targetTime.getEpochSecond();
        long foundTimestampSec = -1;

        // 1. Find the first timestamp >= our target. This is very fast due to the index.
        String findSql = "SELECT timestamp_sec FROM kline_data WHERE symbol = ? AND timeframe = ? AND timestamp_sec >= ? ORDER BY timestamp_sec ASC LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(findSql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            pstmt.setLong(3, targetTimestampSec);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    foundTimestampSec = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find closest timestamp for {} ({})", symbol.name(), timeframe, e);
            return 0;
        }

        if (foundTimestampSec == -1) {
            // Target time is after all available data, so return the last index.
            return Math.max(0, getTotalKLineCount(symbol, timeframe) - 1);
        }

        // 2. Count how many records come before the one we found. This count is the index.
        String countSql = "SELECT COUNT(*) FROM kline_data WHERE symbol = ? AND timeframe = ? AND timestamp_sec < ?";
        try (PreparedStatement pstmt = connection.prepareStatement(countSql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            pstmt.setLong(3, foundTimestampSec);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to count preceding timestamps for {} ({})", symbol.name(), timeframe, e);
        }
        return 0;
    }


    public List<KLine> getKLinesStartingFrom(Symbol symbol, String timeframe, long startTimestamp, int limit) {
        List<KLine> klines = new ArrayList<>();
        String sql = "SELECT * FROM kline_data WHERE symbol = ? AND timeframe = ? AND timestamp_sec >= ? ORDER BY timestamp_sec ASC LIMIT ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            pstmt.setLong(3, startTimestamp);
            pstmt.setInt(4, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    klines.add(new KLine(
                        Instant.ofEpochSecond(rs.getLong("timestamp_sec")),
                        new BigDecimal(rs.getString("open")),
                        new BigDecimal(rs.getString("high")),
                        new BigDecimal(rs.getString("low")),
                        new BigDecimal(rs.getString("close")),
                        new BigDecimal(rs.getString("volume"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve K-lines by start timestamp.", e);
        }
        return klines;
    }

    /**
     * An efficient method to retrieve a specific slice of data using LIMIT and OFFSET.
     */
    public List<KLine> getKLinesByIndex(Symbol symbol, String timeframe, int offset, int limit) {
        List<KLine> klines = new ArrayList<>();
        String sql = "SELECT * FROM kline_data WHERE symbol = ? AND timeframe = ? ORDER BY timestamp_sec ASC LIMIT ? OFFSET ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            pstmt.setInt(3, limit);
            pstmt.setInt(4, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                 while (rs.next()) {
                    klines.add(new KLine(
                        Instant.ofEpochSecond(rs.getLong("timestamp_sec")),
                        new BigDecimal(rs.getString("open")),
                        new BigDecimal(rs.getString("high")),
                        new BigDecimal(rs.getString("low")),
                        new BigDecimal(rs.getString("close")),
                        new BigDecimal(rs.getString("volume"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve K-lines by index.", e);
        }
        return klines;
    }

    public List<KLine> getKLinesBetween(Symbol symbol, String timeframe, Instant startTime, Instant endTime) {
        List<KLine> klines = new ArrayList<>();
        String sql = "SELECT * FROM kline_data WHERE symbol = ? AND timeframe = ? AND timestamp_sec BETWEEN ? AND ? ORDER BY timestamp_sec ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            pstmt.setLong(3, startTime.getEpochSecond());
            pstmt.setLong(4, endTime.getEpochSecond());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    klines.add(new KLine(
                        Instant.ofEpochSecond(rs.getLong("timestamp_sec")),
                        new BigDecimal(rs.getString("open")),
                        new BigDecimal(rs.getString("high")),
                        new BigDecimal(rs.getString("low")),
                        new BigDecimal(rs.getString("close")),
                        new BigDecimal(rs.getString("volume"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve K-lines between timestamps.", e);
        }
        return klines;
    }

    public int getTotalKLineCount(Symbol symbol, String timeframe) {
        String sql = "SELECT COUNT(*) FROM kline_data WHERE symbol = ? AND timeframe = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get K-line count.", e);
        }
        return 0;
    }
    
    public void saveKLines(List<KLine> klines, Symbol symbol, String timeframe) {
        String sql = "INSERT OR REPLACE INTO kline_data (symbol, timeframe, timestamp_sec, open, high, low, close, volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                for (KLine kline : klines) {
                    pstmt.setString(1, symbol.name());
                    pstmt.setString(2, timeframe);
                    pstmt.setLong(3, kline.timestamp().getEpochSecond());
                    pstmt.setString(4, kline.open().toPlainString());
                    pstmt.setString(5, kline.high().toPlainString());
                    pstmt.setString(6, kline.low().toPlainString());
                    pstmt.setString(7, kline.close().toPlainString());
                    pstmt.setString(8, kline.volume().toPlainString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                logger.error("Error during batch insert, transaction rolled back.", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Failed to set transaction properties.", e);
        }
    }
    
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed.");
            }
        } catch (SQLException e) {
            logger.error("Failed to close the database connection.", e);
        }
    }
}
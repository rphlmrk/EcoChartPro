package com.EcoChartPro.utils;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.TradeTick;
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
import java.util.UUID;

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
    
    private static final String CREATE_TRADES_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS trades (
            symbol TEXT NOT NULL,
            timestamp_ms INTEGER NOT NULL,
            price TEXT NOT NULL,
            quantity TEXT NOT NULL,
            side TEXT NOT NULL
        );
    """;

    private static final String CREATE_TRADES_INDEX_SQL = """
        CREATE INDEX IF NOT EXISTS idx_trades_query
        ON trades (symbol, timestamp_ms);
    """;

    // [NEW] Schema for trade-specific K-line data
    private static final String CREATE_TRADE_KLINES_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS trade_kline_data (
            trade_id TEXT NOT NULL,
            symbol TEXT NOT NULL,
            timeframe TEXT NOT NULL,
            timestamp_sec INTEGER NOT NULL,
            open TEXT NOT NULL,
            high TEXT NOT NULL,
            low TEXT NOT NULL,
            close TEXT NOT NULL,
            volume TEXT NOT NULL,
            PRIMARY KEY (trade_id, timeframe, timestamp_sec)
        );
    """;
    
    // [NEW] Index for efficient querying of a trade's candles
    private static final String CREATE_TRADE_KLINES_INDEX_SQL = """
        CREATE INDEX IF NOT EXISTS idx_trade_kline_query
        ON trade_kline_data (trade_id, timeframe, timestamp_sec);
    """;

    private DatabaseManager() {
        init(DB_URL);
    }
    
    /**
     * Public constructor to create a manager for a specific database file.
     * @param dbUrl The full JDBC URL (e.g., "jdbc:sqlite:/path/to/data.db").
     */
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
            stmt.execute(CREATE_TRADES_TABLE_SQL);
            stmt.execute(CREATE_TRADES_INDEX_SQL);
            // [NEW] Create the new table on initialization
            stmt.execute(CREATE_TRADE_KLINES_TABLE_SQL);
            stmt.execute(CREATE_TRADE_KLINES_INDEX_SQL);
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
            return Math.max(0, getTotalKLineCount(symbol, timeframe) - 1);
        }

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

    public List<KLine> getAllKLines(Symbol symbol, String timeframe) {
        List<KLine> klines = new ArrayList<>();
        String sql = "SELECT timestamp_sec, open, high, low, close, volume FROM kline_data WHERE symbol = ? AND timeframe = ? ORDER BY timestamp_sec ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.name());
            pstmt.setString(2, timeframe);
            
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
            logger.error("Failed to retrieve all k-lines for {} on timeframe {}. DB error.", symbol.name(), timeframe, e);
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
    
    public void saveTrades(List<TradeTick> trades, String symbol) {
        String sql = "INSERT OR IGNORE INTO trades (symbol, timestamp_ms, price, quantity, side) VALUES (?, ?, ?, ?, ?)";
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                for (TradeTick trade : trades) {
                    pstmt.setString(1, symbol);
                    pstmt.setLong(2, trade.timestamp().toEpochMilli());
                    pstmt.setString(3, trade.price().toPlainString());
                    pstmt.setString(4, trade.quantity().toPlainString());
                    pstmt.setString(5, trade.side());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                logger.error("Error during trade batch insert, transaction rolled back.", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Failed to set transaction properties for saving trades.", e);
        }
    }

    public List<TradeTick> getTrades(String symbol, long startTimeMs, long endTimeMs) {
        List<TradeTick> trades = new ArrayList<>();
        String sql = "SELECT * FROM trades WHERE symbol = ? AND timestamp_ms BETWEEN ? AND ? ORDER BY timestamp_ms ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setLong(2, startTimeMs);
            pstmt.setLong(3, endTimeMs);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    trades.add(new TradeTick(
                        Instant.ofEpochMilli(rs.getLong("timestamp_ms")),
                        new BigDecimal(rs.getString("price")),
                        new BigDecimal(rs.getString("quantity")),
                        rs.getString("side")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve trades between timestamps.", e);
        }
        return trades;
    }
    
    /**
     * [NEW] Saves a list of K-lines associated with a specific trade ID.
     * Uses INSERT OR IGNORE to prevent duplicates if data is saved multiple times.
     */
    public void saveTradeCandles(UUID tradeId, String symbol, String timeframe, List<KLine> candles) {
        String sql = "INSERT OR IGNORE INTO trade_kline_data (trade_id, symbol, timeframe, timestamp_sec, open, high, low, close, volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                for (KLine kline : candles) {
                    pstmt.setString(1, tradeId.toString());
                    pstmt.setString(2, symbol);
                    pstmt.setString(3, timeframe);
                    pstmt.setLong(4, kline.timestamp().getEpochSecond());
                    pstmt.setString(5, kline.open().toPlainString());
                    pstmt.setString(6, kline.high().toPlainString());
                    pstmt.setString(7, kline.low().toPlainString());
                    pstmt.setString(8, kline.close().toPlainString());
                    pstmt.setString(9, kline.volume().toPlainString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                connection.commit();
                logger.debug("Saved batch of {} candles for trade {}", candles.size(), tradeId);
            } catch (SQLException e) {
                connection.rollback();
                logger.error("Error during trade candle batch insert, transaction rolled back.", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Failed to set transaction properties for saving trade candles.", e);
        }
    }

    /**
     * [NEW] Retrieves all K-lines associated with a specific trade ID and timeframe.
     */
    public List<KLine> getCandlesForTrade(UUID tradeId, String timeframe) {
        List<KLine> klines = new ArrayList<>();
        String sql = "SELECT * FROM trade_kline_data WHERE trade_id = ? AND timeframe = ? ORDER BY timestamp_sec ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tradeId.toString());
            pstmt.setString(2, timeframe);

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
            logger.error("Failed to retrieve K-lines for trade {}", tradeId, e);
        }
        return klines;
    }

    /**
     * [NEW] Deletes all candle data for trades that were closed before the specified timestamp.
     */
    public void pruneOldTradeCandles(Instant olderThan) {
        // This query finds all trade_ids where the last candle for that trade is older than the retention period.
        String sql = """
            DELETE FROM trade_kline_data
            WHERE trade_id IN (
                SELECT trade_id
                FROM trade_kline_data
                GROUP BY trade_id
                HAVING MAX(timestamp_sec) < ?
            )
        """;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, olderThan.getEpochSecond());
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Pruned candle data for old trades. {} records removed.", rowsAffected);
            } else {
                logger.debug("No old trade candle data to prune.");
            }
        } catch (SQLException e) {
            logger.error("Failed to prune old trade candle data.", e);
        }
    }
    
    /**
     * [NEW] Deletes all records from the trade_kline_data table.
     */
    public void clearAllTradeCandles() {
        String sql = "DELETE FROM trade_kline_data;";
        try (Statement stmt = connection.createStatement()) {
            int rowsAffected = stmt.executeUpdate(sql);
            logger.info("Cleared all cached trade candle data. {} records deleted.", rowsAffected);
        } catch (SQLException e) {
            logger.error("Failed to clear trade candle data.", e);
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
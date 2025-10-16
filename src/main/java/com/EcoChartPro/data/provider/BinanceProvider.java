package com.EcoChartPro.data.provider;

import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * An implementation of DataProvider for fetching data from the Binance exchange.
 * It uses the REST API for historical data and symbol lists, and delegates
 * live streaming to the LiveDataManager.
 */
public class BinanceProvider implements DataProvider {

    private static final Logger logger = LoggerFactory.getLogger(BinanceProvider.class);
    private static final String API_BASE_URL = "https://api.binance.com/api/v3";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final Gson gson = new Gson();

    /**
     * [NEW] DTO for parsing 24h ticker data.
     */
    public static class TickerData {
        public String symbol;
        public String priceChange;
        public String priceChangePercent;
        public String lastPrice;
        // Add other fields like highPrice, lowPrice, etc. as needed
    }

    @Override
    public String getProviderName() {
        return "Binance";
    }

    @Override
    public List<ChartDataSource> getAvailableSymbols() {
        String url = API_BASE_URL + "/exchangeInfo";
        Request request = new Request.Builder().url(url).build();
        logger.info("Fetching all available symbols from Binance: {}", url);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                logger.error("Failed to fetch exchange info. Code: {}, Message: {}",
                        response.code(), response.body() != null ? response.body().string() : "N/A");
                return Collections.emptyList();
            }

            String jsonBody = response.body().string();
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            Type listType = new TypeToken<List<BinanceSymbolData>>() {}.getType();
            List<BinanceSymbolData> binanceSymbols = gson.fromJson(root.get("symbols"), listType);

            List<ChartDataSource> sources = binanceSymbols.stream()
                    .filter(s -> "TRADING".equals(s.status) && s.quoteAsset.equals("USDT")) // Filter for trading USDT pairs
                    .map(s -> new ChartDataSource(
                            getProviderName(),
                            s.symbol.toLowerCase(), // e.g. "btcusdt"
                            s.baseAsset + "/" + s.quoteAsset, // e.g. "BTC/USDT"
                            null, // No local DB path for a live provider
                            // Provide a standard list of timeframes
                            List.of("1m", "5m", "15m", "30m", "1H", "4H", "1D")
                    ))
                    .collect(Collectors.toList());

            logger.info("Successfully loaded {} tradable USDT symbols from Binance.", sources.size());
            return sources;

        } catch (Exception e) {
            logger.error("Error fetching or parsing exchange info from Binance.", e);
            return Collections.emptyList();
        }
    }

    /**
     * [NEW] Fetches 24-hour price change statistics for a single symbol.
     * @param symbol The symbol in application format (e.g., "btcusdt").
     * @return A TickerData object or null on failure.
     */
    public TickerData get24hTickerData(String symbol) {
        String binanceSymbol = BinanceDataUtils.toBinanceSymbol(symbol).toUpperCase();
        String url = API_BASE_URL + "/ticker/24hr?symbol=" + binanceSymbol;
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                logger.error("Failed to fetch 24h ticker data for {}. Code: {}", symbol, response.code());
                return null;
            }
            return gson.fromJson(response.body().string(), TickerData.class);
        } catch (IOException e) {
            logger.error("Network error while fetching 24h ticker for {}", symbol, e);
            return null;
        }
    }

    @Override
    public List<KLine> getHistoricalData(String symbol, String timeframe, int limit) {
        return getHistoricalData(symbol, timeframe, limit, null, null);
    }

    /**
     * [MODIFIED] Fetches historical K-line records with optional time range parameters.
     */
    public List<KLine> getHistoricalData(String symbol, String timeframe, int limit, Long startTimeMillis, Long endTimeMillis) {
        String binanceSymbol = BinanceDataUtils.toBinanceSymbol(symbol).toUpperCase();
        String binanceInterval = BinanceDataUtils.toBinanceInterval(timeframe);
        
        StringBuilder urlBuilder = new StringBuilder(String.format("%s/klines?symbol=%s&interval=%s&limit=%d",
                API_BASE_URL, binanceSymbol, binanceInterval, limit));
        if (startTimeMillis != null) {
            urlBuilder.append("&startTime=").append(startTimeMillis);
        }
        if (endTimeMillis != null) {
            urlBuilder.append("&endTime=").append(endTimeMillis);
        }
        String url = urlBuilder.toString();

        Request request = new Request.Builder().url(url).build();
        logger.info("Fetching historical data from Binance: {}", url);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                logger.error("Failed to fetch data from Binance. Code: {}, Message: {}",
                        response.code(), response.body() != null ? response.body().string() : "N/A");
                return Collections.emptyList();
            }

            String jsonBody = response.body().string();
            Type listType = new TypeToken<List<List<Object>>>() {}.getType();
            List<List<Object>> rawData = gson.fromJson(jsonBody, listType);

            List<KLine> klineDataList = new ArrayList<>();
            for (List<Object> rawBar : rawData) {
                klineDataList.add(new KLine(
                    Instant.ofEpochMilli(((Number) rawBar.get(0)).longValue()),
                    new BigDecimal((String) rawBar.get(1)), // open
                    new BigDecimal((String) rawBar.get(2)), // high
                    new BigDecimal((String) rawBar.get(3)), // low
                    new BigDecimal((String) rawBar.get(4)), // close
                    new BigDecimal((String) rawBar.get(5))  // volume
                ));
            }
            return klineDataList;

        } catch (IOException e) {
            logger.error("Network error while fetching data for {} @ {}", symbol, timeframe, e);
            return Collections.emptyList();
        }
    }

    /**
     * [NEW] Fetches a large amount of historical data by making chunked requests to the API.
     * @param symbol The symbol to fetch (e.g., "BTC/USDT").
     * @param timeframe The timeframe string (e.g., "1H").
     * @param startTimeMillis The earliest timestamp to fetch data from.
     * @return A list of all KLine data retrieved.
     */
    public List<KLine> backfillHistoricalData(String symbol, String timeframe, long startTimeMillis) {
        List<KLine> allData = new ArrayList<>();
        long currentStartTime = startTimeMillis;
        final int batchLimit = 1000;

        logger.info("Starting historical data backfill for {} @ {} from {}", symbol, timeframe, Instant.ofEpochMilli(startTimeMillis));

        while (true) {
            List<KLine> batch = getHistoricalData(symbol, timeframe, batchLimit, currentStartTime, null);
            
            if (batch == null || batch.isEmpty()) {
                logger.info("Backfill complete. No more data returned from API.");
                break;
            }
            
            allData.addAll(batch);
            
            // Set the start time for the next batch to be right after the last candle we received.
            currentStartTime = batch.get(batch.size() - 1).timestamp().toEpochMilli() + 1;
            logger.debug("Fetched batch of {} candles. Next fetch starts at {}.", batch.size(), Instant.ofEpochMilli(currentStartTime));

            // If we receive fewer candles than we asked for, it means we've reached the end of available history.
            if (batch.size() < batchLimit) {
                logger.info("Backfill complete. Received last batch of data.");
                break;
            }

            // A small delay to respect API rate limits.
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Backfill process was interrupted.");
                break;
            }
        }
        
        logger.info("Total historical klines backfilled for {} @ {}: {}", symbol, timeframe, allData.size());
        return allData;
    }


    public void connectToLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        LiveDataManager.getInstance().subscribe(symbol, timeframe, onKLineUpdate);
    }

    public void disconnectFromLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        LiveDataManager.getInstance().unsubscribe(symbol, timeframe, onKLineUpdate);
    }

    // Helper inner class for parsing the /exchangeInfo endpoint JSON
    private static class BinanceSymbolData {
        String symbol;
        String status;
        String baseAsset;
        String quoteAsset;
    }
}
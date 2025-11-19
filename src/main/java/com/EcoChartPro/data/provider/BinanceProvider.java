package com.EcoChartPro.data.provider;

import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.TradeTick;
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
 * An implementation of DataProvider for fetching data from the Binance
 * exchange.
 * Uses REST API for history/symbols and LiveDataManager for streaming.
 */
public class BinanceProvider implements DataProvider {

    private static final Logger logger = LoggerFactory.getLogger(BinanceProvider.class);
    private static final String API_BASE_URL = "https://api.binance.com/api/v3";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final Gson gson = new Gson();

    // [FIX] Removed "1s" from supported list
    private static final List<String> BINANCE_TIMEFRAMES = List.of(
            "1m", "3m", "5m", "15m", "30m",
            "1H", "2H", "4H", "6H", "8H", "12H",
            "1D", "3D", "1W", "1M");

    public static class TickerData {
        public String symbol;
        public String priceChange;
        public String priceChangePercent;
        public String lastPrice;
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
            Type listType = new TypeToken<List<BinanceSymbolData>>() {
            }.getType();
            List<BinanceSymbolData> binanceSymbols = gson.fromJson(root.get("symbols"), listType);

            List<ChartDataSource> sources = binanceSymbols.stream()
                    .filter(s -> "TRADING".equals(s.status) && s.quoteAsset.equals("USDT"))
                    .map(s -> new ChartDataSource(
                            getProviderName(),
                            s.symbol.toLowerCase(),
                            s.baseAsset + "/" + s.quoteAsset,
                            null,
                            BINANCE_TIMEFRAMES))
                    .collect(Collectors.toList());

            logger.info("Successfully loaded {} tradable USDT symbols from Binance.", sources.size());
            return sources;

        } catch (Exception e) {
            logger.error("Error fetching or parsing exchange info from Binance.", e);
            return Collections.emptyList();
        }
    }

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
        try {
            return getHistoricalData(symbol, timeframe, limit, null, null);
        } catch (IOException e) {
            logger.error("Network error while fetching data for {} @ {}", symbol, timeframe, e);
            return Collections.emptyList();
        }
    }

    public List<KLine> getHistoricalData(String symbol, String timeframe, int limit, Long startTimeMillis,
            Long endTimeMillis) throws IOException {
        String binanceSymbol = BinanceDataUtils.toBinanceSymbol(symbol).toUpperCase();
        String binanceInterval = BinanceDataUtils.toBinanceInterval(timeframe);

        StringBuilder urlBuilder = new StringBuilder(String.format("%s/klines?symbol=%s&interval=%s&limit=%d",
                API_BASE_URL, binanceSymbol, binanceInterval, limit));

        if (startTimeMillis != null && startTimeMillis > 0) {
            urlBuilder.append("&startTime=").append(startTimeMillis);
        }
        if (endTimeMillis != null && endTimeMillis > 0) {
            urlBuilder.append("&endTime=").append(endTimeMillis);
        }
        String url = urlBuilder.toString();

        Request request = new Request.Builder().url(url).build();
        logger.info("Fetching historical data from Binance: {}", url);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String errorBody = response.body() != null ? response.body().string() : "N/A";
                logger.error("Failed to fetch data from Binance. Code: {}, Message: {}", response.code(), errorBody);
                throw new IOException("Binance API error: " + response.code() + " - " + errorBody);
            }

            String jsonBody = response.body().string();
            Type listType = new TypeToken<List<List<Object>>>() {
            }.getType();
            List<List<Object>> rawData = gson.fromJson(jsonBody, listType);

            List<KLine> klineDataList = new ArrayList<>();
            for (List<Object> rawBar : rawData) {
                klineDataList.add(new KLine(
                        Instant.ofEpochMilli(((Number) rawBar.get(0)).longValue()),
                        new BigDecimal((String) rawBar.get(1)),
                        new BigDecimal((String) rawBar.get(2)),
                        new BigDecimal((String) rawBar.get(3)),
                        new BigDecimal((String) rawBar.get(4)),
                        new BigDecimal((String) rawBar.get(5))));
            }
            return klineDataList;
        }
    }

    @Override
    public List<TradeTick> getHistoricalTrades(String symbol, long startTimeMillis, int limit) {
        String binanceSymbol = BinanceDataUtils.toBinanceSymbol(symbol).toUpperCase();
        String url = String.format("%s/historicalTrades?symbol=%s&limit=%d", API_BASE_URL, binanceSymbol, limit);
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return Collections.emptyList();
            }
            String jsonBody = response.body().string();
            Type listType = new TypeToken<List<BinanceTradeData>>() {
            }.getType();
            List<BinanceTradeData> rawTrades = gson.fromJson(jsonBody, listType);

            return rawTrades.stream()
                    .map(trade -> new TradeTick(
                            Instant.ofEpochMilli(trade.time),
                            new BigDecimal(trade.price),
                            new BigDecimal(trade.qty),
                            !trade.isBuyerMaker ? "buy" : "sell"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Network error fetching historical trades for {}", symbol, e);
            return Collections.emptyList();
        }
    }

    public List<KLine> backfillHistoricalData(String symbol, String timeframe, long startTimeMillis) {
        List<KLine> allData = new ArrayList<>();
        long currentStartTime = startTimeMillis;
        final int batchLimit = 1000;

        logger.info("Starting historical data backfill for {} @ {} from {}", symbol, timeframe,
                Instant.ofEpochMilli(startTimeMillis));

        while (true) {
            int retries = 3;
            List<KLine> batch = null;
            while (retries > 0) {
                try {
                    batch = getHistoricalData(symbol, timeframe, batchLimit, currentStartTime, null);
                    break;
                } catch (IOException e) {
                    retries--;
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (batch == null || batch.isEmpty())
                break;
            allData.addAll(batch);
            currentStartTime = batch.get(batch.size() - 1).timestamp().toEpochMilli() + 1;
            if (batch.size() < batchLimit)
                break;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return allData;
    }

    @Override
    public void connectToLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        LiveDataManager.getInstance().subscribeToKLine(symbol, timeframe, onKLineUpdate);
    }

    @Override
    public void disconnectFromLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        LiveDataManager.getInstance().unsubscribeFromKLine(symbol, timeframe, onKLineUpdate);
    }

    @Override
    public void connectToTradeStream(String symbol, Consumer<TradeTick> onTradeUpdate) {
        LiveDataManager.getInstance().subscribeToTrades(symbol, onTradeUpdate);
    }

    @Override
    public void disconnectFromTradeStream(String symbol, Consumer<TradeTick> onTradeUpdate) {
        LiveDataManager.getInstance().unsubscribeFromTrades(symbol, onTradeUpdate);
    }

    private static class BinanceSymbolData {
        String symbol;
        String status;
        String baseAsset;
        String quoteAsset;
    }

    private static class BinanceTradeData {
        long id, time;
        String price, qty;
        boolean isBuyerMaker;
    }
}
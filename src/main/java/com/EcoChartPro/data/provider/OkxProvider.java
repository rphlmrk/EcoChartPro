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
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * An implementation of DataProvider for fetching public data from the OKX exchange.
 * It uses the REST API for historical data and symbol lists.
 */
public class OkxProvider implements DataProvider {

    private static final Logger logger = LoggerFactory.getLogger(OkxProvider.class);
    private static final String API_BASE_URL = "https://www.okx.com";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

    @Override
    public String getProviderName() {
        return "OKX";
    }

    @Override
    public List<ChartDataSource> getAvailableSymbols() {
        String url = API_BASE_URL + "/api/v5/public/instruments?instType=SWAP";
        Request request = new Request.Builder().url(url).build();
        logger.info("Fetching all available SWAP symbols from OKX: {}", url);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                logger.error("Failed to fetch instruments from OKX. Code: {}, Message: {}",
                        response.code(), response.body() != null ? response.body().string() : "N/A");
                return Collections.emptyList();
            }

            String jsonBody = response.body().string();
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            Type listType = new TypeToken<List<OkxInstrumentData>>() {}.getType();
            List<OkxInstrumentData> okxInstruments = gson.fromJson(root.get("data"), listType);

            List<ChartDataSource> sources = okxInstruments.stream()
                    .filter(s -> "live".equals(s.state))
                    .map(s -> {
                        String displayName = s.instId.replaceFirst("-", "/");
                        return new ChartDataSource(
                            getProviderName(),
                            s.instId.toLowerCase(), 
                            displayName,
                            null, 
                            List.of("1m", "5m", "15m", "30m", "1H", "4H", "1D")
                        );
                    })
                    .collect(Collectors.toList());

            logger.info("Successfully loaded {} tradable SWAP symbols from OKX.", sources.size());
            return sources;

        } catch (Exception e) {
            logger.error("Error fetching or parsing instruments from OKX.", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<KLine> getHistoricalData(String symbol, String timeframe, int limit) {
        try {
            return getHistoricalData(symbol, timeframe, limit, null, null);
        } catch (IOException e) {
            logger.error("Network error while fetching k-line data for {} @ {}", symbol, timeframe, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<TradeTick> getHistoricalTrades(String symbol, long startTimeMillis, int limit) {
        logger.warn("getHistoricalTrades not yet implemented for OkxProvider.");
        return Collections.emptyList();
    }
    
    public List<KLine> getHistoricalData(String symbol, String timeframe, int limit, Long before) throws IOException {
        return getHistoricalData(symbol, timeframe, limit, before, null);
    }
    
    public List<KLine> getHistoricalData(String symbol, String timeframe, int limit, Long before, Long after) throws IOException {
        String okxSymbol = OkxDataUtils.toOkxSymbol(symbol);
        String okxInterval = OkxDataUtils.toOkxInterval(timeframe);

        String url = String.format("%s/api/v5/market/history-candles?instId=%s&bar=%s&limit=%d"
                        + (before != null ? "&before=" + before : "")
                        + (after != null ? "&after=" + after : ""),
                API_BASE_URL, okxSymbol, okxInterval, limit);

        Request request = new Request.Builder().url(url).build();
        logger.info("Fetching historical data from OKX: {}", url);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String errorBody = response.body() != null ? response.body().string() : "N/A";
                logger.error("Failed to fetch k-line data from OKX. Code: {}, Message: {}", response.code(), errorBody);
                throw new IOException("OKX API error: " + response.code() + " - " + errorBody);
            }

            String jsonBody = response.body().string();
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            Type listType = new TypeToken<List<List<String>>>() {}.getType();
            List<List<String>> rawData = gson.fromJson(root.get("data"), listType);
            if (rawData == null) return Collections.emptyList();

            List<KLine> klineDataList = new ArrayList<>();
            for (List<String> rawBar : rawData) {
                klineDataList.add(new KLine(
                    Instant.ofEpochMilli(Long.parseLong(rawBar.get(0))),
                    new BigDecimal(rawBar.get(1)), 
                    new BigDecimal(rawBar.get(2)), 
                    new BigDecimal(rawBar.get(3)), 
                    new BigDecimal(rawBar.get(4)), 
                    new BigDecimal(rawBar.get(5))
                ));
            }
            // OKX returns in reverse chronological order (newest first)
            Collections.reverse(klineDataList);
            return klineDataList;
        }
    }

    public List<KLine> backfillHistoricalData(String symbol, String timeframe, long startTimeMillis) {
        List<KLine> allData = new ArrayList<>();
        Long currentBefore = null;
        final int batchLimit = 100;

        logger.info("Starting historical data backfill for {} @ {} from {}", symbol, timeframe, Instant.ofEpochMilli(startTimeMillis));

        while (true) {
            int retries = 3;
            List<KLine> batch = null;
            while (retries > 0) {
                try {
                    batch = getHistoricalData(symbol, timeframe, batchLimit, currentBefore, null);
                    break;
                } catch (IOException e) {
                    retries--;
                    logger.warn("Retry {}/3 for OKX backfill due to: {}. Retrying in 2s...", (3 - retries), e.getMessage());
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }

            if (batch == null || batch.isEmpty()) {
                logger.info("Backfill complete. No more data returned or retries failed.");
                break;
            }

            allData.addAll(0, batch);
            currentBefore = batch.get(0).timestamp().toEpochMilli();

            if (currentBefore <= startTimeMillis) {
                logger.info("Backfill complete. Reached start time.");
                break;
            }
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("OKX backfill process was interrupted.");
                break;
            }
        }
        
        allData.removeIf(k -> k.timestamp().toEpochMilli() < startTimeMillis);
        logger.info("Total historical klines backfilled for {} @ {}: {}", symbol, timeframe, allData.size());
        return allData;
    }

    public List<KLine> backfillHistoricalDataForward(String symbol, String timeframe, long startTimeMillis) {
        List<KLine> allData = new ArrayList<>();
        Long currentAfter = startTimeMillis;
        final int batchLimit = 100;

        logger.info("Starting forward historical data backfill for {} @ {} from {}", symbol, timeframe, Instant.ofEpochMilli(startTimeMillis));

        while (true) {
            int retries = 3;
            List<KLine> batch = null;
            while (retries > 0) {
                try {
                    batch = getHistoricalData(symbol, timeframe, batchLimit, null, currentAfter);
                    break;
                } catch (IOException e) {
                    retries--;
                    logger.warn("Retry {}/3 for OKX forward backfill due to: {}. Retrying in 2s...", (3 - retries), e.getMessage());
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }

            if (batch == null || batch.isEmpty()) {
                logger.info("Forward backfill complete. No more data returned or retries failed.");
                break;
            }

            allData.addAll(batch);
            currentAfter = batch.get(batch.size() - 1).timestamp().toEpochMilli();

            if (batch.size() < batchLimit) {
                logger.info("Forward backfill complete. Received last batch of data.");
                break;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("OKX forward backfill process was interrupted.");
                break;
            }
        }
        logger.info("Total historical klines forward-backfilled for {} @ {}: {}", symbol, timeframe, allData.size());
        return allData;
    }

    @Override
    public void connectToLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        LiveDataManager.getInstance().subscribe(symbol, timeframe, onKLineUpdate);
    }

    @Override
    public void disconnectFromLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        LiveDataManager.getInstance().unsubscribe(symbol, timeframe, onKLineUpdate);
    }
    
    @Override
    public void connectToTradeStream(String symbol, Consumer<TradeTick> onTradeUpdate) {
        logger.warn("Trade stream subscription not yet fully implemented in LiveDataManager.");
    }

    @Override
    public void disconnectFromTradeStream(String symbol, Consumer<TradeTick> onTradeUpdate) {
        logger.warn("Trade stream unsubscription not yet fully implemented in LiveDataManager.");
    }
    
    private static class OkxInstrumentData {
        String instId;
        String baseCcy;
        String quoteCcy;
        String state;
    }
}
package com.EcoChartPro.data.provider;

import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.SymbolInfo;
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
 * An implementation of DataProvider for fetching data from the Binance exchange.
 * It uses the REST API for historical data and symbol lists, and delegates
 * live streaming to the LiveDataManager.
 */
public class BinanceProvider implements DataProvider {

    private static final Logger logger = LoggerFactory.getLogger(BinanceProvider.class);
    private static final String API_BASE_URL = "https://api.binance.com/api/v3";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

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

    public List<KLine> getHistoricalData(String symbol, String timeframe, int limit) {
        String binanceSymbol = BinanceDataUtils.toBinanceSymbol(symbol).toUpperCase();
        String binanceInterval = BinanceDataUtils.toBinanceInterval(timeframe);
        String url = String.format("%s/klines?symbol=%s&interval=%s&limit=%d",
                API_BASE_URL, binanceSymbol, binanceInterval, limit);

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
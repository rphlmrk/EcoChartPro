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
                        // [FIX] Format display name correctly: "BTC-USDT-SWAP" -> "BTC/USDT-SWAP"
                        String displayName = s.instId.replaceFirst("-", "/");
                        return new ChartDataSource(
                            getProviderName(),
                            s.instId.toLowerCase(), // e.g., "btc-usdt-swap"
                            displayName,
                            null, // No local DB path for a live provider
                            // Provide a standard list of common timeframes
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
        String okxSymbol = OkxDataUtils.toOkxSymbol(symbol);
        String okxInterval = OkxDataUtils.toOkxInterval(timeframe);

        String url = String.format("%s/api/v5/market/history-candles?instId=%s&bar=%s&limit=%d",
                API_BASE_URL, okxSymbol, okxInterval, limit);

        Request request = new Request.Builder().url(url).build();
        logger.info("Fetching historical data from OKX: {}", url);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                logger.error("Failed to fetch k-line data from OKX. Code: {}, Message: {}",
                        response.code(), response.body() != null ? response.body().string() : "N/A");
                return Collections.emptyList();
            }

            String jsonBody = response.body().string();
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            Type listType = new TypeToken<List<List<String>>>() {}.getType();
            List<List<String>> rawData = gson.fromJson(root.get("data"), listType);

            List<KLine> klineDataList = new ArrayList<>();
            for (List<String> rawBar : rawData) {
                // [ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]
                klineDataList.add(new KLine(
                    Instant.ofEpochMilli(Long.parseLong(rawBar.get(0))),
                    new BigDecimal(rawBar.get(1)), // open
                    new BigDecimal(rawBar.get(2)), // high
                    new BigDecimal(rawBar.get(3)), // low
                    new BigDecimal(rawBar.get(4)), // close
                    new BigDecimal(rawBar.get(5))  // vol (base currency volume)
                ));
            }
            // OKX returns data in reverse chronological order (newest first)
            Collections.reverse(klineDataList);
            return klineDataList;

        } catch (IOException e) {
            logger.error("Network error while fetching k-line data for {} @ {}", symbol, timeframe, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void connectToLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        LiveDataManager.getInstance().subscribe(symbol, timeframe, onKLineUpdate);
    }

    @Override
    public void disconnectFromLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        LiveDataManager.getInstance().unsubscribe(symbol, timeframe, onKLineUpdate);
    }
    
    // Helper inner class for parsing the /public/instruments endpoint JSON
    private static class OkxInstrumentData {
        String instId;
        String baseCcy;
        String quoteCcy;
        String state;
    }
}
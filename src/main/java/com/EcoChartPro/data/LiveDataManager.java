package com.EcoChartPro.data;

import com.EcoChartPro.data.provider.BinanceDataUtils;
import com.EcoChartPro.data.provider.BinanceWebSocketClient;
import com.EcoChartPro.data.provider.OkxDataUtils;
import com.EcoChartPro.data.provider.OkxWebSocketClient;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.utils.DataSourceManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * [MODIFIED] A thread-safe, singleton service that multiplexes WebSocket connections
 * for the entire application, delegating to exchange-specific clients for handling
 * subscriptions, message routing, and reconnections.
 */
public class LiveDataManager {
    private static final Logger logger = LoggerFactory.getLogger(LiveDataManager.class);
    private static final LiveDataManager INSTANCE = new LiveDataManager();

    private final Map<String, I_ExchangeWebSocketClient> exchangeClients = new ConcurrentHashMap<>();
    private final Map<String, String> symbolToExchangeMap = new ConcurrentHashMap<>();
    private final Set<String> activeSubscriptions = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, List<Consumer<KLine>>> subscribers = new ConcurrentHashMap<>();

    private LiveDataManager() {}

    public static LiveDataManager getInstance() {
        return INSTANCE;
    }

    public void initialize(List<DataSourceManager.ChartDataSource> allSources) {
        symbolToExchangeMap.clear();
        for (DataSourceManager.ChartDataSource source : allSources) {
            if (source.providerName() != null && !source.providerName().equals("Local Files")) {
                 symbolToExchangeMap.put(source.symbol(), source.providerName());
            }
        }
        logger.info("LiveDataManager initialized with {} symbol-to-exchange mappings.", symbolToExchangeMap.size());
    }

    public synchronized void subscribe(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        String exchange = symbolToExchangeMap.get(symbol);
        if (exchange == null) {
            logger.error("Cannot subscribe to live data for symbol '{}': Unknown exchange.", symbol);
            return;
        }

        String streamName = buildStreamName(symbol, timeframe, exchange);
        subscribers.computeIfAbsent(streamName, k -> new CopyOnWriteArrayList<>()).add(onKLineUpdate);

        if (activeSubscriptions.add(streamName)) {
            logger.info("New subscription added: {}. Total active: {}. Updating {} client.", streamName, activeSubscriptions.size(), exchange);
            updateClientSubscriptions(exchange);
        }
    }

    public synchronized void unsubscribe(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        String exchange = symbolToExchangeMap.get(symbol);
        if (exchange == null) {
            logger.warn("Cannot unsubscribe for symbol '{}': Unknown exchange.", symbol);
            return;
        }
        
        String streamName = buildStreamName(symbol, timeframe, exchange);
        List<Consumer<KLine>> streamSubscribers = subscribers.get(streamName);
        
        if (streamSubscribers != null) {
            streamSubscribers.remove(onKLineUpdate);
            if (streamSubscribers.isEmpty()) {
                subscribers.remove(streamName);
                if (activeSubscriptions.remove(streamName)) {
                    logger.info("Last subscriber for {} removed. Total active: {}. Updating {} client.", streamName, activeSubscriptions.size(), exchange);
                    updateClientSubscriptions(exchange);
                }
            }
        }
    }

    private void updateClientSubscriptions(String exchange) {
        I_ExchangeWebSocketClient client = exchangeClients.computeIfAbsent(exchange, this::createClientForExchange);
        
        Set<String> exchangeStreams = activeSubscriptions.stream()
                .filter(stream -> getExchangeForStream(stream).equals(exchange))
                .collect(Collectors.toSet());
        
        client.updateSubscriptions(exchangeStreams);
    }
    
    private I_ExchangeWebSocketClient createClientForExchange(String exchange) {
        logger.info("Creating new WebSocket client for exchange: {}", exchange);
        I_ExchangeWebSocketClient client;
        switch (exchange) {
            case "Binance":
                client = new BinanceWebSocketClient();
                client.setMessageHandler(message -> this.handleStreamMessage(message, "Binance"));
                break;
            case "OKX":
                client = new OkxWebSocketClient();
                client.setMessageHandler(message -> this.handleStreamMessage(message, "OKX"));
                break;
            default:
                throw new IllegalArgumentException("No WebSocket client implementation for exchange: " + exchange);
        }
        return client;
    }
    
    private void handleStreamMessage(String message, String exchange) {
        try {
            switch (exchange) {
                case "Binance":
                    handleBinanceMessage(message);
                    break;
                case "OKX":
                    handleOkxMessage(message);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error parsing incoming WebSocket message from {}: {}", exchange, message, e);
        }
    }

    private void handleBinanceMessage(String message) {
        JsonObject root = JsonParser.parseString(message).getAsJsonObject();
        String streamName = root.get("stream").getAsString();
        JsonObject data = root.getAsJsonObject("data");
        String eventType = data.get("e").getAsString();

        if (!"kline".equals(eventType)) return;

        List<Consumer<KLine>> streamSubscribers = subscribers.get(streamName);
        if (streamSubscribers == null || streamSubscribers.isEmpty()) return;

        JsonObject klineJson = data.getAsJsonObject("k");
        KLine kline = new KLine(
                Instant.ofEpochMilli(klineJson.get("t").getAsLong()),
                new BigDecimal(klineJson.get("o").getAsString()),
                new BigDecimal(klineJson.get("h").getAsString()),
                new BigDecimal(klineJson.get("l").getAsString()),
                new BigDecimal(klineJson.get("c").getAsString()),
                new BigDecimal(klineJson.get("v").getAsString())
        );

        for (Consumer<KLine> consumer : streamSubscribers) {
            consumer.accept(kline);
        }
    }
    
    private void handleOkxMessage(String message) {
        JsonObject root = JsonParser.parseString(message).getAsJsonObject();

        if (root.has("event") || !root.has("arg") || !root.has("data")) {
            return;
        }

        JsonObject arg = root.getAsJsonObject("arg");
        String channel = arg.get("channel").getAsString();
        String instId = arg.get("instId").getAsString();
        String streamName = String.format("%s:%s", channel, instId);

        List<Consumer<KLine>> streamSubscribers = subscribers.get(streamName);
        if (streamSubscribers == null || streamSubscribers.isEmpty()) return;

        JsonArray dataArray = root.getAsJsonArray("data");
        for (JsonElement candleElement : dataArray) {
            JsonArray rawBar = candleElement.getAsJsonArray();
            KLine kline = new KLine(
                Instant.ofEpochMilli(rawBar.get(0).getAsLong()),
                new BigDecimal(rawBar.get(1).getAsString()), 
                new BigDecimal(rawBar.get(2).getAsString()), 
                new BigDecimal(rawBar.get(3).getAsString()), 
                new BigDecimal(rawBar.get(4).getAsString()), 
                new BigDecimal(rawBar.get(5).getAsString())
            );

            for (Consumer<KLine> consumer : streamSubscribers) {
                consumer.accept(kline);
            }
        }
    }

    private String getExchangeForStream(String streamName) {
        if (streamName.contains("@")) {
            return "Binance";
        } else if (streamName.contains(":")) {
            return "OKX";
        }
        return "Unknown";
    }

    private String buildStreamName(String symbol, String timeframe, String exchange) {
        switch (exchange) {
            case "Binance":
                String binanceSymbol = BinanceDataUtils.toBinanceSymbol(symbol);
                String binanceInterval = BinanceDataUtils.toBinanceInterval(timeframe);
                return String.format("%s@kline_%s", binanceSymbol, binanceInterval);
            case "OKX":
                 String okxSymbol = OkxDataUtils.toOkxSymbol(symbol);
                 String okxInterval = OkxDataUtils.toOkxInterval(timeframe);
                 String channel = "candle" + okxInterval;
                 return String.format("%s:%s", channel, okxSymbol);
            default:
                throw new IllegalArgumentException("Cannot build stream name for unknown exchange: " + exchange);
        }
    }
}
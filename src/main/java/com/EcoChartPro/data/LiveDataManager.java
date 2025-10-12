package com.EcoChartPro.data;

import com.EcoChartPro.data.provider.BinanceDataUtils;
import com.EcoChartPro.model.KLine;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A thread-safe, singleton service that maintains a single WebSocket connection
 * for the entire application, handling all stream subscriptions, message routing,
 * and automatic reconnections.
 */
public class LiveDataManager {
    private static final Logger logger = LoggerFactory.getLogger(LiveDataManager.class);
    private static final LiveDataManager INSTANCE = new LiveDataManager();

    private static final String WEBSOCKET_BASE_URL = "wss://stream.binance.com:9443/ws/";
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;

    private enum ConnectionState { CONNECTED, CONNECTING, DISCONNECTED, CLOSING }
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private WebSocketClient webSocketClient;
    private final Set<String> activeSubscriptions = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, List<Consumer<KLine>>> subscribers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "WebSocketReconnectScheduler"));
    private final AtomicLong reconnectDelayMs = new AtomicLong(INITIAL_RECONNECT_DELAY_MS);

    private LiveDataManager() {}

    public static LiveDataManager getInstance() {
        return INSTANCE;
    }

    public synchronized void subscribe(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        String streamName = buildStreamName(symbol, timeframe);
        subscribers.computeIfAbsent(streamName, k -> new CopyOnWriteArrayList<>()).add(onKLineUpdate);

        if (activeSubscriptions.add(streamName)) {
            logger.info("New subscription added: {}. Total active: {}", streamName, activeSubscriptions.size());
            reconnect(); // Reconnect with the new stream name in the URI
        }
    }

    public synchronized void unsubscribe(String symbol, String timeframe, Consumer<KLine> onKLineUpdate) {
        String streamName = buildStreamName(symbol, timeframe);
        List<Consumer<KLine>> streamSubscribers = subscribers.get(streamName);
        if (streamSubscribers != null) {
            streamSubscribers.remove(onKLineUpdate);
            if (streamSubscribers.isEmpty()) {
                subscribers.remove(streamName);
                if (activeSubscriptions.remove(streamName)) {
                    logger.info("Last subscriber for {} removed. Total active: {}", streamName, activeSubscriptions.size());
                    reconnect(); // Reconnect with the updated stream list
                }
            }
        }
    }

    private synchronized void reconnect() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
        }
        // The onClose handler will trigger the scheduleReconnect logic, which calls connect().
    }

    private synchronized void connect() {
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) return;
        if (activeSubscriptions.isEmpty()) {
            logger.info("No active subscriptions. WebSocket connection not required.");
            state = ConnectionState.DISCONNECTED;
            return;
        }

        state = ConnectionState.CONNECTING;
        try {
            String combinedStreams = String.join("/", activeSubscriptions);
            URI serverUri = new URI(WEBSOCKET_BASE_URL + "../stream?streams=" + combinedStreams);
            logger.info("LiveDataManager connecting to: {}", serverUri);

            webSocketClient = new WebSocketClient(serverUri) {
                @Override public void onOpen(ServerHandshake h) {
                    logger.info("WebSocket connection established.");
                    state = ConnectionState.CONNECTED;
                    reconnectDelayMs.set(INITIAL_RECONNECT_DELAY_MS);
                }
                @Override public void onMessage(String msg) { handleStreamMessage(msg); }
                @Override public void onClose(int code, String reason, boolean remote) {
                    logger.warn("WebSocket closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
                    state = ConnectionState.DISCONNECTED;
                    if (remote && state != ConnectionState.CLOSING) scheduleReconnect();
                }
                @Override public void onError(Exception ex) { logger.error("WebSocket error occurred.", ex); }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            logger.error("Invalid WebSocket URI or connection failed.", e);
            state = ConnectionState.DISCONNECTED;
        }
    }

    private void handleStreamMessage(String message) {
        try {
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
        } catch (Exception e) {
            logger.error("Error parsing incoming WebSocket message: {}", message, e);
        }
    }

    private void scheduleReconnect() {
        if (state == ConnectionState.CONNECTING || state == ConnectionState.CLOSING) return;

        long currentDelay = reconnectDelayMs.get();
        logger.info("Scheduling WebSocket reconnect attempt in {} ms.", currentDelay);

        try {
            reconnectScheduler.schedule(this::connect, currentDelay, TimeUnit.MILLISECONDS);
            long nextDelay = Math.min(currentDelay * 2, MAX_RECONNECT_DELAY_MS);
            reconnectDelayMs.set(nextDelay);
        } catch (RejectedExecutionException e) {
            logger.warn("Reconnect scheduling failed. Scheduler may be shutting down.");
        }
    }
    
    private String buildStreamName(String symbol, String timeframe) {
        String binanceSymbol = BinanceDataUtils.toBinanceSymbol(symbol);
        String binanceInterval = BinanceDataUtils.toBinanceInterval(timeframe);
        return String.format("%s@kline_%s", binanceSymbol, binanceInterval);
    }
}
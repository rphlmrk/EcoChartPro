package com.EcoChartPro.data.provider;

import com.EcoChartPro.data.I_ExchangeWebSocketClient;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class BinanceWebSocketClient implements I_ExchangeWebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private static final String WEBSOCKET_BASE_URL = "wss://stream.binance.com:9443/ws/";
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;

    private enum ConnectionState { CONNECTED, CONNECTING, DISCONNECTED, CLOSING }
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private WebSocketClient webSocketClient;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Binance-WS-Reconnect"));
    private final AtomicLong reconnectDelayMs = new AtomicLong(INITIAL_RECONNECT_DELAY_MS);
    private Consumer<String> messageHandler;
    private volatile Set<String> activeSubscriptions = Set.of();

    @Override
    public void setMessageHandler(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public synchronized void updateSubscriptions(Set<String> streamNames) {
        this.activeSubscriptions = streamNames;
        logger.info("Updating Binance subscriptions. New set has {} streams. Triggering reconnect.", streamNames.size());
        
        if (webSocketClient != null && webSocketClient.isOpen()) {
            state = ConnectionState.CLOSING; // Signal intent to close for subscription update
            webSocketClient.close();
        } else {
            // No active connection, so just schedule a new one
            scheduleReconnect();
        }
    }

    @Override
    public synchronized void disconnect() {
        if (webSocketClient != null) {
            state = ConnectionState.CLOSING;
            webSocketClient.close();
        }
        reconnectScheduler.shutdownNow();
        logger.info("Binance WebSocket client explicitly disconnected and scheduler shut down.");
    }
    
    private synchronized void connect() {
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) return;
        if (activeSubscriptions.isEmpty()) {
            logger.info("No active Binance subscriptions. WebSocket connection not required.");
            state = ConnectionState.DISCONNECTED;
            return;
        }

        state = ConnectionState.CONNECTING;
        try {
            String combinedStreams = String.join("/", activeSubscriptions);
            URI serverUri = new URI(WEBSOCKET_BASE_URL + "../stream?streams=" + combinedStreams);
            logger.info("Binance client connecting to: {}", serverUri);

            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake h) {
                    logger.info("Binance WebSocket connection established.");
                    state = ConnectionState.CONNECTED;
                    reconnectDelayMs.set(INITIAL_RECONNECT_DELAY_MS);
                }

                @Override
                public void onMessage(String msg) {
                    if (messageHandler != null) {
                        messageHandler.accept(msg);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.warn("Binance WebSocket closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
                    state = ConnectionState.DISCONNECTED;
                    if (state != ConnectionState.CLOSING) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("Binance WebSocket error occurred.", ex);
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            logger.error("Invalid Binance WebSocket URI or connection failed.", e);
            state = ConnectionState.DISCONNECTED;
        }
    }

    private void scheduleReconnect() {
        if (state == ConnectionState.CONNECTING || state == ConnectionState.CLOSING) return;

        long currentDelay = reconnectDelayMs.get();
        logger.info("Scheduling Binance WebSocket reconnect attempt in {} ms.", currentDelay);

        try {
            reconnectScheduler.schedule(this::connect, currentDelay, TimeUnit.MILLISECONDS);
            long nextDelay = Math.min(currentDelay * 2, MAX_RECONNECT_DELAY_MS);
            reconnectDelayMs.set(nextDelay);
        } catch (RejectedExecutionException e) {
            logger.warn("Binance reconnect scheduling failed. Scheduler may be shutting down.");
        }
    }
}
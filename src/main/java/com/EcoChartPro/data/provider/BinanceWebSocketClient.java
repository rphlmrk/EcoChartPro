package com.EcoChartPro.data.provider;

import com.EcoChartPro.data.I_ExchangeWebSocketClient;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class BinanceWebSocketClient implements I_ExchangeWebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private static final String WEBSOCKET_BASE_URL = "wss://stream.binance.com:9443/";
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;
    private static final long PING_INTERVAL_SECONDS = 25;


    private enum ConnectionState { CONNECTED, CONNECTING, DISCONNECTED, CLOSING }
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private WebSocketClient webSocketClient;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Binance-WS-Reconnect"));
    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Binance-WS-Ping"));
    private ScheduledFuture<?> pingTask;
    private long pingSentTimeNs = 0;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);


    private final AtomicLong reconnectDelayMs = new AtomicLong(INITIAL_RECONNECT_DELAY_MS);
    private Consumer<String> messageHandler;
    private Consumer<String> reconnectHandler;
    private volatile boolean wasUnintentionalDisconnect = false;
    private volatile Set<String> activeSubscriptions = Set.of();
    // Flag to manage update-driven reconnects
    private volatile boolean needsReconnectAfterUpdate = false; 

    @Override
    public void setMessageHandler(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void setReconnectHandler(Consumer<String> reconnectHandler) {
        this.reconnectHandler = reconnectHandler;
    }

    @Override
    public synchronized void updateSubscriptions(Set<String> streamNames) {
        this.activeSubscriptions = streamNames;
        logger.info("Updating Binance subscriptions. New set has {} streams. Triggering reconnect.", streamNames.size());
        
        if (webSocketClient != null && webSocketClient.isOpen()) {
            // Signal that this close requires a reconnect for the update
            needsReconnectAfterUpdate = true; 
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
        stopPingTimer();
        // Shutdown schedulers to prevent any further reconnect attempts
        reconnectScheduler.shutdownNow();
        pingScheduler.shutdownNow();
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
            URI serverUri = new URI(WEBSOCKET_BASE_URL + "stream?streams=" + combinedStreams);
            logger.info("Binance client connecting to: {}", serverUri);

            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake h) {
                    logger.info("Binance WebSocket connection established.");
                    state = ConnectionState.CONNECTED;
                    reconnectDelayMs.set(INITIAL_RECONNECT_DELAY_MS);
                    startPingTimer();
                    if (wasUnintentionalDisconnect && reconnectHandler != null) {
                        logger.info("Detected reconnect after unintentional disconnect. Triggering handler.");
                        reconnectHandler.accept("Binance");
                    }
                    wasUnintentionalDisconnect = false; // Reset flag
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
                    
                    // Updated reconnect logic using the dedicated flag
                    boolean isUpdateClose = needsReconnectAfterUpdate;
                    boolean isPermanentClose = (state == ConnectionState.CLOSING && !isUpdateClose);
                    
                    state = ConnectionState.DISCONNECTED;
                    stopPingTimer();

                    if (isUpdateClose) {
                        needsReconnectAfterUpdate = false; // Reset the flag
                        logger.info("Reconnecting after subscription update.");
                        scheduleReconnect();
                    } else if (!isPermanentClose) {
                        // This handles unintentional disconnects
                        wasUnintentionalDisconnect = true;
                        logger.info("Reconnecting after unintentional disconnect.");
                        scheduleReconnect();
                    } else {
                        logger.info("Intentional disconnect. No reconnect scheduled.");
                    }
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("Binance WebSocket error occurred.", ex);
                }

                @Override
                public void onWebsocketPong(WebSocket conn, Framedata f) {
                    if (pingSentTimeNs > 0) {
                        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pingSentTimeNs);
                        logger.trace("Received Binance pong frame. Latency: {} ms", latencyMs);
                        pcs.firePropertyChange("latencyMeasured", null, latencyMs);
                        pingSentTimeNs = 0; // Reset
                    }
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

    private void startPingTimer() {
        if (pingTask != null && !pingTask.isDone()) {
            pingTask.cancel(true);
        }
        pingTask = pingScheduler.scheduleAtFixedRate(() -> {
            if (webSocketClient != null && webSocketClient.isOpen()) {
                pingSentTimeNs = System.nanoTime();
                webSocketClient.sendPing();
                logger.trace("Sent Binance WebSocket ping frame.");
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopPingTimer() {
        if (pingTask != null) {
            pingTask.cancel(true);
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
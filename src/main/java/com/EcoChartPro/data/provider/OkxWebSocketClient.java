package com.EcoChartPro.data.provider;

import com.EcoChartPro.data.I_ExchangeWebSocketClient;
import com.google.gson.Gson;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

public class OkxWebSocketClient implements I_ExchangeWebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(OkxWebSocketClient.class);

    private static final String WEBSOCKET_URL = "wss://ws.okx.com:8443/ws/v5/business";
    private static final long PING_INTERVAL_SECONDS = 25;
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;

    private enum ConnectionState { CONNECTED, CONNECTING, DISCONNECTED, CLOSING }

    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private WebSocketClient webSocketClient;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "OKX-WS-Reconnect"));
    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "OKX-WS-Ping"));
    private ScheduledFuture<?> pingTask;

    private final AtomicLong reconnectDelayMs = new AtomicLong(INITIAL_RECONNECT_DELAY_MS);
    private Consumer<String> messageHandler;
    private Consumer<String> reconnectHandler;
    private volatile boolean wasUnintentionalDisconnect = false;
    private volatile Set<String> activeSubscriptions = Set.of();
    private final Gson gson = new Gson();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private long pingSentTimeNs = 0;
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
        logger.info("Updating OKX subscriptions. New set has {} streams. Triggering reconnect.", streamNames.size());
        
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
        // Shutdown schedulers to prevent any further reconnect attempts
        reconnectScheduler.shutdownNow();
        pingScheduler.shutdownNow();
        logger.info("OKX WebSocket client explicitly disconnected and schedulers shut down.");
    }

    private synchronized void connect() {
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) return;
        state = ConnectionState.CONNECTING;

        try {
            URI serverUri = new URI(WEBSOCKET_URL);
            logger.info("OKX client connecting to: {}", serverUri);

            webSocketClient = new WebSocketClient(serverUri, new Draft_6455(Collections.singletonList(new PerMessageDeflateExtension()))) {
                @Override
                public void onOpen(ServerHandshake h) {
                    logger.info("OKX WebSocket connection established.");
                    state = ConnectionState.CONNECTED;
                    reconnectDelayMs.set(INITIAL_RECONNECT_DELAY_MS);
                    if (wasUnintentionalDisconnect && reconnectHandler != null) {
                        logger.info("Detected reconnect after unintentional disconnect. Triggering handler.");
                        reconnectHandler.accept("OKX");
                    }
                    wasUnintentionalDisconnect = false; // Reset flag
                    startPingTimer();
                    sendSubscriptionRequest();
                }

                @Override
                public void onMessage(String msg) {
                    if (msg.equals("pong")) {
                        if (pingSentTimeNs > 0) {
                            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pingSentTimeNs);
                            logger.trace("Received OKX pong. Latency: {} ms", latencyMs);
                            pcs.firePropertyChange("latencyMeasured", null, latencyMs);
                            pingSentTimeNs = 0; // Reset
                        }
                    } else {
                        if (messageHandler != null) {
                            messageHandler.accept(msg);
                        }
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.warn("OKX WebSocket closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
                    
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
                    logger.error("OKX WebSocket error occurred.", ex);
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            logger.error("Invalid OKX WebSocket URI or connection failed.", e);
            state = ConnectionState.DISCONNECTED;
        }
    }

    private void sendSubscriptionRequest() {
        if (activeSubscriptions.isEmpty()) {
            return;
        }
        
        List<Map<String, String>> args = activeSubscriptions.stream().map(stream -> {
            String[] parts = stream.split(":", 2);
            return Map.of("channel", parts[0], "instId", parts[1]);
        }).collect(Collectors.toList());

        Map<String, Object> request = Map.of("op", "subscribe", "args", args);
        String jsonRequest = gson.toJson(request);
        
        logger.info("Sending OKX subscription request: {}", jsonRequest);
        webSocketClient.send(jsonRequest);
    }
    
    private void startPingTimer() {
        if (pingTask != null && !pingTask.isDone()) {
            pingTask.cancel(true);
        }
        pingTask = pingScheduler.scheduleAtFixedRate(() -> {
            if (webSocketClient != null && webSocketClient.isOpen()) {
                pingSentTimeNs = System.nanoTime(); // Record time before sending
                webSocketClient.send("ping");
                logger.trace("Sent OKX WebSocket ping.");
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopPingTimer() {
        if (pingTask != null) {
            pingTask.cancel(true);
        }
    }

    private void scheduleReconnect() {
        if (state == ConnectionState.CONNECTING || state == ConnectionState.CLOSING) return;

        long currentDelay = reconnectDelayMs.get();
        logger.info("Scheduling OKX WebSocket reconnect attempt in {} ms.", currentDelay);

        try {
            reconnectScheduler.schedule(this::connect, currentDelay, TimeUnit.MILLISECONDS);
            long nextDelay = Math.min(currentDelay * 2, MAX_RECONNECT_DELAY_MS);
            reconnectDelayMs.set(nextDelay);
        } catch (RejectedExecutionException e) {
            logger.warn("OKX reconnect scheduling failed. Scheduler may be shutting down.");
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
package com.EcoChartPro.data;

import java.beans.PropertyChangeListener;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Defines the contract for an exchange-specific WebSocket client that manages a single connection
 * for multiple data stream subscriptions.
 */
public interface I_ExchangeWebSocketClient {

    /**
     * Connects or reconnects the WebSocket client using the provided stream names.
     * If already connected, this will typically involve a disconnect/reconnect cycle to update subscriptions.
     *
     * @param streamNames The full set of stream names to subscribe to for this connection.
     */
    void updateSubscriptions(Set<String> streamNames);

    /**
     * Gracefully disconnects the WebSocket client.
     */
    void disconnect();

    /**
     * Sets the handler that will consume raw incoming messages from the WebSocket.
     *
     * @param messageHandler A consumer that accepts the raw JSON string message.
     */
    void setMessageHandler(Consumer<String> messageHandler);

    /**
     * [NEW] Sets the handler that will be invoked when the client successfully reconnects
     * after an unintentional disconnection.
     *
     * @param reconnectHandler A consumer that accepts the exchange name.
     */
    void setReconnectHandler(Consumer<String> reconnectHandler);

    /**
     * [NEW] Adds a property change listener to the client.
     * @param listener The listener to add.
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * [NEW] Removes a property change listener from the client.
     * @param listener The listener to remove.
     */
    void removePropertyChangeListener(PropertyChangeListener listener);
}
package com.EcoChartPro.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A singleton service that runs in the background to periodically check for an active internet connection.
 * It fires a "connectivityChanged" property change event when the status changes.
 */
public final class InternetConnectivityService {
    private static final Logger logger = LoggerFactory.getLogger(InternetConnectivityService.class);
    private static volatile InternetConnectivityService instance;

    private static final String TEST_HOST = "1.1.1.1"; // Cloudflare DNS
    private static final int TEST_PORT = 53; // DNS port
    private static final int TIMEOUT_MS = 2000;
    private static final int CHECK_INTERVAL_SECONDS = 5;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Internet-Connectivity-Check"));
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final AtomicBoolean isConnected = new AtomicBoolean(true); // Assume connected at start

    private InternetConnectivityService() {}

    public static InternetConnectivityService getInstance() {
        if (instance == null) {
            synchronized (InternetConnectivityService.class) {
                if (instance == null) {
                    instance = new InternetConnectivityService();
                }
            }
        }
        return instance;
    }

    /**
     * Starts the periodic connectivity check. Should be called once on application startup.
     */
    public void start() {
        logger.info("Starting internet connectivity checker (every {} seconds).", CHECK_INTERVAL_SECONDS);
        scheduler.scheduleAtFixedRate(this::checkConnectivity, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stops the connectivity checker. Should be called on application shutdown.
     */
    public void stop() {
        logger.info("Stopping internet connectivity checker.");
        scheduler.shutdownNow();
    }

    private void checkConnectivity() {
        boolean currentlyConnected;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(TEST_HOST, TEST_PORT), TIMEOUT_MS);
            currentlyConnected = true;
        } catch (IOException e) {
            // This is expected if there's no internet, so we don't log an error, just a debug message.
            logger.trace("Connectivity check failed: {}", e.getMessage());
            currentlyConnected = false;
        }

        // Only fire an event if the state has changed
        if (isConnected.getAndSet(currentlyConnected) != currentlyConnected) {
            logger.warn("Internet connectivity status changed to: {}", currentlyConnected ? "CONNECTED" : "DISCONNECTED");
            pcs.firePropertyChange("connectivityChanged", !currentlyConnected, currentlyConnected);
        }
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
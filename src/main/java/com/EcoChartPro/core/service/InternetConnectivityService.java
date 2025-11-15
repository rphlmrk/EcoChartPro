package com.EcoChartPro.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
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

    // [MODIFIED] Use a reliable HTTPS endpoint for the check.
    private static final String TEST_URL = "https://www.google.com";
    private static final int TIMEOUT_MS = 2500;
    private static final int CHECK_INTERVAL_SECONDS = 10; // Increased interval as HTTP is slightly heavier

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
        // [MODIFIED] Use an HTTP HEAD request, which is more robust against firewalls.
        boolean currentlyConnected = false;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(TEST_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            // We consider any 2xx or 3xx response as success.
            if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
                currentlyConnected = true;
                logger.trace("Connectivity check successful to {}", TEST_URL);
            } else {
                 logger.trace("Connectivity check to {} failed with response code: {}", TEST_URL, responseCode);
            }
        } catch (IOException e) {
            logger.trace("Connectivity check to {} failed with exception: {}", TEST_URL, e.getMessage());
            currentlyConnected = false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        // Only fire an event if the state has changed
        if (isConnected.getAndSet(currentlyConnected) != currentlyConnected) {
            if (currentlyConnected) {
                logger.info("Internet connectivity status changed to: CONNECTED");
            } else {
                logger.warn("Internet connectivity status changed to: DISCONNECTED");
            }
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
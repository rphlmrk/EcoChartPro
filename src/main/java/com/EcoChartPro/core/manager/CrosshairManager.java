package com.EcoChartPro.core.manager;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.ChartConfig;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A singleton manager that serves as the single source of truth for the
 * crosshair's position across the entire application. It uses a timestamp-based
 * FPS limiter to ensure high performance and prevent UI lag.
 */
public final class CrosshairManager implements PropertyChangeListener {

    private static volatile CrosshairManager instance;
    private final java.beans.PropertyChangeSupport pcs = new java.beans.PropertyChangeSupport(this);

    /**
     * MODIFICATION: A wrapper record to bundle the crosshair's data point
     * with the source component that generated the event.
     */
    public record CrosshairUpdate(DrawingObjectPoint point, Object source) {}

    private volatile DrawingObjectPoint currentPoint;
    private volatile Object lastSource;
    private volatile boolean syncEnabled = true; // MODIFICATION: New flag

    private long lastUpdateTime = 0;
    private int frameDelayMs;

    private CrosshairManager() {
        this.frameDelayMs = SettingsService.getInstance().getCrosshairFps().getDelayMs();
        // [FIX] Corrected the method call to match the signature in SettingsService.
        // The propertyChange method already filters for "crosshairFpsChanged".
        SettingsService.getInstance().addPropertyChangeListener(this);
    }

    public static CrosshairManager getInstance() {
        if (instance == null) {
            synchronized (CrosshairManager.class) {
                if (instance == null) {
                    instance = new CrosshairManager();
                }
            }
        }
        return instance;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("crosshairFpsChanged".equals(evt.getPropertyName())) {
            ChartConfig.CrosshairFPS newFps = (ChartConfig.CrosshairFPS) evt.getNewValue();
            if (newFps != null) {
                this.frameDelayMs = newFps.getDelayMs();
            }
        }
    }

    public void updatePosition(DrawingObjectPoint point, Object source) {
        this.currentPoint = point;
        this.lastSource = source;

        long now = System.currentTimeMillis();
        if ((now - lastUpdateTime) > frameDelayMs) {
            fireUpdate();
            lastUpdateTime = now;
        }
    }

    public void clearPosition() {
        this.currentPoint = null;
        fireUpdate();
    }
    
    private void fireUpdate() {
        // Fire the new CrosshairUpdate record as the payload
        pcs.firePropertyChange("crosshairMoved", null, new CrosshairUpdate(this.currentPoint, this.lastSource));
    }

    public DrawingObjectPoint getCurrentPoint() {
        return currentPoint;
    }

    public Object getLastSource() {
        return lastSource;
    }

    /**
     * New getter for the sync state.
     */
    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    /**
     * New setter for the sync state.
     */
    public void setSyncEnabled(boolean syncEnabled) {
        boolean oldVal = this.syncEnabled;
        this.syncEnabled = syncEnabled;
        pcs.firePropertyChange("syncStateChanged", oldVal, this.syncEnabled);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }
}
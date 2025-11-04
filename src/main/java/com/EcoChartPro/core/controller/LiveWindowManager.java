package com.EcoChartPro.core.controller;

import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;

/**
 * A singleton manager that holds the state for an active LIVE trading session.
 * This provides a central point for new windows to query when syncing to an
 * existing live session, decoupling live mode from the ReplaySessionManager.
 */
public class LiveWindowManager {

    private static volatile LiveWindowManager instance;
    private ChartDataSource activeDataSource;
    private boolean isSessionActive = false;

    private LiveWindowManager() {}

    public static LiveWindowManager getInstance() {
        if (instance == null) {
            synchronized (LiveWindowManager.class) {
                if (instance == null) {
                    instance = new LiveWindowManager();
                }
            }
        }
        return instance;
    }

    /**
     * Starts a new live session, setting the active data source.
     * @param source The data source for the live session.
     */
    public void startSession(ChartDataSource source) {
        this.activeDataSource = source;
        this.isSessionActive = true;
    }

    /**
     * Ends the current live session, clearing the state.
     */
    public void endSession() {
        this.activeDataSource = null;
        this.isSessionActive = false;
    }

    /**
     * Gets the data source of the currently active live session.
     * @return The active ChartDataSource, or null if no session is active.
     */
    public ChartDataSource getActiveDataSource() {
        return this.activeDataSource;
    }

    /**
     * Checks if a live session is currently active.
     * @return true if a session is active, false otherwise.
     */
    public boolean isActive() {
        return this.isSessionActive;
    }
}
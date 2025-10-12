package com.EcoChartPro.core.controller;

import com.EcoChartPro.model.KLine;

/**
 * An interface for components that need to be notified of changes
 * in the replay session's state or live data ticks.
 */
public interface ReplayStateListener {

    /**
     * Called for every single bar advancement (tick) in Replay or Live mode.
     * @param newBar The new K-line that has just been processed.
     */
    void onReplayTick(KLine newBar);

    /**
     * Called when the replay session officially starts or is initialized.
     * This signals to the listener that it should set up its initial state
     * from the ReplaySessionManager.
     */
    void onReplaySessionStart();

    /**
     * Called when the replay session is paused or finishes.
     */
    void onReplayStateChanged();
}
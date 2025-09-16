package com.EcoChartPro.core.controller;

import com.EcoChartPro.model.KLine;
import java.util.List;

/**
 * An interface for components that need to be notified of changes
 * in the replay session's state.
 */
public interface ReplayStateListener {

    /**
     * Called by the ReplaySessionManager for every single bar advancement (tick).
     * @param newM1Bar The new 1-minute K-line that has just been processed.
     */
    void onReplayTick(KLine newM1Bar);

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
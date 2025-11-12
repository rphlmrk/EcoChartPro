package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.core.trading.SessionType;

/**
 * A container for all services and managers that constitute a single, isolated session (e.g., Live or Replay).
 * This class is the core of the refactoring away from session-specific singletons.
 */
public class WorkspaceContext {

    private final PaperTradingService paperTradingService;
    private final DrawingManager drawingManager;
    private final LiveSessionTrackerService sessionTracker;
    private final UndoManager undoManager;

    public WorkspaceContext() {
        // Instantiate services in order of dependency to allow constructor injection.
        // NOTE: We are assuming UndoManager and DrawingManager have been converted to non-singletons.
        this.undoManager = new UndoManager();
        this.drawingManager = new DrawingManager(this.undoManager);
        this.paperTradingService = new PaperTradingService(this.drawingManager);
        this.sessionTracker = new LiveSessionTrackerService(this.paperTradingService);

        // Wire up the tracker to listen to events from its own paper trading service.
        this.paperTradingService.addPropertyChangeListener(this.sessionTracker);
    }

    // --- Getters ---

    public PaperTradingService getPaperTradingService() {
        return paperTradingService;
    }

    public DrawingManager getDrawingManager() {
        return drawingManager;
    }

    public LiveSessionTrackerService getSessionTracker() {
        return sessionTracker;
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }
}
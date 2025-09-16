package com.EcoChartPro.core.commands;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.model.drawing.DrawingObject;

/**
 * A command to update a drawing object in the DrawingManager.
 * This command stores both the state before and after the update to allow for undo.
 */
public class UpdateDrawingCommand implements UndoableCommand {
    private final DrawingManager drawingManager;
    private final DrawingObject stateBefore;
    private final DrawingObject stateAfter;

    /**
     * Constructs a command to update a drawing.
     * @param stateBefore The state of the DrawingObject before the modification.
     * @param stateAfter The state of the DrawingObject after the modification.
     */
    public UpdateDrawingCommand(DrawingObject stateBefore, DrawingObject stateAfter) {
        this.drawingManager = DrawingManager.getInstance();
        this.stateBefore = stateBefore;
        this.stateAfter = stateAfter;
    }

    @Override
    public void execute() {
        // The forward action is to apply the "after" state.
        drawingManager.performUpdate(stateAfter);
    }

    @Override
    public void undo() {
        // The reverse action is to revert to the "before" state.
        drawingManager.performUpdate(stateBefore);
    }
}
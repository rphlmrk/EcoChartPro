package com.EcoChartPro.core.commands;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.model.drawing.DrawingObject;

/**
 * A command to remove a drawing object from the DrawingManager.
 * It stores the object that was removed so it can be restored on undo.
 */
public class RemoveDrawingCommand implements UndoableCommand {
    private final DrawingManager drawingManager;
    private final DrawingObject drawingObject; // Store the object to be able to add it back

    /**
     * Constructs a command to remove a drawing.
     * @param drawingObject The DrawingObject to be removed.
     * @param drawingManager The specific DrawingManager instance to operate on.
     */
    public RemoveDrawingCommand(DrawingObject drawingObject, DrawingManager drawingManager) {
        this.drawingManager = drawingManager;
        this.drawingObject = drawingObject;
    }

    @Override
    public void execute() {
        // The forward action is to remove the object.
        drawingManager.performRemove(drawingObject.id());
    }

    @Override
    public void undo() {
        // The reverse of removing is to add the object back.
        drawingManager.performAdd(drawingObject);
    }
}
package com.EcoChartPro.core.commands;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.model.drawing.DrawingObject;

/**
 * A command to add a drawing object to the DrawingManager.
 * This class encapsulates the action of adding a drawing, allowing it to be undone.
 */
public class AddDrawingCommand implements UndoableCommand {
    private final DrawingManager drawingManager;
    private final DrawingObject drawingObject;

    /**
     * Constructs a command to add a drawing.
     * @param drawingObject The DrawingObject to be added.
     */
    public AddDrawingCommand(DrawingObject drawingObject) {
        this.drawingManager = DrawingManager.getInstance();
        this.drawingObject = drawingObject;
    }

    @Override
    public void execute() {
        drawingManager.performAdd(drawingObject);
    }

    @Override
    public void undo() {
        // The reverse of adding is removing.
        drawingManager.performRemove(drawingObject.id());
    }
}
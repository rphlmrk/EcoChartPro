package com.EcoChartPro.core.manager;

import com.EcoChartPro.core.commands.UndoableCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A manager that handles the undo and redo functionality for a single workspace.
 * It uses two stacks to keep track of executed commands.
 */
public final class UndoManager {

    private static final Logger logger = LoggerFactory.getLogger(UndoManager.class);

    private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
    private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public UndoManager() {
        // Public constructor for non-singleton instantiation
    }

    /**
     * Executes a command, adds it to the undo history, and clears the redo history.
     * @param command The command to execute.
     */
    public void executeCommand(UndoableCommand command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();
        logger.debug("Executed command: {}. Undo stack size: {}.", command.getClass().getSimpleName(), undoStack.size());
        fireStateChange();
    }

    /**
     * New method to add a command to history without executing it.
     * This is used for operations like dragging, where the final state is already
     * on screen and we just need to record the "before" and "after" states for undo.
     * @param command The pre-executed command to add to the undo stack.
     */
    public void addCommandToHistory(UndoableCommand command) {
        undoStack.push(command);
        redoStack.clear(); // A new action always clears the redo stack
        logger.debug("Added command to history: {}. Undo stack size: {}.", command.getClass().getSimpleName(), undoStack.size());
        fireStateChange();
    }


    /**
     * Undoes the most recent command.
     * If there are no commands to undo, this method does nothing.
     */
    public void undo() {
        if (canUndo()) {
            UndoableCommand command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            logger.debug("Undid command: {}. Redo stack size: {}.", command.getClass().getSimpleName(), redoStack.size());
            fireStateChange();
        } else {
            logger.warn("Attempted to undo, but undo stack is empty.");
        }
    }

    /**
     * Redoes the most recently undone command.
     * If there are no commands to redo, this method does nothing.
     */
    public void redo() {
        if (canRedo()) {
            UndoableCommand command = redoStack.pop();
            command.execute();
            undoStack.push(command);
            logger.debug("Redid command: {}. Undo stack size: {}.", command.getClass().getSimpleName(), undoStack.size());
            fireStateChange();
        } else {
            logger.warn("Attempted to redo, but redo stack is empty.");
        }
    }

    /**
     * Checks if there is a command that can be undone.
     * @return true if the undo stack is not empty, false otherwise.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Checks if there is a command that can be redone.
     * @return true if the redo stack is not empty, false otherwise.
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Notifies listeners that the state of the undo/redo stacks has changed.
     */
    private void fireStateChange() {
        pcs.firePropertyChange("stateChanged", null, null);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
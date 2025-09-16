package com.EcoChartPro.core.commands;

/**
 * Represents a command that can be executed and undone.
 * This is the core interface for the Command Pattern used in the undo/redo system.
 */
public interface UndoableCommand {
    /**
     * Executes the command's primary action.
     */
    void execute();

    /**
     * Reverts the action performed by execute().
     */
    void undo();
}
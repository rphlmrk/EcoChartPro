package com.EcoChartPro.ui.action;

import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.ui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Manages the creation and assembly of the main application menu bar.
 */
public class MenuBarManager {

    private final MainWindow owner;
    private final boolean isReplayMode;

    public record MenuBarResult(JComponent menu, JMenuItem undoMenuItem, JMenuItem redoMenuItem) {}

    public MenuBarManager(MainWindow owner, boolean isReplayMode) {
        this.owner = owner;
        this.isReplayMode = isReplayMode;
    }

    public MenuBarResult createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(createFileMenu());
        MenuBarResult editMenuResult = createEditMenu();
        menuBar.add(editMenuResult.menu());
        menuBar.add(createToolsMenu());
        
        // FIX: The create methods now return JMenuItem instead of JMenu
        // and are added directly to the menu bar.
        menuBar.add(createInsightsMenuItem());
        menuBar.add(createProgressionMenuItem());

        // The final result uses the full menu bar
        return new MenuBarResult(menuBar, editMenuResult.undoMenuItem(), editMenuResult.redoMenuItem());
    }

    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        if (isReplayMode) {
            JMenuItem newSyncedWindowItem = new JMenuItem("New Synced Window");
            newSyncedWindowItem.addActionListener(e -> owner.openNewSyncedWindow());
            fileMenu.add(newSyncedWindowItem);

            JMenuItem saveSessionItem = new JMenuItem("Save Replay Session...");
            // FIX: Pass the isReplayMode flag to the saveSessionWithUI method.
            saveSessionItem.addActionListener(e -> owner.getSessionController().saveSessionWithUI(owner, isReplayMode));
            fileMenu.add(saveSessionItem);
            fileMenu.addSeparator();

            JMenuItem importTradesItem = new JMenuItem("Import Trade History from CSV...");
            importTradesItem.addActionListener(e -> owner.getSessionController().importTradeHistory(owner));
            fileMenu.add(importTradesItem);

            JMenuItem exportTradesItem = new JMenuItem("Export Trade History to CSV...");
            exportTradesItem.addActionListener(e -> owner.getSessionController().exportTradeHistory(owner));
            fileMenu.add(exportTradesItem);
            fileMenu.addSeparator();
        }
        JMenuItem closeItem = new JMenuItem("Close Window");
        closeItem.addActionListener(e -> owner.getSessionController().handleWindowClose(owner, isReplayMode));
        fileMenu.add(closeItem);
        return fileMenu;
    }

    private MenuBarResult createEditMenu() {
        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoMenuItem = new JMenuItem("Undo");
        undoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        undoMenuItem.addActionListener(e -> UndoManager.getInstance().undo());
        editMenu.add(undoMenuItem);

        JMenuItem redoMenuItem = new JMenuItem("Redo");
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        if (isMac) {
            redoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
        } else {
            redoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }
        redoMenuItem.addActionListener(e -> UndoManager.getInstance().redo());
        editMenu.add(redoMenuItem);

        return new MenuBarResult(editMenu, undoMenuItem, redoMenuItem);
    }

    private JMenu createToolsMenu() {
        JMenu toolsMenu = new JMenu("Tools");
        JMenuItem javaEditorItem = new JMenuItem("Java Editor...");
        javaEditorItem.addActionListener(e -> owner.getUiManager().openJavaEditor());
        toolsMenu.add(javaEditorItem);

        JMenuItem calculatorItem = new JMenuItem("Position Size Calculator...");
        calculatorItem.addActionListener(e -> owner.getUiManager().openPositionSizeCalculator());
        toolsMenu.add(calculatorItem);

        toolsMenu.addSeparator();

        JMenuItem shortcutsItem = new JMenuItem("Keyboard Shortcuts...");
        shortcutsItem.addActionListener(e -> showShortcutsDialog());
        toolsMenu.add(shortcutsItem);

        JMenuItem settingsItem = new JMenuItem("Settings...");
        settingsItem.setEnabled(true);
        settingsItem.addActionListener(e -> owner.getUiManager().openSettingsDialog());
        toolsMenu.add(settingsItem);

        return toolsMenu;
    }

    // FIX: Changed from JMenu to JMenuItem and added direct action listener.
    private JMenuItem createInsightsMenuItem() {
        JMenuItem insightsMenuItem = new JMenuItem("Insights");
        insightsMenuItem.setEnabled(isReplayMode); // Only enable in replay mode
        insightsMenuItem.addActionListener(e -> owner.getUiManager().openInsightsDialog());
        return insightsMenuItem;
    }
    
    // FIX: Changed from JMenu to JMenuItem and added direct action listener.
    private JMenuItem createProgressionMenuItem() {
        JMenuItem progressionMenuItem = new JMenuItem("Progression");
        progressionMenuItem.addActionListener(e -> owner.getUiManager().openAchievementsDialog());
        return progressionMenuItem;
    }

    private void showShortcutsDialog() {
        String shortcuts = """
            <html>
                <style>
                    table { width: 100%; border-collapse: collapse; }
                    th, td { border: 1px solid #555; padding: 6px; text-align: left; }
                    th { background-color: #3C3F41; }
                    kbd {
                        background-color: #4E5254;
                        border-radius: 3px;
                        border: 1px solid #555;
                        padding: 2px 4px;
                        font-family: monospace;
                    }
                </style>
                <body>
                    <h2>Global Shortcuts</h2>
                    <table>
                        <tr><th>Shortcut</th><th>Action</th></tr>
                        <tr><td><kbd>Alt</kbd> + <kbd>T</kbd></td><td>Activate Trendline Tool</td></tr>
                        <tr><td><kbd>Alt</kbd> + <kbd>R</kbd></td><td>Activate Rectangle Tool</td></tr>
                        <tr><td><kbd>Delete</kbd></td><td>Delete Selected Drawing</td></tr>
                        <tr><td><kbd>Esc</kbd></td><td>Deactivate Drawing Tool / Deselect</td></tr>
                    </table>
                    <h2>On-Chart Actions</h2>
                    <table>
                        <tr><th>Action</th><th>Description</th></tr>
                        <tr><td>Type Timeframe (e.g., <kbd>5m</kbd>, <kbd>1h</kbd>) + <kbd>Enter</kbd></td><td>Change active chart's timeframe</td></tr>
                        <tr><td>Mouse Wheel</td><td>Zoom in/out on chart</td></tr>
                        <tr><td>Click & Drag</td><td>Pan chart</td></tr>
                    </table>
                </body>
            </html>
            """;
        JOptionPane.showMessageDialog(owner, new JLabel(shortcuts), "Keyboard Shortcuts", JOptionPane.PLAIN_MESSAGE);
    }
}
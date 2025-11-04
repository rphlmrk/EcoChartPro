package com.EcoChartPro.ui.action;

import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.dialogs.AboutDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.List;

/**
 * Manages the creation and assembly of the main application menu bar.
 */
public class MenuBarManager {

    private final MainWindow owner;
    private final boolean isReplayMode;
    private int currentShortcutIndex = 0;

    public record MenuBarResult(JComponent menu, JMenuItem undoMenuItem, JMenuItem redoMenuItem, JLabel connectivityStatusLabel, JLabel latencyLabel) {}

    public MenuBarManager(MainWindow owner, boolean isReplayMode) {
        this.owner = owner;
        this.isReplayMode = isReplayMode;
    }

    public MenuBarResult createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // --- 1. LEFT GROUP: Menus ---
        menuBar.add(createFileMenu());
        MenuBarResult editMenuResult = createEditMenu();
        menuBar.add(editMenuResult.menu());
        menuBar.add(createToolsMenu());
        menuBar.add(createInsightsMenu());
        menuBar.add(createHelpMenu());

        // --- First Spacer ---
        menuBar.add(Box.createHorizontalGlue());

        // --- 2. CENTER GROUP: Rotating Shortcuts ---
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        String undoShortcut = isMac ? "Cmd+Z: Undo" : "Ctrl+Z: Undo";
        String redoShortcut = isMac ? "Cmd+Shift+Z: Redo" : "Ctrl+Y: Redo";
        final List<String> idleShortcuts = List.of(
            "Alt+T: Trendline",
            "Alt+R: Rectangle",
            "On Chart: Type Timeframe (e.g., 5m, 1h) + Enter",
            undoShortcut,
            redoShortcut
        );
        
        JLabel shortcutsLabel = new JLabel(idleShortcuts.get(0), JLabel.CENTER);
        shortcutsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        menuBar.add(shortcutsLabel);

        new Timer(3000, e -> {
            currentShortcutIndex = (currentShortcutIndex + 1) % idleShortcuts.size();
            shortcutsLabel.setText(idleShortcuts.get(currentShortcutIndex));
        }).start();

        // --- Second Spacer ---
        menuBar.add(Box.createHorizontalGlue());

        // --- 3. RIGHT GROUP: Status Icons ---
        JLabel connectivityStatusLabel = new JLabel();
        connectivityStatusLabel.setToolTipText("Internet Connection Status");
        connectivityStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        menuBar.add(connectivityStatusLabel);

        JLabel latencyLabel = new JLabel("-- ms");
        latencyLabel.setToolTipText("Data Latency");
        latencyLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 10));
        menuBar.add(latencyLabel);

        return new MenuBarResult(menuBar, editMenuResult.undoMenuItem(), editMenuResult.redoMenuItem(), connectivityStatusLabel, latencyLabel);
    }

    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        
        JMenuItem newSyncedWindowItem = new JMenuItem("New Synced Window");
        newSyncedWindowItem.addActionListener(e -> owner.openNewSyncedWindow());
        fileMenu.add(newSyncedWindowItem);

        if (isReplayMode) {
            JMenuItem saveSessionItem = new JMenuItem("Save Replay Session...");
            saveSessionItem.addActionListener(e -> owner.getSessionController().saveSessionWithUI(owner, isReplayMode));
            fileMenu.add(saveSessionItem);
        }
        fileMenu.addSeparator();

        JMenuItem importTradesItem = new JMenuItem("Import Trade History from CSV...");
        importTradesItem.addActionListener(e -> owner.getSessionController().importTradeHistory(owner));
        fileMenu.add(importTradesItem);

        JMenuItem exportTradesItem = new JMenuItem("Export Trade History to CSV...");
        exportTradesItem.addActionListener(e -> owner.getSessionController().exportTradeHistory(owner));
        fileMenu.add(exportTradesItem);
        fileMenu.addSeparator();
        
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

        return new MenuBarResult(editMenu, undoMenuItem, redoMenuItem, null, null);
    }

    private JMenu createToolsMenu() {
        JMenu toolsMenu = new JMenu("Tools");
        JMenuItem javaEditorItem = new JMenuItem("Java Editor...");
        javaEditorItem.addActionListener(e -> owner.getUiManager().openJavaEditor());
        toolsMenu.add(javaEditorItem);

        JMenuItem marketplaceItem = new JMenuItem("Community Marketplace...");
        marketplaceItem.addActionListener(e -> owner.getUiManager().openMarketplaceDialog());
        toolsMenu.add(marketplaceItem);
        
        toolsMenu.addSeparator();

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

    private JMenu createInsightsMenu() {
        JMenu insightsMenu = new JMenu("Insights");
        JMenuItem showInsightsItem = new JMenuItem("Show Insights Dialog...");
        // [FIX] Insights are available for both live and replay modes.
        showInsightsItem.setEnabled(true); 
        showInsightsItem.addActionListener(e -> owner.getUiManager().openInsightsDialog());
        insightsMenu.add(showInsightsItem);

        JMenuItem showAchievementsItem = new JMenuItem("Show Achievements...");
        showAchievementsItem.addActionListener(e -> owner.getUiManager().openAchievementsDialog());
        insightsMenu.add(showAchievementsItem);

        return insightsMenu;
    }

    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
    
        JMenuItem aboutItem = new JMenuItem("About Eco Chart Pro");
        aboutItem.addActionListener(e -> {
            AboutDialog aboutDialog = new AboutDialog(owner);
            aboutDialog.setVisible(true);
        });
        helpMenu.add(aboutItem);
    
        JMenuItem docsItem = new JMenuItem("View Documentation");
        docsItem.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI("https://github.com/rphlmrk/EcoChartPro"));
                } else {
                    JOptionPane.showMessageDialog(owner, "Could not open browser. Please visit:\nhttps://github.com/rphlmrk/EcoChartPro", "Browser Not Supported", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(owner, "Error opening documentation link:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        helpMenu.add(docsItem);
    
        return helpMenu;
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
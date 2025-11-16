package com.EcoChartPro.ui.action;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.ui.PrimaryFrame;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;

/**
 * [REFACTORED] Manages the dynamic, context-sensitive title bar for a JFrame.
 * This class is now a JPanel that serves as a custom, all-in-one title bar,
 * integrating menus, navigation, status, and window controls.
 */
public class TitleBarManager extends JPanel {

    private final PrimaryFrame owner;

    // --- UI Components ---
    private JMenuBar menuBar;
    private JLabel titleStatusLabel;
    private JToggleButton analysisNavButton, replayNavButton, liveNavButton;
    private ButtonGroup navGroup;
    private JLabel shortcutsLabel;
    
    // --- Window Dragging Fields ---
    private Point initialClick;

    public TitleBarManager(PrimaryFrame owner) {
        super(new BorderLayout(10, 0));
        this.owner = owner;
        initializeUI();
        addDragListeners();
    }

    /**
     * Builds the layout and components of the custom title bar.
     */
    private void initializeUI() {
        setOpaque(true);
        setBackground(UIManager.getColor("MenuBar.background"));
        setBorder(new MatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        setPreferredSize(new Dimension(0, 32));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(createNavigationControls(), BorderLayout.WEST);
        centerPanel.add(createStatusArea(), BorderLayout.EAST);

        titleStatusLabel = new JLabel("", JLabel.CENTER);
        titleStatusLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
        centerPanel.add(titleStatusLabel, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
        add(createWindowControls(), BorderLayout.EAST);
    }
    
    private JPanel createWindowControls() {
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlsPanel.setOpaque(false);

        JButton minimizeButton = createControlButton("\u2014"); // Underscore
        minimizeButton.addActionListener(e -> owner.setState(Frame.ICONIFIED));

        JButton maximizeButton = createControlButton("\u25A1"); // Square
        maximizeButton.addActionListener(e -> {
            owner.setExtendedState(owner.getExtendedState() ^ Frame.MAXIMIZED_BOTH);
        });

        JButton closeButton = createControlButton("\u2715"); // X
        closeButton.addActionListener(e -> {
            // [FIX] Dispatch a window closing event to trigger listeners (e.g., save prompt)
            owner.dispatchEvent(new java.awt.event.WindowEvent(owner, java.awt.event.WindowEvent.WINDOW_CLOSING));
        });

        controlsPanel.add(minimizeButton);
        controlsPanel.add(maximizeButton);
        controlsPanel.add(closeButton);

        return controlsPanel;
    }
    
    private JButton createControlButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setFont(new Font("SansSerif", Font.PLAIN, 14));
        button.setMargin(new Insets(0, 8, 0, 8));
        return button;
    }

    private void addDragListeners() {
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getSource() == TitleBarManager.this || e.getSource() == titleStatusLabel.getParent()) {
                    initialClick = e.getPoint();
                } else {
                    initialClick = null;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                initialClick = null;
            }
        };

        addMouseListener(dragAdapter);
        titleStatusLabel.getParent().addMouseListener(dragAdapter);

        MouseMotionAdapter motionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (initialClick != null) {
                    int thisX = owner.getLocation().x;
                    int thisY = owner.getLocation().y;
                    int xMoved = e.getX() - initialClick.x;
                    int yMoved = e.getY() - initialClick.y;
                    int X = thisX + xMoved;
                    int Y = thisY + yMoved;
                    owner.setLocation(X, Y);
                }
            }
        };

        addMouseMotionListener(motionAdapter);
        titleStatusLabel.getParent().addMouseMotionListener(motionAdapter);
    }

    private JPanel createNavigationControls() {
        JPanel navButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        navButtonPanel.setOpaque(false);
        navButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        navGroup = new ButtonGroup();
        analysisNavButton = createNavButton("Analysis", "ANALYSIS", true);
        replayNavButton = createNavButton("Replay", "REPLAY", false);
        liveNavButton = createNavButton("Live", "LIVE", false);

        navGroup.add(analysisNavButton);
        navGroup.add(replayNavButton);
        navGroup.add(liveNavButton);

        navButtonPanel.add(analysisNavButton);
        navButtonPanel.add(replayNavButton);
        navButtonPanel.add(liveNavButton);

        return navButtonPanel;
    }

    private JToggleButton createNavButton(String text, String actionCommand, boolean selected) {
        JToggleButton button = new JToggleButton(text, selected);
        button.setActionCommand(actionCommand);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.addActionListener(e -> {
            owner.getMainCardLayout().show(owner.getMainContentPanel(), actionCommand);
            if (!"REPLAY".equals(actionCommand)) {
                ReplaySessionManager.getInstance().pause();
            }
            owner.revalidate();

            if ("LIVE".equals(actionCommand)) {
                owner.getReplayWorkspacePanel().getDrawingToolbar().setVisible(false);
                owner.getReplayWorkspacePanel().getPropertiesToolbar().setVisible(false);
                if (owner.getLiveWorkspacePanel().getActiveChartPanel() != null) {
                    owner.getLiveWorkspacePanel().getDrawingToolbar().setVisible(true);
                }
            } else if ("REPLAY".equals(actionCommand)) {
                owner.getLiveWorkspacePanel().getDrawingToolbar().setVisible(false);
                owner.getLiveWorkspacePanel().getPropertiesToolbar().setVisible(false);
                if (owner.getReplayWorkspacePanel().getActiveChartPanel() != null) {
                    owner.getReplayWorkspacePanel().getDrawingToolbar().setVisible(true);
                }
            } else {
                owner.getLiveWorkspacePanel().getDrawingToolbar().setVisible(false);
                owner.getLiveWorkspacePanel().getPropertiesToolbar().setVisible(false);
                owner.getReplayWorkspacePanel().getDrawingToolbar().setVisible(false);
                owner.getReplayWorkspacePanel().getPropertiesToolbar().setVisible(false);
            }
        });
        return button;
    }

    private JPanel createStatusArea() {
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusPanel.setOpaque(false);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(0,0,0,10));

        shortcutsLabel = new JLabel("", JLabel.CENTER);
        shortcutsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        startShortcutRotation(shortcutsLabel);

        statusPanel.add(shortcutsLabel);
        return statusPanel;
    }

    private void startShortcutRotation(JLabel shortcutsLabel) {
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        String undoShortcut = isMac ? "Cmd+Z: Undo" : "Ctrl+Z: Undo";
        String redoShortcut = isMac ? "Cmd+Shift+Z: Redo" : "Ctrl+Y: Redo";
        final List<String> idleShortcuts = List.of(
            "Alt+T: Trendline", "Alt+R: Rectangle",
            "On Chart: Type Timeframe (e.g., 5m) + Enter", undoShortcut, redoShortcut
        );
        final int[] currentIndex = {0};
        shortcutsLabel.setText(idleShortcuts.get(0));
        new Timer(3000, e -> {
            currentIndex[0] = (currentIndex[0] + 1) % idleShortcuts.size();
            shortcutsLabel.setText(idleShortcuts.get(currentIndex[0]));
        }).start();
    }


    public void setMenuBar(JMenuBar menuBar) {
        this.menuBar = menuBar;
        this.menuBar.setBorder(null);
        add(this.menuBar, BorderLayout.WEST);
    }

    public void setStaticTitle(String text) {
        this.titleStatusLabel.setText(text);
    }


    public void setToolActiveTitle(String toolName) {
        this.titleStatusLabel.setText(String.format("%s Active | Right-click or Esc to cancel", toolName));
    }


    public void restoreIdleTitle() {
        this.titleStatusLabel.setText("");
    }

    public void dispose() {
        // No resources to dispose
    }
    
    public ButtonGroup getNavGroup() { return navGroup; }
    public JToggleButton getReplayNavButton() { return replayNavButton; }
    public JToggleButton getLiveNavButton() { return liveNavButton; }
}
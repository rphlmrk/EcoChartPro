package com.EcoChartPro.ui.action;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.ui.PrimaryFrame;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final JPanel leftPanel;
    private ShortcutDisplayPanel shortcutDisplayPanel;

    // --- Window Dragging Fields ---
    private Point initialClick;

    // --- Context-Aware Fields ---
    private String currentContext = "HOME";
    private Timer shortcutTimer;
    private final List<Shortcut> homeShortcuts = Collections.emptyList();
    private final List<Shortcut> replayShortcuts;
    private final List<Shortcut> liveShortcuts;

    private record Shortcut(List<String> keys, String description) {
    }

    public TitleBarManager(PrimaryFrame owner) {
        super(new BorderLayout());
        this.owner = owner;
        this.leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        this.leftPanel.setOpaque(false);

        // Define shortcuts before initializing UI
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        String modKey = isMac ? "Cmd" : "Ctrl";
        String redoShift = isMac ? "Shift" : "";
        String redoKey = isMac ? "Z" : "Y";

        replayShortcuts = List.of(
                new Shortcut(List.of("→"), "Next Bar"),
                new Shortcut(List.of("Space"), "Play/Pause"),
                new Shortcut(List.of(modKey, "Z"), "Undo"),
                new Shortcut(List.of(modKey, redoShift, redoKey), "Redo"));
        liveShortcuts = List.of(
                new Shortcut(List.of("B"), "Buy"),
                new Shortcut(List.of("S"), "Sell"),
                new Shortcut(List.of(modKey, "Z"), "Undo"),
                new Shortcut(List.of(modKey, redoShift, redoKey), "Redo"));

        initializeUI();
        addDragListeners();
        updateContext("HOME"); // Set initial state
    }

    private void initializeUI() {
        setOpaque(true);
        setBackground(UIManager.getColor("app.titlebar.background"));
        setBorder(new MatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        setPreferredSize(new Dimension(0, 38));

        // --- Left Section (Tabs + Menu) ---
        leftPanel.add(createNavigationControls());
        add(leftPanel, BorderLayout.WEST);

        // --- Center Section (Status Text) ---
        titleStatusLabel = new JLabel("", JLabel.CENTER);
        titleStatusLabel.setFont(UIManager.getFont("Label.font"));
        add(titleStatusLabel, BorderLayout.CENTER);

        // --- Right Section (Shortcuts + Window Controls) ---
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        rightPanel.setOpaque(false);

        shortcutDisplayPanel = new ShortcutDisplayPanel();
        rightPanel.add(shortcutDisplayPanel);
        rightPanel.add(createWindowControls());
        add(rightPanel, BorderLayout.EAST);
    }

    private JPanel createWindowControls() {
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlsPanel.setOpaque(false);
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

        JButton minimizeButton = createControlButton("\u2014");
        minimizeButton.addActionListener(e -> owner.setState(Frame.ICONIFIED));

        JButton maximizeButton = createControlButton("\u25A1");
        // [FIX] Logic to handle maximize while respecting Taskbar/Dock insets
        maximizeButton.addActionListener(e -> toggleMaximize());

        JButton closeButton = createControlButton("\u2715");
        closeButton.addActionListener(e -> owner
                .dispatchEvent(new java.awt.event.WindowEvent(owner, java.awt.event.WindowEvent.WINDOW_CLOSING)));

        controlsPanel.add(minimizeButton);
        controlsPanel.add(maximizeButton);
        controlsPanel.add(closeButton);

        return controlsPanel;
    }

    /**
     * Toggles the window between maximized and normal states.
     * Calculates the usable screen bounds to avoid covering the OS taskbar.
     */
    private void toggleMaximize() {
        int state = owner.getExtendedState();

        // Bitwise check if MAXIMIZED_BOTH is set
        if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            owner.setExtendedState(Frame.NORMAL);
        } else {
            // Get the screen configuration where the window currently resides
            GraphicsConfiguration config = owner.getGraphicsConfiguration();
            Rectangle bounds = config.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);

            // Calculate usable area (Screen size minus Taskbar/Dock)
            Rectangle usableBounds = new Rectangle(
                    bounds.x + insets.left,
                    bounds.y + insets.top,
                    bounds.width - (insets.left + insets.right),
                    bounds.height - (insets.top + insets.bottom));

            // Apply bounds constraint and then maximize
            owner.setMaximizedBounds(usableBounds);
            owner.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    private JButton createControlButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setMargin(new Insets(2, 10, 2, 10));
        return button;
    }

    private void addDragListeners() {
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Component clicked = getComponentAt(e.getPoint());
                if (clicked == TitleBarManager.this || clicked instanceof JLabel
                        || clicked.getParent() == TitleBarManager.this) {
                    initialClick = e.getPoint();
                } else {
                    initialClick = null;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                initialClick = null;
            }

            // [FIX] Added double-click listener to maximize window
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    toggleMaximize();
                }
            }
        };
        addMouseListener(dragAdapter);

        MouseMotionAdapter motionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (initialClick != null) {
                    // If dragging while maximized, revert to normal first
                    if ((owner.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                        owner.setExtendedState(Frame.NORMAL);
                        // Recalculate initial click relative to new size to prevent jump
                        initialClick = new Point(owner.getWidth() / 2, e.getY());
                    }

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
    }

    private JPanel createNavigationControls() {
        JPanel navButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        navButtonPanel.setOpaque(false);
        navButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        navGroup = new ButtonGroup();
        analysisNavButton = createNavButton("Home", "HOME", true);
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
        JToggleButton button = new StyledNavButton(text, selected);
        button.setActionCommand(actionCommand);
        button.addActionListener(e -> {
            owner.getMainCardLayout().show(owner.getMainContentPanel(), actionCommand);
            updateContext(actionCommand); // [NEW] Update UI based on context
            owner.revalidate();

            // This logic can be simplified as the context update handles visibility
            if ("LIVE".equals(actionCommand)) {
                if (owner.getLiveWorkspacePanel().getActiveChartPanel() != null) {
                    owner.getLiveWorkspacePanel().getDrawingToolbar().setVisible(true);
                }
            } else if ("REPLAY".equals(actionCommand)) {
                if (owner.getReplayWorkspacePanel().getActiveChartPanel() != null) {
                    owner.getReplayWorkspacePanel().getDrawingToolbar().setVisible(true);
                }
            }
        });
        return button;
    }

    private void updateContext(String newContext) {
        if (newContext.equals(this.currentContext)) {
            return;
        }
        this.currentContext = newContext;

        // Pause replay if navigating away from the replay tab
        if (!"REPLAY".equals(newContext)) {
            ReplaySessionManager.getInstance().pause();
        }

        // Hide all floating toolbars when context changes
        owner.getLiveWorkspacePanel().getDrawingToolbar().setVisible(false);
        owner.getLiveWorkspacePanel().getPropertiesToolbar().setVisible(false);
        owner.getReplayWorkspacePanel().getDrawingToolbar().setVisible(false);
        owner.getReplayWorkspacePanel().getPropertiesToolbar().setVisible(false);

        updateMenuBar(newContext);
        updateShortcutRotation(newContext);
    }

    private void updateMenuBar(String context) {
        if (this.menuBar != null) {
            leftPanel.remove(this.menuBar);
        }

        JMenuBar newMenuBar = switch (context) {
            case "REPLAY" -> owner.createReplayMenuBar();
            case "LIVE" -> owner.createLiveMenuBar();
            default -> owner.createHomeMenuBar(); // HOME and others
        };

        this.menuBar = newMenuBar;
        this.menuBar.setBorder(null);
        this.leftPanel.add(this.menuBar);

        leftPanel.revalidate();
        leftPanel.repaint();
    }

    private void updateShortcutRotation(String context) {
        if (shortcutTimer != null && shortcutTimer.isRunning()) {
            shortcutTimer.stop();
        }

        List<Shortcut> shortcuts = switch (context) {
            case "REPLAY" -> replayShortcuts;
            case "LIVE" -> liveShortcuts;
            default -> homeShortcuts;
        };

        if (shortcuts.isEmpty()) {
            shortcutDisplayPanel.setVisible(false);
            return;
        }

        shortcutDisplayPanel.setVisible(true);
        final int[] currentIndex = { 0 };
        shortcutDisplayPanel.setShortcut(shortcuts.get(0));

        shortcutTimer = new Timer(4000, e -> {
            currentIndex[0] = (currentIndex[0] + 1) % shortcuts.size();
            shortcutDisplayPanel.setShortcut(shortcuts.get(currentIndex[0]));
        });
        shortcutTimer.start();
    }

    public void setMenuBar(JMenuBar menuBar) {
        // This method is now only called once at initialization
        if (this.menuBar != null) {
            leftPanel.remove(this.menuBar);
        }
        this.menuBar = menuBar;
        this.menuBar.setBorder(null);
        this.leftPanel.add(this.menuBar);
    }

    public void setStaticTitle(String text) {
        this.titleStatusLabel.setText(text);
    }

    public void setToolActiveTitle(String toolName) {
        this.titleStatusLabel.setText(String.format("%s Active", toolName));
    }

    public void restoreIdleTitle() {
        this.titleStatusLabel.setText("");
    }

    public void dispose() {
        if (shortcutTimer != null) {
            shortcutTimer.stop();
        }
    }

    public ButtonGroup getNavGroup() {
        return navGroup;
    }

    public JToggleButton getReplayNavButton() {
        return replayNavButton;
    }

    public JToggleButton getLiveNavButton() {
        return liveNavButton;
    }

    private static class StyledNavButton extends JToggleButton {
        public StyledNavButton(String text, boolean selected) {
            super(text, selected);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setFont(getFont().deriveFont(Font.BOLD));
            setMargin(new Insets(6, 20, 6, 20));
            setForeground(UIManager.getColor("Button.disabledText"));
            addChangeListener(e -> {
                if (isSelected()) {
                    setForeground(UIManager.getColor("app.titlebar.tab.selected.foreground"));
                } else {
                    setForeground(UIManager.getColor("Button.disabledText"));
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (isSelected()) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(UIManager.getColor("app.titlebar.tab.selected.background"));
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2d.dispose();
            }
            super.paintComponent(g);
        }
    }

    private static class ShortcutDisplayPanel extends JPanel {
        ShortcutDisplayPanel() {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
        }

        void setShortcut(Shortcut shortcut) {
            removeAll();
            for (String key : shortcut.keys()) {
                if (key.isEmpty())
                    continue;
                add(new JLabel(KeyIcon.create(key)));
            }
            JLabel descLabel = new JLabel(shortcut.description());
            descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            add(descLabel);
            revalidate();
            repaint();
        }
    }

    private static class KeyIcon {
        private static final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();
        private static final Font KEY_FONT = new Font("SansSerif", Font.PLAIN, 10);

        public static ImageIcon create(String keyText) {
            return iconCache.computeIfAbsent(keyText, KeyIcon::generateIcon);
        }

        private static ImageIcon generateIcon(String keyText) {
            FontMetrics fm = new JLabel().getFontMetrics(KEY_FONT);
            int textWidth = fm.stringWidth(keyText);
            int width = textWidth + 8;
            int height = 16;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(UIManager.getColor("Component.borderColor"));
            g2d.fill(new RoundRectangle2D.Float(0, 0, width, height, 5, 5));

            g2d.setFont(KEY_FONT);
            g2d.setColor(UIManager.getColor("Label.disabledForeground"));
            int x = (width - textWidth) / 2;
            int y = (height - fm.getHeight()) / 2 + fm.getAscent();
            g2d.drawString(keyText, x, y);

            g2d.dispose();
            return new ImageIcon(image);
        }
    }
}
package com.EcoChartPro.ui.action;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.ui.PrimaryFrame;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TitleBarManager extends JPanel {

    private final PrimaryFrame owner;

    // --- UI Components ---
    private JMenuBar menuBar;
    private JLabel titleStatusLabel;
    private JToggleButton analysisNavButton, replayNavButton, liveNavButton, statsNavButton;
    private ButtonGroup navGroup; // Needs public getter
    private final JPanel leftPanel;
    private ShortcutDisplayPanel shortcutDisplayPanel;

    private Point initialClick;
    private String currentContext = "HOME";
    private Timer shortcutTimer;
    
    private final List<Shortcut> homeShortcuts = Collections.emptyList();
    private final List<Shortcut> replayShortcuts;
    private final List<Shortcut> liveShortcuts;

    private record Shortcut(List<String> keys, String description) {}

    public TitleBarManager(PrimaryFrame owner) {
        super(new BorderLayout());
        this.owner = owner;
        this.leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        this.leftPanel.setOpaque(false);

        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        String modKey = isMac ? "Cmd" : "Ctrl";

        replayShortcuts = List.of(
                new Shortcut(List.of("→"), "Next Bar"),
                new Shortcut(List.of("Space"), "Play/Pause"),
                new Shortcut(List.of(modKey, "Z"), "Undo"),
                new Shortcut(List.of(modKey, "F"), "Search"));

        liveShortcuts = List.of(
                new Shortcut(List.of("B"), "Buy"),
                new Shortcut(List.of("S"), "Sell"),
                new Shortcut(List.of(modKey, "Z"), "Undo"),
                new Shortcut(List.of(modKey, "F"), "Search"));

        initializeUI();
        addDragListeners();
        updateContext("HOME");
    }

    private void initializeUI() {
        setOpaque(true);
        setBackground(UIManager.getColor("app.titlebar.background"));
        setBorder(new MatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        setPreferredSize(new Dimension(0, 38));

        leftPanel.add(createNavigationControls());
        add(leftPanel, BorderLayout.WEST);

        titleStatusLabel = new JLabel("", JLabel.CENTER);
        titleStatusLabel.setFont(UIManager.getFont("Label.font"));
        add(titleStatusLabel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        rightPanel.setOpaque(false);

        shortcutDisplayPanel = new ShortcutDisplayPanel();
        rightPanel.add(shortcutDisplayPanel);
        rightPanel.add(createWindowControls());
        add(rightPanel, BorderLayout.EAST);
    }

    private JPanel createNavigationControls() {
        JPanel navButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        navButtonPanel.setOpaque(false);
        navButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        navGroup = new ButtonGroup();
        analysisNavButton = createNavButton("Home", "HOME", true);
        statsNavButton = createNavButton("Analysis", "ANALYSIS", false); 
        replayNavButton = createNavButton("Replay", "REPLAY", false);
        liveNavButton = createNavButton("Live", "LIVE", false);

        navGroup.add(analysisNavButton);
        navGroup.add(statsNavButton);
        navGroup.add(replayNavButton);
        navGroup.add(liveNavButton);

        navButtonPanel.add(analysisNavButton);
        navButtonPanel.add(statsNavButton);
        navButtonPanel.add(replayNavButton);
        navButtonPanel.add(liveNavButton);

        return navButtonPanel;
    }

    private JToggleButton createNavButton(String text, String actionCommand, boolean selected) {
        JToggleButton button = new StyledNavButton(text, selected);
        button.setActionCommand(actionCommand);
        button.addActionListener(e -> {
            owner.getMainCardLayout().show(owner.getMainContentPanel(), actionCommand);
            updateContext(actionCommand);
            owner.revalidate();
            
            if ("ANALYSIS".equals(actionCommand)) {
                owner.refreshAnalysisData();
            }

            if ("LIVE".equals(actionCommand) && owner.getLiveWorkspacePanel().getActiveChartPanel() != null) {
                owner.getLiveWorkspacePanel().getDrawingToolbar().setVisible(true);
            } else if ("REPLAY".equals(actionCommand) && owner.getReplayWorkspacePanel().getActiveChartPanel() != null) {
                owner.getReplayWorkspacePanel().getDrawingToolbar().setVisible(true);
            }
        });
        return button;
    }
    
    private JPanel createWindowControls() {
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlsPanel.setOpaque(false);
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

        JButton minimizeButton = createControlButton("\u2014");
        minimizeButton.addActionListener(e -> owner.setState(Frame.ICONIFIED));

        JButton maximizeButton = createControlButton("\u25A1");
        maximizeButton.addActionListener(e -> toggleMaximize());

        JButton closeButton = createControlButton("\u2715");
        closeButton.addActionListener(e -> owner.dispatchEvent(new java.awt.event.WindowEvent(owner, java.awt.event.WindowEvent.WINDOW_CLOSING)));

        controlsPanel.add(minimizeButton);
        controlsPanel.add(maximizeButton);
        controlsPanel.add(closeButton);
        return controlsPanel;
    }

    private void toggleMaximize() {
        int state = owner.getExtendedState();
        if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            owner.setExtendedState(Frame.NORMAL);
        } else {
            GraphicsConfiguration config = owner.getGraphicsConfiguration();
            Rectangle bounds = config.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
            Rectangle usableBounds = new Rectangle(bounds.x + insets.left, bounds.y + insets.top, bounds.width - (insets.left + insets.right), bounds.height - (insets.top + insets.bottom));
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
                if (clicked == TitleBarManager.this || clicked instanceof JLabel || clicked.getParent() == TitleBarManager.this) {
                    initialClick = e.getPoint();
                } else {
                    initialClick = null;
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) { initialClick = null; }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) toggleMaximize();
            }
        };
        addMouseListener(dragAdapter);
        MouseMotionAdapter motionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (initialClick != null) {
                    if ((owner.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                        owner.setExtendedState(Frame.NORMAL);
                        initialClick = new Point(owner.getWidth() / 2, e.getY());
                    }
                    int thisX = owner.getLocation().x;
                    int thisY = owner.getLocation().y;
                    int xMoved = e.getX() - initialClick.x;
                    int yMoved = e.getY() - initialClick.y;
                    owner.setLocation(thisX + xMoved, thisY + yMoved);
                }
            }
        };
        addMouseMotionListener(motionAdapter);
    }

    private void updateContext(String newContext) {
        if (newContext.equals(this.currentContext)) return;
        this.currentContext = newContext;

        if (!"REPLAY".equals(newContext)) {
            ReplaySessionManager.getInstance().pause();
        }

        owner.getLiveWorkspacePanel().getDrawingToolbar().setVisible(false);
        owner.getLiveWorkspacePanel().getPropertiesToolbar().setVisible(false);
        owner.getReplayWorkspacePanel().getDrawingToolbar().setVisible(false);
        owner.getReplayWorkspacePanel().getPropertiesToolbar().setVisible(false);

        updateMenuBar(newContext);
        updateShortcutRotation(newContext);
    }

    private void updateMenuBar(String context) {
        if (this.menuBar != null) leftPanel.remove(this.menuBar);
        JMenuBar newMenuBar = switch (context) {
            case "REPLAY" -> owner.createReplayMenuBar();
            case "LIVE" -> owner.createLiveMenuBar();
            default -> owner.createHomeMenuBar();
        };
        this.menuBar = newMenuBar;
        this.menuBar.setBorder(null);
        this.leftPanel.add(this.menuBar);
        leftPanel.revalidate();
        leftPanel.repaint();
    }

    private void updateShortcutRotation(String context) {
        if (shortcutTimer != null && shortcutTimer.isRunning()) shortcutTimer.stop();
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
        if (this.menuBar != null) leftPanel.remove(this.menuBar);
        this.menuBar = menuBar;
        this.menuBar.setBorder(null);
        this.leftPanel.add(this.menuBar);
    }

    public void setStaticTitle(String text) { this.titleStatusLabel.setText(text); }
    public void setToolActiveTitle(String toolName) { this.titleStatusLabel.setText(String.format("%s Active", toolName)); }
    public void restoreIdleTitle() { this.titleStatusLabel.setText(""); }
    public void dispose() { if (shortcutTimer != null) shortcutTimer.stop(); }

    // [FIX] Added missing getter
    public ButtonGroup getNavGroup() { return navGroup; }
    
    public JToggleButton getReplayNavButton() { return replayNavButton; }
    public JToggleButton getLiveNavButton() { return liveNavButton; }
    public JToggleButton getAnalysisNavButton() { return statsNavButton; } 

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
                if (isSelected()) setForeground(UIManager.getColor("app.titlebar.tab.selected.foreground"));
                else setForeground(UIManager.getColor("Button.disabledText"));
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
        ShortcutDisplayPanel() { super(new FlowLayout(FlowLayout.LEFT, 4, 0)); setOpaque(false); }
        void setShortcut(Shortcut shortcut) {
            removeAll();
            for (String key : shortcut.keys()) {
                if (key.isEmpty()) continue;
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
        public static ImageIcon create(String keyText) { return iconCache.computeIfAbsent(keyText, KeyIcon::generateIcon); }
        private static ImageIcon generateIcon(String keyText) {
            FontMetrics fm = new JLabel().getFontMetrics(KEY_FONT);
            int width = fm.stringWidth(keyText) + 8;
            int height = 16;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(UIManager.getColor("Component.borderColor"));
            g2d.fill(new RoundRectangle2D.Float(0, 0, width, height, 5, 5));
            g2d.setFont(KEY_FONT);
            g2d.setColor(UIManager.getColor("Label.disabledForeground"));
            g2d.drawString(keyText, (width - fm.stringWidth(keyText)) / 2, (height - fm.getHeight()) / 2 + fm.getAscent());
            g2d.dispose();
            return new ImageIcon(image);
        }
    }
}
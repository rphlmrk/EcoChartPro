package com.EcoChartPro.ui;

import com.EcoChartPro.core.gamification.Achievement;
import com.EcoChartPro.ui.dashboard.DashboardFrame;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A singleton service for displaying non-intrusive "toast" notifications,
 * such as achievement unlocks and discipline hints.
 */
public final class NotificationService {

    // Internal DTO to represent any notification.
    private record NotificationPayload(String header, String message, String iconPath, Color accentColor) {}

    private static volatile NotificationService instance;
    private final Queue<NotificationPayload> notificationQueue = new LinkedList<>();
    private boolean isShowingNotification = false;

    private static final int TOAST_WIDTH = 350; // A bit wider for longer hint messages
    private static final int TOAST_HEIGHT = 80;
    private static final int SCREEN_PADDING = 20;
    private static final int DISPLAY_DURATION_MS = 5000; // Longer for hints
    private static final int FADE_OUT_DURATION_MS = 500;

    private NotificationService() {}

    public static NotificationService getInstance() {
        if (instance == null) {
            synchronized (NotificationService.class) {
                if (instance == null) {
                    instance = new NotificationService();
                }
            }
        }
        return instance;
    }

    /**
     * Public method to queue and show an achievement notification.
     * Ensures that notifications are shown one by one.
     * @param achievement The achievement that was unlocked.
     */
    public void showAchievementUnlocked(Achievement achievement) {
        SwingUtilities.invokeLater(() -> {
            NotificationPayload payload = new NotificationPayload(
                "Achievement Unlocked!",
                achievement.title(),
                achievement.iconPath(),
                javax.swing.UIManager.getColor("app.color.accent")
            );
            notificationQueue.offer(payload);
            if (!isShowingNotification) {
                showNextNotification();
            }
        });
    }

    /**
     * Public method to queue and show a less intrusive "nudge" or "hint" notification.
     * @param title The header for the hint (e.g., "Discipline Hint").
     * @param message The detailed message of the hint.
     * @param iconPath The path to the icon for the hint.
     */
    public void showDisciplineNudge(String title, String message, String iconPath) {
        SwingUtilities.invokeLater(() -> {
            NotificationPayload payload = new NotificationPayload(
                title,
                message,
                iconPath,
                javax.swing.UIManager.getColor("app.color.neutral")
            );
            notificationQueue.offer(payload);
            if (!isShowingNotification) {
                showNextNotification();
            }
        });
    }


    private void showNextNotification() {
        if (notificationQueue.isEmpty()) {
            isShowingNotification = false;
            return;
        }

        isShowingNotification = true;
        NotificationPayload payload = notificationQueue.poll();
        Frame owner = findVisibleMainWindow();

        if (owner == null) {
            isShowingNotification = false;
            return;
        }

        NotificationToast toast = new NotificationToast(owner, payload);
        
        int x = owner.getX() + owner.getWidth() - TOAST_WIDTH - SCREEN_PADDING;
        int y = owner.getY() + owner.getHeight() - TOAST_HEIGHT - SCREEN_PADDING;
        
        toast.setLocation(x, y);
        toast.setVisible(true);
    }
    
    private Frame findVisibleMainWindow() {
        for (Frame frame : Frame.getFrames()) {
            if (frame instanceof MainWindow && frame.isVisible()) {
                return frame;
            }
        }
        for (Frame frame : Frame.getFrames()) {
            if (frame instanceof DashboardFrame && frame.isVisible()) {
                 return frame;
            }
        }
        return null;
    }

    /**
     * A custom JWindow that displays the notification toast.
     * Handles its own appearance and fade-out animation.
     */
    private class NotificationToast extends JWindow {
        private Timer fadeOutTimer;
        private final Timer visibilityTimer; // Made into an instance variable
        private float opacity = 1.0f;

        NotificationToast(Frame owner, NotificationPayload payload) {
            super(owner);
            setSize(TOAST_WIDTH, TOAST_HEIGHT);
            setAlwaysOnTop(true);

            // --- Root Panel with custom painting ---
            JPanel mainPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color bgColor = javax.swing.UIManager.getColor("Panel.background");
                    g2d.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 230));
                    g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
                    g2d.setColor(payload.accentColor());
                    g2d.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 20, 20));
                    g2d.dispose();
                    super.paintComponent(g);
                }
            };
            mainPanel.setOpaque(false);

            // --- Content Panel (Icon and Text) ---
            JPanel contentPanel = new JPanel(new BorderLayout(15, 0));
            contentPanel.setOpaque(false);
            contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 10));

            JLabel iconLabel = new JLabel(UITheme.getIcon(payload.iconPath(), 40, 40, payload.accentColor()));
            contentPanel.add(iconLabel, BorderLayout.WEST);

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.add(Box.createVerticalGlue()); // For vertical centering

            JLabel titleLabel = new JLabel(payload.header());
            titleLabel.setFont(javax.swing.UIManager.getFont("app.font.widget_content"));
            titleLabel.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JTextArea messageArea = new JTextArea(payload.message());
            messageArea.setWrapStyleWord(true);
            messageArea.setLineWrap(true);
            messageArea.setOpaque(false);
            messageArea.setEditable(false);
            messageArea.setFocusable(false);
            messageArea.setFont(javax.swing.UIManager.getFont("app.font.widget_title").deriveFont(14f));
            messageArea.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
            messageArea.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            textPanel.add(titleLabel);
            textPanel.add(Box.createVerticalStrut(4));
            textPanel.add(messageArea);
            textPanel.add(Box.createVerticalGlue()); // For vertical centering
            contentPanel.add(textPanel, BorderLayout.CENTER);

            // --- Close Button ---
            JButton closeButton = new JButton("×");
            closeButton.setFont(new Font("SansSerif", Font.BOLD, 22));
            closeButton.setOpaque(false);
            closeButton.setContentAreaFilled(false);
            closeButton.setBorderPainted(false);
            closeButton.setFocusPainted(false);
            closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            closeButton.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
            closeButton.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { closeButton.setForeground(javax.swing.UIManager.getColor("Label.foreground")); }
                @Override public void mouseExited(MouseEvent e) { closeButton.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground")); }
            });
            closeButton.addActionListener(e -> closeAndShowNext());

            JPanel buttonContainer = new JPanel(new BorderLayout());
            buttonContainer.setOpaque(false);
            buttonContainer.add(closeButton, BorderLayout.NORTH);
            buttonContainer.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 2));

            // --- Assemble the final layout ---
            mainPanel.add(contentPanel, BorderLayout.CENTER);
            mainPanel.add(buttonContainer, BorderLayout.EAST);
            add(mainPanel);

            visibilityTimer = new Timer(DISPLAY_DURATION_MS, e -> startFadeOut());
            visibilityTimer.setRepeats(false);
            visibilityTimer.start();
        }

        private void closeAndShowNext() {
            visibilityTimer.stop();
            if (fadeOutTimer != null) {
                fadeOutTimer.stop();
            }
            dispose();
            showNextNotification();
        }

        private void startFadeOut() {
            int frameRate = 1000 / 60;
            int steps = FADE_OUT_DURATION_MS / frameRate;
            float opacityStep = 1.0f / steps;

            fadeOutTimer = new Timer(frameRate, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    opacity -= opacityStep;
                    if (opacity <= 0.0f) {
                        opacity = 0.0f;
                        fadeOutTimer.stop();
                        dispose();
                        showNextNotification();
                    }
                    setOpacity(opacity);
                }
            });
            fadeOutTimer.start();
        }
    }
}
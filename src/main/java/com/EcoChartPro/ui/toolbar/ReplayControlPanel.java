package com.EcoChartPro.ui.toolbar;

import com.EcoChartPro.core.controller.ReplayController;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.ui.components.TimezoneListPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class ReplayControlPanel extends JPanel implements PropertyChangeListener {

    private final ReplayController controller;
    private final JToggleButton playPauseButton;
    private final JButton nextBarButton;
    private final JComboBox<String> speedComboBox;
    private final JLabel dateTimeLabel;
    private final BlurrableLabel accountBalanceLabel; // Changed to custom BlurrableLabel
    private JPopupMenu timezonePopup;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    private BigDecimal initialBalance;
    private BigDecimal lastKnownBalance;
    private boolean isBalanceVisible = true;

    public ReplayControlPanel(ReplayController controller) {
        this.controller = controller;
        this.controller.addPropertyChangeListener(this);

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        playPauseButton = new JToggleButton("Play");
        playPauseButton.setToolTipText("Play or Pause the replay session");
        playPauseButton.setFocusPainted(false);
        playPauseButton.addActionListener(e -> controller.togglePlayPause());

        nextBarButton = new JButton("Next Bar");
        nextBarButton.setToolTipText("Advance to the next 1-minute bar");
        nextBarButton.setFocusPainted(false);
        nextBarButton.addActionListener(e -> controller.nextBar());

        JLabel speedLabel = new JLabel("Speed:");
        speedLabel.setForeground(UIManager.getColor("Label.foreground"));
        String[] speeds = {"0.05s", "0.1s", "0.2s", "0.5s", "1s", "2s", "5s"};
        speedComboBox = new JComboBox<>(speeds);
        speedComboBox.setSelectedItem("1s");
        speedComboBox.setToolTipText("Select playback speed");
        speedComboBox.setFocusable(false);
        speedComboBox.setMaximumSize(new Dimension(80, speedComboBox.getPreferredSize().height));
        speedComboBox.addActionListener(e -> {
            String selected = (String) speedComboBox.getSelectedItem();
            if (selected == null) return;
            try {
                int delay = (int) (Double.parseDouble(selected.replace("s", "")) * 1000);
                controller.setSpeed(delay);
            } catch (NumberFormatException ex) {
                controller.setSpeed(1000);
            }
        });

        dateTimeLabel = new JLabel("Ready");
        dateTimeLabel.setFont(UIManager.getFont("app.font.widget_content"));
        dateTimeLabel.setForeground(UIManager.getColor("Label.foreground"));
        dateTimeLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        dateTimeLabel.setToolTipText("Click to change display timezone");
        dateTimeLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        dateTimeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showTimezonePopup();
            }
        });

        // Use the new BlurrableLabel
        accountBalanceLabel = new BlurrableLabel("Balance: $0.00");
        accountBalanceLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        accountBalanceLabel.setForeground(UIManager.getColor("Label.foreground"));
        accountBalanceLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        accountBalanceLabel.setToolTipText("Click to show/hide balance");
        accountBalanceLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        accountBalanceLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleBalanceVisibility();
            }
        });

        add(playPauseButton);
        add(Box.createHorizontalStrut(10));
        add(nextBarButton);
        add(Box.createHorizontalStrut(20));
        add(speedLabel);
        add(Box.createHorizontalStrut(5));
        add(speedComboBox);
        add(Box.createHorizontalGlue());
        add(accountBalanceLabel);
        add(Box.createHorizontalStrut(20));
        add(dateTimeLabel);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        SwingUtilities.invokeLater(() -> {
            switch (evt.getPropertyName()) {
                case "timeUpdated":
                    dateTimeLabel.setText((String) evt.getNewValue());
                    break;
                case "balanceUpdated":
                    BigDecimal newBalance = (BigDecimal) evt.getNewValue();
                    this.lastKnownBalance = newBalance;
                    accountBalanceLabel.setText("Balance: " + CURRENCY_FORMAT.format(newBalance));
                    updateBalanceColor(newBalance, this.initialBalance);
                    break;
                case "initialBalance":
                    this.initialBalance = (BigDecimal) evt.getNewValue();
                    if (this.lastKnownBalance != null) {
                        updateBalanceColor(this.lastKnownBalance, this.initialBalance);
                    }
                    break;
                case "replayStateChanged":
                    updateButtonStates();
                    break;
            }
        });
    }

    private void toggleBalanceVisibility() {
        isBalanceVisible = !isBalanceVisible;
        accountBalanceLabel.setBlurred(!isBalanceVisible);
        updateBalanceColor(lastKnownBalance, initialBalance);
    }

    private void updateBalanceColor(BigDecimal currentBalance, BigDecimal initialBalance) {
        if (!isBalanceVisible || initialBalance == null || currentBalance == null) {
            accountBalanceLabel.setForeground(UIManager.getColor("Label.foreground"));
            return;
        }

        int comparison = currentBalance.compareTo(initialBalance);
        if (comparison > 0) {
            accountBalanceLabel.setForeground(UIManager.getColor("app.color.positive"));
        } else if (comparison < 0) {
            accountBalanceLabel.setForeground(UIManager.getColor("app.color.negative"));
        } else {
            accountBalanceLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
    }

    private void updateButtonStates() {
        ReplaySessionManager manager = ReplaySessionManager.getInstance();
        boolean canGoForward = !manager.isReplayFinished();
        boolean isPlaying = manager.isPlaying();

        nextBarButton.setEnabled(canGoForward);
        playPauseButton.setEnabled(canGoForward);
        playPauseButton.setSelected(isPlaying);
        playPauseButton.setText(isPlaying ? "Pause" : "Play");

        if (!canGoForward && isPlaying) {
            playPauseButton.setSelected(false);
            playPauseButton.setText("Play");
        }
    }

    private void showTimezonePopup() {
        if (timezonePopup == null || !timezonePopup.isVisible()) {
            timezonePopup = new JPopupMenu();
            timezonePopup.setFocusable(true);
            timezonePopup.setBorder(UIManager.getBorder("PopupMenu.border"));
            
            TimezoneListPanel listPanel = new TimezoneListPanel(
                com.EcoChartPro.core.settings.SettingsService.getInstance().getDisplayZoneId(),
                selectedZoneId -> {
                    com.EcoChartPro.core.settings.SettingsService.getInstance().setDisplayZoneId(selectedZoneId);
                    timezonePopup.setVisible(false);
                }
            );
            timezonePopup.add(listPanel);
            timezonePopup.show(dateTimeLabel, 0, -timezonePopup.getPreferredSize().height);
        } else {
            timezonePopup.setVisible(false);
        }
    }

    /**
     * A custom JLabel that can render its content with a blur effect.
     * This is achieved by painting the component to an off-screen image,
     * applying a blur filter (ConvolveOp), and then drawing the blurred
     * image onto the screen.
     */
    private static class BlurrableLabel extends JLabel {
        private boolean blurred = false;
        private ConvolveOp blurOperator;

        public BlurrableLabel(String text) {
            super(text);
            // Creates a 5x5 kernel for a medium blur effect.
            int blurRadius = 5;
            int matrixSize = blurRadius * blurRadius;
            float[] blurMatrix = new float[matrixSize];
            for (int i = 0; i < matrixSize; i++) {
                blurMatrix[i] = 1.0f / (float) matrixSize;
            }
            // EDGE_NO_OP prevents issues at the image borders
            this.blurOperator = new ConvolveOp(new Kernel(blurRadius, blurRadius, blurMatrix), ConvolveOp.EDGE_NO_OP, null);
        }

        public void setBlurred(boolean blurred) {
            if (this.blurred != blurred) {
                this.blurred = blurred;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!blurred || getWidth() <= 0 || getHeight() <= 0) {
                // If not blurred, use the fast, standard painting method.
                super.paintComponent(g);
                return;
            }

            // 1. Create an off-screen image buffer with transparency.
            BufferedImage buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = buffer.createGraphics();

            // 2. Trick the JLabel into painting its normal content onto our buffer.
            super.paintComponent(g2d);
            g2d.dispose();

            // 3. Apply the blur filter to the off-screen image.
            BufferedImage blurredImage = blurOperator.filter(buffer, null);

            // 4. Draw the final, blurred image onto the component's actual graphics context.
            g.drawImage(blurredImage, 0, 0, null);
        }
    }
}
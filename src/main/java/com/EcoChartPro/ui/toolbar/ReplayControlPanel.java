package com.EcoChartPro.ui.toolbar;

import com.EcoChartPro.core.controller.ReplayController;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.ui.components.TimezoneListPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private final JLabel accountBalanceLabel;
    private JPopupMenu timezonePopup;
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    
    private BigDecimal initialBalance;

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

        accountBalanceLabel = new JLabel("Balance: $0.00");
        accountBalanceLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        accountBalanceLabel.setForeground(UIManager.getColor("Label.foreground"));
        accountBalanceLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

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
                    accountBalanceLabel.setText("Balance: " + CURRENCY_FORMAT.format(newBalance));
                    updateBalanceColor(newBalance, this.initialBalance);
                    break;
                case "initialBalance":
                    this.initialBalance = (BigDecimal) evt.getNewValue();
                    try {
                        BigDecimal currentBalance = (BigDecimal) CURRENCY_FORMAT.parse(accountBalanceLabel.getText().replace("Balance: ", ""));
                        updateBalanceColor(currentBalance, this.initialBalance);
                    } catch(Exception e) {
                        // Ignore
                    }
                    break;
                case "replayStateChanged":
                    updateButtonStates();
                    break;
            }
        });
    }

    private void updateBalanceColor(BigDecimal currentBalance, BigDecimal initialBalance) {
        if (initialBalance == null || currentBalance == null) {
            accountBalanceLabel.setForeground(UIManager.getColor("Label.foreground"));
            return;
        };
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
                com.EcoChartPro.core.settings.SettingsManager.getInstance().getDisplayZoneId(),
                selectedZoneId -> {
                    com.EcoChartPro.core.settings.SettingsManager.getInstance().setDisplayZoneId(selectedZoneId);
                    timezonePopup.setVisible(false);
                }
            );
            timezonePopup.add(listPanel);
            timezonePopup.show(dateTimeLabel, 0, -timezonePopup.getPreferredSize().height);
        } else {
            timezonePopup.setVisible(false);
        }
    }
}
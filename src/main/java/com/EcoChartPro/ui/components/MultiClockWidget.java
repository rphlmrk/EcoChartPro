package com.EcoChartPro.ui.components;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.GeneralConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays multiple clocks for different timezones in the status bar.
 * Updates every second.
 */
public class MultiClockWidget extends JPanel implements PropertyChangeListener {

    private final List<JLabel> clockLabels = new ArrayList<>();
    private final List<ZoneId> zones = new ArrayList<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Timer updateTimer;

    public MultiClockWidget() {
        setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(0, 0, 0, 5));

        // Load initial configuration
        refreshClocks(SettingsService.getInstance().getStatusBarClocks());

        // Listen for settings changes
        SettingsService.getInstance().addPropertyChangeListener(this);

        // Start update timer (1 second tick)
        updateTimer = new Timer(1000, e -> onTick());
        updateTimer.start();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("statusBarClocksChanged".equals(evt.getPropertyName())) {
            @SuppressWarnings("unchecked")
            List<GeneralConfig.StatusBarClock> newConfig = (List<GeneralConfig.StatusBarClock>) evt.getNewValue();
            refreshClocks(newConfig);
        }
    }

    private void refreshClocks(List<GeneralConfig.StatusBarClock> config) {
        removeAll();
        clockLabels.clear();
        zones.clear();

        if (config == null)
            return;

        for (int i = 0; i < config.size(); i++) {
            GeneralConfig.StatusBarClock clock = config.get(i);

            // Create label for the clock name (e.g., "NY:")
            JLabel nameLabel = new JLabel(clock.label() + ": ");
            nameLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            nameLabel.setFont(UIManager.getFont("Label.font").deriveFont(11f));

            // Create label for the time
            JLabel timeLabel = new JLabel("--:--:--");
            timeLabel.setForeground(UIManager.getColor("Label.foreground"));
            timeLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 11f));

            add(nameLabel);
            add(timeLabel);

            clockLabels.add(timeLabel);
            zones.add(clock.zoneId());

            // Add separator/spacer if not the last item
            if (i < config.size() - 1) {
                JLabel separator = new JLabel("  |  ");
                separator.setForeground(UIManager.getColor("Component.borderColor"));
                add(separator);
            }
        }

        // Update immediately to avoid waiting 1 second for text
        onTick();

        revalidate();
        repaint();
    }

    private void onTick() {
        Instant now = Instant.now();
        for (int i = 0; i < clockLabels.size(); i++) {
            try {
                ZonedDateTime zdt = now.atZone(zones.get(i));
                clockLabels.get(i).setText(formatter.format(zdt));
            } catch (Exception e) {
                clockLabels.get(i).setText("Error");
            }
        }
    }

    @Override
    public void removeNotify() {
        updateTimer.stop();
        SettingsService.getInstance().removePropertyChangeListener(this);
        super.removeNotify();
    }
}
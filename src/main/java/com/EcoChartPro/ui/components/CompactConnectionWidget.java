package com.EcoChartPro.ui.components;

import com.EcoChartPro.core.service.InternetConnectivityService;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A compact widget designed for the status bar that displays
 * connectivity status and real-time latency (ping).
 */
public class CompactConnectionWidget extends JPanel implements PropertyChangeListener {

    private final JLabel iconLabel;
    private final JLabel textLabel;

    // Thresholds for visual feedback
    private static final int LATENCY_GOOD = 100;
    private static final int LATENCY_WARN = 300;

    public CompactConnectionWidget() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(2, 5, 2, 5));

        // --- Init Components ---
        iconLabel = new JLabel();
        textLabel = new JLabel("Initializing...");
        textLabel.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        textLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        add(iconLabel);
        add(textLabel);

        // --- Register Listeners ---
        InternetConnectivityService.getInstance().addPropertyChangeListener(this);
        // LiveDataManager is a singleton that fires latency events via its internal PCS
        LiveDataManager.getInstance().addPropertyChangeListener("realLatencyUpdated", this);
        LiveDataManager.getInstance().addPropertyChangeListener("liveDataSystemStateChanged", this);

        // Initial State Check
        updateConnectionUI(InternetConnectivityService.getInstance().isConnected());
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        SwingUtilities.invokeLater(() -> {
            String prop = evt.getPropertyName();

            if ("connectivityChanged".equals(prop)) {
                boolean connected = (Boolean) evt.getNewValue();
                updateConnectionUI(connected);
            } else if ("realLatencyUpdated".equals(prop)) {
                long latency = (Long) evt.getNewValue();
                updateLatencyUI(latency);
            } else if ("liveDataSystemStateChanged".equals(prop)) {
                // Handle system-wide state changes (e.g., SYNCING)
                LiveDataManager.LiveDataSystemState state = (LiveDataManager.LiveDataSystemState) evt.getNewValue();
                if (state == LiveDataManager.LiveDataSystemState.SYNCING) {
                    setSyncingState();
                }
            }
        });
    }

    private void updateConnectionUI(boolean connected) {
        if (connected) {
            // Default "Connected" state waiting for latency data
            Color color = UIManager.getColor("Label.foreground"); // Neutral color until ping comes in
            iconLabel.setIcon(UITheme.getIcon(UITheme.Icons.WIFI_ON, 16, 16, color));
            textLabel.setText("Connected");
            textLabel.setForeground(color);
            setToolTipText("Internet connection active");
        } else {
            Color color = UIManager.getColor("app.color.negative");
            iconLabel.setIcon(UITheme.getIcon(UITheme.Icons.WIFI_OFF, 16, 16, color));
            textLabel.setText("Disconnected");
            textLabel.setForeground(color);
            setToolTipText("No internet connection detected");
        }
    }

    private void updateLatencyUI(long latencyMs) {
        if (!InternetConnectivityService.getInstance().isConnected())
            return;

        Color statusColor;
        String statusTooltip;

        if (latencyMs < LATENCY_GOOD) {
            statusColor = UIManager.getColor("app.color.positive"); // Green
            statusTooltip = "Excellent Connection";
        } else if (latencyMs < LATENCY_WARN) {
            statusColor = UIManager.getColor("app.trading.pending"); // Yellow/Orange
            statusTooltip = "Fair Connection";
        } else {
            statusColor = UIManager.getColor("app.color.negative"); // Red
            statusTooltip = "Poor Connection";
        }

        iconLabel.setIcon(UITheme.getIcon(UITheme.Icons.WIFI_ON, 16, 16, statusColor));
        textLabel.setText(latencyMs + " ms");
        textLabel.setForeground(statusColor);
        setToolTipText(statusTooltip + " (" + latencyMs + "ms)");
    }

    private void setSyncingState() {
        Color color = UIManager.getColor("app.color.accent");
        iconLabel.setIcon(UITheme.getIcon(UITheme.Icons.REFRESH, 16, 16, color));
        textLabel.setText("Syncing...");
        textLabel.setForeground(color);
        setToolTipText("Backfilling missing data...");
    }

    @Override
    public void removeNotify() {
        // Cleanup listeners when removed from UI
        InternetConnectivityService.getInstance().removePropertyChangeListener(this);
        LiveDataManager.getInstance().removePropertyChangeListener("realLatencyUpdated", this);
        LiveDataManager.getInstance().removePropertyChangeListener("liveDataSystemStateChanged", this);
        super.removeNotify();
    }
}
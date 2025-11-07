package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.controller.SessionController;
import com.EcoChartPro.core.service.InternetConnectivityService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.ui.dashboard.components.FloatingToolbarPanel;
import com.EcoChartPro.ui.dialogs.NoInternetDialog;
import com.EcoChartPro.ui.dialogs.SessionDialog;
import com.EcoChartPro.utils.SessionManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;

public class LiveViewPanel extends JPanel implements PropertyChangeListener {

    private final ComprehensiveReportPanel reportPanel;
    private JButton resumeLiveButton;
    private JButton newLiveButton;

    public LiveViewPanel() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        this.reportPanel = new ComprehensiveReportPanel();

        add(createHeaderPanel(), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(this.reportPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setUI(new com.formdev.flatlaf.ui.FlatScrollBarUI());
        add(scrollPane, BorderLayout.CENTER);

        add(createBottomToolbar(), BorderLayout.SOUTH);

        // Add a component listener to update button states when the panel is shown
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                updateButtonStates();
            }
        });

        // Listen for global connectivity changes
        InternetConnectivityService.getInstance().addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("connectivityChanged".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(this::updateButtonStates);
        }
    }

    public ComprehensiveReportPanel getReportPanel() {
        return reportPanel;
    }

    private void updateButtonStates() {
        boolean isConnected = InternetConnectivityService.getInstance().isConnected();
        boolean liveSessionExists = SessionManager.getInstance().getLiveAutoSaveFilePath().map(File::exists).orElse(false);

        // New Live button depends only on connection
        newLiveButton.setEnabled(isConnected);
        newLiveButton.setToolTipText(isConnected ? "Start a fresh live paper trading session and clear any previous one" : "Internet connection required to start a new live session");

        // Resume button depends on both connection and file existence
        resumeLiveButton.setEnabled(liveSessionExists && isConnected);
        if (!isConnected) {
            resumeLiveButton.setToolTipText("Internet connection required to resume live session");
        } else if (!liveSessionExists) {
            resumeLiveButton.setToolTipText("No live session found to resume");
        } else {
            resumeLiveButton.setToolTipText("Resume your last live paper trading session");
        }
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel title = new JLabel("Live Paper Trading");
        title.setFont(UIManager.getFont("app.font.heading"));
        title.setForeground(UIManager.getColor("Label.foreground"));
        headerPanel.add(title, BorderLayout.CENTER);
        return headerPanel;
    }

    private JPanel createBottomToolbar() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        resumeLiveButton = new JButton("Resume Last Session");
        styleToolbarButton(resumeLiveButton);
        resumeLiveButton.addActionListener(e -> handleResumeLive());
        buttonPanel.add(resumeLiveButton);

        buttonPanel.add(createToolbarSeparator());

        newLiveButton = new JButton("New Forward Test...");
        styleToolbarButton(newLiveButton);
        newLiveButton.setToolTipText("Start a fresh live paper trading session and clear any previous one");
        newLiveButton.addActionListener(e -> handleNewLive());
        buttonPanel.add(newLiveButton);

        FloatingToolbarPanel floatingPanel = new FloatingToolbarPanel(50, 50);
        floatingPanel.setLayout(new BorderLayout());
        floatingPanel.add(buttonPanel, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        wrapper.add(floatingPanel);

        updateButtonStates(); // Set initial state
        return wrapper;
    }

    private void handleResumeLive() {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);

        // Loop until connected or the user cancels
        while (!InternetConnectivityService.getInstance().isConnected()) {
            NoInternetDialog dialog = new NoInternetDialog(parentFrame);
            NoInternetDialog.Result result = dialog.showDialog();
            if (result == NoInternetDialog.Result.CANCEL) {
                return; // User cancelled, so exit the action.
            }
            // If RETRY, the loop will check the connection again.
        }

        try {
            ReplaySessionState state = SessionManager.getInstance().loadLiveSession();
            SessionController.getInstance().startLiveSession(state);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to load live session: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleNewLive() {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);

        // Loop until connected or the user cancels
        while (!InternetConnectivityService.getInstance().isConnected()) {
            NoInternetDialog dialog = new NoInternetDialog(parentFrame);
            NoInternetDialog.Result result = dialog.showDialog();
            if (result == NoInternetDialog.Result.CANCEL) {
                return; // User cancelled, so exit the action.
            }
            // If RETRY, the loop will check the connection again.
        }

        // If we exit the loop, we are connected. Proceed.
        SessionDialog dialog = new SessionDialog(parentFrame, SessionDialog.SessionMode.LIVE_PAPER_TRADING);
        dialog.setVisible(true);

        if (dialog.isLaunched()) {
            SessionController.getInstance().startNewLiveSession(
                dialog.getSelectedDataSource(),
                dialog.getStartingBalance(),
                dialog.getLeverage()
            );
        }
    }

    private void styleToolbarButton(JButton button) {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setForeground(UIManager.getColor("Button.disabledText"));
        button.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 14f));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(2, 10, 2, 10));

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                if (button.isEnabled()) {
                    button.setForeground(UIManager.getColor("Button.foreground"));
                }
            }
            public void mouseExited(MouseEvent evt) {
                button.setForeground(UIManager.getColor("Button.disabledText"));
            }
        });
    }

    private JSeparator createToolbarSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 24));
        separator.setForeground(UIManager.getColor("Separator.foreground"));
        return separator;
    }
}
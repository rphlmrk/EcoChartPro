package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.controller.SessionController;
import com.EcoChartPro.ui.dashboard.components.FloatingToolbarPanel;
import com.EcoChartPro.ui.dialogs.SessionDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LiveViewPanel extends JPanel {

    private final ComprehensiveReportPanel reportPanel;

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
    }

    public ComprehensiveReportPanel getReportPanel() {
        return reportPanel;
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

        JButton launchLiveButton = new JButton("Launch Live Charting");
        styleToolbarButton(launchLiveButton);
        launchLiveButton.addActionListener(e -> handleLaunchLive());
        buttonPanel.add(launchLiveButton);

        buttonPanel.add(createToolbarSeparator());

        JButton recoverButton = new JButton("Recover Session");
        styleToolbarButton(recoverButton);
        recoverButton.setToolTipText("Recover a live session after an unexpected shutdown (Not Yet Implemented)");
        recoverButton.setEnabled(false);
        buttonPanel.add(recoverButton);

        FloatingToolbarPanel floatingPanel = new FloatingToolbarPanel(50, 50);
        floatingPanel.setLayout(new BorderLayout());
        floatingPanel.add(buttonPanel, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        wrapper.add(floatingPanel);
        return wrapper;
    }

    private void handleLaunchLive() {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        // Pass the LIVE mode, making the dialog context-aware
        SessionDialog dialog = new SessionDialog(parentFrame, SessionDialog.SessionMode.LIVE_PAPER_TRADING);
        dialog.setVisible(true);

        if (dialog.isLaunched()) {
            // The dialog now ensures it is a LIVE session. No need to check the mode again.
            SessionController.getInstance().startLiveSession(
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
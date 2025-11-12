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
    // [REMOVED] All buttons are now obsolete as they are in PrimaryFrame's menu.
    // private JButton resumeLiveButton;
    // private JButton newLiveButton;
    // private JButton loadLiveButton;

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

        // [REMOVED] The bottom toolbar is no longer needed.
        // add(createBottomToolbar(), BorderLayout.SOUTH);

        // [REMOVED] Component listener and property change listener are no longer needed
        // as the buttons that relied on them are gone.
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // [REMOVED] This logic is no longer needed.
    }

    public ComprehensiveReportPanel getReportPanel() {
        return reportPanel;
    }
    
    // [REMOVED] The updateButtonStates method is no longer needed.

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

    // [REMOVED] The following methods are all obsolete as they deal with UI components
    // and logic that have been moved to PrimaryFrame.
    // createBottomToolbar()
    // handleResumeLive()
    // handleLoadLive()
    // handleNewLive()
    // styleToolbarButton()
    // createToolbarSeparator()
}
package com.EcoChartPro.ui.dashboard;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class LiveViewPanel extends JPanel implements PropertyChangeListener {

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
        // [FIX] Increased unit increment
        scrollPane.getVerticalScrollBar().setUnitIncrement(40);
        scrollPane.getVerticalScrollBar().setUI(new com.formdev.flatlaf.ui.FlatScrollBarUI());
        add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // This listener is currently not used but kept for potential future use.
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
}
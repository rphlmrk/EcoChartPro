package com.EcoChartPro.ui.dashboard;

import javax.swing.*;
import java.awt.*;

public class DashboardViewPanel extends JPanel {
    private final JLabel title;
    private final JLabel subTitle;

    public DashboardViewPanel() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        
        JPanel welcomePanel = new JPanel();
        welcomePanel.setLayout(new BoxLayout(welcomePanel, BoxLayout.Y_AXIS));
        welcomePanel.setOpaque(false);
        
        title = new JLabel("Eco Chart Pro");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        subTitle = new JLabel("Your Professional Charting and Replay Solution");
        subTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        welcomePanel.add(title);
        welcomePanel.add(Box.createVerticalStrut(10));
        welcomePanel.add(subTitle);
        
        add(welcomePanel);
        updateUI(); // Apply initial theme
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (title != null) { // Check for null during construction
            title.setFont(UIManager.getFont("app.font.heading").deriveFont(48f));
            title.setForeground(UIManager.getColor("Label.foreground"));
            subTitle.setFont(UIManager.getFont("app.font.subheading"));
            subTitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        }
    }
}
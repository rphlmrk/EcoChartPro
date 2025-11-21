package com.EcoChartPro.ui.sidebar;

import com.EcoChartPro.ui.home.theme.UITheme;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;

public class SidebarHeaderPanel extends JPanel {
    private final JButton toggleButton;
    private final Icon collapseIcon;
    private final Icon expandIcon;
    private final Border expandedBorder;
    private final Border collapsedBorder;

    public SidebarHeaderPanel(ActionListener toggleAction) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(0, 38));

        this.expandedBorder = BorderFactory.createEmptyBorder(10, 15, 10, 10);
        this.collapsedBorder = BorderFactory.createEmptyBorder(10, 10, 10, 5);

        setBorder(expandedBorder);

        collapseIcon = UITheme.getIcon(UITheme.Icons.ARROW_CIRCLE_LEFT, 20, 20, UIManager.getColor("Label.disabledForeground"));
        expandIcon = UITheme.getIcon(UITheme.Icons.ARROW_CIRCLE_RIGHT, 20, 20, UIManager.getColor("Label.disabledForeground"));
        
        toggleButton = new JButton(collapseIcon);
        styleNavButton(toggleButton);
        toggleButton.addActionListener(toggleAction);
        add(toggleButton, BorderLayout.EAST);
    }

    public void setCollapsed(boolean collapsed) {
        toggleButton.setIcon(collapsed ? expandIcon : collapseIcon);
        setBorder(collapsed ? collapsedBorder : expandedBorder);
    }

    private void styleNavButton(JButton button) {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}
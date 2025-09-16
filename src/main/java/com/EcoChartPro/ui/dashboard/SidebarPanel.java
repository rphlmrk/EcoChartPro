package com.EcoChartPro.ui.dashboard;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SidebarPanel extends JPanel {

    private final MainContentPanel mainContentPanel;
    private final DashboardFrame.BackgroundLayeredPane backgroundPane;
    private final Map<String, String> viewTabs = new LinkedHashMap<>();
    private final ButtonGroup navigationGroup = new ButtonGroup();
    private JToggleButton selectedButton;

    public SidebarPanel(MainContentPanel mainContentPanel, DashboardFrame.BackgroundLayeredPane backgroundPane) {
        this.mainContentPanel = mainContentPanel;
        this.backgroundPane = backgroundPane;
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));

        viewTabs.put("DASHBOARD", "Dashboard");
        viewTabs.put("LIVE", "Live market");
        viewTabs.put("REPLAY", "Replay Mode");
        
        boolean isFirst = true;
        for (Map.Entry<String, String> entry : viewTabs.entrySet()) {
            add(createNavigationTab(entry.getValue(), entry.getKey(), isFirst));
            isFirst = false;
        }
    }

    private JToggleButton createNavigationTab(String text, String viewName, boolean selected) {
        JToggleButton button = new JToggleButton(text, selected);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 14f));
        button.setBorder(BorderFactory.createEmptyBorder(10, 25, 10, 25));

        if (selected) {
            selectedButton = button;
            button.setForeground(UIManager.getColor("Button.foreground"));
        } else {
            button.setForeground(UIManager.getColor("Button.disabledText"));
        }

        button.addActionListener(e -> {
            selectedButton = button;
            mainContentPanel.switchToView(viewName);
            backgroundPane.updateBackgroundImage(viewName);

            for (Component comp : getComponents()) {
                if (comp instanceof AbstractButton) {
                    AbstractButton btn = (AbstractButton) comp;
                    btn.setForeground(btn.isSelected() ? UIManager.getColor("Button.foreground") : UIManager.getColor("Button.disabledText"));
                }
            }
            repaint();
        });

        navigationGroup.add(button);
        return button;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bgColor = UIManager.getColor("Panel.background");
        Color transparentBg = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 220);
        g2d.setColor(transparentBg);
        g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 50, 50));
        
        if (selectedButton != null) {
            Rectangle bounds = selectedButton.getBounds();
            g2d.setColor(UIManager.getColor("Component.focusedBorderColor"));
            g2d.fill(new RoundRectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height, 50, 50));
        }

        g2d.dispose();
    }
    
    public Set<String> getBackgroundKeys() {
        return viewTabs.keySet();
    }
    
    public String getSelectedViewKey() {
        if (selectedButton != null) {
            for (Map.Entry<String, String> entry : viewTabs.entrySet()) {
                if (entry.getValue().equals(selectedButton.getText())) {
                    return entry.getKey();
                }
            }
        }
        return "DASHBOARD"; // Fallback
    }
}
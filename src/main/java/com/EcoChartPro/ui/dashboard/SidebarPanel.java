package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.core.trading.SessionType;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SidebarPanel extends JPanel {

    private final MainContentPanel mainContentPanel;
    private final DashboardFrame.BackgroundLayeredPane backgroundPane;
    private final Map<String, String> viewTabs = new LinkedHashMap<>();
    private final ButtonGroup navigationGroup = new ButtonGroup();
    private JToggleButton selectedButton;
    private PropertyChangeSupport pcs; // Initialized in constructor

    public SidebarPanel(MainContentPanel mainContentPanel, DashboardFrame.BackgroundLayeredPane backgroundPane) {
        // Super() is called implicitly here. UI delegate might call addPropertyChangeListener.
        this.mainContentPanel = mainContentPanel;
        this.backgroundPane = backgroundPane;

        // Initialize pcs after super() has completed.
        this.pcs = new PropertyChangeSupport(this);

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
    
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        // Guard against calls from the UI delegate during super() constructor execution.
        if (pcs != null) {
            pcs.addPropertyChangeListener(listener);
        }
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (pcs != null) {
            pcs.removePropertyChangeListener(listener);
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

            SessionType type = "LIVE".equals(viewName) ? SessionType.LIVE : SessionType.REPLAY;
            PaperTradingService.getInstance().setActiveSessionType(type);
            LiveSessionTrackerService.getInstance().setActiveSessionType(type);

            for (Component comp : getComponents()) {
                if (comp instanceof AbstractButton) {
                    AbstractButton btn = (AbstractButton) comp;
                    btn.setForeground(btn.isSelected() ? UIManager.getColor("Button.foreground") : UIManager.getColor("Button.disabledText"));
                }
            }
            repaint();
            
            pcs.firePropertyChange("viewSwitched", null, viewName);
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
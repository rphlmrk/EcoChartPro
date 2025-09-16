package com.EcoChartPro.ui.sidebar;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

public class SidebarNavPanel extends JPanel {
    private final ButtonGroup navigationGroup = new ButtonGroup();
    private final List<JToggleButton> navButtons = new ArrayList<>();
    private JToggleButton selectedButton;
    private final JPanel tabsContainer;
    private final JButton prevButton;
    private final JButton nextButton;
    private int firstVisibleTabIndex = 0;
    private static final int VISIBLE_TABS = 3;

    public SidebarNavPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        createNavButton("Position", "VIEW_POSITIONS", null, true);
        createNavButton("Journal", "VIEW_JOURNAL", null, false);
        createNavButton("Checklists", "VIEW_CHECKLISTS", null, false);
        prevButton = createArrowButton("<");
        prevButton.addActionListener(e -> scroll(-1));
        add(prevButton, BorderLayout.WEST);
        nextButton = createArrowButton(">");
        nextButton.addActionListener(e -> scroll(1));
        add(nextButton, BorderLayout.EAST);
        tabsContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        tabsContainer.setOpaque(false);
        add(tabsContainer, BorderLayout.CENTER);
        MouseAdapter scrollListener = new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getWheelRotation() < 0) {
                    scroll(-1);
                } else {
                    scroll(1);
                }
            }
        };
        this.addMouseWheelListener(scrollListener);
        updateVisibleTabs();
    }

    private void scroll(int direction) {
        firstVisibleTabIndex += direction;
        firstVisibleTabIndex = Math.max(0, Math.min(firstVisibleTabIndex, navButtons.size() - VISIBLE_TABS));
        updateVisibleTabs();
    }

    private void updateVisibleTabs() {
        tabsContainer.removeAll();
        int endTabIndex = Math.min(firstVisibleTabIndex + VISIBLE_TABS, navButtons.size());
        for (int i = firstVisibleTabIndex; i < endTabIndex; i++) {
            tabsContainer.add(navButtons.get(i));
        }
        prevButton.setEnabled(firstVisibleTabIndex > 0);
        nextButton.setEnabled(firstVisibleTabIndex < navButtons.size() - VISIBLE_TABS);
        tabsContainer.revalidate();
        tabsContainer.repaint();
    }

    private void createNavButton(String text, String actionCommand, String iconPath, boolean isSelected) {
        JToggleButton button = new JToggleButton(text);
        styleNavButton(button, iconPath);
        button.setActionCommand(actionCommand);
        button.addActionListener(e -> {
            selectedButton = (JToggleButton) e.getSource();
            fireActionEvent(e.getActionCommand());
            repaint();
        });
        navigationGroup.add(button);
        navButtons.add(button);
        if (isSelected) {
            button.setSelected(true);
            selectedButton = button;
        }
    }

    private JButton createArrowButton(String text) {
        JButton button = new JButton(text);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        return button;
    }

    private void styleNavButton(JToggleButton button, String iconPath) {
        button.setForeground(UIManager.getColor("Label.disabledForeground"));
        button.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        button.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                button.setForeground(UIManager.getColor("Label.foreground"));
            } else {
                button.setForeground(UIManager.getColor("Label.disabledForeground"));
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (selectedButton != null && selectedButton.isShowing()) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setColor(UIManager.getColor("Component.focusedBorderColor"));
            Point containerLocation = tabsContainer.getLocation();
            Rectangle bounds = selectedButton.getBounds();
            int highlightY = bounds.y + bounds.height - 3;
            g2d.fillRect(containerLocation.x + bounds.x + 5, highlightY, bounds.width - 10, 3);
            g2d.dispose();
        }
    }

    public void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }
    
    private void fireActionEvent(String command) {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command);
        for (ActionListener l : listenerList.getListeners(ActionListener.class)) {
            l.actionPerformed(e);
        }
    }
}
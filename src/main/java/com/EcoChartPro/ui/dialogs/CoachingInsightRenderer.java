package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.InsightSeverity;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;

public class CoachingInsightRenderer extends JPanel implements ListCellRenderer<CoachingInsight> {

    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final JTextArea descriptionArea;

    public CoachingInsightRenderer() {
        setLayout(new BorderLayout(15, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setOpaque(true);

        iconLabel = new JLabel();
        titleLabel = new JLabel();
        titleLabel.setFont(javax.swing.UIManager.getFont("app.font.widget_title"));

        descriptionArea = new JTextArea();
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setOpaque(false);
        descriptionArea.setEditable(false);
        descriptionArea.setFont(javax.swing.UIManager.getFont("app.font.widget_content"));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(descriptionArea);

        add(iconLabel, BorderLayout.WEST);
        add(textPanel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends CoachingInsight> list, CoachingInsight insight, int index, boolean isSelected, boolean cellHasFocus) {
        titleLabel.setText(insight.title());
        descriptionArea.setText(insight.description());

        Color iconColor;
        String iconPath;

        switch (insight.severity()) {
            case HIGH:
                iconColor = javax.swing.UIManager.getColor("app.color.negative");
                iconPath = UITheme.Icons.ERROR_CIRCLE;
                break;
            case MEDIUM:
                iconColor = javax.swing.UIManager.getColor("app.color.neutral");
                iconPath = UITheme.Icons.INFO;
                break;
            case LOW:
            default:
                iconColor = javax.swing.UIManager.getColor("app.color.positive");
                iconPath = UITheme.Icons.CHECKMARK;
                break;
        }
        iconLabel.setIcon(UITheme.getIcon(iconPath, 24, 24, iconColor));

        if (isSelected) {
            setBackground(javax.swing.UIManager.getColor("List.selectionBackground"));
            titleLabel.setForeground(javax.swing.UIManager.getColor("List.selectionForeground"));
            descriptionArea.setForeground(javax.swing.UIManager.getColor("List.selectionForeground"));
        } else {
            setBackground(javax.swing.UIManager.getColor("List.background"));
            titleLabel.setForeground(javax.swing.UIManager.getColor("List.foreground"));
            descriptionArea.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        }

        return this;
    }
}
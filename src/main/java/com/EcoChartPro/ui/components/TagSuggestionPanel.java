package com.EcoChartPro.ui.components;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * A panel that displays a list of strings as clickable, stylized buttons.
 * When a button is clicked, it fires a PropertyChangeEvent with the button's text.
 */
public class TagSuggestionPanel extends JPanel {

    /**
     * Constructs a new panel with a title and a list of tag suggestions.
     * @param title The title to display in the panel's border.
     * @param tags  The list of strings to be displayed as clickable tags.
     */
    public TagSuggestionPanel(String title, List<String> tags) {
        super(new FlowLayout(FlowLayout.LEFT, 5, 5));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(), title, TitledBorder.LEFT, TitledBorder.TOP));
        setOpaque(false);

        if (tags != null) {
            for (String tag : tags) {
                JButton tagButton = new JButton(tag);
                styleTagButton(tagButton);
                tagButton.addActionListener(e -> {
                    // Fire an event to notify the parent container of the selection.
                    firePropertyChange("tagClicked", null, tag);
                });
                add(tagButton);
            }
        }
    }

    /**
     * Applies a consistent, modern style to the tag buttons.
     * @param button The JButton to be styled.
     */
    private void styleTagButton(JButton button) {
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
    }
}
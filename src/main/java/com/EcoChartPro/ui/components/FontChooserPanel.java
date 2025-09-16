package com.EcoChartPro.ui.components;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * A panel for selecting a font, including family, style, and size, with a live preview.
 */
public class FontChooserPanel extends JPanel {

    private final JList<String> fontList;
    private final JList<String> styleList;
    private final JList<Integer> sizeList;
    private final JLabel previewLabel;

    private Font selectedFont;

    public FontChooserPanel(Font initialFont) {
        setLayout(new BorderLayout(10, 10));

        // --- Create list components ---
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontList = new JList<>(fontNames);

        String[] styleNames = {"Plain", "Bold", "Italic", "Bold Italic"};
        styleList = new JList<>(styleNames);

        Integer[] fontSizes = {8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72};
        sizeList = new JList<>(fontSizes);

        // --- Layout the selection lists ---
        JPanel listsPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        listsPanel.add(createListPanel("Font Family", fontList));
        listsPanel.add(createListPanel("Font Style", styleList));
        listsPanel.add(createListPanel("Font Size", sizeList));
        add(listsPanel, BorderLayout.CENTER);

        // --- Create preview panel ---
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(new TitledBorder("Preview"));
        previewLabel = new JLabel("AaBbYyZz", SwingConstants.CENTER);
        previewLabel.setPreferredSize(new Dimension(100, 80));
        previewPanel.add(previewLabel, BorderLayout.CENTER);
        add(previewPanel, BorderLayout.SOUTH);

        // --- Set initial values ---
        setSelectedFont(initialFont);

        // --- Add listeners ---
        fontList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updatePreview(); });
        styleList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updatePreview(); });
        sizeList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updatePreview(); });
    }

    private JPanel createListPanel(String title, JList<?> list) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(title));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private void updatePreview() {
        String name = fontList.getSelectedValue();
        String styleStr = styleList.getSelectedValue();
        Integer size = sizeList.getSelectedValue();

        if (name == null || styleStr == null || size == null) {
            return; // Not all selections have been made yet
        }

        int style;
        switch (styleStr) {
            case "Bold":
                style = Font.BOLD;
                break;
            case "Italic":
                style = Font.ITALIC;
                break;
            case "Bold Italic":
                style = Font.BOLD | Font.ITALIC;
                break;
            default:
                style = Font.PLAIN;
                break;
        }

        this.selectedFont = new Font(name, style, size);
        previewLabel.setFont(this.selectedFont);
    }

    public void setSelectedFont(Font font) {
        if (font == null) {
            font = new Font("SansSerif", Font.PLAIN, 12);
        }
        fontList.setSelectedValue(font.getFamily(), true);

        String styleStr;
        if (font.isBold() && font.isItalic()) {
            styleStr = "Bold Italic";
        } else if (font.isBold()) {
            styleStr = "Bold";
        } else if (font.isItalic()) {
            styleStr = "Italic";
        } else {
            styleStr = "Plain";
        }
        styleList.setSelectedValue(styleStr, true);
        sizeList.setSelectedValue(font.getSize(), true);
        
        // Ensure the preview is updated with the initial font
        updatePreview();
    }

    public Font getSelectedFont() {
        // Ensure the font object is up-to-date before returning it
        updatePreview();
        return this.selectedFont;
    }
}
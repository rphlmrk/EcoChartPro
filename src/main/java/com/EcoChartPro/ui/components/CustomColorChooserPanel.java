package com.EcoChartPro.ui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * A custom color chooser panel with a curated palette of presets, shades,
 * and an opacity slider.
 */
public class CustomColorChooserPanel extends JPanel {

    // A curated list of primary chromatic colors.
    private static final Color[] PRESET_COLORS = {
        new Color(244, 67, 54),  // Red
        new Color(255, 140, 40), // Orange (EcoChartPro Accent)
        new Color(255, 235, 59), // Yellow
        new Color(76, 175, 80),  // Green
        new Color(0, 150, 136),  // Teal
        new Color(33, 150, 243),  // Blue
        new Color(156, 39, 176), // Purple
        new Color(233, 30, 99)   // Pink
    };

    // A dedicated, manually defined ramp for the grayscale column.
    private static final Color[] GRAYSCALE_COLORS = {
        Color.WHITE,
        new Color(224, 224, 224), // Light Gray
        Color.GRAY,
        new Color(80, 80, 80),   // Dark Gray
        Color.BLACK
    };

    private static final int SHADE_COUNT = 4; // Number of tints/shades to generate per color (must be even).
    private static final int SWATCH_SIZE = 22;
    private static final int GAP = 3;

    private Color currentColor;
    private final JSlider opacitySlider;
    private final Consumer<Color> onColorChange;
    private final JLabel opacityValueLabel;

    /**
     * Constructs the color chooser panel.
     * @param initialColor The color to pre-select.
     * @param onColorChange A callback that is fired whenever the color is changed.
     */
    public CustomColorChooserPanel(Color initialColor, Consumer<Color> onColorChange) {
        this.currentColor = initialColor;
        this.onColorChange = onColorChange;

        setLayout(new BorderLayout(0, 10));
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Main Color Grid Panel ---
        int numRows = SHADE_COUNT + 1;
        int numCols = PRESET_COLORS.length + 1; // +1 for the grayscale column
        JPanel gridPanel = new JPanel(new GridLayout(numRows, numCols, GAP, GAP));
        gridPanel.setOpaque(false);

        // Build the grid row by row to maintain the column-based arrangement
        for (int r = 0; r < numRows; r++) {
            // Add the swatches for the chromatic colors
            for (Color preset : PRESET_COLORS) {
                if (r == 0) {
                    // Top row: The pure preset color
                    gridPanel.add(createColorSwatch(preset));
                } else if (r <= SHADE_COUNT / 2) {
                    // Upper rows: Tints (lighter versions)
                    int tintIndex = r - 1;
                    float factor = (tintIndex + 1.0f) / (SHADE_COUNT / 2.0f + 1.0f);
                    gridPanel.add(createColorSwatch(interpolateColor(preset, Color.WHITE, factor)));
                } else {
                    // Lower rows: Shades (darker versions)
                    int shadeIndex = r - (SHADE_COUNT / 2) - 1;
                    float factor = (shadeIndex + 1.0f) / (SHADE_COUNT / 2.0f + 1.0f);
                    gridPanel.add(createColorSwatch(interpolateColor(preset, Color.BLACK, factor)));
                }
            }
            // Finally, add the corresponding grayscale swatch for this row
            gridPanel.add(createColorSwatch(GRAYSCALE_COLORS[r]));
        }
        add(gridPanel, BorderLayout.CENTER);

        // --- Opacity Control Panel ---
        JPanel opacityPanel = new JPanel(new BorderLayout(10, 0));
        opacityPanel.setOpaque(false);
        
        JLabel opacityLabel = new JLabel("Opacity:");
        opacityLabel.setForeground(UIManager.getColor("Label.foreground"));
        
        this.opacitySlider = new JSlider(0, 255, initialColor.getAlpha());
        this.opacityValueLabel = new JLabel(String.format("%d%%", (int)(initialColor.getAlpha() * 100 / 255.0)));
        opacityValueLabel.setForeground(UIManager.getColor("Label.foreground"));
        opacityValueLabel.setPreferredSize(new Dimension(40, 10)); // Prevent resizing

        opacitySlider.addChangeListener(e -> {
            int alpha = opacitySlider.getValue();
            opacityValueLabel.setText(String.format("%d%%", (int)(alpha * 100 / 255.0)));
            // Update current color with new alpha and notify listener
            if (currentColor != null) {
                currentColor = new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), alpha);
                this.onColorChange.accept(currentColor);
            }
        });

        opacityPanel.add(opacityLabel, BorderLayout.WEST);
        opacityPanel.add(opacitySlider, BorderLayout.CENTER);
        opacityPanel.add(opacityValueLabel, BorderLayout.EAST);
        
        add(opacityPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Creates a single color swatch panel that acts like a button.
     */
    private JPanel createColorSwatch(Color color) {
        JPanel swatch = new JPanel();
        swatch.setBackground(color);
        swatch.setPreferredSize(new Dimension(SWATCH_SIZE, SWATCH_SIZE));
        swatch.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Panel.background")));
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        swatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int currentAlpha = (currentColor != null) ? currentColor.getAlpha() : 255;
                currentColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), currentAlpha);
                onColorChange.accept(currentColor);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                swatch.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.focusedBorderColor"), 2));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                swatch.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Panel.background")));
            }
        });
        
        return swatch;
    }

    /**
     * Interpolates between two colors by a given factor.
     * @param c1 The base color.
     * @param c2 The color to mix towards.
     * @param factor A value from 0.0 to 1.0.
     * @return The resulting interpolated color.
     */
    private Color interpolateColor(Color c1, Color c2, float factor) {
        float R = c1.getRed()   + factor * (c2.getRed()   - c1.getRed());
        float G = c1.getGreen() + factor * (c2.getGreen() - c1.getGreen());
        float B = c1.getBlue()  + factor * (c2.getBlue()  - c1.getBlue());
        return new Color(
            (int) Math.max(0, Math.min(255, R)),
            (int) Math.max(0, Math.min(255, G)),
            (int) Math.max(0, Math.min(255, B))
        );
    }
}
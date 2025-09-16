package com.EcoChartPro.core.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;

public final class ThemeManager {

    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);

    private static final String[] CUSTOM_UI_KEYS = {
        "Panel.background", "Component.background", "ToolBar.background",
        "TabbedPane.background", "List.background", "Tree.background",
        "Table.background", "ScrollPane.background", "Viewport.background",
        "Component.borderColor", "Component.focusedBorderColor",
        "Button.background", "Button.hoverBackground", "Button.pressedBackground",
        // Custom semantic color keys
        "app.color.positive", "app.color.negative", "app.color.accent", "app.color.neutral",
        "app.trading.long", "app.trading.short", "app.trading.pending", "app.trading.pnlProfit", "app.trading.pnlLoss",
        "app.dialog.input.background", "app.dialog.input.foreground", "app.dialog.input.border",
        "app.journal.profit", "app.journal.loss", "app.journal.breakeven",
        "app.journal.plan.good", "app.journal.plan.ok", "app.journal.plan.bad",
        "app.chart.separator", "app.chart.separatorLabel",
        // Custom font keys
        "app.font.heading", "app.font.subheading", "app.font.widget_title", "app.font.widget_content", "app.font.value_large",
        // Standard component keys that we might override
        "ProgressBar.trackColor", "ProgressBar.foreground"
    };

    public enum Theme {
        DARK("Dark"),
        LIGHT("Light");

        private final String displayName;
        Theme(String displayName) { this.displayName = displayName; }
        @Override
        public String toString() { return displayName; }
    }

    private ThemeManager() {}

    public static void applyTheme(Theme theme) {
        try {
            for (String key : CUSTOM_UI_KEYS) {
                UIManager.put(key, null);
            }

            // --- Apply Base Theme ---
            if (theme == Theme.LIGHT) {
                FlatLightLaf.setup();
                logger.info("Applying FlatLightLaf theme.");
            } else {
                FlatDarkLaf.setup();
                logger.info("Applying FlatDarkLaf theme.");
            }
            
            // --- Define Common Fonts ---
            UIManager.put("app.font.heading", new Font("SansSerif", Font.BOLD, 32));
            UIManager.put("app.font.subheading", new Font("SansSerif", Font.PLAIN, 18));
            UIManager.put("app.font.widget_title", new Font("SansSerif", Font.BOLD, 16));
            UIManager.put("app.font.widget_content", new Font("SansSerif", Font.PLAIN, 14));
            UIManager.put("app.font.value_large", new Font("SansSerif", Font.BOLD, 24));
            
            // --- Apply Overrides and Custom Colors ---
            if (theme == Theme.LIGHT) {
                // Light Theme Custom Colors
                UIManager.put("app.color.positive", new Color(0, 128, 0)); // Darker Green
                UIManager.put("app.color.negative", new Color(211, 47, 47));
                UIManager.put("app.color.accent", new Color(0, 150, 136)); // Teal
                UIManager.put("app.color.neutral", new Color(0, 105, 217)); // Brighter Blue

                // Trading & Journal Colors
                UIManager.put("app.trading.long", new Color(0, 128, 0));
                UIManager.put("app.trading.short", new Color(211, 47, 47));
                UIManager.put("app.trading.pending", new Color(255, 158, 27)); // Amber
                UIManager.put("app.trading.pnlProfit", new Color(0, 105, 217, 150)); // Blue with alpha
                UIManager.put("app.trading.pnlLoss", new Color(255, 138, 101, 150)); // Orange with alpha
                UIManager.put("app.journal.profit", new Color(0, 150, 136, 180));
                UIManager.put("app.journal.loss", new Color(244, 67, 54, 180));
                UIManager.put("app.journal.breakeven", new Color(255, 152, 0, 180));
                UIManager.put("app.journal.plan.good", new Color(0, 150, 136, 180));
                UIManager.put("app.journal.plan.ok", new Color(255, 152, 0, 180));
                UIManager.put("app.journal.plan.bad", new Color(244, 67, 54, 180));

                // Dialog & Chart Colors
                UIManager.put("app.dialog.input.background", new Color(242, 242, 242, 230));
                UIManager.put("app.dialog.input.foreground", Color.BLACK);
                UIManager.put("app.dialog.input.border", new Color(0x3399FF));
                UIManager.put("app.chart.separator", new Color(100, 100, 100, 60));
                UIManager.put("app.chart.separatorLabel", new Color(50, 50, 50, 180));
            } else { // DARK Theme
                // Darker Overrides
                UIManager.put("Panel.background", new Color(0x212325));
                UIManager.put("Component.background", new Color(0x212325));
                UIManager.put("ToolBar.background", new Color(0x212325));
                UIManager.put("Component.borderColor", new Color(0x35383C));
                UIManager.put("Component.focusedBorderColor", new Color(0x2A65B1));
                
                // Dark Theme Custom Colors
                UIManager.put("app.color.positive", new Color(0x4CAF50));
                UIManager.put("app.color.negative", new Color(0xF44336));
                UIManager.put("app.color.accent", new Color(0x00BFA5)); // Teal
                UIManager.put("app.color.neutral", new Color(0x2196F3));

                // Trading & Journal Colors
                UIManager.put("app.trading.long", new Color(0x4CAF50));
                UIManager.put("app.trading.short", new Color(0xF44336));
                UIManager.put("app.trading.pending", new Color(0xFFC107));
                UIManager.put("app.trading.pnlProfit", new Color(0x2196F3, true)); // Blue with alpha
                UIManager.put("app.trading.pnlLoss", new Color(0xFF9800, true)); // Orange with alpha
                UIManager.put("app.journal.profit", new Color(0, 150, 136, 180));
                UIManager.put("app.journal.loss", new Color(244, 67, 54, 180));
                UIManager.put("app.journal.breakeven", new Color(255, 152, 0, 180));
                UIManager.put("app.journal.plan.good", new Color(0, 150, 136, 180));
                UIManager.put("app.journal.plan.ok", new Color(255, 152, 0, 180));
                UIManager.put("app.journal.plan.bad", new Color(244, 67, 54, 180));

                // Dialog & Chart Colors
                UIManager.put("app.dialog.input.background", new Color(43, 43, 43, 230));
                UIManager.put("app.dialog.input.foreground", Color.WHITE);
                UIManager.put("app.dialog.input.border", new Color(0x3399FF));
                UIManager.put("app.chart.separator", new Color(192, 192, 192, 60));
                UIManager.put("app.chart.separatorLabel", new Color(255, 255, 255, 180));
            }

            final boolean isMacOS = System.getProperty("os.name").toLowerCase().contains("mac");
            if (isMacOS) {
                UIManager.put("TitlePane.unifiedBackground", true);
                logger.info("Configuring unified window decorations for macOS.");
            }

            for (Frame frame : Frame.getFrames()) {
                SwingUtilities.updateComponentTreeUI(frame);
                frame.revalidate();
                frame.repaint();
            }
            logger.info("Updated component tree UI for all open frames.");

        } catch (Exception ex) {
            logger.error("Failed to set new look and feel theme: {}", theme, ex);
        }
    }
}
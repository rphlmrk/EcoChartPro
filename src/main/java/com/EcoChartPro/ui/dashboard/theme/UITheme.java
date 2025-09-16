package com.EcoChartPro.ui.dashboard.theme;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public final class UITheme {

    private UITheme() {}

    public static final class Icons {
        // --- Dashboard & Main ---
        public static final String EXPAND = "/icons/expand.svg";
        public static final String DASHBOARD = "/icons/dashboard.svg";
        public static final String REPLAY = "/icons/replay.svg";
        public static final String LIVE = "/icons/live.svg";
        public static final String JOURNAL = "/icons/journal.svg";
        public static final String SETTINGS = "/icons/settings.svg";
        public static final String APP_LOGO = "/icons/app-logo.svg";

        // --- Symbols & General UI ---
        public static final String BTC = "/icons/btc.svg";
        public static final String ETH = "/icons/eth.svg";
        public static final String SOL = "/icons/sol.svg";
        public static final String SEARCH = "/icons/search.svg";
        public static final String CHECKMARK = "/icons/checkmark.svg";
        public static final String INFO = "/icons/info.svg";
        public static final String JUMP_TO = "/icons/jumpto.svg";
        public static final String INDICATORS = "/icons/function.svg";
        public static final String REFRESH = "/icons/refresh.svg";
        public static final String HELP = "/icons/help.svg";
        public static final String ERROR_CIRCLE = "/icons/error-circle.svg";

        // --- Drawing Tools ---
        public static final String TRENDLINE = "/icons/trendline.svg";
        public static final String RECTANGLE = "/icons/rectangle.svg";
        public static final String HORIZONTAL_LINE = "/icons/horizontal-line.svg";
        public static final String VERTICAL_LINE = "/icons/vertical-line.svg";
        public static final String RAY = "/icons/ray.svg";
        public static final String HORIZONTAL_RAY = "/icons/horizontal-ray.svg";
        public static final String PRICE_RANGE = "/icons/price-range.svg";
        public static final String DATE_RANGE = "/icons/date-range.svg";
        public static final String MEASURE = "/icons/measure.svg";
        public static final String TEXT = "/icons/text.svg";
        public static final String ANCHORED_TEXT = "/icons/anchored-text.svg";
        public static final String PROTECTED_LEVEL = "/icons/protected-level.svg";
        public static final String FIB_RETRACEMENT = "/icons/fib-retracement.svg";
        public static final String FIB_EXTENSION = "/icons/fib-extension.svg";

        // --- Editor & API ---
        public static final String SCRIPT_JS = "/icons/js.svg";
        public static final String PLUGIN_JAVA = "/icons/java.svg";
        public static final String API_CLASS = "/icons/class.svg";
        public static final String API_METHOD = "/icons/method.svg";
        public static final String API_FIELD = "/icons/field.svg";
        public static final String NEW_FILE = "/icons/new-file.svg";
        public static final String SAVE = "/icons/save.svg";
        public static final String DELETE = "/icons/delete.svg";
        public static final String APPLY = "/icons/checkmark.svg";
        public static final String FOLDER = "/icons/folder.svg";

        // --- Navigation & Controls ---
        public static final String ARROW_LEFT = "/icons/arrow-left.svg";
        public static final String ARROW_RIGHT = "/icons/arrow-right.svg";
        public static final String ARROW_CIRCLE_LEFT = "/icons/arrow-circle-left.svg";
        public static final String ARROW_CIRCLE_RIGHT = "/icons/arrow-circle-right.svg";
        public static final String FAST_FORWARD = "/icons/fast-forward.svg";
        public static final String CHEVRON_DOUBLE_LEFT = "/icons/chevron-double-left.svg";
        public static final String CHEVRON_DOUBLE_RIGHT = "/icons/chevron-double-right.svg";
        public static final String DRAG_HANDLE = "/icons/drag-handle.svg";
        public static final String VISIBILITY_ON = "/icons/visibility-on.svg";
        public static final String DROPDOWN_ARROW_DOWN = "/icons/dropdown-arrow-down.svg";
        public static final String DROPDOWN_ARROW_RIGHT = "/icons/dropdown-arrow-right.svg";
        public static final String PLAY = "/icons/play.svg"; 
        public static final String PAUSE = "/icons/pause.svg"; 

        // --- Trading & Sidebar ---
        public static final String TRADE_ARROW_UP = "/icons/arrow-up.svg";
        public static final String TRADE_ARROW_DOWN = "/icons/arrow-down.svg";
        public static final String POSITIONS = "/icons/positions.svg";
        public static final String HISTORY = "/icons/history.svg";
        public static final String REPORT = "/icons/report.svg";
        public static final String CHECKLISTS = "/icons/checklists.svg";
        // achievements
        public static final String TROPHY = "/icons/trophy.svg";
        
        // --- Toolbar Refactor Icons ---
        public static final String LAYOUT_GRID = "/icons/layout-grid.svg";
        public static final String CLOCK = "/icons/clock.svg";
        public static final String CROSSHAIR = "/icons/crosshairs.svg";
        public static final String LAYOUT_1 = "/icons/layout-1.svg";
        public static final String LAYOUT_2_H = "/icons/layout-2-h.svg";
        public static final String LAYOUT_3_L = "/icons/layout-3-l.svg";
        public static final String LAYOUT_3_R = "/icons/layout-3-r.svg";
        public static final String LAYOUT_4 = "/icons/layout-4.svg";
        public static final String LAYOUT_3_V = "/icons/layout-3-v.svg";
        public static final String LAYOUT_4_V = "/icons/layout-4-v.svg";
        public static final String LAYOUT_2_V = "/icons/layout-2-v.svg";
        public static final String LAYOUT_3_H = "/icons/layout-3-h.svg";
    }
    
    @Deprecated
    public static Icon getThemedIcon(String path, int width, int height) {
        return getIcon(path, width, height);
    }
    
    public static Icon getIcon(String path, int width, int height) {
        Color defaultColor = javax.swing.UIManager.getColor("Label.foreground");
        return getIcon(path, width, height, defaultColor);
    }

    public static Icon getIcon(String path, int width, int height, Color color) {
        URL resource = UITheme.class.getResource(path);
        if (resource == null) {
            System.err.println("SVG icon resource not found: " + path);
            return new ImageIcon(new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB));
        }

        FlatSVGIcon icon = new FlatSVGIcon(resource);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        return icon.derive(width, height);
    }
}
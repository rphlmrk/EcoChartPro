package com.EcoChartPro.ui.dashboard.widgets;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import java.util.Collections;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

public class AreaChartWidget extends JPanel {

    private final EquityCurveChart chart;
    private final JLabel titleLabel;

    public AreaChartWidget() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        titleLabel = new JLabel("Finished Trades PNL");
        titleLabel.setFont(UIManager.getFont("app.font.widget_content"));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.add(titleLabel, BorderLayout.WEST);
        JLabel icon = new JLabel(UITheme.getThemedIcon(UITheme.Icons.EXPAND, 16, 16));
        header.add(icon, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        chart = new EquityCurveChart();
        add(chart, BorderLayout.CENTER);
        // [MODIFIED] Footer and related labels have been removed from this component.
    }
    
    // [MODIFIED] Replaced all previous data methods with a single, simple one.
    public void updateData(List<JournalAnalysisService.EquityPoint> equityCurve) {
        chart.updateData(equityCurve);
    }

    // [MODIFIED] Title is now static, but we provide a method if it needs to be changed.
    public void setTitle(String title) {
        titleLabel.setText(title);
    }
}
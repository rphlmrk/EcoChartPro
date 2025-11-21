package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.home.widgets.TrendChartWidget;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ComparativeAnalysisPanel extends JPanel {

    private final JournalAnalysisService analysisService = new JournalAnalysisService();
    private List<Trade> allTrades = Collections.emptyList();

    private final JComboBox<String> periodComboBox;
    private final JTable resultsTable;
    private final DefaultTableModel tableModel;

    private final TrendChartWidget pnlChart;
    private final TrendChartWidget winRateChart;
    private final TrendChartWidget profitFactorChart;

    private static final String[] COLUMN_NAMES = {
        "Period", "Total P&L", "Win Rate", "Profit Factor", "Avg R:R", "Expectancy", "# of Trades"
    };

    public ComparativeAnalysisPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setOpaque(false);

        // --- Top Controls & Charts Panel ---
        JPanel northPanel = new JPanel(new BorderLayout(10, 10));
        northPanel.setOpaque(false);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlsPanel.setOpaque(false);
        controlsPanel.add(new JLabel("Compare Performance By:"));
        periodComboBox = new JComboBox<>(new String[]{"Monthly", "Quarterly (3mo)", "Semi-Annually (6mo)", "Yearly"});
        periodComboBox.addActionListener(e -> updateTableData());
        controlsPanel.add(periodComboBox);
        northPanel.add(controlsPanel, BorderLayout.NORTH);

        JPanel chartsPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        chartsPanel.setOpaque(false);
        pnlChart = new TrendChartWidget();
        winRateChart = new TrendChartWidget();
        profitFactorChart = new TrendChartWidget();
        chartsPanel.add(pnlChart);
        chartsPanel.add(winRateChart);
        chartsPanel.add(profitFactorChart);
        chartsPanel.setPreferredSize(new Dimension(800, 200)); // Give charts some initial height
        northPanel.add(chartsPanel, BorderLayout.CENTER);

        add(northPanel, BorderLayout.NORTH);

        // --- Results Table ---
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultsTable = new JTable(tableModel);
        resultsTable.setFillsViewportHeight(true);
        resultsTable.setRowHeight(24);
        resultsTable.setShowGrid(true);
        resultsTable.setGridColor(UIManager.getColor("Component.borderColor"));
        
        // --- Custom Cell Renderers ---
        resultsTable.setDefaultRenderer(Object.class, new PerformanceTableCellRenderer());
        
        // Set column widths
        TableColumnModel columnModel = resultsTable.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(120); // Period
        columnModel.getColumn(1).setPreferredWidth(100); // P&L
        columnModel.getColumn(2).setPreferredWidth(80); // Win Rate
        columnModel.getColumn(3).setPreferredWidth(90); // Profit Factor
        columnModel.getColumn(4).setPreferredWidth(80); // Avg R:R
        columnModel.getColumn(5).setPreferredWidth(100); // Expectancy
        columnModel.getColumn(6).setPreferredWidth(80); // # Trades

        add(new JScrollPane(resultsTable), BorderLayout.CENTER);
    }

    public void loadData(List<Trade> trades) {
        this.allTrades = (trades != null) ? trades : Collections.emptyList();
        updateTableData();
    }
    
    private void updateTableData() {
        if (allTrades.isEmpty()) {
            tableModel.setRowCount(0);
            pnlChart.setData("Total P&L", Collections.emptyList());
            winRateChart.setData("Win Rate (%)", Collections.emptyList());
            profitFactorChart.setData("Profit Factor", Collections.emptyList());
            return;
        }

        String selectedPeriod = (String) periodComboBox.getSelectedItem();
        ChronoUnit periodUnit;
        long periodAmount;

        switch (selectedPeriod) {
            case "Quarterly (3mo)":
                periodUnit = ChronoUnit.MONTHS;
                periodAmount = 3;
                break;
            case "Semi-Annually (6mo)":
                periodUnit = ChronoUnit.MONTHS;
                periodAmount = 6;
                break;
            case "Yearly":
                periodUnit = ChronoUnit.YEARS;
                periodAmount = 1;
                break;
            case "Monthly":
            default:
                periodUnit = ChronoUnit.MONTHS;
                periodAmount = 1;
                break;
        }

        Map<LocalDate, JournalAnalysisService.OverallStats> periodStats = analysisService.analyzePerformanceByPeriod(allTrades, periodUnit, periodAmount);

        tableModel.setRowCount(0); // Clear existing data

        DateTimeFormatter formatter;
        if ("Monthly".equals(selectedPeriod)) {
            formatter = DateTimeFormatter.ofPattern("MMM yy");
        } else if ("Yearly".equals(selectedPeriod)) {
            formatter = DateTimeFormatter.ofPattern("yyyy");
        } else { // Quarterly/Semi-Annually
            formatter = DateTimeFormatter.ofPattern("MMM yy");
        }

        List<TrendChartWidget.DataPoint> pnlData = new ArrayList<>();
        List<TrendChartWidget.DataPoint> winRateData = new ArrayList<>();
        List<TrendChartWidget.DataPoint> pfData = new ArrayList<>();
        
        // The map is sorted descending, so we iterate to populate the table
        for (Map.Entry<LocalDate, JournalAnalysisService.OverallStats> entry : periodStats.entrySet()) {
            LocalDate periodStart = entry.getKey();
            JournalAnalysisService.OverallStats stats = entry.getValue();

            String periodLabel = getPeriodLabel(periodStart, selectedPeriod, formatter);
            
            Object[] rowData = {
                periodLabel,
                stats.totalPnl(),
                stats.winRate(),
                stats.profitFactor(),
                stats.avgRiskReward(),
                stats.expectancy(),
                stats.totalTrades()
            };
            tableModel.addRow(rowData);
            
            // Add data for charts (will be reversed later)
            pnlData.add(new TrendChartWidget.DataPoint(periodLabel, stats.totalPnl().doubleValue()));
            winRateData.add(new TrendChartWidget.DataPoint(periodLabel, stats.winRate() * 100)); // As percentage
            pfData.add(new TrendChartWidget.DataPoint(periodLabel, stats.profitFactor().doubleValue()));
        }

        // Reverse data for chronological chart display
        Collections.reverse(pnlData);
        Collections.reverse(winRateData);
        Collections.reverse(pfData);

        pnlChart.setData("Total P&L", pnlData);
        winRateChart.setData("Win Rate (%)", winRateData);
        profitFactorChart.setData("Profit Factor", pfData);
    }
    
    private String getPeriodLabel(LocalDate periodStart, String selectedPeriod, DateTimeFormatter formatter) {
        if ("Quarterly (3mo)".equals(selectedPeriod)) {
            return "Q" + ((periodStart.getMonthValue() - 1) / 3 + 1) + " " + periodStart.getYear();
        }
        if ("Semi-Annually (6mo)".equals(selectedPeriod)) {
            return (periodStart.getMonthValue() == 1 ? "H1" : "H2") + " " + periodStart.getYear();
        }
        return formatter.format(periodStart);
    }
    
    /**
     * Custom renderer to format numbers and apply colors.
     */
    private static class PerformanceTableCellRenderer extends DefaultTableCellRenderer {
        private final DecimalFormat pnlFormat = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        private final DecimalFormat percentFormat = new DecimalFormat("0.0'%'");
        private final DecimalFormat decimalFormat = new DecimalFormat("0.00");
        private final Color positiveColor = UIManager.getColor("app.color.positive");
        private final Color negativeColor = UIManager.getColor("app.color.negative");

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setHorizontalAlignment(JLabel.RIGHT);
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());

            if (value instanceof String) {
                 setHorizontalAlignment(JLabel.LEFT);
                 setText((String) value);
            } else if (value instanceof BigDecimal pnl) {
                setText(pnlFormat.format(pnl));
                setForeground(isSelected ? table.getSelectionForeground() : (pnl.signum() >= 0 ? positiveColor : negativeColor));
            } else if (value instanceof Double dbl) {
                // For Win Rate or Avg R:R
                if (table.getColumnName(column).equals("Win Rate")) {
                    setText(percentFormat.format(dbl * 100));
                } else {
                    setText(decimalFormat.format(dbl));
                     if (table.getColumnName(column).equals("Avg R:R")) {
                        setForeground(isSelected ? table.getSelectionForeground() : (dbl >= 1.0 ? positiveColor : negativeColor));
                    }
                }
            } else if (value instanceof Integer) {
                setText(String.valueOf(value));
            }
            
            return this;
        }
    }
}
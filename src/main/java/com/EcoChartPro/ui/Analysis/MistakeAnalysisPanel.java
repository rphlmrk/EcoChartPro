package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.model.MistakeStats; // <-- THIS LINE IS THE FIX

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MistakeAnalysisPanel extends JPanel {
    private final JTable mistakesTable;
    private final MistakeTableModel tableModel;

    public MistakeAnalysisPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tableModel = new MistakeTableModel();
        mistakesTable = new JTable(tableModel);
        mistakesTable.setAutoCreateRowSorter(true);
        mistakesTable.setFillsViewportHeight(true);
        mistakesTable.setRowHeight(25);

        // Custom renderer for P&L columns
        PnlCellRenderer pnlRenderer = new PnlCellRenderer();
        mistakesTable.getColumnModel().getColumn(2).setCellRenderer(pnlRenderer);
        mistakesTable.getColumnModel().getColumn(3).setCellRenderer(pnlRenderer);

        add(new JScrollPane(mistakesTable), BorderLayout.CENTER);
    }

    public void updateData(Map<String, MistakeStats> mistakeData) {
        if (mistakeData == null) {
            tableModel.setMistakeStats(Collections.emptyList());
        } else {
            tableModel.setMistakeStats(new ArrayList<>(mistakeData.values()));
        }
    }

    private static class MistakeTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Mistake", "Frequency", "Total P&L", "Avg P&L/Trade"};
        private List<MistakeStats> stats = new ArrayList<>();

        public void setMistakeStats(List<MistakeStats> stats) {
            this.stats = stats;
            // Sort by total P&L impact by default (most negative first)
            this.stats.sort((s1, s2) -> s1.totalPnl().compareTo(s2.totalPnl()));
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return stats.size(); }

        @Override
        public int getColumnCount() { return columnNames.length; }

        @Override
        public String getColumnName(int column) { return columnNames[column]; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0: return String.class;
                case 1: return Integer.class;
                case 2: case 3: return BigDecimal.class;
                default: return Object.class;
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MistakeStats stat = stats.get(rowIndex);
            switch (columnIndex) {
                case 0: return stat.mistakeName();
                case 1: return stat.frequency();
                case 2: return stat.totalPnl();
                case 3: return stat.averagePnl();
                default: return null;
            }
        }
    }

    private static class PnlCellRenderer extends DefaultTableCellRenderer {
        private static final DecimalFormat FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        private final Color positiveColor = UIManager.getColor("app.color.positive");
        private final Color negativeColor = UIManager.getColor("app.color.negative");

        public PnlCellRenderer() {
            setHorizontalAlignment(JLabel.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof BigDecimal pnl) {
                setText(FORMAT.format(pnl));
                if (pnl.signum() > 0) {
                    setForeground(isSelected ? Color.WHITE : positiveColor);
                } else if (pnl.signum() < 0) {
                    setForeground(isSelected ? Color.WHITE : negativeColor);
                } else {
                    setForeground(isSelected ? Color.WHITE : table.getForeground());
                }
            }
            return this;
        }
    }
}
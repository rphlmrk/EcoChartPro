package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.ui.toolbar.components.SymbolProgressCache;
import com.EcoChartPro.ui.toolbar.components.SymbolProgressCache.SymbolProgress;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

public class SymbolSearchDialog extends JDialog {

    private final JLabel inputLabel;
    private final JList<SymbolProgress> resultsList;
    private final DefaultListModel<SymbolProgress> listModel;
    private final boolean isReplayMode;
    private final EventListenerList listenerList = new EventListenerList();

    public SymbolSearchDialog(Frame owner, boolean isReplayMode) {
        super(owner, false);
        this.isReplayMode = isReplayMode;
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // Transparent background
        setFocusableWindowState(false); // *** This remains essential ***

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIManager.getColor("Panel.background"));
        mainPanel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        inputLabel = new JLabel();
        inputLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        inputLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        mainPanel.add(inputLabel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        resultsList = new JList<>(listModel);
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setCellRenderer(new SimpleSymbolRenderer());

        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    confirmSelection();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setPreferredSize(new Dimension(250, 300));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Key bindings have been removed from here and moved to KeyboardShortcutManager
        setContentPane(mainPanel);
        pack();
    }
    
    public void confirmSelection() {
        ChartDataSource selected = getSelectedSymbol();
        if (selected != null) {
            fireActionPerformed(selected);
        } else {
             // If nothing is selected, treat it as a cancellation
            fireActionPerformed(null);
        }
    }

    public void showDialog(Component parent, String initialInput) {
        if (parent != null) {
            setLocation(parent.getLocationOnScreen().x + 20, parent.getLocationOnScreen().y + 20);
        }
        updateSearch(initialInput);
        setVisible(true);
    }

    public void updateSearch(String input) {
        inputLabel.setText("Find symbol: " + input);

        listModel.clear();
        if (!input.isEmpty()) {
            // [FIX] This filtering logic is now inside SymbolSelectionPanel, but we replicate it here for simplicity.
            // A better long-term solution might be to share the filter logic.
            String lowerCaseQuery = input.toLowerCase().trim();
            List<SymbolProgress> filtered = SymbolProgressCache.getInstance().getProgressForAllSymbols().stream()
                .filter(info -> {
                    boolean isLocalData = info.source().dbPath() != null;
                    return isReplayMode == isLocalData;
                })
                .filter(info -> info.displayName().toLowerCase().contains(lowerCaseQuery))
                .collect(Collectors.toList());

            listModel.addAll(filtered);
        }

        if (!listModel.isEmpty()) {
            resultsList.setSelectedIndex(0);
        }
    }
    
    public void moveSelectionUp() {
        int currentIndex = resultsList.getSelectedIndex();
        if (currentIndex > 0) {
            resultsList.setSelectedIndex(currentIndex - 1);
            resultsList.ensureIndexIsVisible(currentIndex - 1);
        }
    }

    public void moveSelectionDown() {
        int currentIndex = resultsList.getSelectedIndex();
        if (currentIndex < listModel.getSize() - 1) {
            resultsList.setSelectedIndex(currentIndex + 1);
            resultsList.ensureIndexIsVisible(currentIndex + 1);
        }
    }

    public ChartDataSource getSelectedSymbol() {
        SymbolProgress selected = resultsList.getSelectedValue();
        return (selected != null) ? selected.source() : null;
    }

    public void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    protected void fireActionPerformed(ChartDataSource source) {
        ActionEvent e = new ActionEvent(source, ActionEvent.ACTION_PERFORMED, "symbolSelected");
        for (ActionListener listener : listenerList.getListeners(ActionListener.class)) {
            listener.actionPerformed(e);
        }
    }

    private static class SimpleSymbolRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SymbolProgress info) {
                label.setText(info.displayName());
            }
            label.setBorder(new EmptyBorder(5, 5, 5, 5));
            return label;
        }
    }
}
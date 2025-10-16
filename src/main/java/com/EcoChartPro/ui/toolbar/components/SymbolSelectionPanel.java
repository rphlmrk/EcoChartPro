package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A self-contained panel for selecting a symbol.
 * It displays a searchable list of available data sources along with the
 * backtesting progress for each symbol, retrieved from a cache.
 */
public class SymbolSelectionPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(SymbolSelectionPanel.class);
    private final EventListenerList listenerList = new EventListenerList();
    private final List<SymbolProgressCache.SymbolProgressInfo> progressInfoList;
    private final DefaultListModel<SymbolProgressCache.SymbolProgressInfo> listModel = new DefaultListModel<>();
    private final boolean isReplayMode;

    public SymbolSelectionPanel(boolean isReplayMode) {
        this.isReplayMode = isReplayMode;
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("Panel.background"));

        // 1. Data Loading from cache with filtering and sorting based on mode
        this.progressInfoList = SymbolProgressCache.getInstance().getAllProgressInfo().stream()
                .filter(info -> {
                    boolean isLocalData = info.source().dbPath() != null;
                    // If in replay mode, we want local data. If not in replay mode, we want non-local (live) data.
                    return isReplayMode == isLocalData;
                })
                .sorted(Comparator.comparing(info -> info.source().displayName()))
                .collect(Collectors.toList());
        listModel.addAll(this.progressInfoList);

        // 2. UI Components
        JTextField searchField = createSearchField();
        JList<SymbolProgressCache.SymbolProgressInfo> suggestionsList = createSuggestionsList();

        JScrollPane scrollPane = new JScrollPane(suggestionsList);
        scrollPane.setBorder(null);

        add(searchField, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 350));
    }

    private JTextField createSearchField() {
        JTextField searchField = new JTextField();
        searchField.setBackground(UIManager.getColor("TextField.background"));
        searchField.setForeground(UIManager.getColor("TextField.foreground"));
        searchField.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEmptyBorder(5, 5, 5, 5), "Search Symbol"));

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String searchText = searchField.getText().toLowerCase().trim();
                List<SymbolProgressCache.SymbolProgressInfo> filtered = progressInfoList.stream()
                        .filter(info -> info.source().displayName().toLowerCase().contains(searchText))
                        .collect(Collectors.toList());
                listModel.clear();
                listModel.addAll(filtered);
            }
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });
        return searchField;
    }

    private JList<SymbolProgressCache.SymbolProgressInfo> createSuggestionsList() {
        JList<SymbolProgressCache.SymbolProgressInfo> suggestionsList = new JList<>(listModel);
        suggestionsList.setCellRenderer(new SymbolProgressRenderer(this.isReplayMode));
        suggestionsList.setBackground(UIManager.getColor("List.background"));
        suggestionsList.setSelectionBackground(UIManager.getColor("List.selectionBackground"));

        suggestionsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    SymbolProgressCache.SymbolProgressInfo selected = suggestionsList.getSelectedValue();
                    if (selected != null) {
                        fireActionPerformed(selected.source());
                    }
                }
            }
        });
        return suggestionsList;
    }

    public void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    public void removeActionListener(ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    protected void fireActionPerformed(ChartDataSource source) {
        Object[] listeners = listenerList.getListenerList();
        ActionEvent e = new ActionEvent(source, ActionEvent.ACTION_PERFORMED, "symbolSelected");
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ActionListener.class) {
                ((ActionListener) listeners[i + 1]).actionPerformed(e);
            }
        }
    }

    /**
     * Inner class for rendering the JList items with a progress bar.
     */
    private static class SymbolProgressRenderer extends JPanel implements ListCellRenderer<SymbolProgressCache.SymbolProgressInfo> {
        private final JLabel label = new JLabel();
        private final JProgressBar progressBar = new JProgressBar(0, 100);
        private final boolean isReplayMode;

        SymbolProgressRenderer(boolean isReplayMode) {
            this.isReplayMode = isReplayMode;
            setLayout(new BorderLayout(10, 0));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            label.setFont(UIManager.getFont("List.font"));
            progressBar.setStringPainted(true);
            progressBar.setPreferredSize(new Dimension(80, progressBar.getPreferredSize().height));

            add(label, BorderLayout.CENTER);
            add(progressBar, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends SymbolProgressCache.SymbolProgressInfo> list, SymbolProgressCache.SymbolProgressInfo value, int index, boolean isSelected, boolean cellHasFocus) {
            label.setText(value.source().displayName());
            
            if (isReplayMode) {
                progressBar.setValue(value.progressPercent());
                progressBar.setString(value.progressPercent() + "%");
                progressBar.setVisible(true);
            } else {
                progressBar.setVisible(false);
            }

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            
            return this;
        }
    }
}
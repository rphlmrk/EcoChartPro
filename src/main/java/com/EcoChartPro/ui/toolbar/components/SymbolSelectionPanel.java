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
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * [MODIFIED] A self-contained panel for selecting a symbol.
 * For live charting, it provides a searchable and filterable list of all available data sources.
 * For replay mode, it shows local files with their backtesting progress.
 */
public class SymbolSelectionPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(SymbolSelectionPanel.class);
    private final EventListenerList listenerList = new EventListenerList();
    private final List<SymbolProgressCache.SymbolProgressInfo> fullDataList;
    private final DefaultListModel<SymbolProgressCache.SymbolProgressInfo> listModel = new DefaultListModel<>();
    private final boolean isReplayMode;
    
    // UI components for filtering
    private final JTextField searchField;
    // [FIX] Replaced JComboBox with a ButtonGroup to manage toggle buttons for a stable UI.
    private final ButtonGroup providerButtonGroup = new ButtonGroup();

    public SymbolSelectionPanel(boolean isReplayMode) {
        this.isReplayMode = isReplayMode;
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("Panel.background"));

        // 1. Data Loading from cache with filtering based on mode
        this.fullDataList = SymbolProgressCache.getInstance().getAllProgressInfo().stream()
                .filter(info -> {
                    boolean isLocalData = info.source().dbPath() != null;
                    // If in replay mode, we want local data. If not in replay mode, we want non-local (live) data.
                    return isReplayMode == isLocalData;
                })
                .sorted(Comparator.comparing(info -> info.source().displayName()))
                .collect(Collectors.toList());
        listModel.addAll(this.fullDataList);

        // 2. UI Components
        searchField = createSearchField();
        JList<SymbolProgressCache.SymbolProgressInfo> suggestionsList = createSuggestionsList();

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(searchField, BorderLayout.CENTER);
        // Only show the provider filter when not in replay mode
        if (!isReplayMode) {
            JPanel providerFilterPanel = createProviderFilterPanel();
            topPanel.add(providerFilterPanel, BorderLayout.NORTH);
        }

        JScrollPane scrollPane = new JScrollPane(suggestionsList);
        scrollPane.setBorder(null);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 350));
    }

    /**
     * [NEW] Creates a panel with toggle buttons for filtering by exchange/provider.
     * This replaces the flickering JComboBox.
     */
    private JPanel createProviderFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEmptyBorder(5, 5, 0, 5), "Exchange"));

        // Add the "All" button first and select it
        JToggleButton allButton = new JToggleButton("All", true);
        allButton.setActionCommand("All");
        allButton.setMargin(new Insets(2, 5, 2, 5));
        allButton.addActionListener(e -> filterList());
        providerButtonGroup.add(allButton);
        panel.add(allButton);

        // Populate with providers found in our live data list
        List<String> providers = fullDataList.stream()
                .map(info -> info.source().providerName())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        for (String provider : providers) {
            JToggleButton providerButton = new JToggleButton(provider);
            providerButton.setActionCommand(provider);
            providerButton.setMargin(new Insets(2, 5, 2, 5));
            providerButton.addActionListener(e -> filterList());
            providerButtonGroup.add(providerButton);
            panel.add(providerButton);
        }
        return panel;
    }

    private JTextField createSearchField() {
        JTextField field = new JTextField();
        field.setBackground(UIManager.getColor("TextField.background"));
        field.setForeground(UIManager.getColor("TextField.foreground"));
        field.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEmptyBorder(5, 5, 5, 5), "Search Symbol"));

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterList(); }
            @Override public void removeUpdate(DocumentEvent e) { filterList(); }
            @Override public void changedUpdate(DocumentEvent e) { filterList(); }
        });
        return field;
    }

    private void filterList() {
        String searchText = searchField.getText().toLowerCase().trim();
        
        // [FIX] Get the selected provider from the button group's selection model.
        ButtonModel selectedModel = providerButtonGroup.getSelection();
        String selectedProvider = (selectedModel != null) ? selectedModel.getActionCommand() : "All";

        List<SymbolProgressCache.SymbolProgressInfo> filtered = fullDataList.stream()
                .filter(info -> {
                    boolean matchesProvider = "All".equals(selectedProvider) || selectedProvider.equals(info.source().providerName());
                    boolean matchesSearch = info.source().displayName().toLowerCase().contains(searchText);
                    return matchesProvider && matchesSearch;
                })
                .collect(Collectors.toList());
        
        listModel.clear();
        listModel.addAll(filtered);
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
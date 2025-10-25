package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * [MODIFIED] A self-contained panel for selecting a symbol.
 * For live charting, it provides a searchable and filterable list of all available data sources.
 * For replay mode, it shows local files with their backtesting progress.
 */
public class SymbolSelectionPanel extends JPanel implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(SymbolSelectionPanel.class);
    private final EventListenerList listenerList = new EventListenerList();
    private final List<SymbolProgressCache.SymbolProgressInfo> fullDataList;
    private final DefaultListModel<SymbolProgressCache.SymbolProgressInfo> listModel = new DefaultListModel<>();
    private final JList<SymbolProgressCache.SymbolProgressInfo> suggestionsList;
    private final boolean isReplayMode;
    
    // UI components for filtering
    private final JTextField searchField;
    private final ButtonGroup providerButtonGroup = new ButtonGroup();
    private final JToggleButton favoritesToggle;

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
        suggestionsList = createSuggestionsList();

        JPanel topPanel = new JPanel(new BorderLayout(0, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        topPanel.add(searchField, BorderLayout.CENTER);
        
        JPanel filterPanel = new JPanel(new BorderLayout());
        JPanel providerFilterPanel = createProviderFilterPanel();
        filterPanel.add(providerFilterPanel, BorderLayout.CENTER);

        favoritesToggle = new JToggleButton("Favorites ★", false);
        favoritesToggle.setMargin(new Insets(2, 5, 2, 5));
        favoritesToggle.addActionListener(e -> filterList());
        JPanel favoritesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        favoritesPanel.add(favoritesToggle);
        filterPanel.add(favoritesPanel, BorderLayout.WEST);

        topPanel.add(filterPanel, BorderLayout.SOUTH);

        JScrollPane scrollPane = new JScrollPane(suggestionsList);
        scrollPane.setBorder(null);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 350));
        
        SettingsManager.getInstance().addPropertyChangeListener("favoritesChanged", this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("favoritesChanged".equals(evt.getPropertyName())) {
            // Repaint the list to update the star icons
            suggestionsList.repaint();
            // If the favorites filter is on, we need to re-apply it
            if (favoritesToggle.isSelected()) {
                filterList();
            }
        }
    }
    
    /**
     * [NEW] Creates a panel with toggle buttons for filtering by exchange/provider.
     * This replaces the flickering JComboBox.
     */
    private JPanel createProviderFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));

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
            BorderFactory.createEmptyBorder(0, 0, 0, 0), "Search Symbol"));

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterList(); }
            @Override public void removeUpdate(DocumentEvent e) { filterList(); }
            @Override public void changedUpdate(DocumentEvent e) { filterList(); }
        });
        return field;
    }

    private void filterList() {
        String searchText = searchField.getText().toLowerCase().trim();
        
        ButtonModel selectedModel = providerButtonGroup.getSelection();
        String selectedProvider = (selectedModel != null) ? selectedModel.getActionCommand() : "All";
        boolean showOnlyFavorites = favoritesToggle.isSelected();
        SettingsManager sm = SettingsManager.getInstance();

        List<SymbolProgressCache.SymbolProgressInfo> filtered = fullDataList.stream()
                .filter(info -> {
                    boolean matchesFavorites = !showOnlyFavorites || sm.isFavoriteSymbol(info.source().symbol());
                    boolean matchesProvider = "All".equals(selectedProvider) || selectedProvider.equals(info.source().providerName());
                    boolean matchesSearch = info.source().displayName().toLowerCase().contains(searchText);
                    return matchesFavorites && matchesProvider && matchesSearch;
                })
                .collect(Collectors.toList());
        
        listModel.clear();
        listModel.addAll(filtered);
    }

    private JList<SymbolProgressCache.SymbolProgressInfo> createSuggestionsList() {
        JList<SymbolProgressCache.SymbolProgressInfo> list = new JList<>(listModel);
        list.setCellRenderer(new SymbolProgressRenderer(this.isReplayMode));
        list.setBackground(UIManager.getColor("List.background"));
        list.setSelectionBackground(UIManager.getColor("List.selectionBackground"));

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index == -1) return;

                SymbolProgressCache.SymbolProgressInfo info = list.getModel().getElementAt(index);
                if (info == null) return;
                
                // Define the clickable area for the star icon (at the start of the cell)
                Rectangle starBounds = new Rectangle(5, 5, 24, 24);
                Rectangle cellBounds = list.getCellBounds(index, index);
                Point relativeClick = new Point(e.getX() - cellBounds.x, e.getY() - cellBounds.y);

                // Check if the click was on the star
                if (starBounds.contains(relativeClick)) {
                    SettingsManager sm = SettingsManager.getInstance();
                    String symbol = info.source().symbol();
                    if (sm.isFavoriteSymbol(symbol)) {
                        sm.removeFavoriteSymbol(symbol);
                    } else {
                        sm.addFavoriteSymbol(symbol);
                    }
                    e.consume(); // Prevent the list selection from changing
                } else {
                    // Regular click on the item
                    fireActionPerformed(info.source());
                }
            }
        });
        return list;
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
     * Inner class for rendering the JList items with a progress bar and favorite star.
     */
    private static class SymbolProgressRenderer extends JPanel implements ListCellRenderer<SymbolProgressCache.SymbolProgressInfo> {
        private final JLabel label = new JLabel();
        private final JLabel starLabel = new JLabel();
        private final JProgressBar progressBar = new JProgressBar(0, 100);
        private final boolean isReplayMode;

        private static final Icon STAR_FILLED = UITheme.getIcon(UITheme.Icons.STAR_FILLED, 16, 16, new Color(255, 193, 7));
        private static final Icon STAR_EMPTY = UITheme.getIcon(UITheme.Icons.STAR_EMPTY, 16, 16, UIManager.getColor("Label.disabledForeground"));

        SymbolProgressRenderer(boolean isReplayMode) {
            this.isReplayMode = isReplayMode;
            setLayout(new BorderLayout(10, 0));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            label.setFont(UIManager.getFont("List.font"));
            progressBar.setStringPainted(true);
            progressBar.setPreferredSize(new Dimension(80, progressBar.getPreferredSize().height));
            
            starLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

            add(starLabel, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
            add(progressBar, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends SymbolProgressCache.SymbolProgressInfo> list, SymbolProgressCache.SymbolProgressInfo value, int index, boolean isSelected, boolean cellHasFocus) {
            label.setText(value.source().displayName());
            
            boolean isFavorite = SettingsManager.getInstance().isFavoriteSymbol(value.source().symbol());
            starLabel.setIcon(isFavorite ? STAR_FILLED : STAR_EMPTY);
            starLabel.setToolTipText(isFavorite ? "Remove from Favorites" : "Add to Favorites");
            
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
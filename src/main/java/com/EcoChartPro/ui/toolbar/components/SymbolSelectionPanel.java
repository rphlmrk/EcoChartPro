package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.toolbar.components.SymbolProgressCache.SymbolProgress;
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
 * A self-contained panel for selecting a symbol.
 * For live charting, it provides a searchable and filterable list of all available data sources.
 * For replay mode, it shows local files with their backtesting progress.
 */
public class SymbolSelectionPanel extends JPanel implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(SymbolSelectionPanel.class);
    private final EventListenerList listenerList = new EventListenerList();
    private final DefaultListModel<SymbolProgress> listModel = new DefaultListModel<>();
    private final JList<SymbolProgress> suggestionsList;
    private final boolean isReplayMode;
    
    // UI components for filtering
    private final JTextField searchField;
    private final ButtonGroup providerButtonGroup = new ButtonGroup();
    private final JToggleButton favoritesToggle;

    public SymbolSelectionPanel(boolean isReplayMode) {
        this.isReplayMode = isReplayMode;
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("Panel.background"));

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
        
        filterList(); // Initial population
        SettingsService.getInstance().addPropertyChangeListener(this);
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
     * Creates a panel with toggle buttons for filtering by exchange/provider.
     * This replaces the flickering JComboBox.
     */
    private JPanel createProviderFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));

        JToggleButton allButton = new JToggleButton("All", true);
        allButton.setActionCommand("All");
        allButton.setMargin(new Insets(2, 5, 2, 5));
        allButton.addActionListener(e -> filterList());
        providerButtonGroup.add(allButton);
        panel.add(allButton);

        List<String> providers = SymbolProgressCache.getInstance().getProgressForAllSymbols().stream()
                .filter(info -> (isReplayMode && info.source().dbPath() != null) || (!isReplayMode && info.source().dbPath() == null))
                .map(SymbolProgress::providerName)
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
        String searchText = searchField.getText();
        ButtonModel selectedModel = providerButtonGroup.getSelection();
        String selectedProvider = (selectedModel != null) ? selectedModel.getActionCommand() : "All";
        boolean showOnlyFavorites = favoritesToggle.isSelected();
        
        String lowerCaseQuery = (searchText != null) ? searchText.toLowerCase().trim() : "";
        SettingsService sm = SettingsService.getInstance();

        List<SymbolProgress> filtered = SymbolProgressCache.getInstance().getProgressForAllSymbols().stream()
                .filter(info -> {
                    boolean isLocalData = info.source().dbPath() != null;
                    return isReplayMode == isLocalData;
                })
                .filter(info -> !showOnlyFavorites || sm.isFavoriteSymbol(info.symbol()))
                .filter(info -> "All".equalsIgnoreCase(selectedProvider) || selectedProvider.equals(info.providerName()))
                .filter(info -> lowerCaseQuery.isEmpty() || info.displayName().toLowerCase().contains(lowerCaseQuery))
                .sorted(Comparator.comparing((SymbolProgress info) -> !sm.isFavoriteSymbol(info.symbol()))
                                  .thenComparing(SymbolProgress::displayName))
                .collect(Collectors.toList());
        
        listModel.clear();
        listModel.addAll(filtered);
    }

    private JList<SymbolProgress> createSuggestionsList() {
        JList<SymbolProgress> list = new JList<>(listModel);
        list.setCellRenderer(new SymbolProgressRenderer(this.isReplayMode));
        list.setBackground(UIManager.getColor("List.background"));
        list.setSelectionBackground(UIManager.getColor("List.selectionBackground"));

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index == -1) return;

                SymbolProgress info = list.getModel().getElementAt(index);
                if (info == null) return;
                
                Rectangle starBounds = new Rectangle(5, 5, 24, 24);
                Rectangle cellBounds = list.getCellBounds(index, index);
                Point relativeClick = new Point(e.getX() - cellBounds.x, e.getY() - cellBounds.y);

                if (starBounds.contains(relativeClick)) {
                    SettingsService sm = SettingsService.getInstance();
                    String symbol = info.symbol();
                    if (sm.isFavoriteSymbol(symbol)) {
                        sm.removeFavoriteSymbol(symbol);
                    } else {
                        sm.addFavoriteSymbol(symbol);
                    }
                    e.consume(); 
                } else {
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
    private static class SymbolProgressRenderer extends JPanel implements ListCellRenderer<SymbolProgress> {
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
        public Component getListCellRendererComponent(JList<? extends SymbolProgress> list, SymbolProgress value, int index, boolean isSelected, boolean cellHasFocus) {
            label.setText(value.displayName());
            
            boolean isFavorite = SettingsService.getInstance().isFavoriteSymbol(value.symbol());
            starLabel.setIcon(isFavorite ? STAR_FILLED : STAR_EMPTY);
            starLabel.setToolTipText(isFavorite ? "Remove from Favorites" : "Add to Favorites");
            
            if (isReplayMode) {
                int progress = (int) Math.round(value.progressPercentage());
                progressBar.setValue(progress);
                progressBar.setString(progress + "%");
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
package com.EcoChartPro.ui.trading;

import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.settings.Checklist;
import com.EcoChartPro.core.settings.ChecklistManager;
import com.EcoChartPro.core.settings.MistakeManager;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.EmotionalState;
import com.EcoChartPro.model.PlanAdherence;
import com.EcoChartPro.model.SetupQuality;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.components.TagSuggestionPanel;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class JournalEntryDialog extends JDialog {

    private final Trade trade;
    private final WorkspaceContext context;

    private JTextArea notesArea;
    private JTextField tagsField;
    private JComboBox<PlanAdherence> planAdherenceComboBox;
    private JComboBox<EmotionalState> emotionalStateComboBox;
    private JComboBox<SetupQuality> setupQualityComboBox;
    private JComboBox<Checklist> checklistComboBox;
    private JList<String> mistakesList;
    private DefaultListModel<String> mistakesListModel;
    private JTextArea lessonsLearnedArea;

    private static final Checklist NO_CHECKLIST_SELECTED = new Checklist(null, "None / Not Applicable", new ArrayList<>());

    public JournalEntryDialog(Frame owner, Trade trade, WorkspaceContext context) {
        super(owner, "Edit Journal Entry for Trade #" + trade.id().toString().substring(0, 8), true);
        this.trade = trade;
        this.context = context;

        setSize(550, 650);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Notes & Tags", createNotesPanel());
        tabbedPane.addTab("Guided Reflection", createReflectionPanel());

        add(tabbedPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        populateChecklists();
        loadInitialData();
    }

    private void populateChecklists() {
        checklistComboBox.addItem(NO_CHECKLIST_SELECTED);
        List<Checklist> checklists = ChecklistManager.getInstance().getChecklists();
        for (Checklist checklist : checklists) {
            checklistComboBox.addItem(checklist);
        }
    }

    private void loadInitialData() {
        notesArea.setText(trade.notes());
        if (trade.tags() != null) {
            tagsField.setText(String.join(", ", trade.tags()));
        }

        planAdherenceComboBox.setSelectedItem(trade.planAdherence() != null ? trade.planAdherence() : PlanAdherence.NOT_RATED);
        emotionalStateComboBox.setSelectedItem(trade.emotionalState() != null ? trade.emotionalState() : EmotionalState.NEUTRAL);
        setupQualityComboBox.setSelectedItem(trade.setupQuality() != null ? trade.setupQuality() : SetupQuality.NOT_RATED);

        if (trade.checklistId() != null) {
            UUID idToFind = trade.checklistId();
            for (int i = 0; i < checklistComboBox.getItemCount(); i++) {
                Checklist item = checklistComboBox.getItemAt(i);
                if (item.id() != null && item.id().equals(idToFind)) {
                    checklistComboBox.setSelectedIndex(i);
                    break;
                }
            }
        } else {
            checklistComboBox.setSelectedItem(NO_CHECKLIST_SELECTED);
        }

        if (trade.identifiedMistakes() != null) {
            mistakesListModel.addAll(trade.identifiedMistakes());
        }
        lessonsLearnedArea.setText(trade.lessonsLearned());
    }

    private JPanel createReflectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("Plan Adherence:"), gbc);
        gbc.gridx = 1;
        planAdherenceComboBox = new JComboBox<>(PlanAdherence.values());
        panel.add(planAdherenceComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Emotional State:"), gbc);
        gbc.gridx = 1;
        emotionalStateComboBox = new JComboBox<>(EmotionalState.values());
        panel.add(emotionalStateComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Setup Quality:"), gbc);
        gbc.gridx = 1;
        setupQualityComboBox = new JComboBox<>(SetupQuality.values());
        panel.add(setupQualityComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Strategy / Checklist:"), gbc);
        gbc.gridx = 1;
        checklistComboBox = new JComboBox<>();
        panel.add(checklistComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.5;
        panel.add(createMistakesPanel(), gbc);
        
        gbc.gridy = 5;
        panel.add(createLessonsLearnedPanel(), gbc);

        return panel;
    }
    
    private void handleSave() {
        trade.setNotes(notesArea.getText());
        trade.setTags(Arrays.stream(tagsField.getText().split(","))
                            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        
        trade.setPlanAdherence((PlanAdherence) planAdherenceComboBox.getSelectedItem());
        trade.setEmotionalState((EmotionalState) emotionalStateComboBox.getSelectedItem());
        trade.setSetupQuality((SetupQuality) setupQualityComboBox.getSelectedItem());

        Checklist selectedChecklist = (Checklist) checklistComboBox.getSelectedItem();
        if (selectedChecklist != null && selectedChecklist.id() != null) {
            trade.setChecklistId(selectedChecklist.id());
        } else {
            trade.setChecklistId(null);
        }
        
        ArrayList<String> mistakes = new ArrayList<>();
        for (int i = 0; i < mistakesListModel.size(); i++) {
            mistakes.add(mistakesListModel.getElementAt(i));
        }
        trade.setIdentifiedMistakes(mistakes);
        
        trade.setLessonsLearned(lessonsLearnedArea.getText());

        context.getPaperTradingService().updateTradeJournalReflection(trade);
        dispose();
    }
    
    private JPanel createNotesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(5, 0, 5, 0);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        notesArea = new JTextArea(10, 40);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane notesScrollPane = new JScrollPane(notesArea);
        notesScrollPane.setBorder(BorderFactory.createTitledBorder("General Notes"));
        panel.add(notesScrollPane, gbc);

        gbc.gridy = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel tagsPanel = new JPanel(new BorderLayout(5, 0));
        tagsPanel.add(new JLabel("Tags:"), BorderLayout.WEST);
        tagsField = new JTextField();
        tagsPanel.add(tagsField, BorderLayout.CENTER);
        tagsPanel.setBorder(BorderFactory.createTitledBorder("Custom Tags (comma-separated)"));
        panel.add(tagsPanel, gbc);

        gbc.gridy = 2;
        JPanel suggestionsContainer = new JPanel();
        suggestionsContainer.setLayout(new BoxLayout(suggestionsContainer, BoxLayout.Y_AXIS));

        List<String> timeframeTags = List.of("HTF", "MTF", "LTF");
        TagSuggestionPanel timeframePanel = new TagSuggestionPanel("Timeframe", timeframeTags);
        timeframePanel.addPropertyChangeListener("tagClicked", this::handleTagSuggestion);
        suggestionsContainer.add(timeframePanel);

        List<String> marketConditionTags = List.of("Trending", "Ranging", "Volatile", "Choppy");
        TagSuggestionPanel marketConditionPanel = new TagSuggestionPanel("Market Condition", marketConditionTags);
        marketConditionPanel.addPropertyChangeListener("tagClicked", this::handleTagSuggestion);
        suggestionsContainer.add(marketConditionPanel);

        panel.add(suggestionsContainer, gbc);

        return panel;
    }
    
    private void handleTagSuggestion(PropertyChangeEvent evt) {
        String newTag = (String) evt.getNewValue();
        String currentText = tagsField.getText().trim();

        Set<String> existingTags = new LinkedHashSet<>();
        if (!currentText.isEmpty()) {
            Arrays.stream(currentText.split("\\s*,\\s*"))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .forEach(existingTags::add);
        }

        existingTags.add(newTag);
        tagsField.setText(String.join(", ", existingTags));
    }

    private JPanel createMistakesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Identified Mistakes"));
        mistakesListModel = new DefaultListModel<>();
        mistakesList = new JList<>(mistakesListModel);
        panel.add(new JScrollPane(mistakesList), BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        List<String> mistakes = MistakeManager.getInstance().getMistakes();
        JComboBox<String> mistakeComboBox = new JComboBox<>(mistakes.toArray(new String[0]));
        inputPanel.add(mistakeComboBox, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> {
            String selectedMistake = (String) mistakeComboBox.getSelectedItem();
            if (selectedMistake != null && !mistakesListModel.contains(selectedMistake)) {
                mistakesListModel.addElement(selectedMistake);
            }
        });

        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> {
            int selectedIndex = mistakesList.getSelectedIndex();
            if (selectedIndex != -1) {
                mistakesListModel.remove(selectedIndex);
            }
        });

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        panel.add(inputPanel, BorderLayout.SOUTH);
        return panel;
    }
    
    private JPanel createLessonsLearnedPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createTitledBorder("What could be improved? / Lessons Learned"));
        lessonsLearnedArea = new JTextArea(5, 30);
        lessonsLearnedArea.setLineWrap(true);
        lessonsLearnedArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(lessonsLearnedArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> handleSave());
        panel.add(cancelButton);
        panel.add(saveButton);
        return panel;
    }
}
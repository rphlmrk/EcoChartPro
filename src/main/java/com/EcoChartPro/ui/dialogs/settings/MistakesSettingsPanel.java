package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.MistakeManager;

import javax.swing.*;
import java.awt.*;

public class MistakesSettingsPanel extends JPanel {

    private final MistakeManager mistakeManager;
    private final DefaultListModel<String> listModel;
    private final JList<String> mistakeList;

    public MistakesSettingsPanel(MistakeManager mistakeManager) {
        this.mistakeManager = mistakeManager;
        this.listModel = new DefaultListModel<>();
        this.mistakeList = new JList<>(listModel);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        initUI();
        loadMistakes();
    }

    private void initUI() {
        // --- Title ---
        JLabel titleLabel = new JLabel("Manage Custom Mistakes");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        add(titleLabel, BorderLayout.NORTH);

        // --- List Panel ---
        mistakeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(mistakeList), BorderLayout.CENTER);

        // --- Button Panel ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add (+)");
        JButton removeButton = new JButton("Remove (-)");
        JButton renameButton = new JButton("Rename");

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(renameButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // --- Actions ---
        addButton.addActionListener(e -> addMistake());
        removeButton.addActionListener(e -> removeMistake());
        renameButton.addActionListener(e -> renameMistake());
    }
    
    private void loadMistakes() {
        listModel.clear();
        listModel.addAll(mistakeManager.getMistakes());
    }

    private void addMistake() {
        String newMistake = JOptionPane.showInputDialog(this, "Enter the new mistake:", "Add Mistake", JOptionPane.PLAIN_MESSAGE);
        if (newMistake != null && !newMistake.isBlank()) {
            mistakeManager.addMistake(newMistake);
            loadMistakes(); // Reload to reflect changes
        }
    }

    private void removeMistake() {
        int selectedIndex = mistakeList.getSelectedIndex();
        if (selectedIndex != -1) {
            mistakeManager.deleteMistake(selectedIndex);
            loadMistakes();
        } else {
            JOptionPane.showMessageDialog(this, "Please select a mistake to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void renameMistake() {
        int selectedIndex = mistakeList.getSelectedIndex();
        if (selectedIndex != -1) {
            String currentMistake = listModel.getElementAt(selectedIndex);
            String newMistake = (String) JOptionPane.showInputDialog(this, "Enter the new name:", "Rename Mistake", JOptionPane.PLAIN_MESSAGE, null, null, currentMistake);
            if (newMistake != null && !newMistake.isBlank()) {
                mistakeManager.updateMistake(selectedIndex, newMistake);
                loadMistakes();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please select a mistake to rename.", "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }

    // This panel saves changes immediately, so this method is empty.
    public void applyChanges() {}
}
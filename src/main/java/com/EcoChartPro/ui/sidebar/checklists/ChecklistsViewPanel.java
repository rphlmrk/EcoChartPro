package com.EcoChartPro.ui.sidebar.checklists;

import com.EcoChartPro.core.settings.Checklist;
import com.EcoChartPro.core.settings.ChecklistItem;
import com.EcoChartPro.core.settings.ChecklistManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ChecklistsViewPanel extends JPanel {

    private final JList<Checklist> checklistJList;
    private final DefaultListModel<Checklist> checklistListModel;
    private final JTextArea itemsTextArea;

    public ChecklistsViewPanel() {
        super(new BorderLayout(10, 10));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // --- Header ---
        JLabel headerLabel = new JLabel("My Checklists");
        headerLabel.setFont(UIManager.getFont("app.font.widget_title"));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // --- Main Split Pane ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setOpaque(false);
        splitPane.setResizeWeight(0.4);
        splitPane.setBorder(null);

        // --- Top: List of Checklists ---
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setOpaque(false);
        checklistListModel = new DefaultListModel<>();
        checklistJList = new JList<>(checklistListModel);
        checklistJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        checklistJList.addListSelectionListener(e -> updateItemsView());
        JScrollPane listScrollPane = new JScrollPane(checklistJList);
        listPanel.add(listScrollPane, BorderLayout.CENTER);

        // --- Bottom: Items of Selected Checklist ---
        JPanel itemsPanel = new JPanel(new BorderLayout());
        itemsPanel.setOpaque(false);
        itemsPanel.setBorder(BorderFactory.createTitledBorder("Checklist Items"));
        itemsTextArea = new JTextArea();
        itemsTextArea.setEditable(false);
        itemsTextArea.setLineWrap(true);
        itemsTextArea.setWrapStyleWord(true);
        JScrollPane itemsScrollPane = new JScrollPane(itemsTextArea);
        itemsPanel.add(itemsScrollPane, BorderLayout.CENTER);

        splitPane.setTopComponent(listPanel);
        splitPane.setBottomComponent(itemsPanel);

        // --- Button Panel ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        JButton newButton = new JButton("New");
        newButton.addActionListener(e -> handleNew());
        JButton editButton = new JButton("Edit");
        editButton.addActionListener(e -> handleEdit());
        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> handleDelete());

        buttonPanel.add(newButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);

        // --- Assembly ---
        add(headerLabel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        refreshChecklistList();
    }

    private void refreshChecklistList() {
        checklistListModel.clear();
        checklistListModel.addAll(ChecklistManager.getInstance().getChecklists());
        if (!checklistListModel.isEmpty()) {
            checklistJList.setSelectedIndex(0);
        }
        updateItemsView();
    }

    private void updateItemsView() {
        Checklist selected = checklistJList.getSelectedValue();
        if (selected == null) {
            itemsTextArea.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (ChecklistItem item : selected.items()) {
            sb.append("• ").append(item.text()).append("\n");
            // Check if description is not null and not blank
            if (item.description() != null && !item.description().trim().isEmpty()) {
                // Indent the description and add it line by line
                String[] lines = item.description().split("\\R"); // Split on any newline sequence
                for (String line : lines) {
                    // Add an indentation for sub-items
                    sb.append("    ").append(line).append("\n");
                }
                // Add a blank line after each item with a description for better separation
                sb.append("\n");
            }
        }
        itemsTextArea.setText(sb.toString().trim());
    }

    private void handleNew() {
        ChecklistEditDialog dialog = new ChecklistEditDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            "Create New Checklist",
            null
        );
        dialog.setVisible(true);
        dialog.getChecklist().ifPresent(checklist -> {
            ChecklistManager.getInstance().addChecklist(checklist);
            refreshChecklistList();
        });
    }

    private void handleEdit() {
        Checklist selected = checklistJList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a checklist to edit.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ChecklistEditDialog dialog = new ChecklistEditDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            "Edit Checklist",
            selected
        );
        dialog.setVisible(true);
        dialog.getChecklist().ifPresent(checklist -> {
            ChecklistManager.getInstance().updateChecklist(checklist);
            refreshChecklistList();
        });
    }

    private void handleDelete() {
        Checklist selected = checklistJList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a checklist to delete.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete the '" + selected.name() + "' checklist?",
            "Confirm Deletion",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (choice == JOptionPane.YES_OPTION) {
            ChecklistManager.getInstance().deleteChecklist(selected.id());
            refreshChecklistList();
        }
    }
}
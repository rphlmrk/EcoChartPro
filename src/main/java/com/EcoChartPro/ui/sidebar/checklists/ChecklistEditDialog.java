package com.EcoChartPro.ui.sidebar.checklists;

import com.EcoChartPro.core.settings.Checklist;
import com.EcoChartPro.core.settings.ChecklistItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

public class ChecklistEditDialog extends JDialog {

    private final JTextField nameField;
    private final JList<ChecklistItem> itemsList;
    private final DefaultListModel<ChecklistItem> itemsListModel;
    private Optional<Checklist> result = Optional.empty();
    private final UUID checklistId;

    public ChecklistEditDialog(Frame owner, String title, Checklist existingChecklist) {
        super(owner, title, true);
        this.checklistId = (existingChecklist != null) ? existingChecklist.id() : UUID.randomUUID();

        setLayout(new BorderLayout(10, 10));
        setSize(450, 400); // Increased size for better layout
        setLocationRelativeTo(owner);
        getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // --- Name Panel ---
        JPanel namePanel = new JPanel(new BorderLayout(5, 0));
        namePanel.add(new JLabel("Name:"), BorderLayout.WEST);
        nameField = new JTextField();
        namePanel.add(nameField, BorderLayout.CENTER);

        // --- Items Panel ---
        JPanel itemsPanel = new JPanel(new BorderLayout());
        itemsPanel.setBorder(BorderFactory.createTitledBorder("Items"));
        itemsListModel = new DefaultListModel<>();
        itemsList = new JList<>(itemsListModel);
        itemsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ChecklistItem item) {
                    label.setText(item.text());
                }
                return label;
            }
        });
        // Add double-click listener for editing
        itemsList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    handleEditItem();
                }
            }
        });
        itemsPanel.add(new JScrollPane(itemsList), BorderLayout.CENTER);

        // --- Item Buttons Panel ---
        JPanel itemButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> handleAddItem());
        JButton editButton = new JButton("Edit");
        editButton.addActionListener(e -> handleEditItem());
        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> handleRemoveItem());
        itemButtonsPanel.add(addButton);
        itemButtonsPanel.add(editButton);
        itemButtonsPanel.add(removeButton);
        itemsPanel.add(itemButtonsPanel, BorderLayout.SOUTH);

        // --- Main Buttons Panel ---
        JPanel mainButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> handleSave());
        mainButtonsPanel.add(cancelButton);
        mainButtonsPanel.add(saveButton);

        // --- Assembly ---
        add(namePanel, BorderLayout.NORTH);
        add(itemsPanel, BorderLayout.CENTER);
        add(mainButtonsPanel, BorderLayout.SOUTH);

        // --- Prefill Data ---
        if (existingChecklist != null) {
            nameField.setText(existingChecklist.name());
            itemsListModel.addAll(existingChecklist.items());
        }
    }

    private void handleAddItem() {
        showItemEditDialog(null).ifPresent(itemsListModel::addElement);
    }

    private void handleEditItem() {
        int selectedIndex = itemsList.getSelectedIndex();
        if (selectedIndex < 0) {
            return; // Nothing selected
        }
        ChecklistItem selectedItem = itemsList.getSelectedValue();
        showItemEditDialog(selectedItem).ifPresent(editedItem -> {
            itemsListModel.set(selectedIndex, editedItem);
        });
    }

    private Optional<ChecklistItem> showItemEditDialog(ChecklistItem existingItem) {
        // --- UI Components for the Dialog ---
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));

        JPanel textPanel = new JPanel(new BorderLayout(5, 5));
        textPanel.add(new JLabel("Item Text:"), BorderLayout.NORTH);
        JTextField textInput = new JTextField(30);
        textPanel.add(textInput, BorderLayout.CENTER);

        JPanel descriptionPanel = new JPanel(new BorderLayout(5, 5));
        descriptionPanel.add(new JLabel("Details / Sub-items (optional):"), BorderLayout.NORTH);
        JTextArea descriptionInput = new JTextArea(8, 30);
        descriptionInput.setLineWrap(true);
        descriptionInput.setWrapStyleWord(true);
        descriptionPanel.add(new JScrollPane(descriptionInput), BorderLayout.CENTER);

        contentPanel.add(textPanel, BorderLayout.NORTH);
        contentPanel.add(descriptionPanel, BorderLayout.CENTER);

        // --- Pre-fill data if editing ---
        if (existingItem != null) {
            textInput.setText(existingItem.text());
            descriptionInput.setText(existingItem.description());
        }

        // --- Show the custom dialog ---
        int result = JOptionPane.showConfirmDialog(this, contentPanel,
                existingItem == null ? "Add Checklist Item" : "Edit Checklist Item",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        // --- Process the result ---
        if (result == JOptionPane.OK_OPTION) {
            String text = textInput.getText().trim();
            if (text.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Item text cannot be empty.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return Optional.empty(); // Cancel the operation
            }
            String description = descriptionInput.getText();
            UUID id = (existingItem != null) ? existingItem.id() : UUID.randomUUID();
            return Optional.of(new ChecklistItem(id, text, description));
        }
        return Optional.empty();
    }

    private void handleRemoveItem() {
        int[] selectedIndices = itemsList.getSelectedIndices();
        if (selectedIndices.length == 0) return;
        // Remove from bottom to top to avoid index shifting issues
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            itemsListModel.remove(selectedIndices[i]);
        }
    }

    private void handleSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Checklist name cannot be empty.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (itemsListModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Checklist must have at least one item.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<ChecklistItem> items = new ArrayList<>();
        IntStream.range(0, itemsListModel.size()).forEach(i -> items.add(itemsListModel.getElementAt(i)));

        this.result = Optional.of(new Checklist(this.checklistId, name, items));
        dispose();
    }

    public Optional<Checklist> getChecklist() {
        return result;
    }
}
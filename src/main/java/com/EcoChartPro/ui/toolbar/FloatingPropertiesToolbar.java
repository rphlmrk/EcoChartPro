package com.EcoChartPro.ui.toolbar;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.TextObject;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A floating JDialog that serves as a contextual properties panel for selected drawings.
 */
public class FloatingPropertiesToolbar extends JDialog implements PropertyChangeListener {

    private final MainWindow mainWindow;
    private final JButton colorButton;
    private final JSpinner thicknessSpinner;
    private final JToggleButton lockButton;
    private final JButton moreOptionsButton;
    private final JButton templateButton; // New button
    private final JButton deleteButton;

    private final Icon lockOnIcon = UITheme.getIcon("/icons/lock_on.svg", 18, 18);
    private final Icon lockOffIcon = UITheme.getIcon("/icons/lock_off.svg", 18, 18);

    public FloatingPropertiesToolbar(MainWindow owner) {
        super(owner, false); // false = non-modal
        this.mainWindow = owner;

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // Transparent background
        setFocusableWindowState(false); // Prevent it from stealing focus

        JPanel contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        contentPanel.setBackground(UIManager.getColor("Panel.background"));
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 5));
        Border padding = BorderFactory.createEmptyBorder(2, 5, 2, 5);
        contentPanel.setBorder(padding);
        setContentPane(contentPanel);

        // --- Toolbar Components ---

        // Color Button
        colorButton = new JButton();
        colorButton.setToolTipText("Line Color");
        colorButton.setPreferredSize(new Dimension(24, 24));
        colorButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        colorButton.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        // Thickness Spinner
        SpinnerNumberModel thicknessModel = new SpinnerNumberModel(2, 1, 10, 1);
        thicknessSpinner = new JSpinner(thicknessModel);
        thicknessSpinner.setToolTipText("Line Thickness");
        thicknessSpinner.setPreferredSize(new Dimension(50, 24));

        // Lock Button
        lockButton = new JToggleButton(lockOffIcon);
        lockButton.setSelectedIcon(lockOnIcon);
        configureToolbarButton(lockButton);

        // More Options Button
        moreOptionsButton = new JButton(UITheme.getIcon(UITheme.Icons.SETTINGS, 18, 18));
        moreOptionsButton.setToolTipText("More Options...");
        configureToolbarButton(moreOptionsButton);

        // --- NEW: Template Button ---
        templateButton = new JButton(UITheme.getIcon(UITheme.Icons.TEMPLATE, 18, 18));
        templateButton.setToolTipText("Drawing Templates...");
        configureToolbarButton(templateButton);

        // Delete Button
        deleteButton = new JButton(UITheme.getIcon(UITheme.Icons.DELETE, 18, 18));
        deleteButton.setToolTipText("Delete Drawing");
        configureToolbarButton(deleteButton);

        contentPanel.add(colorButton);
        contentPanel.add(thicknessSpinner);
        contentPanel.add(lockButton);
        contentPanel.add(moreOptionsButton);
        contentPanel.add(templateButton);
        contentPanel.add(deleteButton);

        setupActions();
        DrawingManager.getInstance().addPropertyChangeListener("selectedDrawingChanged", this);

        pack();
    }

    @Override
    public void dispose() {
        DrawingManager.getInstance().removePropertyChangeListener("selectedDrawingChanged", this);
        super.dispose();
    }

    private void setupActions() {
        DrawingManager drawingManager = DrawingManager.getInstance();

        deleteButton.addActionListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId != null) {
                drawingManager.removeDrawing(selectedId);
                drawingManager.setSelectedDrawingId(null);
            }
        });

        thicknessSpinner.addChangeListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId == null) return;
            DrawingObject drawing = drawingManager.getDrawingById(selectedId);
            if (drawing == null || drawing instanceof TextObject || drawing.isLocked()) return;

            int newThickness = (int) thicknessSpinner.getValue();
            if ((int) drawing.stroke().getLineWidth() == newThickness) return;

            BasicStroke oldStroke = drawing.stroke();
            BasicStroke newStroke = new BasicStroke(newThickness, oldStroke.getEndCap(), oldStroke.getLineJoin(), oldStroke.getMiterLimit(), oldStroke.getDashArray(), oldStroke.getDashPhase());
            drawingManager.updateDrawing(drawing.withStroke(newStroke));
        });

        colorButton.addActionListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId == null) return;
            DrawingObject drawing = drawingManager.getDrawingById(selectedId);
            if (drawing == null || drawing.isLocked()) return;

            Consumer<Color> onColorUpdate = newColor -> {
                setCurrentColor(newColor);
                drawingManager.updateDrawing(drawing.withColor(newColor));
            };

            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(drawing.color(), onColorUpdate);
            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            popupMenu.add(colorPanel);
            popupMenu.show(colorButton, 0, colorButton.getHeight());
        });

        lockButton.addActionListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId == null) return;
            DrawingObject drawing = drawingManager.getDrawingById(selectedId);
            if (drawing == null) return;

            boolean newLockedState = lockButton.isSelected();
            drawingManager.updateDrawing(drawing.withLocked(newLockedState));
        });

        moreOptionsButton.addActionListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId == null) return;
            DrawingObject drawing = drawingManager.getDrawingById(selectedId);
            if (drawing == null || drawing.isLocked()) return;

            drawing.showSettingsDialog(this.mainWindow, drawingManager);
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("selectedDrawingChanged".equals(evt.getPropertyName())) {
            UUID selectedId = (UUID) evt.getNewValue();

            if (selectedId == null) {
                this.setVisible(false);
                ChartPanel activePanel = mainWindow.getWorkspaceManager().getActiveChartPanel();
                if (activePanel == null || activePanel.getDrawingController().getActiveTool() == null) {
                    mainWindow.getTitleBarManager().restoreIdleTitle();
                }
                return;
            }

            DrawingObject drawing = DrawingManager.getInstance().getDrawingById(selectedId);
            if (drawing == null) {
                this.setVisible(false);
                mainWindow.getTitleBarManager().restoreIdleTitle();
                return;
            }

            String lockedStatus = drawing.isLocked() ? " (Locked)" : "";
            mainWindow.getTitleBarManager().setStaticTitle("Object Selected" + lockedStatus + " | Press Delete to remove");

            setLockedState(drawing.isLocked());
            getThicknessSpinner().setEnabled(!drawing.isLocked() && !(drawing instanceof TextObject));
            setCurrentColor(drawing.color());
            if (!(drawing instanceof TextObject)) {
                getThicknessSpinner().setValue((int) drawing.stroke().getLineWidth());
            }

            ChartPanel activeChartPanel = mainWindow.getWorkspaceManager().getActiveChartPanel();
            if (activeChartPanel != null && activeChartPanel.isShowing()) {
                Point chartLocation = activeChartPanel.getLocationOnScreen();
                int x = chartLocation.x + (activeChartPanel.getWidth() / 2) - (getWidth() / 2);
                int y = chartLocation.y + 20;
                setLocation(x, y);
                setVisible(true);
            }
        }
    }

    private void configureToolbarButton(AbstractButton button) {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public JButton getColorButton() { return colorButton; }
    public JSpinner getThicknessSpinner() { return thicknessSpinner; }
    public JToggleButton getLockButton() { return lockButton; }
    public JButton getMoreOptionsButton() { return moreOptionsButton; }
    public JButton getTemplateButton() { return templateButton; }
    public JButton getDeleteButton() { return deleteButton; }

    public void setCurrentColor(Color color) {
        colorButton.setBackground(color);
    }

    public void setLockedState(boolean isLocked) {
        lockButton.setSelected(isLocked);
        lockButton.setToolTipText(isLocked ? "Unlock Drawing" : "Lock Drawing");

        boolean isEditable = !isLocked;
        colorButton.setEnabled(isEditable);
        thicknessSpinner.setEnabled(isEditable);
        moreOptionsButton.setEnabled(isEditable);
        templateButton.setEnabled(isEditable);
    }
}
package com.EcoChartPro.ui;

import com.EcoChartPro.ui.dialogs.AchievementsDialog;
import com.EcoChartPro.ui.dialogs.MarketplaceDialog;
import com.EcoChartPro.ui.dialogs.PositionSizeCalculatorDialog;
import com.EcoChartPro.ui.dialogs.SettingsDialog;
import com.EcoChartPro.ui.editor.JavaEditorDialog;

import java.math.BigDecimal;

/**
 * Manages the creation and display of UI dialogs and windows.
 */
public class UIManager {

    private final ChartWorkspacePanel ownerPanel;
    private JavaEditorDialog javaEditorDialog;
    private SettingsDialog settingsDialog;
    private PositionSizeCalculatorDialog positionSizeCalculatorDialog;
    // [REMOVED] InsightsDialog field
    private AchievementsDialog achievementsDialog;
    private MarketplaceDialog marketplaceDialog;

    public UIManager(ChartWorkspacePanel ownerPanel) {
        this.ownerPanel = ownerPanel;
    }

    public void openJavaEditor() {
        if (javaEditorDialog == null || !javaEditorDialog.isDisplayable()) {
            javaEditorDialog = new JavaEditorDialog(ownerPanel.getFrameOwner(), ownerPanel);
        }
        javaEditorDialog.setVisible(true);
        javaEditorDialog.toFront();
        javaEditorDialog.requestFocus();
    }

    public void openPositionSizeCalculator() {
        openPositionSizeCalculator(null);
    }

    public void openPositionSizeCalculator(BigDecimal initialPrice) {
        if (positionSizeCalculatorDialog == null || !positionSizeCalculatorDialog.isDisplayable()) {
            positionSizeCalculatorDialog = new PositionSizeCalculatorDialog(ownerPanel.getFrameOwner(), ownerPanel);
        }
        if (initialPrice != null) {
            positionSizeCalculatorDialog.setEntryPrice(initialPrice);
        }
        positionSizeCalculatorDialog.setVisible(true);
        positionSizeCalculatorDialog.toFront();
        positionSizeCalculatorDialog.requestFocus();
    }

    /**
     * Switches the main view to the Analysis/Insights tab.
     */
    public void openInsightsDialog() {
        // Instead of opening a dialog, we switch the main view to the ANALYSIS tab.
        if (ownerPanel.getFrameOwner() instanceof PrimaryFrame frame) {
            frame.getTitleBarManager().getAnalysisNavButton().doClick();
        }
    }

    public void openSettingsDialog() {
        if (settingsDialog == null || !settingsDialog.isDisplayable()) {
            settingsDialog = new SettingsDialog(ownerPanel.getFrameOwner());
        }
        settingsDialog.setVisible(true);
        settingsDialog.toFront();
        settingsDialog.requestFocus();
    }
    
    /**
     * Opens the achievements dialog.
     */
    public void openAchievementsDialog() {
        if (achievementsDialog == null || !achievementsDialog.isDisplayable()) {
            achievementsDialog = new AchievementsDialog(ownerPanel.getFrameOwner());
        }
        achievementsDialog.setVisible(true);
        achievementsDialog.toFront();
        achievementsDialog.requestFocus();
    }

    /**
     * Opens the community marketplace dialog.
     */
    public void openMarketplaceDialog() {
        if (marketplaceDialog == null || !marketplaceDialog.isDisplayable()) {
            marketplaceDialog = new MarketplaceDialog(ownerPanel.getFrameOwner());
        }
        marketplaceDialog.setVisible(true);
        marketplaceDialog.toFront();
        marketplaceDialog.requestFocus();
    }
    
    public void disposeDialogs() {
        if (javaEditorDialog != null) {
            javaEditorDialog.dispose();
        }
        if (settingsDialog != null) {
            settingsDialog.dispose();
        }
        if (positionSizeCalculatorDialog != null) {
            positionSizeCalculatorDialog.dispose();
        }
        if (achievementsDialog != null) {
            achievementsDialog.dispose();
        }
        if (marketplaceDialog != null) {
            marketplaceDialog.dispose();
        }
    }
}
package com.EcoChartPro.ui;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.ui.dialogs.AchievementsDialog;
import com.EcoChartPro.ui.dialogs.InsightsDialog;
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
    private InsightsDialog insightsDialog;
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

    public void openInsightsDialog() {
        if (insightsDialog == null || !insightsDialog.isDisplayable()) {
            insightsDialog = new InsightsDialog(ownerPanel.getFrameOwner());
        }

        // [FIX] Retrieve the current session state from the active workspace context (Replay or Live)
        ReplaySessionState currentState = ownerPanel.getWorkspaceContext()
                                                    .getPaperTradingService()
                                                    .getCurrentSessionState();

        // Load the data into the dialog. If null, the dialog handles it gracefully (shows empty state).
        insightsDialog.loadSessionData(currentState);

        insightsDialog.setVisible(true);
        insightsDialog.toFront();
        insightsDialog.requestFocus();
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
        if (insightsDialog != null) {
            insightsDialog.dispose();
        }
        if (achievementsDialog != null) {
            achievementsDialog.dispose();
        }
        if (marketplaceDialog != null) {
            marketplaceDialog.dispose();
        }
    }
}
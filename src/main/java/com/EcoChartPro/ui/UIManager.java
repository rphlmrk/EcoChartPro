package com.EcoChartPro.ui;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.ui.dialogs.AchievementsDialog;
import com.EcoChartPro.ui.dialogs.InsightsDialog;
import com.EcoChartPro.ui.dialogs.MarketplaceDialog;
import com.EcoChartPro.ui.dialogs.PositionSizeCalculatorDialog;
import com.EcoChartPro.ui.dialogs.SettingsDialog;
import com.EcoChartPro.ui.editor.JavaEditorDialog;
import java.math.BigDecimal;
import javax.swing.*;
import java.awt.Frame;

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
            // This logic will need to be updated to get the state from the correct context.
            // ReplaySessionState currentState = PaperTradingService.getInstance().getCurrentSessionState();
            // if (currentState != null) {
            //     insightsDialog.loadSessionData(currentState);
            // }
        }
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
     * method to open the achievements dialog.
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
     * [NEW] Opens the community marketplace dialog.
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
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

/**
 * Manages the creation and display of UI dialogs and windows.
 */
public class UIManager {

    private final MainWindow owner;
    private JavaEditorDialog javaEditorDialog;
    private SettingsDialog settingsDialog;
    private PositionSizeCalculatorDialog positionSizeCalculatorDialog;
    private InsightsDialog insightsDialog; // Managed single instance
    private AchievementsDialog achievementsDialog;
    private MarketplaceDialog marketplaceDialog; // [NEW]

    public UIManager(MainWindow owner) {
        this.owner = owner;
    }

    public void openJavaEditor() {
        if (javaEditorDialog == null || !javaEditorDialog.isDisplayable()) {
            javaEditorDialog = new JavaEditorDialog(owner);
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
            positionSizeCalculatorDialog = new PositionSizeCalculatorDialog(owner);
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
            insightsDialog = new InsightsDialog(owner);
            // Load the initial data from the current session upon creation
            ReplaySessionState currentState = PaperTradingService.getInstance().getCurrentSessionState();
            if (currentState != null) {
                insightsDialog.loadSessionData(currentState);
            }
        }
        insightsDialog.setVisible(true);
        insightsDialog.toFront(); // Bring existing instance to front if already open
        insightsDialog.requestFocus();
    }

    public void openSettingsDialog() {
        if (settingsDialog == null || !settingsDialog.isDisplayable()) {
            settingsDialog = new SettingsDialog(owner);
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
            achievementsDialog = new AchievementsDialog(owner);
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
            marketplaceDialog = new MarketplaceDialog(owner);
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
        if (insightsDialog != null) { // Dispose of the InsightsDialog as well
            insightsDialog.dispose();
        }
        if (achievementsDialog != null) {
            achievementsDialog.dispose();
        }
        if (marketplaceDialog != null) { // [NEW]
            marketplaceDialog.dispose();
        }
    }
}
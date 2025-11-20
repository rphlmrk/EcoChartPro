package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.gamification.Achievement;
import com.EcoChartPro.core.gamification.AchievementService;
import com.EcoChartPro.core.gamification.GamificationService;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * A dialog window that displays all available achievements and their current
 * locked/unlocked state.
 */
public class AchievementsDialog extends JDialog {

    public AchievementsDialog(Frame owner) {
        super(owner, "Achievements & Goals", true);
        setSize(850, 600);
        setLocationRelativeTo(owner);
        setLayout(new GridBagLayout());

        GamificationService gamificationService = GamificationService.getInstance();
        ProfileHeaderPanel profileHeader = new ProfileHeaderPanel();
        GamificationService.XpProgress progress = gamificationService.getCurrentLevelXpProgress();
        profileHeader.updateData(
                gamificationService.getCurrentLevel(),
                gamificationService.getLevelTitle(gamificationService.getCurrentLevel()),
                progress.currentXpInLevel(),
                progress.requiredXpForLevel());

        AchievementService achievementService = AchievementService.getInstance();
        List<Achievement> allAchievements = achievementService.getAllAchievements();
        JList<Achievement> achievementList = new JList<>(allAchievements.toArray(new Achievement[0]));

        achievementList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        achievementList.setVisibleRowCount(-1);
        achievementList.setCellRenderer(new AchievementCellRenderer());
        achievementList.setOpaque(false);
        achievementList.setBackground(new Color(0, 0, 0, 0));

        achievementList.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                super.setSelectionInterval(-1, -1);
            }
        });

        JScrollPane scrollPane = new JScrollPane(achievementList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);

        // [FIX] Increased unit increment for faster scrolling (was 16)
        scrollPane.getVerticalScrollBar().setUnitIncrement(40);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        add(profileHeader, gbc);

        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(scrollPane, gbc);
    }

    private static class AchievementCellRenderer implements ListCellRenderer<Achievement> {
        @Override
        public Component getListCellRendererComponent(JList<? extends Achievement> list,
                Achievement achievement,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            AchievementService service = AchievementService.getInstance();
            boolean isUnlocked = service.isUnlocked(achievement.id());
            AchievementPanel panel = new AchievementPanel(achievement, isUnlocked);

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setOpaque(false);
            wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 15));
            wrapper.add(panel, BorderLayout.CENTER);
            return wrapper;
        }
    }
}
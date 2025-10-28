package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.settings.MistakeManager;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.settings.SettingsManager.CrosshairFPS;
import com.EcoChartPro.core.settings.SettingsManager.PeakHoursDisplayStyle;
import com.EcoChartPro.core.settings.SettingsManager.ToolbarPosition;
import com.EcoChartPro.core.settings.SettingsManager.TradingSession;
import com.EcoChartPro.core.theme.ThemeManager.Theme;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SettingsDialog extends JDialog {

    private final SettingsManager settingsManager = SettingsManager.getInstance();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    public SettingsDialog(Frame owner) {
        super(owner, "Application Settings", true);

        setSize(1280, 800);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JSplitPane splitPane = new JSplitPane();
        splitPane.setResizeWeight(0.2);
        
        splitPane.setLeftComponent(createNavigationPanel());
        
        contentPanel.add(createGeneralSettingsPanel(), "General");
        contentPanel.add(createAppearanceSettingsPanel(), "Appearance");
        contentPanel.add(createSessionSettingsPanel(), "Sessions");
        contentPanel.add(createDrawingToolsSettingsPanel(), "Drawing Tools");
        contentPanel.add(createJournalingSettingsPanel(), "Journaling");
        contentPanel.add(createDisciplineSettingsPanel(), "Trading Discipline");
        contentPanel.add(createSimulationSettingsPanel(), "Simulation");
        splitPane.setRightComponent(contentPanel);

        add(splitPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private JComponent createNavigationPanel() {
        DefaultListModel<String> listModel = new DefaultListModel<>();
        listModel.addElement("General");
        listModel.addElement("Appearance");
        listModel.addElement("Sessions");
        listModel.addElement("Drawing Tools");
        listModel.addElement("Journaling");
        listModel.addElement("Trading Discipline");
        listModel.addElement("Simulation");

        JList<String> navList = new JList<>(listModel);
        navList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navList.setSelectedIndex(0);
        navList.setFont(new Font("SansSerif", Font.PLAIN, 16));
        navList.setBorder(new EmptyBorder(10, 10, 10, 10));

        navList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = navList.getSelectedValue();
                cardLayout.show(contentPanel, selected);
            }
        });
        
        return new JScrollPane(navList);
    }

    private JComponent createDisciplineSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // --- Main Toggle ---
        JPanel enablePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enablePanel.setBorder(BorderFactory.createTitledBorder("Discipline Coach"));
        JCheckBox enableCoachCheckbox = new JCheckBox("Enable Overtraining & Fatigue Analysis");
        enableCoachCheckbox.setSelected(settingsManager.isDisciplineCoachEnabled());
        enablePanel.add(enableCoachCheckbox);
        enablePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, enablePanel.getPreferredSize().height));

        // --- Optimal Trade Count Panel ---
        JPanel tradeCountPanel = new JPanel();
        tradeCountPanel.setLayout(new BoxLayout(tradeCountPanel, BoxLayout.Y_AXIS));
        tradeCountPanel.setBorder(BorderFactory.createTitledBorder("Daily Trade Limit"));
        tradeCountPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JRadioButton autoDetectRadio = new JRadioButton("Auto-detect optimal trade count (Recommended)");
        JRadioButton manualSetRadio = new JRadioButton("Manually set trade count limit:");
        ButtonGroup group = new ButtonGroup();
        group.add(autoDetectRadio);
        group.add(manualSetRadio);

        int overrideValue = settingsManager.getOptimalTradeCountOverride();
        int spinnerValue = (overrideValue == -1) ? GamificationService.getInstance().getOptimalTradeCount() : overrideValue;
        JSpinner manualCountSpinner = new JSpinner(new SpinnerNumberModel(spinnerValue, 1, 50, 1));
        manualCountSpinner.setMaximumSize(manualCountSpinner.getPreferredSize());

        JPanel manualPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        manualPanel.add(manualSetRadio);
        manualPanel.add(manualCountSpinner);
        manualPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        tradeCountPanel.add(autoDetectRadio);
        tradeCountPanel.add(manualPanel);
        tradeCountPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, tradeCountPanel.getPreferredSize().height));
        
        // --- Real-time Nudges Panel ---
        JPanel nudgePanel = new JPanel();
        nudgePanel.setLayout(new BoxLayout(nudgePanel, BoxLayout.Y_AXIS));
        nudgePanel.setBorder(BorderFactory.createTitledBorder("Real-time Nudges"));
        nudgePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- In-Session Streak Notifications Panel ---
        JPanel streakNudgePanel = new JPanel();
        streakNudgePanel.setLayout(new BoxLayout(streakNudgePanel, BoxLayout.Y_AXIS));
        streakNudgePanel.setBorder(BorderFactory.createTitledBorder("In-Session Streak Notifications"));
        streakNudgePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox winStreakNudgeCheckbox = new JCheckBox("Show 'On Fire' notification for winning streaks (3+ wins).");
        winStreakNudgeCheckbox.setSelected(settingsManager.isWinStreakNudgeEnabled());
        winStreakNudgeCheckbox.addActionListener(e -> settingsManager.setWinStreakNudgeEnabled(winStreakNudgeCheckbox.isSelected()));

        JCheckBox lossStreakNudgeCheckbox = new JCheckBox("Show 'Take a Break' notification for losing streaks (3+ losses).");
        lossStreakNudgeCheckbox.setSelected(settingsManager.isLossStreakNudgeEnabled());
        
        JSpinner fastForwardTimeSpinner = createTimeSpinner(settingsManager.getFastForwardTime());
        fastForwardTimeSpinner.addChangeListener(e -> {
            Date date = (Date) fastForwardTimeSpinner.getValue();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            LocalTime newTime = LocalTime.of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
            settingsManager.setFastForwardTime(newTime);
        });

        lossStreakNudgeCheckbox.addActionListener(e -> {
            boolean isSelected = lossStreakNudgeCheckbox.isSelected();
            settingsManager.setLossStreakNudgeEnabled(isSelected);
            fastForwardTimeSpinner.setEnabled(isSelected);
        });
        fastForwardTimeSpinner.setEnabled(lossStreakNudgeCheckbox.isSelected());

        JPanel lossStreakPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        lossStreakPanel.add(lossStreakNudgeCheckbox);
        lossStreakPanel.add(new JLabel("Fast forward to (UTC):"));
        lossStreakPanel.add(fastForwardTimeSpinner);

        streakNudgePanel.add(winStreakNudgeCheckbox);
        streakNudgePanel.add(lossStreakPanel);
        streakNudgePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, streakNudgePanel.getPreferredSize().height * 2));
        
        // --- Visual Aids Panel ---
        JPanel visualAidsPanel = new JPanel();
        visualAidsPanel.setLayout(new BoxLayout(visualAidsPanel, BoxLayout.Y_AXIS));
        visualAidsPanel.setBorder(BorderFactory.createTitledBorder("Visual Aids"));
        visualAidsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JCheckBox showPeakHoursCheckbox = new JCheckBox("Visually highlight peak performance hours on the chart");
        showPeakHoursCheckbox.setSelected(settingsManager.isShowPeakHoursLines());

        visualAidsPanel.add(showPeakHoursCheckbox);

        // --- Peak Hours Style Sub-Panel ---
        JPanel peakHoursStylePanel = new JPanel(new GridBagLayout());
        peakHoursStylePanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0)); // Indent
        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.insets = new Insets(2, 2, 2, 2);
        pgbc.anchor = GridBagConstraints.WEST;

        ButtonGroup styleGroup = new ButtonGroup();
        JRadioButton shadeRadio = new JRadioButton(PeakHoursDisplayStyle.SHADE_AREA.toString());
        JRadioButton linesRadio = new JRadioButton(PeakHoursDisplayStyle.INDICATOR_LINES.toString());
        JRadioButton barRadio = new JRadioButton(PeakHoursDisplayStyle.BOTTOM_BAR.toString());
        JSpinner barHeightSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getPeakHoursBottomBarHeight(), 2, 10, 1));
        barHeightSpinner.setPreferredSize(new Dimension(50, barHeightSpinner.getPreferredSize().height));

        styleGroup.add(shadeRadio); styleGroup.add(linesRadio); styleGroup.add(barRadio);

        JButton shadeColorButton = createColorPickerButton("Color for Shade/Bar", settingsManager.getPeakHoursColorShade(), settingsManager::setPeakHoursColorShade);
        JButton startColorButton = createColorPickerButton("Color for Start Indicator", settingsManager.getPeakHoursColorStart(), settingsManager::setPeakHoursColorStart);
        JButton endColorButton = createColorPickerButton("Color for End Indicator", settingsManager.getPeakHoursColorEnd(), settingsManager::setPeakHoursColorEnd);
        
        JPanel barPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        barPanel.setOpaque(false);
        barPanel.add(barRadio);
        barPanel.add(new JLabel("Height:"));
        barPanel.add(barHeightSpinner);
        
        pgbc.gridx = 0; pgbc.gridy = 0; peakHoursStylePanel.add(shadeRadio, pgbc);
        pgbc.gridx = 1; peakHoursStylePanel.add(linesRadio, pgbc);
        
        pgbc.gridx = 0; pgbc.gridy = 1; peakHoursStylePanel.add(barPanel, pgbc);
        
        pgbc.gridx = 0; pgbc.gridy = 2; pgbc.insets = new Insets(8, 2, 2, 2);
        peakHoursStylePanel.add(new JLabel("Shade/Bar Color:"), pgbc);
        pgbc.gridx = 1; peakHoursStylePanel.add(shadeColorButton, pgbc);
        
        pgbc.gridx = 0; pgbc.gridy = 3; pgbc.insets = new Insets(2, 2, 2, 2);
        peakHoursStylePanel.add(new JLabel("Start Indicator Color:"), pgbc);
        pgbc.gridx = 1; peakHoursStylePanel.add(startColorButton, pgbc);

        pgbc.gridx = 0; pgbc.gridy = 4;
        peakHoursStylePanel.add(new JLabel("End Indicator Color:"), pgbc);
        pgbc.gridx = 1; peakHoursStylePanel.add(endColorButton, pgbc);

        visualAidsPanel.add(peakHoursStylePanel);

        // Logic for the style panel
        final Runnable updateStyleControls = () -> {
            PeakHoursDisplayStyle currentStyle = settingsManager.getPeakHoursDisplayStyle();
            shadeRadio.setSelected(currentStyle == PeakHoursDisplayStyle.SHADE_AREA);
            linesRadio.setSelected(currentStyle == PeakHoursDisplayStyle.INDICATOR_LINES);
            barRadio.setSelected(currentStyle == PeakHoursDisplayStyle.BOTTOM_BAR);

            boolean isLines = currentStyle == PeakHoursDisplayStyle.INDICATOR_LINES;
            startColorButton.setEnabled(isLines);
            endColorButton.setEnabled(isLines);
            shadeColorButton.setEnabled(!isLines);
            barHeightSpinner.setEnabled(!isLines);
        };

        shadeRadio.addActionListener(e -> { settingsManager.setPeakHoursDisplayStyle(PeakHoursDisplayStyle.SHADE_AREA); updateStyleControls.run(); });
        linesRadio.addActionListener(e -> { settingsManager.setPeakHoursDisplayStyle(PeakHoursDisplayStyle.INDICATOR_LINES); updateStyleControls.run(); });
        barRadio.addActionListener(e -> { settingsManager.setPeakHoursDisplayStyle(PeakHoursDisplayStyle.BOTTOM_BAR); updateStyleControls.run(); });

        showPeakHoursCheckbox.addActionListener(e -> {
            boolean isSelected = showPeakHoursCheckbox.isSelected();
            settingsManager.setShowPeakHoursLines(isSelected);
            for (Component comp : peakHoursStylePanel.getComponents()) {
                comp.setEnabled(isSelected);
            }
            if(isSelected) updateStyleControls.run();
        });

        barHeightSpinner.addChangeListener(e -> {
            settingsManager.setPeakHoursBottomBarHeight((Integer) barHeightSpinner.getValue());
        });
        
        // Add nudge checkboxes to the Nudge Panel now
        JCheckBox overtrainingNudgeCheckbox = new JCheckBox("Notify when approaching daily trade limit.");
        overtrainingNudgeCheckbox.setSelected(settingsManager.isOvertrainingNudgeEnabled());
        overtrainingNudgeCheckbox.addActionListener(e -> settingsManager.setOvertrainingNudgeEnabled(overtrainingNudgeCheckbox.isSelected()));

        JCheckBox fatigueNudgeCheckbox = new JCheckBox("Notify when trading outside peak hours.");
        fatigueNudgeCheckbox.setSelected(settingsManager.isFatigueNudgeEnabled());
        fatigueNudgeCheckbox.addActionListener(e -> settingsManager.setFatigueNudgeEnabled(fatigueNudgeCheckbox.isSelected()));

        nudgePanel.add(overtrainingNudgeCheckbox);
        nudgePanel.add(fatigueNudgeCheckbox);
        nudgePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, nudgePanel.getPreferredSize().height));

        // Initial state sync
        boolean isPeakHoursVisible = showPeakHoursCheckbox.isSelected();
        for (Component comp : peakHoursStylePanel.getComponents()) {
            comp.setEnabled(isPeakHoursVisible);
        }
        if(isPeakHoursVisible) updateStyleControls.run();

        // --- Logic and Listeners ---
        boolean isEnabled = enableCoachCheckbox.isSelected();
        autoDetectRadio.setEnabled(isEnabled);
        manualSetRadio.setEnabled(isEnabled);
        manualCountSpinner.setEnabled(isEnabled && overrideValue != -1);
        if (overrideValue == -1) {
            autoDetectRadio.setSelected(true);
        } else {
            manualSetRadio.setSelected(true);
        }

        enableCoachCheckbox.addActionListener(e -> {
            boolean isNowEnabled = enableCoachCheckbox.isSelected();
            settingsManager.setDisciplineCoachEnabled(isNowEnabled);
            autoDetectRadio.setEnabled(isNowEnabled);
            manualSetRadio.setEnabled(isNowEnabled);
            manualCountSpinner.setEnabled(isNowEnabled && manualSetRadio.isSelected());
        });

        autoDetectRadio.addActionListener(e -> {
            if (autoDetectRadio.isSelected()) {
                manualCountSpinner.setEnabled(false);
                settingsManager.setOptimalTradeCountOverride(-1);
            }
        });

        manualSetRadio.addActionListener(e -> {
            if (manualSetRadio.isSelected()) {
                manualCountSpinner.setEnabled(true);
                settingsManager.setOptimalTradeCountOverride((Integer) manualCountSpinner.getValue());
            }
        });

        manualCountSpinner.addChangeListener(e -> {
            if (manualSetRadio.isSelected()) {
                settingsManager.setOptimalTradeCountOverride((Integer) manualCountSpinner.getValue());
            }
        });

        panel.add(enablePanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(tradeCountPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(createPreferredSessionsPanel()); // NEW: Add preferred sessions panel
        panel.add(Box.createVerticalStrut(10));
        panel.add(createPeakHoursPanel());
        panel.add(Box.createVerticalStrut(10));
        panel.add(nudgePanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(streakNudgePanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(visualAidsPanel);
        panel.add(Box.createVerticalGlue());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setBorder(null);
        return scrollPane;
    }

    private JComponent createPreferredSessionsPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Preferred Trading Sessions (for Auto-Tagging)"));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        List<TradingSession> preferredSessions = settingsManager.getPreferredTradingSessions();
        List<JCheckBox> checkBoxes = new ArrayList<>();

        for (TradingSession session : TradingSession.values()) {
            String sessionName = session.name().charAt(0) + session.name().substring(1).toLowerCase();
            JCheckBox checkBox = new JCheckBox(sessionName);
            checkBox.setSelected(preferredSessions.contains(session));
            panel.add(checkBox);
            checkBoxes.add(checkBox);
        }

        ActionListener listener = e -> {
            List<TradingSession> newPreferredSessions = new ArrayList<>();
            for (int i = 0; i < checkBoxes.size(); i++) {
                if (checkBoxes.get(i).isSelected()) {
                    newPreferredSessions.add(TradingSession.values()[i]);
                }
            }
            settingsManager.setPreferredTradingSessions(newPreferredSessions);
        };

        for (JCheckBox checkBox : checkBoxes) {
            checkBox.addActionListener(listener);
        }
        
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private JComponent createPeakHoursPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Peak Performance Hours (UTC)"));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox overrideCheckbox = new JCheckBox("Manually define peak performance hours");
        overrideCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel hoursGrid = new JPanel(new GridLayout(3, 8, 5, 5));
        hoursGrid.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));
        hoursGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JCheckBox[] hourCheckboxes = new JCheckBox[24];
        for (int i = 0; i < 24; i++) {
            hourCheckboxes[i] = new JCheckBox(String.format("%02d", i));
            hoursGrid.add(hourCheckboxes[i]);
        }

        // --- Logic & Listeners ---
        List<Integer> overrideHours = settingsManager.getPeakPerformanceHoursOverride();
        boolean isOverrideEnabled = !overrideHours.isEmpty();
        overrideCheckbox.setSelected(isOverrideEnabled);

        for (int i = 0; i < 24; i++) {
            hourCheckboxes[i].setEnabled(isOverrideEnabled);
            if (isOverrideEnabled && overrideHours.contains(i)) {
                hourCheckboxes[i].setSelected(true);
            }
        }

        ActionListener hourListener = e -> {
            if (overrideCheckbox.isSelected()) {
                List<Integer> selectedHours = new ArrayList<>();
                for (int i = 0; i < 24; i++) {
                    if (hourCheckboxes[i].isSelected()) {
                        selectedHours.add(i);
                    }
                }
                settingsManager.setPeakPerformanceHoursOverride(selectedHours);
            }
        };
        for (JCheckBox cb : hourCheckboxes) {
            cb.addActionListener(hourListener);
        }

        overrideCheckbox.addActionListener(e -> {
            boolean selected = overrideCheckbox.isSelected();
            for (JCheckBox cb : hourCheckboxes) {
                cb.setEnabled(selected);
            }
            if (!selected) {
                settingsManager.setPeakPerformanceHoursOverride(new ArrayList<>());
            } else {
                hourListener.actionPerformed(null); // Trigger an update
            }
        });

        panel.add(overrideCheckbox);
        panel.add(hoursGrid);
        return panel;
    }

    private JComponent createSimulationSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // --- Costs Panel ---
        JPanel costsPanel = new JPanel(new GridBagLayout());
        costsPanel.setBorder(BorderFactory.createTitledBorder("Simulated Trading Costs"));
        costsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Commission
        gbc.gridx = 0; gbc.gridy = 0;
        costsPanel.add(new JLabel("Commission per Trade ($):"), gbc);
        gbc.gridx = 1;
        JSpinner commissionSpinner = new JSpinner(new SpinnerNumberModel(
            settingsManager.getCommissionPerTrade().doubleValue(), 0.0, 100.0, 0.01
        ));
        commissionSpinner.addChangeListener(e -> {
            settingsManager.setCommissionPerTrade(BigDecimal.valueOf((Double) commissionSpinner.getValue()));
        });
        costsPanel.add(commissionSpinner, gbc);

        // Spread
        gbc.gridx = 0; gbc.gridy = 1;
        costsPanel.add(new JLabel("Simulated Spread (points):"), gbc);
        gbc.gridx = 1;
        JSpinner spreadSpinner = new JSpinner(new SpinnerNumberModel(
            settingsManager.getSimulatedSpreadPoints().doubleValue(), 0.0, 1000.0, 0.1
        ));
        spreadSpinner.addChangeListener(e -> {
            settingsManager.setSimulatedSpreadPoints(BigDecimal.valueOf((Double) spreadSpinner.getValue()));
        });
        costsPanel.add(spreadSpinner, gbc);
        costsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, costsPanel.getPreferredSize().height));
        
        // --- Journaling Panel ---
        JPanel journalingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        journalingPanel.setBorder(BorderFactory.createTitledBorder("Journaling"));
        journalingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox autoJournalCheckbox = new JCheckBox("Pause and prompt for journal entry when a trade is closed");
        autoJournalCheckbox.setSelected(settingsManager.isAutoJournalOnTradeClose());
        autoJournalCheckbox.addActionListener(e -> settingsManager.setAutoJournalOnTradeClose(autoJournalCheckbox.isSelected()));
        journalingPanel.add(autoJournalCheckbox);
        journalingPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, journalingPanel.getPreferredSize().height));


        panel.add(costsPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(journalingPanel);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JComponent createGeneralSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("General Chart Settings"));
        
        // --- Session Highlighting Toggle ---
        JPanel sessionHighlightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox sessionHighlightCheckbox = new JCheckBox("Highlight Trading Sessions on Chart");
        sessionHighlightCheckbox.setSelected(settingsManager.isSessionHighlightingEnabled());
        sessionHighlightCheckbox.addActionListener(e -> settingsManager.setSessionHighlightingEnabled(sessionHighlightCheckbox.isSelected()));
        sessionHighlightPanel.add(sessionHighlightCheckbox);
        sessionHighlightPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, sessionHighlightPanel.getPreferredSize().height));

        JCheckBox daySeparatorCheckbox = new JCheckBox("Show Day/Week Separators");
        daySeparatorCheckbox.setSelected(settingsManager.isDaySeparatorsEnabled());
        daySeparatorCheckbox.addActionListener(e -> settingsManager.setDaySeparatorsEnabled(daySeparatorCheckbox.isSelected()));

        JPanel separatorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        separatorPanel.add(daySeparatorCheckbox);
        separatorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, separatorPanel.getPreferredSize().height));
        
        JPanel axisLabelsPanel = new JPanel();
        axisLabelsPanel.setLayout(new BoxLayout(axisLabelsPanel, BoxLayout.Y_AXIS));
        axisLabelsPanel.setBorder(BorderFactory.createTitledBorder("Price Axis Labels"));
        axisLabelsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox priceLabelsCheckbox = new JCheckBox("Show Price Labels on Axis");
        priceLabelsCheckbox.setSelected(settingsManager.isPriceAxisLabelsEnabled());

        JCheckBox showOrdersCheckbox = new JCheckBox("Show Order Labels");
        showOrdersCheckbox.setSelected(settingsManager.isPriceAxisLabelsShowOrders());
        showOrdersCheckbox.addActionListener(e -> settingsManager.setPriceAxisLabelsShowOrders(showOrdersCheckbox.isSelected()));

        JCheckBox showDrawingsCheckbox = new JCheckBox("Show Drawing Labels");
        showDrawingsCheckbox.setSelected(settingsManager.isPriceAxisLabelsShowDrawings());
        showDrawingsCheckbox.addActionListener(e -> settingsManager.setPriceAxisLabelsShowDrawings(showDrawingsCheckbox.isSelected()));

        JCheckBox showFibonaccisCheckbox = new JCheckBox("Show Fibonacci Labels");
        showFibonaccisCheckbox.setSelected(settingsManager.isPriceAxisLabelsShowFibonaccis());
        showFibonaccisCheckbox.addActionListener(e -> settingsManager.setPriceAxisLabelsShowFibonaccis(showFibonaccisCheckbox.isSelected()));

        JRadioButton leftRadioPos = new JRadioButton("Left");
        leftRadioPos.setActionCommand("LEFT");
        leftRadioPos.setSelected(settingsManager.getPriceAxisLabelPosition() == SettingsManager.PriceAxisLabelPosition.LEFT);

        JRadioButton rightRadioPos = new JRadioButton("Right");
        rightRadioPos.setActionCommand("RIGHT");
        rightRadioPos.setSelected(settingsManager.getPriceAxisLabelPosition() == SettingsManager.PriceAxisLabelPosition.RIGHT);

        ButtonGroup positionGroup = new ButtonGroup();
        positionGroup.add(leftRadioPos);
        positionGroup.add(rightRadioPos);

        ActionListener positionListener = e -> settingsManager.setPriceAxisLabelPosition(SettingsManager.PriceAxisLabelPosition.valueOf(e.getActionCommand()));
        leftRadioPos.addActionListener(positionListener);
        rightRadioPos.addActionListener(positionListener);

        JPanel positionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        positionPanel.add(new JLabel("Label Position:"));
        positionPanel.add(Box.createHorizontalStrut(10));
        positionPanel.add(leftRadioPos);
        positionPanel.add(rightRadioPos);

        priceLabelsCheckbox.addActionListener(e -> {
            boolean enabled = priceLabelsCheckbox.isSelected();
            settingsManager.setPriceAxisLabelsEnabled(enabled);
            showOrdersCheckbox.setEnabled(enabled);
            showDrawingsCheckbox.setEnabled(enabled);
            showFibonaccisCheckbox.setEnabled(enabled);
            leftRadioPos.setEnabled(enabled);
            rightRadioPos.setEnabled(enabled);
        });
        showOrdersCheckbox.setEnabled(priceLabelsCheckbox.isSelected());
        showDrawingsCheckbox.setEnabled(priceLabelsCheckbox.isSelected());
        showFibonaccisCheckbox.setEnabled(priceLabelsCheckbox.isSelected());
        leftRadioPos.setEnabled(priceLabelsCheckbox.isSelected());
        rightRadioPos.setEnabled(priceLabelsCheckbox.isSelected());
        
        JPanel masterTogglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        masterTogglePanel.add(priceLabelsCheckbox);
        
        JPanel detailsPanel = new JPanel(new GridLayout(0, 1));
        detailsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0)); // Indent
        detailsPanel.add(showOrdersCheckbox);
        detailsPanel.add(showDrawingsCheckbox);
        detailsPanel.add(showFibonaccisCheckbox);
        detailsPanel.add(positionPanel);

        axisLabelsPanel.add(masterTogglePanel);
        axisLabelsPanel.add(detailsPanel);
        axisLabelsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, axisLabelsPanel.getPreferredSize().height));
        
        JPanel toolbarPositionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbarPositionPanel.setBorder(BorderFactory.createTitledBorder("Drawing Toolbar Position"));
        
        JRadioButton leftRadio = new JRadioButton("Dock Left");
        leftRadio.setActionCommand("LEFT");
        leftRadio.setSelected(settingsManager.getDrawingToolbarPosition() == ToolbarPosition.LEFT);
        
        JRadioButton rightRadio = new JRadioButton("Dock Right");
        rightRadio.setActionCommand("RIGHT");
        rightRadio.setSelected(settingsManager.getDrawingToolbarPosition() == ToolbarPosition.RIGHT);

        ButtonGroup group = new ButtonGroup();
        group.add(leftRadio);
        group.add(rightRadio);
        
        ActionListener toolbarListener = e -> settingsManager.setDrawingToolbarPosition(ToolbarPosition.valueOf(e.getActionCommand()));
        leftRadio.addActionListener(toolbarListener);
        rightRadio.addActionListener(toolbarListener);

        toolbarPositionPanel.add(leftRadio);
        toolbarPositionPanel.add(rightRadio);
        toolbarPositionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolbarPositionPanel.getPreferredSize().height));

        JPanel interactionPanel = new JPanel(new GridBagLayout());
        interactionPanel.setBorder(BorderFactory.createTitledBorder("Interaction & Snapping"));
        GridBagConstraints igbc = new GridBagConstraints();
        igbc.insets = new Insets(2, 5, 2, 5);
        igbc.anchor = GridBagConstraints.WEST;

        igbc.gridx = 0; igbc.gridy = 0;
        interactionPanel.add(new JLabel("Price Snapping Radius (pixels):"), igbc);
        igbc.gridx = 1;
        JSpinner snapSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getSnapRadius(), 2, 20, 1));
        snapSpinner.addChangeListener(e -> settingsManager.setSnapRadius((Integer) snapSpinner.getValue()));
        interactionPanel.add(snapSpinner, igbc);

        igbc.gridx = 0; igbc.gridy = 1;
        interactionPanel.add(new JLabel("Drawing Hit Threshold (pixels):"), igbc);
        igbc.gridx = 1;
        JSpinner hitSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getDrawingHitThreshold(), 2, 20, 1));
        hitSpinner.addChangeListener(e -> settingsManager.setDrawingHitThreshold((Integer) hitSpinner.getValue()));
        interactionPanel.add(hitSpinner, igbc);
        
        igbc.gridx = 0; igbc.gridy = 2;
        interactionPanel.add(new JLabel("Drawing Handle Size (pixels):"), igbc);
        igbc.gridx = 1;
        JSpinner handleSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getDrawingHandleSize(), 4, 16, 1));
        handleSpinner.addChangeListener(e -> settingsManager.setDrawingHandleSize((Integer) handleSpinner.getValue()));
        interactionPanel.add(handleSpinner, igbc);
        
        igbc.gridx = 2; igbc.weightx = 1.0; 
        interactionPanel.add(new JLabel(), igbc);
        interactionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, interactionPanel.getPreferredSize().height * 4));

        JPanel replayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        replayPanel.setBorder(BorderFactory.createTitledBorder("Replay"));
        replayPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, replayPanel.getPreferredSize().height));

        JPanel toolbarSizePanel = new JPanel(new GridBagLayout());
        toolbarSizePanel.setBorder(BorderFactory.createTitledBorder("Floating Toolbar Size"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        toolbarSizePanel.add(new JLabel("Width:"), gbc);
        gbc.gridx = 1;
        JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getDrawingToolbarWidth(), 25, 500, 5));
        widthSpinner.addChangeListener(e -> settingsManager.setDrawingToolbarWidth((Integer) widthSpinner.getValue()));
        toolbarSizePanel.add(widthSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        toolbarSizePanel.add(new JLabel("Height:"), gbc);
        gbc.gridx = 1;
        JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getDrawingToolbarHeight(), 100, 800, 10));
        heightSpinner.addChangeListener(e -> settingsManager.setDrawingToolbarHeight((Integer) heightSpinner.getValue()));
        toolbarSizePanel.add(heightSpinner, gbc);

        gbc.gridx = 2; gbc.weightx = 1.0; 
        toolbarSizePanel.add(new JLabel(), gbc);
        toolbarSizePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolbarSizePanel.getPreferredSize().height * 3));
        
        JPanel fpsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fpsPanel.setBorder(BorderFactory.createTitledBorder("Crosshair Performance"));
        fpsPanel.add(new JLabel("Refresh Rate:"));
        JComboBox<CrosshairFPS> fpsComboBox = new JComboBox<>(CrosshairFPS.values());
        fpsComboBox.setSelectedItem(settingsManager.getCrosshairFps());
        fpsComboBox.addActionListener(e -> {
            CrosshairFPS selected = (CrosshairFPS) fpsComboBox.getSelectedItem();
            if (selected != null) {
                settingsManager.setCrosshairFps(selected);
            }
        });
        fpsPanel.add(fpsComboBox);
        fpsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, fpsPanel.getPreferredSize().height));
        
        panel.add(createUiScalingPanel());
        panel.add(Box.createVerticalStrut(10));
        panel.add(sessionHighlightPanel); // Add the new panel here
        panel.add(Box.createVerticalStrut(10));
        panel.add(separatorPanel);
        panel.add(axisLabelsPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(interactionPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(fpsPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(createTradeReplayTimeframesPanel());
        panel.add(Box.createVerticalStrut(10));
        
        replayPanel.add(new JLabel("Auto-Save Interval (bars):"));
        JSpinner autoSaveSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getAutoSaveInterval(), 20, 1000, 10));
        autoSaveSpinner.addChangeListener(e -> settingsManager.setAutoSaveInterval((Integer) autoSaveSpinner.getValue()));
        replayPanel.add(autoSaveSpinner);
        panel.add(replayPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(toolbarPositionPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(toolbarSizePanel); 
        
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        return scrollPane;
    }
    
    private JPanel createUiScalingPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("Application Scaling (Restart Required)"));

        panel.add(new JLabel("UI Scale:"));
        String[] scaleOptions = {
            "50%", "55%", "60%", "65%", "75%", "80%", "85%", "90%", "95%", 
            "100%", "125%", "150%", "175%", "200%"
        };
        JComboBox<String> scaleComboBox = new JComboBox<>(scaleOptions);
        
        String currentScale = (int)(settingsManager.getUiScale() * 100) + "%";
        scaleComboBox.setSelectedItem(currentScale);

        scaleComboBox.addActionListener(e -> {
            String selected = (String) scaleComboBox.getSelectedItem();
            if (selected == null) return;
            
            float newScale = Float.parseFloat(selected.replace("%", "")) / 100.0f;
            
            if (Math.abs(settingsManager.getUiScale() - newScale) < 0.001) {
                return;
            }
            
            settingsManager.setUiScale(newScale);

            String[] options = {"Restart Now", "Later"};
            int choice = JOptionPane.showOptionDialog(
                this,
                "A restart is required to apply the new UI scale.",
                "Restart Required",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);

            if (choice == 0) { // Restart Now
                System.setProperty("app.restart", "true");
                for (Window window : Window.getWindows()) {
                    window.dispose();
                }
            }
        });
        
        panel.add(scaleComboBox);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private JPanel createTradeReplayTimeframesPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 3)); // Grid layout
        panel.setBorder(BorderFactory.createTitledBorder("Trade Replay Chart - Available Timeframes"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height * 3));

        List<String> selectedTimeframes = new ArrayList<>(settingsManager.getTradeReplayAvailableTimeframes());

        for (Timeframe tf : Timeframe.getStandardTimeframes()) {
            JCheckBox checkBox = new JCheckBox(tf.displayName());
            checkBox.setSelected(selectedTimeframes.contains(tf.displayName()));

            checkBox.addActionListener(e -> {
                if (checkBox.isSelected()) {
                    if (!selectedTimeframes.contains(tf.displayName())) {
                        selectedTimeframes.add(tf.displayName());
                    }
                } else {
                    selectedTimeframes.remove(tf.displayName());
                }
                if (selectedTimeframes.isEmpty()) {
                    checkBox.setSelected(true);
                    selectedTimeframes.add(tf.displayName());
                    JOptionPane.showMessageDialog(this, "At least one timeframe must be selected.", "Selection Required", JOptionPane.WARNING_MESSAGE);
                }
                settingsManager.setTradeReplayAvailableTimeframes(selectedTimeframes);
            });
            panel.add(checkBox);
        }
        return panel;
    }
    
    private JPanel createJournalingSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Manage Custom Mistakes"));

        MistakeManager mistakeManager = MistakeManager.getInstance();
        DefaultListModel<String> mistakesListModel = new DefaultListModel<>();
        mistakesListModel.addAll(mistakeManager.getMistakes());

        JList<String> mistakesList = new JList<>(mistakesListModel);
        mistakesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(mistakesList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton addButton = new JButton("Add...");
        addButton.addActionListener(e -> {
            String newMistake = JOptionPane.showInputDialog(this, "Enter the new mistake:", "Add Mistake", JOptionPane.PLAIN_MESSAGE);
            if (newMistake != null && !newMistake.isBlank()) {
                mistakeManager.addMistake(newMistake);
                mistakesListModel.clear();
                mistakesListModel.addAll(mistakeManager.getMistakes());
            }
        });
        
        JButton editButton = new JButton("Edit...");
        editButton.addActionListener(e -> {
            int selectedIndex = mistakesList.getSelectedIndex();
            if (selectedIndex != -1) {
                String currentMistake = mistakesListModel.getElementAt(selectedIndex);
                String editedMistake = (String) JOptionPane.showInputDialog(this, "Edit the mistake:", "Edit Mistake", JOptionPane.PLAIN_MESSAGE, null, null, currentMistake);
                if (editedMistake != null && !editedMistake.isBlank()) {
                    mistakeManager.updateMistake(selectedIndex, editedMistake);
                    mistakesListModel.set(selectedIndex, editedMistake);
                }
            }
        });

        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> {
            int selectedIndex = mistakesList.getSelectedIndex();
            if (selectedIndex != -1) {
                int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to remove this mistake?", "Confirm Removal", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    mistakeManager.deleteMistake(selectedIndex);
                    mistakesListModel.remove(selectedIndex);
                }
            }
        });

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createAppearanceSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel themePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        themePanel.setBorder(BorderFactory.createTitledBorder("Application Theme"));
        
        JRadioButton darkRadio = new JRadioButton("Dark");
        darkRadio.setSelected(settingsManager.getCurrentTheme() == Theme.DARK);
        darkRadio.addActionListener(e -> handleThemeChange(Theme.DARK));
        
        JRadioButton lightRadio = new JRadioButton("Light");
        lightRadio.setSelected(settingsManager.getCurrentTheme() == Theme.LIGHT);
        lightRadio.addActionListener(e -> handleThemeChange(Theme.LIGHT));

        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(darkRadio);
        themeGroup.add(lightRadio);
        
        themePanel.add(darkRadio);
        themePanel.add(lightRadio);
        themePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, themePanel.getPreferredSize().height));
        panel.add(themePanel);
        panel.add(Box.createVerticalStrut(10));

        JPanel colorsPanel = new JPanel(new GridBagLayout());
        colorsPanel.setBorder(BorderFactory.createTitledBorder("Chart Colors"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        colorsPanel.add(new JLabel("Bullish Candle:"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select color for up candles", settingsManager.getBullColor(), settingsManager::setBullColor), gbc(1, row++));

        colorsPanel.add(new JLabel("Bearish Candle:"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select color for down candles", settingsManager.getBearColor(), settingsManager::setBearColor), gbc(1, row++));
        
        colorsPanel.add(new JLabel("Chart Background:"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select chart background color", settingsManager.getChartBackground(), settingsManager::setChartBackground), gbc(1, row++));

        colorsPanel.add(new JLabel("Grid Lines:"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select grid line color", settingsManager.getGridColor(), settingsManager::setGridColor), gbc(1, row++));

        colorsPanel.add(new JLabel("Axis Text & Labels:"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select axis text color", settingsManager.getAxisTextColor(), settingsManager::setAxisTextColor), gbc(1, row++));
        
        colorsPanel.add(new JLabel("Crosshair Line:"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select crosshair line color", settingsManager.getCrosshairColor(), settingsManager::setCrosshairColor), gbc(1, row++));

        colorsPanel.add(new JLabel("Crosshair Label Background:"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select crosshair label background color", settingsManager.getCrosshairLabelBackgroundColor(), settingsManager::setCrosshairLabelBackgroundColor), gbc(1, row++));

        colorsPanel.add(new JLabel("Crosshair Label Text:"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select crosshair label text color", settingsManager.getCrosshairLabelForegroundColor(), settingsManager::setCrosshairLabelForegroundColor), gbc(1, row++));
        
        colorsPanel.add(new JLabel("Live Price/Timer Text (Bullish):"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select text color for bullish live price label", settingsManager.getLivePriceLabelBullTextColor(), settingsManager::setLivePriceLabelBullTextColor), gbc(1, row++));
        
        colorsPanel.add(new JLabel("Live Price/Timer Text (Bearish):"), gbc(0, row));
        colorsPanel.add(createColorPickerButton("Select text color for bearish live price label", settingsManager.getLivePriceLabelBearTextColor(), settingsManager::setLivePriceLabelBearTextColor), gbc(1, row++));
        
        colorsPanel.add(new JLabel("Live Price/Timer Font Size:"), gbc(0, row));
        JSpinner fontSizeSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getLivePriceLabelFontSize(), 8, 24, 1));
        fontSizeSpinner.addChangeListener(e -> settingsManager.setLivePriceLabelFontSize((Integer) fontSizeSpinner.getValue()));
        colorsPanel.add(fontSizeSpinner, gbc(1, row++));
        
        gbc.gridx = 2; gbc.weightx = 1.0;
        colorsPanel.add(new JLabel(), gbc); // Glue
        colorsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, colorsPanel.getPreferredSize().height));
        panel.add(colorsPanel);
        panel.add(Box.createVerticalStrut(10));
        
        panel.add(createVolumeProfileSettingsPanel());
        panel.add(Box.createVerticalStrut(10));
        panel.add(createFootprintSettingsPanel());

        panel.add(Box.createVerticalGlue());
        
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        return scrollPane;
    }
    
    private JPanel createFootprintSettingsPanel() {
        JPanel fpPanel = new JPanel(new GridBagLayout());
        fpPanel.setBorder(BorderFactory.createTitledBorder("Volume Footprint Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
    
        int row = 0;
    
        // --- Opacity Slider ---
        gbc.gridx = 0;
        gbc.gridy = row;
        fpPanel.add(new JLabel("Candle Body Opacity:"), gbc);
    
        JSlider opacitySlider = new JSlider(0, 100, settingsManager.getFootprintCandleOpacity());
        JLabel opacityLabel = new JLabel(settingsManager.getFootprintCandleOpacity() + "%");
        opacityLabel.setPreferredSize(new Dimension(40, opacityLabel.getPreferredSize().height));
    
        opacitySlider.addChangeListener(e -> {
            int value = opacitySlider.getValue();
            opacityLabel.setText(value + "%");
            if (!opacitySlider.getValueIsAdjusting()) {
                settingsManager.setFootprintCandleOpacity(value);
            }
        });
    
        gbc.gridx = 1;
        fpPanel.add(opacitySlider, gbc);
        gbc.gridx = 2;
        fpPanel.add(opacityLabel, gbc);
    
        row++;
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        fpPanel.add(new JLabel(), gbc); // Glue to push components to the left
    
        fpPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, fpPanel.getPreferredSize().height));
        return fpPanel;
    }

    private JPanel createVolumeProfileSettingsPanel() {
        JPanel vpPanel = new JPanel(new GridBagLayout());
        vpPanel.setBorder(BorderFactory.createTitledBorder("Visible Range Volume Profile (VRVP)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        
        vpPanel.add(new JLabel("Up Volume Color:"), gbc(0, row));
        vpPanel.add(createColorPickerButton("Select color for bullish volume", settingsManager.getVrvpUpVolumeColor(), settingsManager::setVrvpUpVolumeColor), gbc(1, row++));

        vpPanel.add(new JLabel("Down Volume Color:"), gbc(0, row));
        vpPanel.add(createColorPickerButton("Select color for bearish volume", settingsManager.getVrvpDownVolumeColor(), settingsManager::setVrvpDownVolumeColor), gbc(1, row++));

        vpPanel.add(new JLabel("Point of Control (POC) Color:"), gbc(0, row));
        vpPanel.add(createColorPickerButton("Select color for the POC line", settingsManager.getVrvpPocColor(), settingsManager::setVrvpPocColor), gbc(1, row++));
        
        vpPanel.add(new JLabel("Value Area Up (Not Implemented):"), gbc(0, row));
        vpPanel.add(createColorPickerButton("Select color for value area up", settingsManager.getVrvpValueAreaUpColor(), settingsManager::setVrvpValueAreaUpColor), gbc(1, row++));

        vpPanel.add(new JLabel("Value Area Down (Not Implemented):"), gbc(0, row));
        vpPanel.add(createColorPickerButton("Select color for value area down", settingsManager.getVrvpValueAreaDownColor(), settingsManager::setVrvpValueAreaDownColor), gbc(1, row++));

        gbc.gridy = row++;
        vpPanel.add(new JSeparator(), gbc(0, row, 2, 1));
        gbc.gridy = row++;
        
        vpPanel.add(new JLabel("Row Height (pixels):"), gbc(0, row));
        JSpinner rowHeightSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getVrvpRowHeight(), 1, 10, 1));
        rowHeightSpinner.addChangeListener(e -> settingsManager.setVrvpRowHeight((Integer) rowHeightSpinner.getValue()));
        vpPanel.add(rowHeightSpinner, gbc(1, row++));
        
        vpPanel.add(new JLabel("POC Line Width (pixels):"), gbc(0, row));
        JSpinner pocWidthSpinner = new JSpinner(new SpinnerNumberModel(settingsManager.getVrvpPocLineStroke().getLineWidth(), 1.0f, 5.0f, 0.5f));
        pocWidthSpinner.addChangeListener(e -> {
            float width = ((Number) pocWidthSpinner.getValue()).floatValue();
            settingsManager.setVrvpPocLineStroke(new BasicStroke(width));
        });
        vpPanel.add(pocWidthSpinner, gbc(1, row++));

        gbc.gridx = 2; gbc.weightx = 1.0;
        vpPanel.add(new JLabel(), gbc);
        vpPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, vpPanel.getPreferredSize().height));
        
        return vpPanel;
    }
    
    private GridBagConstraints gbc(int x, int y, int width, int height) {
        GridBagConstraints gbc = gbc(x,y);
        gbc.gridwidth = width;
        gbc.gridheight = height;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }
    
    private JComponent createDrawingToolsSettingsPanel() {
        JTabbedPane tabbedPane = new JTabbedPane();

        Map<String, String> tools = new LinkedHashMap<>();
        tools.put("Trendline", "Trendline");
        tools.put("Ray", "RayObject");
        tools.put("Horizontal Line", "HorizontalLineObject");
        tools.put("Horizontal Ray", "HorizontalRayObject");
        tools.put("Vertical Line", "VerticalLineObject");
        tools.put("Rectangle", "RectangleObject");
        tools.put("Fib Retracement", "FibonacciRetracementObject");
        tools.put("Fib Extension", "FibonacciExtensionObject");
        tools.put("Measure Tool", "MeasureToolObject");
        tools.put("Protected Level", "ProtectedLevelPatternObject");
        tools.put("Text", "TextObject");

        for (Map.Entry<String, String> entry : tools.entrySet()) {
            String displayName = entry.getKey();
            String className = entry.getValue();
            TemplateManagerPanel panel = new TemplateManagerPanel(className, displayName);
            tabbedPane.addTab(displayName, panel);
        }

        return tabbedPane;
    }
    
    private JPanel createSessionSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Trading Session Highlighting"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        Font headerFont = new Font("SansSerif", Font.BOLD, 12);
        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        gbc.gridy = 0;
        gbc.gridx = 0; panel.add(new JLabel("Session") {{ setFont(headerFont); }}, gbc);
        gbc.gridx = 1; panel.add(new JLabel("Enabled") {{ setFont(headerFont); }}, gbc);
        gbc.gridx = 2; panel.add(new JLabel("Start Time (UTC)") {{ setFont(headerFont); }}, gbc);
        gbc.gridx = 3; panel.add(new JLabel("End Time (UTC)") {{ setFont(headerFont); }}, gbc);
        gbc.gridx = 4; panel.add(new JLabel("Color") {{ setFont(headerFont); }}, gbc);
        
        int row = 1;
        for (TradingSession session : TradingSession.values()) {
            gbc.gridy = row++;
            gbc.gridx = 0; panel.add(new JLabel(session.name()) {{ setFont(labelFont); }}, gbc);

            gbc.gridx = 1;
            JCheckBox enabledCheckbox = new JCheckBox();
            enabledCheckbox.setSelected(settingsManager.getSessionEnabled().get(session));
            enabledCheckbox.addActionListener(e -> settingsManager.setSessionEnabled(session, enabledCheckbox.isSelected()));
            panel.add(enabledCheckbox, gbc);

            gbc.gridx = 2;
            JSpinner startSpinner = createTimeSpinner(settingsManager.getSessionStartTimes().get(session));
            startSpinner.addChangeListener(e -> updateSessionTime(e, session, true));
            panel.add(startSpinner, gbc);

            gbc.gridx = 3;
            JSpinner endSpinner = createTimeSpinner(settingsManager.getSessionEndTimes().get(session));
            endSpinner.addChangeListener(e -> updateSessionTime(e, session, false));
            panel.add(endSpinner, gbc);

            gbc.gridx = 4;
            panel.add(createColorButton(settingsManager.getSessionColors().get(session), session), gbc);
        }
        
        gbc.gridy = row; gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);

        return panel;
    }

    private JSpinner createTimeSpinner(LocalTime initialTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, initialTime.getHour());
        calendar.set(Calendar.MINUTE, initialTime.getMinute());
        
        SpinnerDateModel model = new SpinnerDateModel(calendar.getTime(), null, null, Calendar.MINUTE);
        JSpinner spinner = new JSpinner(model);
        spinner.setEditor(new JSpinner.DateEditor(spinner, "HH:mm"));
        return spinner;
    }
    
    private GridBagConstraints gbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }
    
    private JButton createColorPickerButton(String tooltip, Color initialColor, Consumer<Color> onColorUpdate) {
        JButton button = new JButton();
        button.setToolTipText(tooltip);
        button.setBackground(initialColor);
        button.setPreferredSize(new Dimension(100, 25));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        button.addActionListener(e -> {
            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(button.getBackground(), newColor -> {
                button.setBackground(newColor);
                onColorUpdate.accept(newColor);
            });

            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            popupMenu.add(colorPanel);
            popupMenu.show(button, 0, button.getHeight());
        });
        return button;
    }

    private void updateSessionTime(ChangeEvent e, TradingSession session, boolean isStartTime) {
        JSpinner spinner = (JSpinner) e.getSource();
        Date date = (Date) spinner.getValue();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        LocalTime newTime = LocalTime.of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));

        if (isStartTime) {
            settingsManager.setSessionStartTime(session, newTime);
        } else {
            settingsManager.setSessionEndTime(session, newTime);
        }
    }

    private JButton createColorButton(Color initialColor, TradingSession session) {
        JButton button = new JButton();
        button.setBackground(initialColor);
        button.setToolTipText("Click to change session color");
        button.setPreferredSize(new Dimension(80, 25));

        button.setOpaque(true);
        button.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        button.addActionListener(e -> {
            Consumer<Color> onColorUpdate = newColor -> {
                Color finalColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), initialColor.getAlpha());
                button.setBackground(finalColor);
                settingsManager.setSessionColor(session, finalColor);
            };

            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(initialColor, onColorUpdate);

            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            popupMenu.add(colorPanel);
            
            popupMenu.show(button, 0, button.getHeight());
        });
        return button;
    }

    private void handleThemeChange(Theme newTheme) {
        if (settingsManager.getCurrentTheme() == newTheme) {
            return;
        }
        
        settingsManager.setCurrentTheme(newTheme);
        
        String[] options = {"Restart Now", "Later"};
        int choice = JOptionPane.showOptionDialog(
            this,
            "A restart is recommended to fully apply the new theme.\nSome elements may not update correctly until then.",
            "Theme Changed",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[0]
        );
        
        if (choice == 0) { // Restart Now
            System.setProperty("app.restart", "true");
            for (Window window : Window.getWindows()) {
                window.dispose();
            }
        }
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        return buttonPanel;
    }
}
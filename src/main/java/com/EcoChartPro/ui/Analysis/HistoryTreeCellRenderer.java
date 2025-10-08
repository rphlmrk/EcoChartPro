package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.model.EmotionalState;
import com.EcoChartPro.model.PlanAdherence;
import com.EcoChartPro.model.SetupQuality;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.Analysis.HistoryViewPanel.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public class HistoryTreeCellRenderer extends JPanel implements TreeCellRenderer, ListCellRenderer<Object> {

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0'%'");

    private final JLabel iconLabel;
    private final JLabel textLabel;
    private final JLabel tagsLabel; // Re-purposed for subtitles

    private final Icon expandedIcon = UIManager.getIcon("Tree.expandedIcon");
    private final Icon collapsedIcon = UIManager.getIcon("Tree.collapsedIcon");

    public HistoryTreeCellRenderer() {
        super(new BorderLayout(5, 0));
        setOpaque(true); // Required for ListCellRenderer selection background

        iconLabel = new JLabel();
        textLabel = new JLabel();
        
        tagsLabel = new JLabel();
        tagsLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(11f));
        tagsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(textLabel);
        textPanel.add(tagsLabel);

        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.setOpaque(false);
        mainContent.add(textPanel, BorderLayout.CENTER);

        add(iconLabel, BorderLayout.WEST);
        add(mainContent, BorderLayout.CENTER);
    }
    
    private void resetComponent() {
        tagsLabel.setText("");
        tagsLabel.setVisible(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }
    
    private void configureFor(Object userObject, boolean expanded) {
        resetComponent();

        if (userObject instanceof YearNode year) {
            configureForHierarchy(year.year(), expanded, UIManager.getFont("app.font.widget_title").deriveFont(14f));
        } else if (userObject instanceof MonthNode month) {
            configureForHierarchy(month.yearMonth().format(MONTH_FORMATTER), expanded, UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        } else if (userObject instanceof DateNode date) {
            configureForHierarchy(date.date().format(DATE_FORMATTER), expanded, UIManager.getFont("app.font.widget_content").deriveFont(Font.ITALIC));
        } else if (userObject instanceof StrategyNode strategy) {
            configureForStrategyNode(strategy, expanded);
        } else if (userObject instanceof DayOfWeekNode day) {
            configureForDayOfWeekNode(day, expanded);
        } else if (userObject instanceof EmotionalStateNode stateNode) {
            configureForEmotionalStateNode(stateNode, expanded);
        } else if (userObject instanceof PlanAdherenceNode adherenceNode) {
            configureForPlanAdherenceNode(adherenceNode, expanded);
        } else if (userObject instanceof SetupQualityNode qualityNode) {
            configureForSetupQualityNode(qualityNode, expanded);
        } else if (userObject instanceof MistakeNode mistakeNode) {
            configureForMistakeNode(mistakeNode, expanded);
        } else if (userObject instanceof Trade trade) {
            configureForTrade(trade);
        } else {
            iconLabel.setIcon(null);
            textLabel.setText(userObject != null ? userObject.toString() : "");
            textLabel.setFont(UIManager.getFont("app.font.widget_content"));
            textLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        }
    }


    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
        configureFor(userObject, expanded);

        if (selected) {
            setBackground(UIManager.getColor("Tree.selectionBackground"));
            textLabel.setForeground(UIManager.getColor("Tree.selectionForeground"));
            tagsLabel.setForeground(UIManager.getColor("Tree.selectionForeground"));
        } else {
            setBackground(UIManager.getColor("Tree.background"));
        }
        setOpaque(selected);

        return this;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        configureFor(value, false); // 'expanded' is not applicable for lists
        
        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
            textLabel.setForeground(list.getSelectionForeground());
            tagsLabel.setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        
        return this;
    }

    private void configureForHierarchy(Object text, boolean expanded, Font font) {
        iconLabel.setIcon(expanded ? expandedIcon : collapsedIcon);
        textLabel.setText(text.toString());
        textLabel.setFont(font);
        textLabel.setForeground(UIManager.getColor("Tree.foreground"));
    }

    private void configureForStrategyNode(StrategyNode node, boolean expanded) {
        iconLabel.setIcon(expanded ? expandedIcon : collapsedIcon);
        textLabel.setText(String.format("%s (%d trades)", node.tagName(), node.tradeCount()));
        textLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        textLabel.setForeground(UIManager.getColor("Tree.foreground"));
        
        String subtitle = String.format("P&L: %s | Win Rate: %s",
                PNL_FORMAT.format(node.totalPnl()),
                PERCENT_FORMAT.format(node.winRate()));
        tagsLabel.setText(subtitle);
        tagsLabel.setVisible(true);
        tagsLabel.setForeground(node.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
    }

    private void configureForDayOfWeekNode(DayOfWeekNode node, boolean expanded) {
        iconLabel.setIcon(expanded ? expandedIcon : collapsedIcon);
        String dayName = node.day().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        textLabel.setText(String.format("%s (%d trades)", dayName, node.tradeCount()));
        textLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        textLabel.setForeground(UIManager.getColor("Tree.foreground"));

        String subtitle = String.format("P&L: %s | Win Rate: %s",
                PNL_FORMAT.format(node.totalPnl()),
                PERCENT_FORMAT.format(node.winRate()));
        tagsLabel.setText(subtitle);
        tagsLabel.setVisible(true);
        tagsLabel.setForeground(node.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
    }
    
    private void configureForEmotionalStateNode(EmotionalStateNode node, boolean expanded) {
        iconLabel.setIcon(expanded ? expandedIcon : collapsedIcon);
        textLabel.setText(String.format("%s (%d trades)", node.state(), node.tradeCount()));
        textLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        textLabel.setForeground(UIManager.getColor("Tree.foreground"));
        
        String subtitle = String.format("P&L: %s | Win Rate: %s",
                PNL_FORMAT.format(node.totalPnl()),
                PERCENT_FORMAT.format(node.winRate()));
        tagsLabel.setText(subtitle);
        tagsLabel.setVisible(true);
        tagsLabel.setForeground(node.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
    }

    private void configureForPlanAdherenceNode(PlanAdherenceNode node, boolean expanded) {
        iconLabel.setIcon(expanded ? expandedIcon : collapsedIcon);
        textLabel.setText(String.format("%s (%d trades)", node.adherence(), node.tradeCount()));
        textLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        textLabel.setForeground(UIManager.getColor("Tree.foreground"));
        
        String subtitle = String.format("P&L: %s | Win Rate: %s",
                PNL_FORMAT.format(node.totalPnl()),
                PERCENT_FORMAT.format(node.winRate()));
        tagsLabel.setText(subtitle);
        tagsLabel.setVisible(true);
        tagsLabel.setForeground(node.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
    }

    private void configureForSetupQualityNode(SetupQualityNode node, boolean expanded) {
        iconLabel.setIcon(expanded ? expandedIcon : collapsedIcon);
        textLabel.setText(String.format("%s (%d trades)", node.quality(), node.tradeCount()));
        textLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        textLabel.setForeground(UIManager.getColor("Tree.foreground"));
        
        String subtitle = String.format("P&L: %s | Win Rate: %s",
                PNL_FORMAT.format(node.totalPnl()),
                PERCENT_FORMAT.format(node.winRate()));
        tagsLabel.setText(subtitle);
        tagsLabel.setVisible(true);
        tagsLabel.setForeground(node.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
    }

    private void configureForMistakeNode(MistakeNode node, boolean expanded) {
        iconLabel.setIcon(expanded ? expandedIcon : collapsedIcon);
        textLabel.setText(String.format("%s (%d trades)", node.mistake(), node.tradeCount()));
        textLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        textLabel.setForeground(UIManager.getColor("Tree.foreground"));
        
        String subtitle = String.format("P&L: %s | Win Rate: %s",
                PNL_FORMAT.format(node.totalPnl()),
                PERCENT_FORMAT.format(node.winRate()));
        tagsLabel.setText(subtitle);
        tagsLabel.setVisible(true);
        tagsLabel.setForeground(node.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
    }
    
    private void configureForTrade(Trade trade) {
        iconLabel.setIcon(getDirectionIcon(trade.direction()));
        textLabel.setText(trade.symbol().name().toUpperCase() + " | " + PNL_FORMAT.format(trade.profitAndLoss()));
        textLabel.setFont(UIManager.getFont("app.font.widget_content"));
        textLabel.setForeground(UIManager.getColor("Tree.foreground"));
        
        if (trade.tags() != null && !trade.tags().isEmpty()) {
            tagsLabel.setText("Tags: " + String.join(", ", trade.tags()));
            tagsLabel.setVisible(true);
            tagsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        }
    }

    private Icon getDirectionIcon(TradeDirection direction) {
        boolean isLong = direction == TradeDirection.LONG;
        String path = isLong ? UITheme.Icons.TRADE_ARROW_UP : UITheme.Icons.TRADE_ARROW_DOWN;
        Color color = isLong ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative");
        return UITheme.getIcon(path, 14, 14, color);
    }
}
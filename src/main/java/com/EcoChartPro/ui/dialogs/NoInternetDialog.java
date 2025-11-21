package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.ui.home.theme.UITheme;

import javax.swing.*;
import java.awt.*;

public class NoInternetDialog extends JDialog {

    public enum Result {
        RETRY,
        CANCEL
    }

    private Result result = Result.CANCEL;

    public NoInternetDialog(Frame owner) {
        super(owner, "Connection Error", true);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // --- Icon Panel (West) ---
        JLabel iconLabel = new JLabel(UITheme.getIcon(UITheme.Icons.WIFI_OFF, 48, 48, UIManager.getColor("app.color.negative")));
        add(iconLabel, BorderLayout.WEST);

        // --- Message Panel (Center) ---
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        
        JLabel titleLabel = new JLabel("No Internet Connection");
        titleLabel.setFont(UIManager.getFont("app.font.widget_title"));
        messagePanel.add(titleLabel);
        messagePanel.add(Box.createVerticalStrut(5));
        
        JLabel descriptionLabel = new JLabel("<html>A stable internet connection is required to start or resume a live paper trading session. Please check your connection and try again.</html>");
        descriptionLabel.setFont(UIManager.getFont("app.font.widget_content"));
        messagePanel.add(descriptionLabel);
        
        add(messagePanel, BorderLayout.CENTER);
        
        // --- Button Panel (South) ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton retryButton = new JButton("Retry Connection");
        retryButton.addActionListener(e -> {
            this.result = Result.RETRY;
            dispose();
        });
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            this.result = Result.CANCEL;
            dispose();
        });
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(retryButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setResizable(false);
        setLocationRelativeTo(getOwner());
    }

    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
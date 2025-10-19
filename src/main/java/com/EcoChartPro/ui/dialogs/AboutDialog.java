package com.EcoChartPro.ui.dialogs;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.net.URI;
import java.net.URL;

public class AboutDialog extends JDialog {

    public AboutDialog(Frame owner) {
        super(owner, "About Eco Chart Pro", true);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("About", createAboutPanel());
        tabbedPane.addTab("Support the Project ❤️", createSupportPanel());

        // --- Bottom Panel: Close Button ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        bottomPanel.add(closeButton);

        // --- Assemble Dialog ---
        add(tabbedPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(750, 500);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel createAboutPanel() {
        // --- Main content panel with side-by-side layout ---
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        GridBagConstraints gbc = new GridBagConstraints();

        // --- Left Panel: Image ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.4;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 0, 0, 20);

        JLabel imageLabel = new JLabel();
        URL imageUrl = getClass().getResource("/images/about-figure.png");
        if (imageUrl != null) {
            ImageIcon originalIcon = new ImageIcon(imageUrl);
            Image image = originalIcon.getImage();
            int targetHeight = 350;
            int newWidth = (int) (((double) targetHeight / image.getHeight(null)) * image.getWidth(null));
            Image scaledImage = image.getScaledInstance(newWidth, targetHeight, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaledImage));
        } else {
            imageLabel.setText("Image not found");
        }
        mainPanel.add(imageLabel, gbc);

        // --- Right Panel: Info ---
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Eco Chart Pro");
        titleLabel.setFont(UIManager.getFont("app.font.heading"));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel infoPanel = createInfoGridPanel();
        infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- Action Buttons (Styled as links) ---
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setOpaque(false);
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        buttonsPanel.add(createLinkButton("Visit Website", "https://github.com/rphlmrk/EcoChartPro"));
        buttonsPanel.add(createLinkButton("View Release Notes", "https://github.com/rphlmrk/EcoChartPro/releases"));
        buttonsPanel.add(createLinkButton("Check for Updates", e -> JOptionPane.showMessageDialog(this, "You are running the latest version: 25.9.17", "Update Check", JOptionPane.INFORMATION_MESSAGE)));

        rightPanel.add(titleLabel);
        rightPanel.add(Box.createVerticalStrut(20));
        rightPanel.add(infoPanel);
        rightPanel.add(Box.createVerticalStrut(20));
        rightPanel.add(buttonsPanel);
        rightPanel.add(Box.createVerticalGlue()); // Push content to the top

        gbc.gridx = 1;
        gbc.weightx = 0.6;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(rightPanel, gbc);

        return mainPanel;
    }

    private JPanel createInfoGridPanel() {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setOpaque(false);
        GridBagConstraints infoGbc = new GridBagConstraints();
        infoGbc.insets = new Insets(4, 0, 4, 10);
        infoGbc.anchor = GridBagConstraints.WEST;

        String version = "25.9.17";

        infoGbc.gridx = 0; infoGbc.gridy = 0; infoPanel.add(new JLabel("Version:"), infoGbc);
        infoGbc.gridx = 1; infoPanel.add(new JLabel(version), infoGbc);
        infoGbc.gridx = 0; infoGbc.gridy = 1; infoPanel.add(new JLabel("Author:"), infoGbc);
        infoGbc.gridx = 1; infoPanel.add(new JLabel("Raphael Mark"), infoGbc);
        infoGbc.gridx = 0; infoGbc.gridy = 2; infoPanel.add(new JLabel("License:"), infoGbc);
        infoGbc.gridx = 1; infoPanel.add(new JLabel("MIT License"), infoGbc);
        infoGbc.gridx = 2; infoGbc.weightx = 1.0; infoPanel.add(new JLabel(), infoGbc); // Glue

        return infoPanel;
    }

    private JComponent createSupportPanel() {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        editorPane.setOpaque(false);

        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                URL url = e.getURL();
                if (url != null && url.toString().toLowerCase().endsWith(".png")) {
                    // It's a QR code image link
                    try {
                        ImageIcon icon = new ImageIcon(url);
                        JLabel imageLabel = new JLabel(icon);
                        JDialog imageDialog = new JDialog(this, "QR Code", true);
                        imageDialog.getContentPane().add(new JScrollPane(imageLabel));
                        imageDialog.pack();
                        imageDialog.setLocationRelativeTo(this);
                        imageDialog.setVisible(true);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else if (url != null) {
                    // It's a regular web link
                    try {
                        Desktop.getDesktop().browse(url.toURI());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        URL binanceQR = getClass().getResource("/docs/images/binance-pay-qr.png");
        URL usdtQR = getClass().getResource("/docs/images/usdt-trc20-qr.png");

        Font defaultFont = UIManager.getFont("Label.font");
        String fontFamily = defaultFont.getFamily();
        int fontSize = defaultFont.getSize();

        int bodyColor = UIManager.getColor("Label.foreground").getRGB() & 0xFFFFFF;
        int linkColor = UIManager.getColor("Component.focusedBorderColor").getRGB() & 0xFFFFFF;
        int codeBgColor = UIManager.getColor("Component.borderColor").getRGB() & 0xFFFFFF;

        editorPane.setText(String.format("""
            <html><body style='font-family: %s; font-size: %dpt; color: #%s; padding: 15px;'>
                <h2>Support the Project ❤️</h2>
                <p>If you find Eco Chart Pro useful, please consider supporting its continued development. Your support helps cover costs and motivates the implementation of new features on the roadmap.</p>
                
                <h3>⭐ Star the Repository</h3>
                <p>The quickest way to show support is by starring the project on <a style='color: #%s;' href="https://github.com/rphlmrk/EcoChartPro">GitHub</a>!</p>
                
                <h3>💸 Make a Donation</h3>
                <p>If you'd like to contribute financially, you can use any of the methods below.</p>
                
                <hr style='border-color: #%s;'>
                <h4>PayPal</h4>
                <p><a style='color: #%s;' href="https://www.paypal.com/paypalme/raphaelochieng">https://www.paypal.com/paypalme/raphaelochieng</a></p>
                
                <hr style='border-color: #%s;'>
                <h4>Binance Pay</h4>
                <p>Scan the QR code below with your Binance App.</p>
                %s
                
                <hr style='border-color: #%s;'>
                <h4>Crypto (USDT TRC20)</h4>
                <p>Send USDT to the following address on the <b>Tron (TRC20)</b> network.</p>
                <p style='background-color:#%s; padding: 5px; border-radius: 3px; font-family: monospace;'>TCnAh8RH9dyeAwe4deWLPRKEASmct6QUpR</p>
                %s
            </body></html>
            """,
            fontFamily, fontSize, Integer.toHexString(bodyColor),
            Integer.toHexString(linkColor),
            Integer.toHexString(codeBgColor),
            Integer.toHexString(linkColor),
            Integer.toHexString(codeBgColor),
            binanceQR != null ? "<a href='" + binanceQR + "'><img src='" + binanceQR + "' width='150' height='150'></a>" : "<p><i>[QR Code Image Not Found]</i></p>",
            Integer.toHexString(codeBgColor),
            Integer.toHexString(codeBgColor),
            usdtQR != null ? "<a href='" + usdtQR + "'><img src='" + usdtQR + "' width='150' height='150'></a>" : "<p><i>[QR Code Image Not Found]</i></p>"
        ));

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private JButton createLinkButton(String text, String url) {
        return createLinkButton(text, e -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private JButton createLinkButton(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setForeground(UIManager.getColor("Component.focusedBorderColor"));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        button.addActionListener(action);
        return button;
    }
}
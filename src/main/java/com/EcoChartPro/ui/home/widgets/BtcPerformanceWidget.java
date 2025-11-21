package com.EcoChartPro.ui.home.widgets;

import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.home.theme.UITheme;
import com.EcoChartPro.ui.Analysis.TitledContentPanel;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.function.Consumer;

public class BtcPerformanceWidget extends TitledContentPanel {

    private final JLabel priceLabel;
    private final JLabel change24hLabel;
    private final JLabel change24hPercentLabel;

    private final Color positiveColor = UIManager.getColor("app.color.positive");
    private final Color negativeColor = UIManager.getColor("app.color.negative");

    private BigDecimal lastPrice = BigDecimal.ZERO;
    private static final String BTC_SYMBOL = "btcusdt";
    private static final String BTC_TIMEFRAME = "1m";

    private final BinanceProvider binanceProvider = new BinanceProvider();
    private Consumer<KLine> liveDataConsumer; // Field to hold the consumer instance

    public BtcPerformanceWidget() {
        super("BTC/USDT Performance", new JPanel(new GridBagLayout()));
        JPanel content = (JPanel) this.getContentPane();
        content.setOpaque(false);

        priceLabel = new JLabel("Loading...");
        priceLabel.setFont(UIManager.getFont("app.font.value_large"));

        change24hLabel = new JLabel("-");
        change24hLabel.setFont(UIManager.getFont("app.font.widget_content"));

        change24hPercentLabel = new JLabel("-");
        change24hPercentLabel.setFont(UIManager.getFont("app.font.widget_content"));

        setupLayout(content);
        fetchInitialData();
        subscribeToLiveData();
    }

    private void setupLayout(JPanel panel) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 0: Price
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(priceLabel, gbc);

        // Row 1: 24h Change
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(change24hLabel, gbc);

        // Row 1: 24h Percent Change
        gbc.gridx = 1;
        panel.add(change24hPercentLabel, gbc);
        
        // Icon (optional, for flair)
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.weightx = 1.0;
        JLabel btcIcon = new JLabel(UITheme.getIcon(UITheme.Icons.BTC, 48, 48));
        panel.add(btcIcon, gbc);
    }

    private void fetchInitialData() {
        new SwingWorker<BinanceProvider.TickerData, Void>() {
            @Override
            protected BinanceProvider.TickerData doInBackground() throws Exception {
                return binanceProvider.get24hTickerData(BTC_SYMBOL);
            }

            @Override
            protected void done() {
                try {
                    BinanceProvider.TickerData data = get();
                    if (data != null) {
                        update24hStats(data);
                        lastPrice = new BigDecimal(data.lastPrice);
                        priceLabel.setText(String.format("%,.2f", lastPrice));
                    }
                } catch (Exception e) {
                    priceLabel.setText("Error");
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void subscribeToLiveData() {
        this.liveDataConsumer = kline -> {
            SwingUtilities.invokeLater(() -> {
                BigDecimal newPrice = kline.close();
                priceLabel.setText(String.format("%,.2f", newPrice));

                int comparison = newPrice.compareTo(lastPrice);
                if (comparison > 0) {
                    priceLabel.setForeground(positiveColor);
                } else if (comparison < 0) {
                    priceLabel.setForeground(negativeColor);
                }
                
                lastPrice = newPrice;
            });
        };
        LiveDataManager.getInstance().subscribeToKLine(BTC_SYMBOL, BTC_TIMEFRAME, this.liveDataConsumer);
    }

    private void update24hStats(BinanceProvider.TickerData data) {
        BigDecimal priceChange = new BigDecimal(data.priceChange);
        BigDecimal percentChange = new BigDecimal(data.priceChangePercent);
        
        DecimalFormat priceFormat = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        DecimalFormat percentFormat = new DecimalFormat("+#,##0.00'%'");

        change24hLabel.setText(priceFormat.format(priceChange));
        change24hPercentLabel.setText(percentFormat.format(percentChange));

        Color changeColor = priceChange.signum() >= 0 ? positiveColor : negativeColor;
        change24hLabel.setForeground(changeColor);
        change24hPercentLabel.setForeground(changeColor);
    }

    public void cleanup() {
        // Unsubscribe from the WebSocket to prevent resource leaks
        if (this.liveDataConsumer != null) {
            LiveDataManager.getInstance().unsubscribeFromKLine(BTC_SYMBOL, BTC_TIMEFRAME, this.liveDataConsumer);
        }
    }
}
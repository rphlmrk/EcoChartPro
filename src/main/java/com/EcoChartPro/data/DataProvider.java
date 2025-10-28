package com.EcoChartPro.data;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.SymbolInfo;
import com.EcoChartPro.model.TradeTick;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * The core interface for the data abstraction layer. It defines the contract
 * that any data source (local files, REST APIs, WebSockets) must implement
 * to be integrated into the application.
 */
public interface DataProvider {

    /**
     * A user-friendly name for this provider (e.g., "Local Files", "Binance").
     */
    String getProviderName();

    /**
     * Scans for and returns a list of all available symbols from this provider.
     * <p>
     * Note: This returns ChartDataSource for now for backward compatibility with the UI.
     * In the future, this might be refactored to return a list of a more generic SymbolInfo object.
     *
     * @return A list of available ChartDataSource objects.
     */
    List<ChartDataSource> getAvailableSymbols();

    /**
     * Fetches a limited number of historical K-line records for a given symbol and timeframe.
     * @param symbol The symbol to fetch (e.g., "BTC/USDT").
     * @param timeframe The timeframe to fetch (e.g., "1H").
     * @param limit The maximum number of records to retrieve.
     * @return A list of KLine data.
     */
    List<KLine> getHistoricalData(String symbol, String timeframe, int limit);

    /**
     * Fetches individual historical trades for a symbol.
     * @param symbol The symbol to fetch.
     * @param startTimeMillis The start time for the data fetch.
     * @param limit The maximum number of trades.
     * @return A list of TradeTick data.
     */
    List<TradeTick> getHistoricalTrades(String symbol, long startTimeMillis, int limit);

    /**
     * Connects to a live data stream for a given symbol and timeframe.
     * @param symbol The symbol to stream.
     * @param timeframe The timeframe for K-line streams.
     * @param onKLineUpdate A callback function that will be invoked with each new K-line update.
     */
     void connectToLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate);

    /**
     * Disconnects from a previously established live data stream.
     * @param symbol The symbol to stop streaming.
     * @param timeframe The timeframe to stop streaming.
     */
     void disconnectFromLiveStream(String symbol, String timeframe, Consumer<KLine> onKLineUpdate);
     
    /**
     * Connects to a live trade stream for a given symbol.
     * @param symbol The symbol to stream.
     * @param onTradeUpdate A callback for each new trade.
     */
    void connectToTradeStream(String symbol, Consumer<TradeTick> onTradeUpdate);

    /**
     * Disconnects from a live trade stream.
     * @param symbol The symbol to stop streaming.
     * @param onTradeUpdate The callback to remove.
     */
    void disconnectFromTradeStream(String symbol, Consumer<TradeTick> onTradeUpdate);
}
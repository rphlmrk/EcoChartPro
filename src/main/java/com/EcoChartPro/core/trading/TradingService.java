package com.EcoChartPro.core.trading;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Defines the contract for a simulated trading brokerage.
 * It handles order management, position tracking, and account balance.
 */
public interface TradingService {

    void placeOrder(Order order, KLine currentBar);

    void modifyOrder(UUID orderId, BigDecimal newPrice, BigDecimal newStopLoss, BigDecimal newTakeProfit, BigDecimal newTrailingStopDistance);

    void cancelOrder(UUID orderId);

    void closePosition(UUID positionId, KLine closingBar);

    List<Position> getOpenPositions();

    List<Order> getPendingOrders();

    List<Trade> getTradeHistory();

    BigDecimal getAccountBalance();
    
    ReplaySessionState getCurrentSessionState();

    void onBarUpdate(KLine newBar);

    void resetSession(BigDecimal startingBalance, BigDecimal leverage);

    BigDecimal getLeverage();

    @Deprecated
    void updateTradeNotes(UUID tradeId, String notes);

    void updateTradeJournalEntry(UUID tradeId, String notes, List<String> tags);

    /**
     * NEW: Updates a trade with detailed reflection and journal data.
     * @param trade The trade object to update. This object's fields have already been modified.
     */
    void updateTradeJournalReflection(Trade trade);

}
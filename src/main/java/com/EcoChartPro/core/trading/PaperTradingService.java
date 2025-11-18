package com.EcoChartPro.core.trading;

import com.EcoChartPro.core.controller.LiveWindowManager;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.journal.AutomatedTaggingService;
import com.EcoChartPro.core.service.PnlCalculationService;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.data.provider.OkxProvider;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.OrderType;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.trading.JournalEntryDialog;
import com.EcoChartPro.utils.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PaperTradingService implements TradingService {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingService.class);

    // --- State is now held per instance, representing a single session context ---
    private BigDecimal accountBalance = BigDecimal.ZERO;
    private BigDecimal leverage = BigDecimal.ONE;
    private final Map<String, Map<UUID, Position>> openPositionsBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Order>> pendingOrdersBySymbol = new ConcurrentHashMap<>();
    private final Map<String, List<Trade>> tradeHistoryBySymbol = new ConcurrentHashMap<>();
    private String activeSymbol;

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final AutomatedTaggingService automatedTaggingService;
    private final DrawingManager drawingManager;

    // [NEW] In-memory cache for K-lines of active trades in the current session.
    private final Map<UUID, List<KLine>> activeTradeCandles = new ConcurrentHashMap<>();
    private static final int CANDLE_BUFFER = 10; // Number of candles to save before a trade

    public PaperTradingService(DrawingManager drawingManager) {
        this.drawingManager = drawingManager;
        this.automatedTaggingService = new AutomatedTaggingService();
    }
    
    /**
     * [NEW] Switches the active context for the trading service.
     * Ensures that data structures exist for the new symbol.
     * @param newSymbol The symbol identifier to switch to (e.g., "btcusdt").
     */
    public void switchActiveSymbol(String newSymbol) {
        if (newSymbol != null && !newSymbol.equals(this.activeSymbol)) {
            logger.debug("PaperTradingService switching active symbol to: {}", newSymbol);
            this.activeSymbol = newSymbol;
            // Ensure data structures are initialized for the new symbol
            this.openPositionsBySymbol.computeIfAbsent(newSymbol, k -> new ConcurrentHashMap<>());
            this.pendingOrdersBySymbol.computeIfAbsent(newSymbol, k -> new ConcurrentHashMap<>());
            this.tradeHistoryBySymbol.computeIfAbsent(newSymbol, k -> Collections.synchronizedList(new ArrayList<>()));
            
            pcs.firePropertyChange("openPositionsUpdated", null, getOpenPositions());
            pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
            pcs.firePropertyChange("tradeHistoryUpdated", null, getTradeHistory());
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

    @Override
    public ReplaySessionState getCurrentSessionState() {
        ReplaySessionManager rsm = ReplaySessionManager.getInstance();

        Map<String, SymbolSessionState> allSymbolStates = new HashMap<>();

        // Create a set of all symbols that have any state associated with them
        Set<String> allSymbols = new HashSet<>();
        allSymbols.addAll(this.openPositionsBySymbol.keySet());
        allSymbols.addAll(this.pendingOrdersBySymbol.keySet());
        allSymbols.addAll(this.tradeHistoryBySymbol.keySet());
        allSymbols.addAll(drawingManager.getAllKnownSymbols());
        allSymbols.addAll(rsm.getAllKnownSymbols());

        for (String symbol : allSymbols) {
            SymbolSessionState symbolState = new SymbolSessionState(
                rsm.getReplayHeadIndex(symbol),
                new ArrayList<>(this.openPositionsBySymbol.getOrDefault(symbol, Collections.emptyMap()).values()),
                new ArrayList<>(this.pendingOrdersBySymbol.getOrDefault(symbol, Collections.emptyMap()).values()),
                new ArrayList<>(this.tradeHistoryBySymbol.getOrDefault(symbol, Collections.emptyList())),
                drawingManager.getAllDrawingsForSymbol(symbol),
                rsm.getLastTimestamp(symbol)
            );
            allSymbolStates.put(symbol, symbolState);
        }
        
        String lastActiveSymbol;
        // This is a bit of a hack since the service doesn't know if it's in Replay or Live mode.
        // ReplaySessionManager's active symbol is the source of truth for replay.
        if (rsm.getActiveSymbol() != null) {
            lastActiveSymbol = rsm.getActiveSymbol();
        } else { // LIVE
            LiveWindowManager lwm = LiveWindowManager.getInstance();
            if (lwm.isActive() && lwm.getActiveDataSource() != null) {
                lastActiveSymbol = lwm.getActiveDataSource().symbol();
            } else {
                lastActiveSymbol = this.activeSymbol;
            }
        }

        return new ReplaySessionState(
            this.accountBalance,
            lastActiveSymbol,
            allSymbolStates
        );
    }


    public void restoreState(ReplaySessionState state) {
        resetSession(state.accountBalance(), BigDecimal.ONE); 

        if (state.symbolStates() != null) {
            for (Map.Entry<String, SymbolSessionState> entry : state.symbolStates().entrySet()) {
                String symbol = entry.getKey();
                SymbolSessionState symbolState = entry.getValue();

                if (symbolState.tradeHistory() != null) {
                    this.tradeHistoryBySymbol.computeIfAbsent(symbol, k -> Collections.synchronizedList(new ArrayList<>())).addAll(symbolState.tradeHistory());
                }
                if (symbolState.pendingOrders() != null) {
                    this.pendingOrdersBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).putAll(
                        symbolState.pendingOrders().stream().collect(Collectors.toMap(Order::id, Function.identity()))
                    );
                }
                if (symbolState.openPositions() != null) {
                    this.openPositionsBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).putAll(
                        symbolState.openPositions().stream().collect(Collectors.toMap(Position::id, Function.identity()))
                    );
                }
            }
        }
        
        switchActiveSymbol(state.lastActiveSymbol());

        logger.info("Paper trading multi-symbol state restored. Active Symbol: {}. Balance: {}", this.activeSymbol, this.accountBalance);
    }

    @Override
    public void onBarUpdate(KLine newBar) {
        if (this.activeSymbol == null) return;
        
        // [FIX] Make a copy of open positions before checking for fills to prevent opening and closing on the same bar.
        List<Position> positionsAtBarStart = new ArrayList<>(getOpenPositions());

        for (Position position : positionsAtBarStart) {
            List<KLine> candles = activeTradeCandles.get(position.id());
            if (candles != null) {
                candles.add(newBar);
            }
        }
        
        checkPendingOrders(newBar, this.activeSymbol);
        updateTrailingStops(newBar, this.activeSymbol);
        // [FIX] Pass the copy of positions that existed at the start of the bar.
        checkOpenPositions(newBar, this.activeSymbol, positionsAtBarStart);
        
        if (!getOpenPositions().isEmpty()) {
            Map<UUID, BigDecimal> pnlMap = PnlCalculationService.getInstance()
                    .calculateUnrealizedPnl(getOpenPositions(), newBar);
            pcs.firePropertyChange("unrealizedPnlCalculated", null, pnlMap);
        }
    }

    public void updateLivePnl(KLine currentBar) {
        if (this.activeSymbol == null || currentBar == null) return;

        List<Position> openPositions = getOpenPositions();
        if (!openPositions.isEmpty()) {
            Map<UUID, BigDecimal> pnlMap = PnlCalculationService.getInstance()
                    .calculateUnrealizedPnl(openPositions, currentBar);
            pcs.firePropertyChange("unrealizedPnlCalculated", null, pnlMap);
        }
    }

    private void checkPendingOrders(KLine bar, String symbol) {
        Map<UUID, Order> symbolOrders = this.pendingOrdersBySymbol.get(symbol);
        if (symbolOrders == null || symbolOrders.isEmpty()) return;

        boolean ordersChanged = false;
        try {
            for (Order order : new ArrayList<>(symbolOrders.values())) {
                BigDecimal fillPrice = null;
                if (order.direction() == TradeDirection.LONG) {
                    if (order.type() == OrderType.LIMIT && bar.low().compareTo(order.limitPrice()) <= 0) {
                        fillPrice = order.limitPrice();
                    } else if (order.type() == OrderType.STOP && bar.high().compareTo(order.limitPrice()) >= 0) {
                        fillPrice = order.limitPrice();
                    }
                } else { // SHORT
                    if (order.type() == OrderType.LIMIT && bar.high().compareTo(order.limitPrice()) >= 0) {
                        fillPrice = order.limitPrice();
                    } else if (order.type() == OrderType.STOP && bar.low().compareTo(order.limitPrice()) <= 0) {
                        fillPrice = order.limitPrice();
                    }
                }
                if (fillPrice != null) {
                    openPosition(order, fillPrice, bar.timestamp());
                    symbolOrders.remove(order.id());
                    logger.info("Filled order {} for symbol {} at price {}", order.id(), symbol, fillPrice);
                    ordersChanged = true;
                }
            }
        } finally {
            // [FIX] Ensure UI is notified even if an error occurs.
            if (ordersChanged) {
                pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
            }
        }
    }

    private void updateTrailingStops(KLine bar, String symbol) {
        Map<UUID, Position> symbolPositions = this.openPositionsBySymbol.get(symbol);
        if (symbolPositions == null || symbolPositions.isEmpty()) return;

        symbolPositions.values().forEach(position -> {
            if (position.trailingStopDistance() == null || position.trailingStopDistance().compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal newStopLoss = null;
            if (position.direction() == TradeDirection.LONG) {
                BigDecimal potentialNewSl = bar.high().subtract(position.trailingStopDistance());
                if (position.stopLoss() == null || potentialNewSl.compareTo(position.stopLoss()) > 0) {
                    newStopLoss = potentialNewSl;
                }
            } else { // SHORT
                BigDecimal potentialNewSl = bar.low().add(position.trailingStopDistance());
                if (position.stopLoss() == null || potentialNewSl.compareTo(position.stopLoss()) < 0) {
                    newStopLoss = potentialNewSl;
                }
            }

            if (newStopLoss != null) {
                modifyOrderInternal(position.id(), null, newStopLoss, position.takeProfit(), position.trailingStopDistance());
                logger.info("Position {} SL for symbol {} trailed to {}", position.id(), symbol, newStopLoss.toPlainString());
            }
        });
    }

    private void checkOpenPositions(KLine bar, String symbol, List<Position> positionsToCheck) {
        if (positionsToCheck == null || positionsToCheck.isEmpty()) return;
        
        new ArrayList<>(positionsToCheck).forEach(position -> {
            // Re-check if position still exists, as it might have been closed by another logic path
            if (!this.openPositionsBySymbol.getOrDefault(symbol, Collections.emptyMap()).containsKey(position.id())) {
                return;
            }

            BigDecimal closePrice = null;
            String closeReason = "";
            if (position.direction() == TradeDirection.LONG) {
                if (position.stopLoss() != null && bar.low().compareTo(position.stopLoss()) <= 0) {
                    closePrice = position.stopLoss();
                    closeReason = "Stop Loss";
                } else if (position.takeProfit() != null && bar.high().compareTo(position.takeProfit()) >= 0) {
                    closePrice = position.takeProfit();
                    closeReason = "Take Profit";
                }
            } else { // SHORT
                if (position.stopLoss() != null && bar.high().compareTo(position.stopLoss()) >= 0) {
                    closePrice = position.stopLoss();
                    closeReason = "Stop Loss";
                } else if (position.takeProfit() != null && bar.low().compareTo(position.takeProfit()) <= 0) {
                    closePrice = position.takeProfit();
                    closeReason = "Take Profit";
                }
            }
            if (closePrice != null) {
                Trade closedTrade = finalizeTrade(position, closePrice, bar.timestamp(), true);
                logger.info("Closed position {} for symbol {} via {}.", position.id(), symbol, closeReason);

                if (SettingsService.getInstance().isAutoJournalOnTradeClose()) {
                    ReplaySessionManager.getInstance().pause();
                    pcs.firePropertyChange("tradeClosedForJournaling", null, closedTrade);
                }
            }
        });
    }

    @Override
    public void placeOrder(Order order, KLine currentBar) {
        placeOrderInternal(order, currentBar);
    }

    private synchronized void placeOrderInternal(Order order, KLine currentBar) {
        String symbol = order.symbol().name();
        
        try {
            if (order.type() == OrderType.MARKET) {
                if (currentBar == null) {
                    logger.error("Cannot place market order without a current bar context.");
                    return;
                }
                openPosition(order, currentBar.close(), currentBar.timestamp());
                logger.info("Filled market order {} for {} at {}", order.id(), symbol, currentBar.close());
            } else {
                this.pendingOrdersBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).put(order.id(), order);
                logger.info("Placed pending order for {}: {}", symbol, order);
            }
        } finally {
            // [FIX] Ensure UI is notified of the new pending order. openPosition handles its own notifications.
            if (order.type() != OrderType.MARKET) {
                pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
            }
        }
    }

    private void openPosition(Order fromOrder, BigDecimal entryPrice, Instant timestamp) {
        String symbol = fromOrder.symbol().name();
        Position newPosition = new Position(
            fromOrder.id(), fromOrder.symbol(), fromOrder.direction(),
            fromOrder.size(), entryPrice, fromOrder.stopLoss(),
            fromOrder.takeProfit(), fromOrder.trailingStopDistance(),
            timestamp, fromOrder.checklistId()
        );
        
        try {
            this.openPositionsBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).put(newPosition.id(), newPosition);

            // We assume Live mode if ReplaySessionManager is not active on this symbol
            boolean isLiveMode = ReplaySessionManager.getInstance().getActiveSymbol() == null;
            if (isLiveMode) {
                List<KLine> entryBuffer = fetchCandleBuffer(symbol, timestamp, CANDLE_BUFFER);
                activeTradeCandles.put(newPosition.id(), Collections.synchronizedList(new ArrayList<>(entryBuffer)));
                logger.info("Initialized candle cache for new position {} with {} buffer candles.", newPosition.id(), entryBuffer.size());
            }
    
            BigDecimal commission = SettingsService.getInstance().getCommissionPerTrade();
            if (commission != null && commission.compareTo(BigDecimal.ZERO) > 0) {
                this.accountBalance = this.accountBalance.subtract(commission);
                logger.info("Applied entry commission of {} for trade {}. New balance: {}",
                    commission, newPosition.id(), this.accountBalance);
            }
        } finally {
            // [FIX] Ensure UI is always notified about the new open position.
            pcs.firePropertyChange("openPositionsUpdated", null, getOpenPositions());
        }
    }

    private Trade finalizeTrade(Position position, BigDecimal exitPrice, Instant exitTime, boolean planFollowed) {
        // [MODIFIED] All state modifications are now wrapped in a try block.
        String symbol = position.symbol().name();
        Trade completedTrade = null;
        try {
            BigDecimal pnl;
            if (position.direction() == TradeDirection.LONG) {
                pnl = exitPrice.subtract(position.entryPrice()).multiply(position.size());
                BigDecimal spread = SettingsService.getInstance().getSimulatedSpreadPoints();
                if (spread != null && spread.compareTo(BigDecimal.ZERO) > 0) {
                    pnl = pnl.subtract(spread.multiply(position.size()));
                }
            } else { // SHORT
                pnl = position.entryPrice().subtract(exitPrice).multiply(position.size());
                BigDecimal spread = SettingsService.getInstance().getSimulatedSpreadPoints();
                if (spread != null && spread.compareTo(BigDecimal.ZERO) > 0) {
                    pnl = pnl.subtract(spread.multiply(position.size()));
                }
            }
    
            List<String> autoTags = new ArrayList<>();
            ReplaySessionManager rsm = ReplaySessionManager.getInstance();
            boolean isLiveMode = rsm.getActiveSymbol() == null;
    
            if (isLiveMode) {
                List<KLine> cachedCandles = activeTradeCandles.get(position.id());
                if (cachedCandles != null && !cachedCandles.isEmpty()) {
                    Trade tempTrade = new Trade(position.id(), position.symbol(), position.direction(), position.openTimestamp(), position.entryPrice(), exitTime, exitPrice, position.size(), pnl, planFollowed);
                    autoTags = automatedTaggingService.generateTags(tempTrade, cachedCandles);
                }
                List<KLine> candlesToSave = activeTradeCandles.remove(position.id());
                if (candlesToSave != null && !candlesToSave.isEmpty()) {
                    DatabaseManager.getInstance().saveTradeCandles(position.id(), symbol, "1m", candlesToSave);
                } else {
                    logger.warn("No cached candles found for closing trade {}. Data will not be saved.", position.id());
                }
            } else { // REPLAY mode
                if (rsm.getCurrentSource() != null && rsm.getCurrentSource().symbol().equals(symbol)) {
                    try (DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + rsm.getCurrentSource().dbPath().toAbsolutePath())) {
                        List<KLine> tradeKlines = db.getKLinesBetween(new Symbol(symbol), "1m", position.openTimestamp(), exitTime);
                        Trade tempTrade = new Trade(position.id(), position.symbol(), position.direction(), position.openTimestamp(), position.entryPrice(), exitTime, exitPrice, position.size(), pnl, planFollowed);
                        autoTags = automatedTaggingService.generateTags(tempTrade, tradeKlines);
                        if (tradeKlines != null && !tradeKlines.isEmpty()) {
                            DatabaseManager.getInstance().saveTradeCandles(position.id(), symbol, "1m", tradeKlines);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to retrieve or save K-lines for replay mode trade.", e);
                    }
                }
            }
            
            completedTrade = new Trade(
                position.id(), position.symbol(), position.direction(), position.openTimestamp(),
                position.entryPrice(), exitTime, exitPrice, position.size(), pnl, planFollowed,
                null, autoTags, position.checklistId()
            );
            this.tradeHistoryBySymbol.computeIfAbsent(symbol, k -> Collections.synchronizedList(new ArrayList<>())).add(completedTrade);
            this.accountBalance = this.accountBalance.add(pnl);
            this.openPositionsBySymbol.get(symbol).remove(position.id());
            
            logger.info("Trade finalized for {}. Position: {}. PnL: {}. Plan Followed: {}. New Balance: {}. Auto-tags: {}", symbol, position.id(), pnl, planFollowed, this.accountBalance, autoTags);
        } finally {
            // [FIX] UI notifications are now in a finally block to guarantee execution.
            pcs.firePropertyChange("tradeHistoryUpdated", null, getTradeHistory());
            pcs.firePropertyChange("openPositionsUpdated", null, getOpenPositions());
        }
        return completedTrade;
    }
    
    private List<KLine> fetchCandleBuffer(String symbol, Instant beforeTime, int limit) {
        DataProvider provider = null;
        if (symbol.contains("-")) {
            provider = new OkxProvider();
        } else {
            provider = new BinanceProvider();
        }

        try {
            if (provider instanceof BinanceProvider bp) {
                return bp.getHistoricalData(symbol, "1m", limit, null, beforeTime.toEpochMilli() - 1);
            } else if (provider instanceof OkxProvider op) {
                return op.getHistoricalData(symbol, "1m", limit, null, beforeTime.toEpochMilli());
            }
        } catch (IOException e) {
            logger.error("Failed to fetch pre-trade candle buffer for symbol {}: {}", symbol, e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public void modifyOrder(UUID orderId, BigDecimal newPrice, BigDecimal newStopLoss, BigDecimal newTakeProfit, BigDecimal newTrailingStopDistance) {
        modifyOrderInternal(orderId, newPrice, newStopLoss, newTakeProfit, newTrailingStopDistance);
    }

    private synchronized void modifyOrderInternal(UUID orderId, BigDecimal newPrice, BigDecimal newStopLoss, BigDecimal newTakeProfit, BigDecimal newTrailingStopDistance) {
        Optional<String> symbolOpt = findSymbolForTradable(orderId);
        if (symbolOpt.isEmpty()) {
            logger.warn("Could not find order/position with ID {} to modify.", orderId);
            return;
        }
        String symbol = symbolOpt.get();

        try {
            Order existingOrder = this.pendingOrdersBySymbol.get(symbol).get(orderId);
            if (existingOrder != null) {
                Order updatedOrder = new Order(
                    existingOrder.id(), existingOrder.symbol(), existingOrder.type(),
                    existingOrder.status(), existingOrder.direction(), existingOrder.size(),
                    newPrice, newStopLoss, newTakeProfit, newTrailingStopDistance,
                    existingOrder.creationTime(), existingOrder.checklistId()
                );
                this.pendingOrdersBySymbol.get(symbol).put(orderId, updatedOrder);
                logger.info("Modified pending order {} for symbol {}", orderId, symbol);
                return;
            }
            Position existingPosition = this.openPositionsBySymbol.get(symbol).get(orderId);
            if (existingPosition != null) {
                 Position updatedPosition = new Position(
                    existingPosition.id(), existingPosition.symbol(), existingPosition.direction(),
                    existingPosition.size(), existingPosition.entryPrice(),
                    newStopLoss, newTakeProfit, newTrailingStopDistance,
                    existingPosition.openTimestamp(), existingPosition.checklistId()
                 );
                 this.openPositionsBySymbol.get(symbol).put(orderId, updatedPosition);
                 logger.info("Modified open position SL/TP for {} on symbol {}", orderId, symbol);
            }
        } finally {
            // [FIX] Ensure UI is notified regardless of whether an order or position was modified.
            pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
            pcs.firePropertyChange("openPositionsUpdated", null, getOpenPositions());
        }
    }

    @Override
    public void updateTradeJournalEntry(UUID tradeId, String notes, List<String> tags) {
        findTradeById(tradeId).ifPresent(trade -> {
            trade.setNotes(notes);
            trade.setTags(tags);
            logger.info("Updated basic journal entry for trade ID: {}", tradeId);
            pcs.firePropertyChange("tradeHistoryUpdated", null, getTradeHistory());
        });
    }

    @Override
    public void updateTradeJournalReflection(Trade updatedTrade) {
        if (updatedTrade == null || updatedTrade.id() == null) {
            logger.warn("Attempted to update trade journal with a null trade or ID.");
            return;
        }
        String symbol = updatedTrade.symbol().name();
        List<Trade> history = this.tradeHistoryBySymbol.get(symbol);
        if (history == null) {
            logger.warn("Attempted to update journal for trade {} in a symbol ({}) with no history.", updatedTrade.id(), symbol);
            return;
        }

        boolean wasUpdated = false;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).id().equals(updatedTrade.id())) {
                history.set(i, updatedTrade);
                wasUpdated = true;
                break;
            }
        }
        
        if (wasUpdated) {
            logger.info("Updated detailed journal reflection for trade ID: {}", updatedTrade.id());
            if (updatedTrade.identifiedMistakes() != null && !updatedTrade.identifiedMistakes().isEmpty()
                && !(updatedTrade.identifiedMistakes().size() == 1 && "No Mistakes Made".equals(updatedTrade.identifiedMistakes().get(0)))) {
                pcs.firePropertyChange("mistakeLogged", null, updatedTrade);
            }
            pcs.firePropertyChange("tradeHistoryUpdated", null, getTradeHistory());
        } else {
            logger.warn("Attempted to update journal for a non-existent trade with ID: {}", updatedTrade.id());
        }
    }

    @Override
    public void cancelOrder(UUID orderId) {
        cancelOrderInternal(orderId);
    }

    private synchronized void cancelOrderInternal(UUID orderId) {
        findSymbolForTradable(orderId).ifPresent(symbol -> {
            try {
                if (this.pendingOrdersBySymbol.get(symbol).remove(orderId) != null) {
                    logger.info("Cancelled pending order {} for symbol {}", orderId, symbol);
                }
            } finally {
                // [FIX] Always notify the UI after an attempted cancellation.
                pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
            }
        });
    }

    @Override
    public void closePosition(UUID positionId, KLine closingBar) {
        closePositionInternal(positionId, closingBar);
    }

    private synchronized void closePositionInternal(UUID positionId, KLine closingBar) {
        Optional<Position> positionOpt = findPositionById(positionId);
        if (positionOpt.isEmpty()) return;
        
        Position positionToClose = positionOpt.get();
        if (closingBar == null) {
            logger.error("Cannot close position at market without a closing bar context.");
            return;
        }
        Trade closedTrade = finalizeTrade(positionToClose, closingBar.close(), closingBar.timestamp(), false);
        logger.info("Market-closed position: {}", positionId);

        if (SettingsService.getInstance().isAutoJournalOnTradeClose()) {
            ReplaySessionManager.getInstance().pause();
            pcs.firePropertyChange("tradeClosedForJournaling", null, closedTrade);
        }
    }

    @Override
    public void resetSession(BigDecimal startingBalance, BigDecimal leverage) {
        try {
            this.accountBalance = startingBalance;
            this.leverage = (leverage != null && leverage.compareTo(BigDecimal.ZERO) > 0) ? leverage : BigDecimal.ONE;
            this.openPositionsBySymbol.clear();
            this.pendingOrdersBySymbol.clear();
            this.tradeHistoryBySymbol.clear();
            this.activeSymbol = null;
            activeTradeCandles.clear();
            logger.info("Paper Trading Service session reset. Starting Balance: {}. Leverage: {}x", startingBalance, this.leverage);
        } finally {
            // [FIX] Ensure UI is cleared regardless of what happens during reset.
            pcs.firePropertyChange("openPositionsUpdated", null, Collections.emptyList());
            pcs.firePropertyChange("tradeHistoryUpdated", null, Collections.emptyList());
            pcs.firePropertyChange("pendingOrdersUpdated", null, Collections.emptyList());
        }
    }

    public void importTradeHistory(List<Trade> newHistory, BigDecimal startingBalance) {
        resetSession(startingBalance, BigDecimal.ONE);
        if (newHistory != null && !newHistory.isEmpty()) {
            Map<String, List<Trade>> groupedTrades = newHistory.stream().collect(Collectors.groupingBy(t -> t.symbol().name()));
            this.tradeHistoryBySymbol.putAll(groupedTrades);
            
            BigDecimal totalPnl = newHistory.stream()
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            this.accountBalance = this.accountBalance.add(totalPnl);
        }
        pcs.firePropertyChange("tradeHistoryUpdated", null, new ArrayList<>(this.tradeHistoryBySymbol.values().stream().flatMap(List::stream).collect(Collectors.toList())));
        logger.info("Trade history imported. {} total trades across {} symbols. New balance: {}", newHistory.size(), this.tradeHistoryBySymbol.size(), this.accountBalance);
    }

    @Override
    public List<Position> getOpenPositions() {
        if (this.activeSymbol == null) return Collections.emptyList();
        return new ArrayList<>(this.openPositionsBySymbol.getOrDefault(this.activeSymbol, Collections.emptyMap()).values());
    }

    @Override
    public List<Order> getPendingOrders() {
        if (this.activeSymbol == null) return Collections.emptyList();
        return new ArrayList<>(this.pendingOrdersBySymbol.getOrDefault(this.activeSymbol, Collections.emptyMap()).values());
    }

    @Override
    public List<Trade> getTradeHistory() {
        if (this.activeSymbol == null) return Collections.emptyList();
        return new ArrayList<>(this.tradeHistoryBySymbol.getOrDefault(this.activeSymbol, Collections.emptyList()));
    }

    public boolean hasAnyTradesOrPositions() {
        if (!this.tradeHistoryBySymbol.isEmpty() && this.tradeHistoryBySymbol.values().stream().anyMatch(list -> !list.isEmpty())) {
            return true;
        }
        if (!this.openPositionsBySymbol.isEmpty() && this.openPositionsBySymbol.values().stream().anyMatch(map -> !map.isEmpty())) {
            return true;
        }
        return false;
    }

    @Override
    public BigDecimal getAccountBalance() { return this.accountBalance; }

    @Override
    public BigDecimal getLeverage() { return this.leverage; }
    
    private Optional<String> findSymbolForTradable(UUID id) {
        for (String symbol : this.pendingOrdersBySymbol.keySet()) {
            if (this.pendingOrdersBySymbol.get(symbol).containsKey(id)) {
                return Optional.of(symbol);
            }
        }
        for (String symbol : this.openPositionsBySymbol.keySet()) {
            if (this.openPositionsBySymbol.get(symbol).containsKey(id)) {
                return Optional.of(symbol);
            }
        }
        return Optional.empty();
    }

    private Optional<Position> findPositionById(UUID id) {
        return this.openPositionsBySymbol.values().stream()
            .map(map -> map.get(id))
            .filter(java.util.Objects::nonNull)
            .findFirst();
    }

private Optional<Trade> findTradeById(UUID id) {
    return this.tradeHistoryBySymbol.values().stream()
        .flatMap(List::stream)
        .filter(trade -> trade.id().equals(id))
        .findFirst();
}

    @Override
    @Deprecated
    public void updateTradeNotes(UUID tradeId, String notes) {
        findTradeById(tradeId).ifPresent(trade -> {
            List<String> tags = (trade.tags() != null) ? trade.tags() : Collections.emptyList();
            updateTradeJournalEntry(tradeId, notes, tags);
        });
    }
}
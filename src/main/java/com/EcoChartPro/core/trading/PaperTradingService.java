package com.EcoChartPro.core.trading;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.journal.AutomatedTaggingService;
import com.EcoChartPro.core.service.PnlCalculationService;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
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
    private static volatile PaperTradingService instance;

    // --- NEW: Inner class to hold all state for a given session type (Live or Replay) ---
    private static class SessionContext {
        BigDecimal accountBalance = BigDecimal.ZERO;
        BigDecimal leverage = BigDecimal.ONE;
        final Map<String, Map<UUID, Position>> openPositionsBySymbol = new ConcurrentHashMap<>();
        final Map<String, Map<UUID, Order>> pendingOrdersBySymbol = new ConcurrentHashMap<>();
        final Map<String, List<Trade>> tradeHistoryBySymbol = new ConcurrentHashMap<>();
        // This is a UI helper. It mirrors the trade history of the active symbol.
        final List<Trade> sessionTradeHistory = Collections.synchronizedList(new ArrayList<>());
        String activeSymbol;
    }

    private final Map<SessionType, SessionContext> contexts = new EnumMap<>(SessionType.class);
    private SessionType activeSessionType = SessionType.REPLAY; // Default to REPLAY

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final AutomatedTaggingService automatedTaggingService;

    private PaperTradingService() {
        contexts.put(SessionType.REPLAY, new SessionContext());
        contexts.put(SessionType.LIVE, new SessionContext());
        this.automatedTaggingService = new AutomatedTaggingService();
    }

    public static PaperTradingService getInstance() {
        if (instance == null) {
            synchronized (PaperTradingService.class) {
                if (instance == null) {
                    instance = new PaperTradingService();
                }
            }
        }
        return instance;
    }

    private SessionContext getActiveContext() {
        return contexts.get(activeSessionType);
    }

    /**
     * [NEW] Switches the active session type (LIVE or REPLAY).
     * This determines which set of account data and trade history is used.
     * @param type The session type to activate.
     */
    public void setActiveSessionType(SessionType type) {
        if (type != null && this.activeSessionType != type) {
            logger.info("PaperTradingService active session type switched to: {}", type);
            this.activeSessionType = type;
            // Fire property changes to force UI to refresh with new context data
            pcs.firePropertyChange("openPositionsUpdated", null, getOpenPositions());
            pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
            pcs.firePropertyChange("tradeHistoryUpdated", null, getTradeHistory());
        }
    }
    
    /**
     * [NEW] Switches the active context for the trading service.
     * Ensures that data structures exist for the new symbol.
     * @param newSymbol The symbol identifier to switch to (e.g., "btcusdt").
     */
    public void switchActiveSymbol(String newSymbol) {
        SessionContext context = getActiveContext();
        if (newSymbol != null && !newSymbol.equals(context.activeSymbol)) {
            logger.debug("PaperTradingService switching active symbol to: {} for session type {}", newSymbol, activeSessionType);
            context.activeSymbol = newSymbol;
            // Ensure data structures are initialized for the new symbol
            context.openPositionsBySymbol.computeIfAbsent(newSymbol, k -> new ConcurrentHashMap<>());
            context.pendingOrdersBySymbol.computeIfAbsent(newSymbol, k -> new ConcurrentHashMap<>());
            context.tradeHistoryBySymbol.computeIfAbsent(newSymbol, k -> Collections.synchronizedList(new ArrayList<>()));

            // Clear the live session tracker and notify UI components to update
            context.sessionTradeHistory.clear();
            context.sessionTradeHistory.addAll(getTradeHistory()); // Populate with history of new symbol
            
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
        SessionContext context = getActiveContext();
        ReplaySessionManager rsm = ReplaySessionManager.getInstance();
        DrawingManager dm = DrawingManager.getInstance();

        Map<String, SymbolSessionState> allSymbolStates = new HashMap<>();

        // Create a set of all symbols that have any state associated with them
        Set<String> allSymbols = new HashSet<>();
        allSymbols.addAll(context.openPositionsBySymbol.keySet());
        allSymbols.addAll(context.pendingOrdersBySymbol.keySet());
        allSymbols.addAll(context.tradeHistoryBySymbol.keySet());
        allSymbols.addAll(dm.getAllKnownSymbols());
        allSymbols.addAll(rsm.getAllKnownSymbols());

        for (String symbol : allSymbols) {
            SymbolSessionState symbolState = new SymbolSessionState(
                rsm.getReplayHeadIndex(symbol),
                new ArrayList<>(context.openPositionsBySymbol.getOrDefault(symbol, Collections.emptyMap()).values()),
                new ArrayList<>(context.pendingOrdersBySymbol.getOrDefault(symbol, Collections.emptyMap()).values()),
                new ArrayList<>(context.tradeHistoryBySymbol.getOrDefault(symbol, Collections.emptyList())),
                dm.getAllDrawingsForSymbol(symbol),
                rsm.getLastTimestamp(symbol)
            );
            allSymbolStates.put(symbol, symbolState);
        }
        
        String lastActiveSymbol = (activeSessionType == SessionType.REPLAY) ? rsm.getActiveSymbol() : context.activeSymbol;

        return new ReplaySessionState(
            context.accountBalance,
            lastActiveSymbol,
            allSymbolStates
        );
    }


    public void restoreState(ReplaySessionState state) {
        // Restoring a state always applies to the REPLAY context.
        resetSession(SessionType.REPLAY, state.accountBalance(), BigDecimal.ONE); // Leverage is not saved in state yet
        SessionContext context = contexts.get(SessionType.REPLAY);

        if (state.symbolStates() != null) {
            for (Map.Entry<String, SymbolSessionState> entry : state.symbolStates().entrySet()) {
                String symbol = entry.getKey();
                SymbolSessionState symbolState = entry.getValue();

                if (symbolState.tradeHistory() != null) {
                    context.tradeHistoryBySymbol.computeIfAbsent(symbol, k -> Collections.synchronizedList(new ArrayList<>())).addAll(symbolState.tradeHistory());
                }
                if (symbolState.pendingOrders() != null) {
                    context.pendingOrdersBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).putAll(
                        symbolState.pendingOrders().stream().collect(Collectors.toMap(Order::id, Function.identity()))
                    );
                }
                if (symbolState.openPositions() != null) {
                    context.openPositionsBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).putAll(
                        symbolState.openPositions().stream().collect(Collectors.toMap(Position::id, Function.identity()))
                    );
                }
            }
        }
        
        // Set the active symbol from the loaded state
        switchActiveSymbol(state.lastActiveSymbol());

        logger.info("Paper trading multi-symbol state restored for REPLAY session. Active Symbol: {}. Balance: {}", context.activeSymbol, context.accountBalance);
    }

    @Override
    public void onBarUpdate(KLine newBar) {
        // All updates happen for the currently active symbol during replay ticks.
        if (getActiveContext().activeSymbol == null) return;
        
        checkPendingOrders(newBar, getActiveContext().activeSymbol);
        updateTrailingStops(newBar, getActiveContext().activeSymbol);
        checkOpenPositions(newBar, getActiveContext().activeSymbol);
        
        if (!getOpenPositions().isEmpty()) {
            Map<UUID, BigDecimal> pnlMap = PnlCalculationService.getInstance()
                    .calculateUnrealizedPnl(getOpenPositions(), newBar);
            pcs.firePropertyChange("unrealizedPnlCalculated", null, pnlMap);
        }
    }

    /**
     * [NEW] Specifically for live mode to update P&L on every tick without triggering order checks.
     * @param currentBar The currently forming bar with the latest price.
     */
    public void updateLivePnl(KLine currentBar) {
        // This is only for the active symbol in the current session type.
        if (getActiveContext().activeSymbol == null || currentBar == null) return;

        List<Position> openPositions = getOpenPositions();
        if (!openPositions.isEmpty()) {
            Map<UUID, BigDecimal> pnlMap = PnlCalculationService.getInstance()
                    .calculateUnrealizedPnl(openPositions, currentBar);
            pcs.firePropertyChange("unrealizedPnlCalculated", null, pnlMap);
        }
    }

    private void checkPendingOrders(KLine bar, String symbol) {
        Map<UUID, Order> symbolOrders = getActiveContext().pendingOrdersBySymbol.get(symbol);
        if (symbolOrders == null || symbolOrders.isEmpty()) return;

        boolean ordersChanged = false;
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
        if (ordersChanged) {
            pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
        }
    }

    private void updateTrailingStops(KLine bar, String symbol) {
        Map<UUID, Position> symbolPositions = getActiveContext().openPositionsBySymbol.get(symbol);
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

    private void checkOpenPositions(KLine bar, String symbol) {
        Map<UUID, Position> symbolPositions = getActiveContext().openPositionsBySymbol.get(symbol);
        if (symbolPositions == null || symbolPositions.isEmpty()) return;
        
        new ArrayList<>(symbolPositions.values()).forEach(position -> {
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

                if (SettingsManager.getInstance().isAutoJournalOnTradeClose()) {
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
        
        if (order.type() == OrderType.MARKET) {
            if (currentBar == null) {
                logger.error("Cannot place market order without a current bar context.");
                return;
            }
            openPosition(order, currentBar.close(), currentBar.timestamp());
            logger.info("Filled market order {} for {} at {}", order.id(), symbol, currentBar.close());
        } else {
            getActiveContext().pendingOrdersBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).put(order.id(), order);
            pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
            logger.info("Placed pending order for {}: {}", symbol, order);
        }
    }

    private void openPosition(Order fromOrder, BigDecimal entryPrice, Instant timestamp) {
        String symbol = fromOrder.symbol().name();
        SessionContext context = getActiveContext();
        Position newPosition = new Position(
            fromOrder.id(), fromOrder.symbol(), fromOrder.direction(),
            fromOrder.size(), entryPrice, fromOrder.stopLoss(),
            fromOrder.takeProfit(), fromOrder.trailingStopDistance(),
            timestamp, fromOrder.checklistId()
        );
        context.openPositionsBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).put(newPosition.id(), newPosition);

        BigDecimal commission = SettingsManager.getInstance().getCommissionPerTrade();
        if (commission != null && commission.compareTo(BigDecimal.ZERO) > 0) {
            context.accountBalance = context.accountBalance.subtract(commission);
            logger.info("Applied entry commission of {} for trade {}. New balance: {}",
                commission, newPosition.id(), context.accountBalance);
        }

        pcs.firePropertyChange("openPositionsUpdated", null, getOpenPositions());
    }

    private Trade finalizeTrade(Position position, BigDecimal exitPrice, Instant exitTime, boolean planFollowed) {
        String symbol = position.symbol().name();
        SessionContext context = getActiveContext();
        BigDecimal pnl;
        if (position.direction() == TradeDirection.LONG) {
            pnl = exitPrice.subtract(position.entryPrice()).multiply(position.size());
            BigDecimal spread = SettingsManager.getInstance().getSimulatedSpreadPoints();
            if (spread != null && spread.compareTo(BigDecimal.ZERO) > 0) {
                pnl = pnl.subtract(spread.multiply(position.size()));
            }
        } else { // SHORT
            pnl = position.entryPrice().subtract(exitPrice).multiply(position.size());
            BigDecimal spread = SettingsManager.getInstance().getSimulatedSpreadPoints();
            if (spread != null && spread.compareTo(BigDecimal.ZERO) > 0) {
                pnl = pnl.subtract(spread.multiply(position.size()));
            }
        }

        ReplaySessionManager rsm = ReplaySessionManager.getInstance();
        List<String> autoTags = new ArrayList<>();
        if (rsm.getCurrentSource() != null && rsm.getCurrentSource().symbol().equals(symbol)) {
            try (DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + rsm.getCurrentSource().dbPath().toAbsolutePath())) {
                List<KLine> tradeKlines = db.getKLinesBetween(new Symbol(symbol), "1m", position.openTimestamp(), exitTime);
                Trade tempTrade = new Trade(position.id(), position.symbol(), position.direction(), position.openTimestamp(), position.entryPrice(), exitTime, exitPrice, position.size(), pnl, planFollowed);
                autoTags = automatedTaggingService.generateTags(tempTrade, tradeKlines);
            } catch (Exception e) {
                logger.error("Failed to retrieve K-lines for automated tagging.", e);
            }
        }
        
        Trade completedTrade = new Trade(
            position.id(), position.symbol(), position.direction(), position.openTimestamp(),
            position.entryPrice(), exitTime, exitPrice, position.size(), pnl, planFollowed,
            null, autoTags, position.checklistId()
        );
        context.tradeHistoryBySymbol.computeIfAbsent(symbol, k -> Collections.synchronizedList(new ArrayList<>())).add(completedTrade);
        context.sessionTradeHistory.add(completedTrade);
        context.accountBalance = context.accountBalance.add(pnl);
        context.openPositionsBySymbol.get(symbol).remove(position.id());
        
        pcs.firePropertyChange("tradeHistoryUpdated", null, getTradeHistory());
        pcs.firePropertyChange("openPositionsUpdated", null, getOpenPositions());
        
        logger.info("Trade finalized for {}. Position: {}. PnL: {}. Plan Followed: {}. New Balance: {}. Auto-tags: {}", symbol, position.id(), pnl, planFollowed, context.accountBalance, autoTags);
        return completedTrade;
    }

    @Override
    public void modifyOrder(UUID orderId, BigDecimal newPrice, BigDecimal newStopLoss, BigDecimal newTakeProfit, BigDecimal newTrailingStopDistance) {
        modifyOrderInternal(orderId, newPrice, newStopLoss, newTakeProfit, newTrailingStopDistance);
    }

    private synchronized void modifyOrderInternal(UUID orderId, BigDecimal newPrice, BigDecimal newStopLoss, BigDecimal newTakeProfit, BigDecimal newTrailingStopDistance) {
        // Find which symbol the order/position belongs to
        Optional<String> symbolOpt = findSymbolForTradable(orderId);
        if (symbolOpt.isEmpty()) {
            logger.warn("Could not find order/position with ID {} to modify.", orderId);
            return;
        }
        String symbol = symbolOpt.get();
        SessionContext context = getActiveContext();

        Order existingOrder = context.pendingOrdersBySymbol.get(symbol).get(orderId);
        if (existingOrder != null) {
            Order updatedOrder = new Order(
                existingOrder.id(), existingOrder.symbol(), existingOrder.type(),
                existingOrder.status(), existingOrder.direction(), existingOrder.size(),
                newPrice, newStopLoss, newTakeProfit, newTrailingStopDistance,
                existingOrder.creationTime(), existingOrder.checklistId()
            );
            context.pendingOrdersBySymbol.get(symbol).put(orderId, updatedOrder);
            pcs.firePropertyChange("pendingOrdersUpdated", null, getPendingOrders());
            logger.info("Modified pending order {} for symbol {}", orderId, symbol);
            return;
        }
        Position existingPosition = context.openPositionsBySymbol.get(symbol).get(orderId);
        if (existingPosition != null) {
             Position updatedPosition = new Position(
                existingPosition.id(), existingPosition.symbol(), existingPosition.direction(),
                existingPosition.size(), existingPosition.entryPrice(),
                newStopLoss, newTakeProfit, newTrailingStopDistance,
                existingPosition.openTimestamp(), existingPosition.checklistId()
             );
             context.openPositionsBySymbol.get(symbol).put(orderId, updatedPosition);
             pcs.firePropertyChange("openPositionsUpdated", null, getOpenPositions());
             logger.info("Modified open position SL/TP for {} on symbol {}", orderId, symbol);
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
        SessionContext context = getActiveContext();
        List<Trade> history = context.tradeHistoryBySymbol.get(symbol);
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
            // Update session history as well if present
            context.sessionTradeHistory.removeIf(t -> t.id().equals(updatedTrade.id()));
            context.sessionTradeHistory.add(updatedTrade);

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
            if (getActiveContext().pendingOrdersBySymbol.get(symbol).remove(orderId) != null) {
                logger.info("Cancelled pending order {} for symbol {}", orderId, symbol);
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

        if (SettingsManager.getInstance().isAutoJournalOnTradeClose()) {
            ReplaySessionManager.getInstance().pause();
            pcs.firePropertyChange("tradeClosedForJournaling", null, closedTrade);
        }
    }

    @Override
    public void resetSession(BigDecimal startingBalance, BigDecimal leverage) {
        resetSession(this.activeSessionType, startingBalance, leverage);
    }

    public void resetSession(SessionType type, BigDecimal startingBalance, BigDecimal leverage) {
        SessionContext contextToReset = contexts.get(type);
        if (contextToReset == null) return;
        
        contextToReset.accountBalance = startingBalance;
        contextToReset.leverage = (leverage != null && leverage.compareTo(BigDecimal.ZERO) > 0) ? leverage : BigDecimal.ONE;
        contextToReset.openPositionsBySymbol.clear();
        contextToReset.pendingOrdersBySymbol.clear();
        contextToReset.tradeHistoryBySymbol.clear();
        contextToReset.sessionTradeHistory.clear();
        contextToReset.activeSymbol = null;

        // If the reset context is the active one, notify UI
        if (type == this.activeSessionType) {
            pcs.firePropertyChange("openPositionsUpdated", null, Collections.emptyList());
            pcs.firePropertyChange("tradeHistoryUpdated", null, Collections.emptyList());
            pcs.firePropertyChange("pendingOrdersUpdated", null, Collections.emptyList());
        }
        logger.info("Paper Trading Service session reset for {}. Starting Balance: {}. Leverage: {}x", type, startingBalance, contextToReset.leverage);
    }

    public void importTradeHistory(List<Trade> newHistory, BigDecimal startingBalance) {
        resetSession(startingBalance, BigDecimal.ONE);
        SessionContext context = getActiveContext();
        if (newHistory != null && !newHistory.isEmpty()) {
            // Group imported trades by symbol
            Map<String, List<Trade>> groupedTrades = newHistory.stream().collect(Collectors.groupingBy(t -> t.symbol().name()));
            context.tradeHistoryBySymbol.putAll(groupedTrades);
            
            BigDecimal totalPnl = newHistory.stream()
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            context.accountBalance = context.accountBalance.add(totalPnl);
        }
        pcs.firePropertyChange("tradeHistoryUpdated", null, new ArrayList<>(context.tradeHistoryBySymbol.values().stream().flatMap(List::stream).collect(Collectors.toList())));
        logger.info("Trade history imported. {} total trades across {} symbols. New balance: {}", newHistory.size(), context.tradeHistoryBySymbol.size(), context.accountBalance);
    }

    @Override
    public List<Position> getOpenPositions() {
        SessionContext context = getActiveContext();
        if (context.activeSymbol == null) return Collections.emptyList();
        return new ArrayList<>(context.openPositionsBySymbol.getOrDefault(context.activeSymbol, Collections.emptyMap()).values());
    }

    @Override
    public List<Order> getPendingOrders() {
        SessionContext context = getActiveContext();
        if (context.activeSymbol == null) return Collections.emptyList();
        return new ArrayList<>(context.pendingOrdersBySymbol.getOrDefault(context.activeSymbol, Collections.emptyMap()).values());
    }

    @Override
    public List<Trade> getTradeHistory() {
        SessionContext context = getActiveContext();
        if (context.activeSymbol == null) return Collections.emptyList();
        return new ArrayList<>(context.tradeHistoryBySymbol.getOrDefault(context.activeSymbol, Collections.emptyList()));
    }

    /**
     * [NEW] Checks if any trades have been made or positions are open across ALL symbols for the active session type.
     * @return true if there is any trading activity in the session.
     */
    public boolean hasAnyTradesOrPositions() {
        SessionContext context = getActiveContext();
        if (!context.tradeHistoryBySymbol.isEmpty() && context.tradeHistoryBySymbol.values().stream().anyMatch(list -> !list.isEmpty())) {
            return true;
        }
        if (!context.openPositionsBySymbol.isEmpty() && context.openPositionsBySymbol.values().stream().anyMatch(map -> !map.isEmpty())) {
            return true;
        }
        return false;
    }

    @Override
    public BigDecimal getAccountBalance() { return getActiveContext().accountBalance; }

    @Override
    public BigDecimal getLeverage() { return getActiveContext().leverage; }
    
    // --- Helper methods to find tradable objects by ID across all symbols ---
    private Optional<String> findSymbolForTradable(UUID id) {
        SessionContext context = getActiveContext();
        for (String symbol : context.pendingOrdersBySymbol.keySet()) {
            if (context.pendingOrdersBySymbol.get(symbol).containsKey(id)) {
                return Optional.of(symbol);
            }
        }
        for (String symbol : context.openPositionsBySymbol.keySet()) {
            if (context.openPositionsBySymbol.get(symbol).containsKey(id)) {
                return Optional.of(symbol);
            }
        }
        return Optional.empty();
    }

    private Optional<Position> findPositionById(UUID id) {
        return getActiveContext().openPositionsBySymbol.values().stream()
            .map(map -> map.get(id))
            .filter(java.util.Objects::nonNull)
            .findFirst();
    }

    private Optional<Trade> findTradeById(UUID id) {
        return getActiveContext().tradeHistoryBySymbol.values().stream()
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
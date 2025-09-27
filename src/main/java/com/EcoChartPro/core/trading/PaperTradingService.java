package com.EcoChartPro.core.trading;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.journal.AutomatedTaggingService;
import com.EcoChartPro.core.service.PnlCalculationService;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.state.ReplaySessionState;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PaperTradingService implements TradingService {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingService.class);
    private static volatile PaperTradingService instance;

    private BigDecimal accountBalance;
    private BigDecimal leverage;
    private final Map<UUID, Position> openPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Order> pendingOrders = new ConcurrentHashMap<>();
    private final List<Trade> tradeHistory = Collections.synchronizedList(new ArrayList<>());
    private final List<Trade> sessionTradeHistory = Collections.synchronizedList(new ArrayList<>());
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final AutomatedTaggingService automatedTaggingService;

    private PaperTradingService() {
        this.leverage = BigDecimal.ONE;
        this.accountBalance = BigDecimal.ZERO;
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

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

    @Override
    public ReplaySessionState getCurrentSessionState() {
        // This gathers all current state into a snapshot object.
        ReplaySessionManager rsm = ReplaySessionManager.getInstance();
        return new ReplaySessionState(
                rsm.getCurrentSource() != null ? rsm.getCurrentSource().symbol() : "",
                rsm.getReplayHeadIndex(),
                accountBalance,
                new ArrayList<>(openPositions.values()),
                new ArrayList<>(pendingOrders.values()),
                new ArrayList<>(tradeHistory),
                DrawingManager.getInstance().getAllDrawings(),
                rsm.getCurrentBar() != null ? rsm.getCurrentBar().timestamp() : Instant.EPOCH
        );
    }


    public void restoreState(ReplaySessionState state) {
        resetSession(state.accountBalance(), BigDecimal.ONE);

        if (state.tradeHistory() != null) {
            this.tradeHistory.addAll(state.tradeHistory());
        }
        if (state.pendingOrders() != null) {
            this.pendingOrders.putAll(state.pendingOrders().stream()
                    .collect(Collectors.toMap(Order::id, Function.identity())));
        }
        if (state.openPositions() != null) {
            this.openPositions.putAll(state.openPositions().stream()
                    .collect(Collectors.toMap(Position::id, Function.identity())));
        }

        pcs.firePropertyChange("openPositionsUpdated", null, new ArrayList<>(openPositions.values()));
        pcs.firePropertyChange("tradeHistoryUpdated", null, new ArrayList<>(tradeHistory));
        pcs.firePropertyChange("pendingOrdersUpdated", null, new ArrayList<>(pendingOrders.values()));


        logger.info("Paper trading state restored. Balance: {}. Open Positions: {}. Pending Orders: {}. Trade History: {}",
                accountBalance, openPositions.size(), pendingOrders.size(), tradeHistory.size());
    }

    @Override
    public void onBarUpdate(KLine newBar) {
        checkPendingOrders(newBar);
        updateTrailingStops(newBar);
        checkOpenPositions(newBar);
        if (!openPositions.isEmpty()) {
            Map<UUID, BigDecimal> pnlMap = PnlCalculationService.getInstance()
                    .calculateUnrealizedPnl(new ArrayList<>(openPositions.values()), newBar);
            pcs.firePropertyChange("unrealizedPnlCalculated", null, pnlMap);
        }
    }

    private void checkPendingOrders(KLine bar) {
        boolean ordersChanged = false;
        for (Order order : new ArrayList<>(pendingOrders.values())) {
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
                pendingOrders.remove(order.id());
                logger.info("Filled order {} at price {}", order.id(), fillPrice);
                ordersChanged = true;
            }
        }
        if (ordersChanged) {
            pcs.firePropertyChange("pendingOrdersUpdated", null, new ArrayList<>(pendingOrders.values()));
        }
    }

    private void updateTrailingStops(KLine bar) {
        new ArrayList<>(openPositions.values()).forEach(position -> {
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
                modifyOrder(position.id(), null, newStopLoss, position.takeProfit(), position.trailingStopDistance());
                logger.info("Position {} SL trailed to {}", position.id(), newStopLoss.toPlainString());
            }
        });
    }

    private void checkOpenPositions(KLine bar) {
        new ArrayList<>(openPositions.values()).forEach(position -> {
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
                logger.info("Closed position {} via {}.", position.id(), closeReason);

                if (SettingsManager.getInstance().isAutoJournalOnTradeClose()) {
                    ReplaySessionManager.getInstance().pause();
                    pcs.firePropertyChange("tradeClosedForJournaling", null, closedTrade);
                }
            }
        });
    }

    @Override
    public void placeOrder(Order order, KLine currentBar) {
        if (order.type() == OrderType.MARKET) {
            if (currentBar == null) {
                logger.error("Cannot place market order without a current bar context.");
                return;
            }
            openPosition(order, currentBar.close(), currentBar.timestamp());
            logger.info("Filled market order {} at {}", order.id(), currentBar.close());
        } else {
            pendingOrders.put(order.id(), order);
            pcs.firePropertyChange("pendingOrdersUpdated", null, new ArrayList<>(pendingOrders.values()));
            logger.info("Placed pending order: {}", order);
        }
    }

    private void openPosition(Order fromOrder, BigDecimal entryPrice, Instant timestamp) {
        Position newPosition = new Position(
            fromOrder.id(), fromOrder.symbol(), fromOrder.direction(),
            fromOrder.size(), entryPrice, fromOrder.stopLoss(),
            fromOrder.takeProfit(), fromOrder.trailingStopDistance(),
            timestamp, fromOrder.checklistId()
        );
        openPositions.put(newPosition.id(), newPosition);

        // Apply commission on entry
        BigDecimal commission = SettingsManager.getInstance().getCommissionPerTrade();
        if (commission != null && commission.compareTo(BigDecimal.ZERO) > 0) {
            accountBalance = accountBalance.subtract(commission);
            logger.info("Applied entry commission of {} for trade {}. New balance: {}",
                commission, newPosition.id(), accountBalance);
        }

        pcs.firePropertyChange("openPositionsUpdated", null, new ArrayList<>(openPositions.values()));
    }

    private Trade finalizeTrade(Position position, BigDecimal exitPrice, Instant exitTime, boolean planFollowed) {
        BigDecimal pnl;
        if (position.direction() == TradeDirection.LONG) {
            pnl = exitPrice.subtract(position.entryPrice()).multiply(position.size());
            // Apply spread on exit for a long trade
            BigDecimal spread = SettingsManager.getInstance().getSimulatedSpreadPoints();
            if (spread != null && spread.compareTo(BigDecimal.ZERO) > 0) {
                pnl = pnl.subtract(spread.multiply(position.size()));
            }
        } else { // SHORT
            pnl = position.entryPrice().subtract(exitPrice).multiply(position.size());
            // Apply spread on exit for a short trade
            BigDecimal spread = SettingsManager.getInstance().getSimulatedSpreadPoints();
            if (spread != null && spread.compareTo(BigDecimal.ZERO) > 0) {
                pnl = pnl.subtract(spread.multiply(position.size()));
            }
        }

        // --- Automated Tagging ---
        ReplaySessionManager rsm = ReplaySessionManager.getInstance();
        List<String> autoTags = new ArrayList<>();
        if (rsm.getCurrentSource() != null) {
            try (DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + rsm.getCurrentSource().dbPath().toAbsolutePath())) {
                List<KLine> tradeKlines = db.getKLinesBetween(new Symbol(position.symbol().name()), "1m", position.openTimestamp(), exitTime);
                Trade tempTrade = new Trade(position.id(), position.symbol(), position.direction(), position.openTimestamp(), position.entryPrice(), exitTime, exitPrice, position.size(), pnl, planFollowed);
                autoTags = automatedTaggingService.generateTags(tempTrade, tradeKlines);
            } catch (Exception e) {
                logger.error("Failed to retrieve K-lines for automated tagging.", e);
            }
        }
        
        Trade completedTrade = new Trade(
            position.id(), position.symbol(), position.direction(), position.openTimestamp(),
            position.entryPrice(), exitTime, exitPrice, position.size(), pnl, planFollowed,
            null, autoTags, position.checklistId() // FIX: Pass the generated autoTags list here
        );
        tradeHistory.add(completedTrade);
        sessionTradeHistory.add(completedTrade);
        accountBalance = accountBalance.add(pnl);
        openPositions.remove(position.id());
        
        pcs.firePropertyChange("tradeHistoryUpdated", null, new ArrayList<>(tradeHistory));
        pcs.firePropertyChange("openPositionsUpdated", null, new ArrayList<>(openPositions.values()));
        
        logger.info("Trade finalized. Position: {}. PnL: {}. Plan Followed: {}. New Balance: {}. Auto-tags: {}", position.id(), pnl, planFollowed, accountBalance, autoTags);
        return completedTrade;
    }

    @Override
    public void modifyOrder(UUID orderId, BigDecimal newPrice, BigDecimal newStopLoss, BigDecimal newTakeProfit, BigDecimal newTrailingStopDistance) {
        Order existingOrder = pendingOrders.get(orderId);
        if (existingOrder != null) {
            Order updatedOrder = new Order(
                existingOrder.id(), existingOrder.symbol(), existingOrder.type(),
                existingOrder.status(), existingOrder.direction(), existingOrder.size(),
                newPrice, newStopLoss, newTakeProfit, newTrailingStopDistance,
                existingOrder.creationTime(), existingOrder.checklistId()
            );
            pendingOrders.put(orderId, updatedOrder);
            pcs.firePropertyChange("pendingOrdersUpdated", null, new ArrayList<>(pendingOrders.values()));
            logger.info("Modified pending order: {}", orderId);
            return;
        }
        Position existingPosition = openPositions.get(orderId);
        if (existingPosition != null) {
             Position updatedPosition = new Position(
                existingPosition.id(), existingPosition.symbol(), existingPosition.direction(),
                existingPosition.size(), existingPosition.entryPrice(),
                newStopLoss, newTakeProfit, newTrailingStopDistance,
                existingPosition.openTimestamp(), existingPosition.checklistId()
             );
             openPositions.put(orderId, updatedPosition);
             pcs.firePropertyChange("openPositionsUpdated", null, new ArrayList<>(openPositions.values()));
             logger.info("Modified open position SL/TP: {}", orderId);
        }
    }

    @Override
    public void updateTradeJournalEntry(UUID tradeId, String notes, List<String> tags) {
        // This is now partially deprecated in favor of the more detailed reflection method,
        // but we'll keep its logic for backward compatibility or simpler updates.
        for (int i = 0; i < tradeHistory.size(); i++) {
            Trade oldTrade = tradeHistory.get(i);
            if (oldTrade.id().equals(tradeId)) {
                oldTrade.setNotes(notes);
                oldTrade.setTags(tags);
                logger.info("Updated basic journal entry for trade ID: {}", tradeId);
                pcs.firePropertyChange("tradeHistoryUpdated", null, new ArrayList<>(tradeHistory));
                return;
            }
        }
        logger.warn("Attempted to update journal for a non-existent trade with ID: {}", tradeId);
    }

    @Override
    public void updateTradeJournalReflection(Trade updatedTrade) {
        if (updatedTrade == null || updatedTrade.id() == null) {
            logger.warn("Attempted to update trade journal with a null trade or ID.");
            return;
        }

        boolean wasUpdated = false;
        // Update in the main, persistent history
        for (int i = 0; i < tradeHistory.size(); i++) {
            if (tradeHistory.get(i).id().equals(updatedTrade.id())) {
                tradeHistory.set(i, updatedTrade);
                wasUpdated = true;
                break;
            }
        }

        if (wasUpdated) {
            // Also update in the session-specific history to keep them in sync
            for (int i = 0; i < sessionTradeHistory.size(); i++) {
                if (sessionTradeHistory.get(i).id().equals(updatedTrade.id())) {
                    sessionTradeHistory.set(i, updatedTrade);
                    break;
                }
            }

            logger.info("Updated detailed journal reflection for trade ID: {}", updatedTrade.id());

            // Fire specific event if mistakes were logged, for the live discipline tracker
            if (updatedTrade.identifiedMistakes() != null && !updatedTrade.identifiedMistakes().isEmpty()
                && !(updatedTrade.identifiedMistakes().size() == 1 && "No Mistakes Made".equals(updatedTrade.identifiedMistakes().get(0)))) {
                pcs.firePropertyChange("mistakeLogged", null, updatedTrade);
            }

            // Fire the general event to update history views
            pcs.firePropertyChange("tradeHistoryUpdated", null, new ArrayList<>(tradeHistory));
        } else {
            logger.warn("Attempted to update journal for a non-existent trade with ID: {}", updatedTrade.id());
        }
    }

    @Override
    public void cancelOrder(UUID orderId) {
        if (pendingOrders.remove(orderId) != null) {
            logger.info("Cancelled pending order: {}", orderId);
            pcs.firePropertyChange("pendingOrdersUpdated", null, new ArrayList<>(pendingOrders.values()));
        }
    }

    @Override
    public void closePosition(UUID positionId, KLine closingBar) {
        Position positionToClose = openPositions.get(positionId);
        if (positionToClose != null) {
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
    }

    @Override
    public void resetSession(BigDecimal startingBalance, BigDecimal leverage) {
        this.accountBalance = startingBalance;
        this.leverage = (leverage != null && leverage.compareTo(BigDecimal.ZERO) > 0) ? leverage : BigDecimal.ONE;
        this.openPositions.clear();
        this.pendingOrders.clear();
        this.tradeHistory.clear();
        this.sessionTradeHistory.clear();
        pcs.firePropertyChange("openPositionsUpdated", null, Collections.emptyList());
        pcs.firePropertyChange("tradeHistoryUpdated", null, Collections.emptyList());
        pcs.firePropertyChange("pendingOrdersUpdated", null, Collections.emptyList());
        logger.info("Paper Trading Service session reset. Starting Balance: {}. Leverage: {}x", startingBalance, this.leverage);
    }

    public void importTradeHistory(List<Trade> newHistory, BigDecimal startingBalance) {
        resetSession(startingBalance, BigDecimal.ONE);

        if (newHistory != null && !newHistory.isEmpty()) {
            this.tradeHistory.addAll(newHistory);
            BigDecimal totalPnl = newHistory.stream()
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            this.accountBalance = this.accountBalance.add(totalPnl);
        }
        
        pcs.firePropertyChange("tradeHistoryUpdated", null, new ArrayList<>(this.tradeHistory));
        pcs.firePropertyChange("openPositionsUpdated", null, Collections.emptyList());
        logger.info("Trade history imported. {} trades loaded. New current balance: {}", 
            this.tradeHistory.size(), this.accountBalance);
    }

    @Override
    public List<Position> getOpenPositions() {
        return new ArrayList<>(openPositions.values());
    }

    @Override
    public List<Order> getPendingOrders() {
        return new ArrayList<>(pendingOrders.values());
    }

    @Override
    public List<Trade> getTradeHistory() {
        return new ArrayList<>(tradeHistory);
    }

    @Override
    public BigDecimal getAccountBalance() {
        return accountBalance;
    }

    @Override
    public BigDecimal getLeverage() {
        return leverage;
    }

    @Override
    @Deprecated
    public void updateTradeNotes(UUID tradeId, String notes) {
        Trade oldTrade = tradeHistory.stream().filter(t -> t.id().equals(tradeId)).findFirst().orElse(null);
        List<String> tags = (oldTrade != null && oldTrade.tags() != null) ? oldTrade.tags() : Collections.emptyList();
        updateTradeJournalEntry(tradeId, notes, tags);
    }
}
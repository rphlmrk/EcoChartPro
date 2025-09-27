package com.EcoChartPro.core.service;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.trading.Position;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A singleton service dedicated to calculating unrealized profit and loss
 * for open positions.
 */
public class PnlCalculationService {

    private static volatile PnlCalculationService instance;

    private PnlCalculationService() {}

    public static PnlCalculationService getInstance() {
        if (instance == null) {
            synchronized (PnlCalculationService.class) {
                if (instance == null) {
                    instance = new PnlCalculationService();
                }
            }
        }
        return instance;
    }

    /**
     * Calculates the unrealized P&L for a list of open positions against a given market bar.
     * @param positions The list of open positions.
     * @param currentBar The current K-line to use for the market price.
     * @return A map where the key is the Position UUID and the value is its calculated P&L.
     */
    public Map<UUID, BigDecimal> calculateUnrealizedPnl(List<Position> positions, KLine currentBar) {
        if (positions == null || positions.isEmpty() || currentBar == null) {
            return Collections.emptyMap();
        }

        return positions.stream()
            .collect(Collectors.toMap(
                Position::id,
                position -> calculatePnlForPosition(position, currentBar)
            ));
    }

    private BigDecimal calculatePnlForPosition(Position position, KLine currentBar) {
        if (position.direction() == TradeDirection.LONG) {
            return currentBar.close().subtract(position.entryPrice()).multiply(position.size());
        } else { // SHORT
            return position.entryPrice().subtract(currentBar.close()).multiply(position.size());
        }
    }
}
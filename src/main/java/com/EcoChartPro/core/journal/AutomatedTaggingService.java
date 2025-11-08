package com.EcoChartPro.core.journal;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.TradingConfig;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A service to automatically generate objective tags for a trade based on its performance and context.
 */
public class AutomatedTaggingService {

    private record MfeMaeResult(BigDecimal mfe, BigDecimal mae) {}

    public List<String> generateTags(Trade trade, List<KLine> tradeKlines) {
        List<String> tags = new ArrayList<>();
        ZonedDateTime entryZdt = trade.entryTime().atZone(ZoneOffset.UTC);

        // --- Objective Trading Session Tagging ---
        int hour = entryZdt.getHour();
        // Use independent 'if' statements to allow for session overlaps (e.g., London/NY)
        if (hour >= 0 && hour < 9) { // 00:00 - 08:59 UTC (e.g., Tokyo, Singapore)
            tags.add("Asian Session");
        }
        if (hour >= 8 && hour < 17) { // 08:00 - 16:59 UTC (e.g., London, Frankfurt)
            tags.add("London Session");
        }
        if (hour >= 13 && hour < 22) { // 13:00 - 21:59 UTC (e.g., New York)
            tags.add("NY Session");
        }

        // --- NEW: "Out-Side-Trading-Hours" Tag (subjective, based on settings) ---
        SettingsService settings = SettingsService.getInstance();
        List<TradingConfig.TradingSession> preferredSessions = settings.getPreferredTradingSessions();

        // Only perform this check if the user has defined preferred sessions.
        if (preferredSessions != null && !preferredSessions.isEmpty()) {
            boolean isInPreferredHours = false;
            LocalTime tradeTime = entryZdt.toLocalTime();

            for (TradingConfig.TradingSession session : preferredSessions) {
                LocalTime startTime = settings.getSessionStartTimes().get(session);
                LocalTime endTime = settings.getSessionEndTimes().get(session);

                if (startTime != null && endTime != null) {
                    // Check if the session crosses midnight (e.g., Sydney 22:00 - 07:00)
                    if (startTime.isAfter(endTime)) {
                        if (!tradeTime.isBefore(startTime) || tradeTime.isBefore(endTime)) {
                            isInPreferredHours = true;
                            break; // Found a match, no need to check other sessions
                        }
                    } else { // Normal session (e.g., London 08:00 - 17:00)
                        if (!tradeTime.isBefore(startTime) && tradeTime.isBefore(endTime)) {
                            isInPreferredHours = true;
                            break; // Found a match
                        }
                    }
                }
            }

            if (!isInPreferredHours) {
                tags.add("Out-Side-Trading-Hours");
            }
        }

        // --- Trade Duration Tag ---
        long durationMinutes = Duration.between(trade.entryTime(), trade.exitTime()).toMinutes();
        if (durationMinutes < 5) tags.add("Scalp");
        else if (durationMinutes < 60) tags.add("Day-Trade");
        else tags.add("Swing-Trade");
        
        // --- Advanced Trade Management Tags (MFE/MAE) ---
        if (!tradeKlines.isEmpty()) {
            MfeMaeResult mfeMae = calculateMfeMaeForTrade(trade, tradeKlines);
            
            if (trade.profitAndLoss().signum() > 0 && mfeMae.mfe.signum() > 0) {
                BigDecimal efficiency = trade.profitAndLoss().divide(mfeMae.mfe, 2, RoundingMode.HALF_UP);
                if (efficiency.compareTo(new BigDecimal("0.6")) < 0) {
                    tags.add("Left Money on the Table");
                } else if (efficiency.compareTo(new BigDecimal("0.95")) >= 0) {
                    tags.add("Perfect Exit");
                }
            } else if (trade.profitAndLoss().signum() < 0 && trade.profitAndLoss().abs().signum() > 0) {
                 BigDecimal painRatio = mfeMae.mae.divide(trade.profitAndLoss().abs(), 2, RoundingMode.HALF_UP);
                 if (painRatio.compareTo(new BigDecimal("1.5")) > 0) {
                     tags.add("Let Loser Run");
                 }
            }
        }
        
        return tags;
    }

    private MfeMaeResult calculateMfeMaeForTrade(Trade trade, List<KLine> tradeKlines) {
        if (tradeKlines.isEmpty()) {
            return new MfeMaeResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        BigDecimal highestHigh = trade.entryPrice();
        BigDecimal lowestLow = trade.entryPrice();

        for (KLine k : tradeKlines) {
            if (k.high().compareTo(highestHigh) > 0) highestHigh = k.high();
            if (k.low().compareTo(lowestLow) < 0) lowestLow = k.low();
        }
        
        BigDecimal mfe, mae;
        if (trade.direction() == TradeDirection.LONG) {
            mfe = (highestHigh.subtract(trade.entryPrice())).multiply(trade.quantity());
            mae = (trade.entryPrice().subtract(lowestLow)).multiply(trade.quantity());
        } else { // SHORT
            mfe = (trade.entryPrice().subtract(lowestLow)).multiply(trade.quantity());
            mae = (highestHigh.subtract(trade.entryPrice())).multiply(trade.quantity());
        }
        
        return new MfeMaeResult(mfe.max(BigDecimal.ZERO), mae.max(BigDecimal.ZERO));
    }
}
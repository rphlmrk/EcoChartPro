package com.EcoChartPro.core.service;

import com.EcoChartPro.model.Trade;
import com.EcoChartPro.utils.SessionManager;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * A singleton service to manage the logic for prompting users to review their periodic performance.
 */
public class ReviewReminderService {
    private static volatile ReviewReminderService instance;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private ReviewReminderService() {}

    public static ReviewReminderService getInstance() {
        if (instance == null) {
            synchronized (ReviewReminderService.class) {
                if (instance == null) {
                    instance = new ReviewReminderService();
                }
            }
        }
        return instance;
    }

    /**
     * Checks if a performance review is due based on the latest trade data.
     * @param currentTrades The list of all trades currently being displayed/analyzed.
     * @return true if the latest trade's month is after the last reviewed month.
     */
    public boolean isReviewDue(List<Trade> currentTrades) {
        if (currentTrades == null || currentTrades.isEmpty()) {
            return false;
        }

        Optional<YearMonth> lastReviewedMonthOpt = SessionManager.getInstance().getLastReviewedMonth();
        if (lastReviewedMonthOpt.isEmpty()) {
            // If they've never reviewed, a review is due as soon as they have trades.
            return true;
        }

        Optional<LocalDate> lastTradeDateOpt = currentTrades.stream()
            .map(trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate())
            .max(LocalDate::compareTo);

        if (lastTradeDateOpt.isEmpty()) {
            return false;
        }

        YearMonth currentLatestMonth = YearMonth.from(lastTradeDateOpt.get());
        return currentLatestMonth.isAfter(lastReviewedMonthOpt.get());
    }

    /**
     * Marks the review as complete by updating the last reviewed month to match the latest trade data.
     * @param currentTrades The list of trades from which to derive the latest month.
     */
    public void markReviewComplete(List<Trade> currentTrades) {
        if (currentTrades == null || currentTrades.isEmpty()) {
            return;
        }

        Optional<LocalDate> lastTradeDateOpt = currentTrades.stream()
            .map(trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate())
            .max(LocalDate::compareTo);
            
        lastTradeDateOpt.ifPresent(date -> {
            YearMonth latestMonth = YearMonth.from(date);
            SessionManager.getInstance().setLastReviewedMonth(latestMonth);
            // Notify listeners (like the UI) that the review is no longer due.
            pcs.firePropertyChange("reviewStateChanged", true, false); 
        });
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
package com.ibkr.risk;

import com.zerodhatech.models.Tick;
import java.util.Deque;
import java.util.LinkedList;

public class VolatilityAnalyzer {
    private final Deque<Double> priceHistory = new LinkedList<>();
    private final int lookbackPeriod = 20; // 20 ticks

    public double getCurrentVolatility(Tick tick) {
        priceHistory.addLast(tick.getLastTradedPrice());
        if (priceHistory.size() > lookbackPeriod) {
            priceHistory.removeFirst();
        }

        return calculateStandardDeviation();
    }

    private double calculateStandardDeviation() {
        double mean = priceHistory.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = priceHistory.stream()
                .mapToDouble(price -> Math.pow(price - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    public boolean isTooVolatile(Tick tick) {
        return getCurrentVolatility(tick) > (tick.getLastTradedPrice() * 0.05); // 5% threshold
    }
}
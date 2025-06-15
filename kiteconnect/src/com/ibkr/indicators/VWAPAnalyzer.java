package com.ibkr.indicators;

import com.zerodhatech.models.Tick;
import java.util.ArrayDeque;
import java.util.Queue;

public class VWAPAnalyzer {
    private final Queue<Tick> window = new ArrayDeque<>(50);
    private double cumulativeVolume = 0;
    private double cumulativeValue = 0; // Sum of (Typical Price * Volume)

    public void update(Tick tick) {
        // Calculate typical price for this tick
        double typicalPrice = (tick.getHighPrice() + tick.getLowPrice() + tick.getLastTradedPrice()) / 3;
        double volume = tick.getVolumeTradedToday();
        double value = typicalPrice * volume;

        // Add new tick to window
        window.add(tick);
        cumulativeVolume += volume;
        cumulativeValue += value;

        // Maintain fixed window size
        if (window.size() > 50) {
            Tick oldest = window.poll();
            double oldTypicalPrice = (oldest.getHighPrice() + oldest.getLowPrice() + oldest.getLastTradedPrice()) / 3;
            double oldVolume = oldest.getVolumeTradedToday();
            cumulativeVolume -= oldVolume;
            cumulativeValue -= oldTypicalPrice * oldVolume;
        }
    }

    public double getVWAP() {
        return cumulativeVolume > 0 ? cumulativeValue / cumulativeVolume : 0;
    }

    public double getVolatility() {
        if (window.size() < 2) return 0;

        double mean = getVWAP();
        double sumSquaredDiffs = 0;

        for (Tick tick : window) {
            double price = (tick.getHighPrice() + tick.getLowPrice() + tick.getLastTradedPrice()) / 3;
            sumSquaredDiffs += Math.pow(price - mean, 2);
        }

        return Math.sqrt(sumSquaredDiffs / window.size());
    }

    public boolean isAboveVWAP(Tick tick) {
        double vwap = getVWAP();
        return vwap > 0 && tick.getLastTradedPrice() > vwap * 1.005; // 0.5% above
    }

    public boolean isBelowVWAP(Tick tick) {
        double vwap = getVWAP();
        return vwap > 0 && tick.getLastTradedPrice() < vwap * 0.995; // 0.5% below
    }

    public double getDistanceToVWAP(Tick tick) {
        double vwap = getVWAP();
        return vwap > 0 ? (tick.getLastTradedPrice() - vwap) / vwap : 0;
    }
}
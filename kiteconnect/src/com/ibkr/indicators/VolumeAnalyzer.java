package com.ibkr.indicators;

import com.zerodhatech.models.Tick;
import java.util.ArrayDeque;
import java.util.Queue;
// import org.slf4j.Logger; // Example if logging is added
// import org.slf4j.LoggerFactory; // Example if logging is added

public class VolumeAnalyzer {
    // private static final Logger logger = LoggerFactory.getLogger(VolumeAnalyzer.class); // Example
    private final Queue<Double> volumeWindow = new ArrayDeque<>(20); // Changed to Double
    private double rollingSum = 0;

    public void update(Tick tick) {
        if (tick == null) return;

        double lastTradeVol = tick.getLastTradedQuantity();
        if (lastTradeVol <= 0) {
            // logger.trace("VolumeAnalyzer: Ignoring tick with zero or negative lastTradedQuantity for {}", tick.getSymbol());
            return;
        }

        if (volumeWindow.size() >= 20) {
            Double oldestVolume = volumeWindow.poll();
            if (oldestVolume != null) {
                rollingSum -= oldestVolume;
            }
        }
        volumeWindow.add(lastTradeVol);
        rollingSum += lastTradeVol;
    }

    public boolean isBreakoutWithSpike(Tick tick) {
        if (tick == null || volumeWindow.isEmpty()) {
            return false;
        }
        double avgVolume = rollingSum / volumeWindow.size();
        if (avgVolume <= 0) return false;

        boolean volumeSpike = tick.getLastTradedQuantity() > avgVolume * 2.5;

        // logger.trace("isBreakoutWithSpike for {}: LastVol={}, AvgVol={:.2f}, Spike={}",
        //    tick.getSymbol(), tick.getLastTradedQuantity(), avgVolume, volumeSpike);
        return volumeSpike;
    }

    public boolean isBreakdownWithSpike(Tick tick) {
        if (tick == null || volumeWindow.isEmpty()) {
            return false;
        }
        double avgVolume = rollingSum / volumeWindow.size();
        if (avgVolume <= 0) return false;

        boolean volumeSpike = tick.getLastTradedQuantity() > avgVolume * 2.5;

        // logger.trace("isBreakdownWithSpike for {}: LastVol={}, AvgVol={:.2f}, Spike={}",
        //    tick.getSymbol(), tick.getLastTradedQuantity(), avgVolume, volumeSpike);
        return volumeSpike;
    }
}
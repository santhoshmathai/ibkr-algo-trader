package com.ibkr.indicators;

import com.zerodhatech.models.Tick;
import java.util.ArrayDeque;
import java.util.Queue;

public class VolumeAnalyzer {
    private final Queue<Long> volumeWindow = new ArrayDeque<>(20);
    private double rollingSum = 0;

    public void update(Tick tick) {
        if (volumeWindow.size() >= 20) {
            rollingSum -= volumeWindow.poll();
        }
        volumeWindow.add(tick.getVolumeTradedToday());
        rollingSum += tick.getVolumeTradedToday();
    }

    public boolean isBreakoutWithSpike(Tick tick) {
        double avgVolume = rollingSum / volumeWindow.size();
        return tick.getVolumeTradedToday() > avgVolume * 2.5 &&
                tick.getLastTradedPrice() > tick.getOpenPrice() * 1.01;
    }

    public boolean isBreakdownWithSpike(Tick tick) {
        double avgVolume = rollingSum / volumeWindow.size();
        return tick.getVolumeTradedToday() > avgVolume * 2.5 &&
                tick.getLastTradedPrice() < tick.getOpenPrice() * 0.99;
    }
}
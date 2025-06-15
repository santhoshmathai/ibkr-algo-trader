package com.ibkr.risk;


import com.zerodhatech.models.Tick;

public class LiquidityMonitor {
    public boolean isSpreadAcceptable(Tick tick) {
        double spread = tick.getTotalSellQuantity() - tick.getTotalBuyQuantity();
        return spread < (tick.getLastTradedPrice() * 0.01); // 1% spread threshold
    }

    public double getLiquidityScore(Tick tick) {
        double avgVolume = (tick.getTotalBuyQuantity() + tick.getTotalSellQuantity()) / 2;
        return Math.min(1.0, avgVolume / 1000); // Normalized to 1000 shares
    }

    public double getShortAvailability(Tick tick) {
        // Mock implementation - replace with actual shortable shares data
        double volumeRatio = tick.getVolumeTradedToday() / 1000000.0; // Normalized
        return Math.min(1.0, 0.9 - (volumeRatio * 0.2)); // More volume → less available
    }
}
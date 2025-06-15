package com.ibkr.risk;

import com.ibkr.indicators.VWAPAnalyzer;
import com.zerodhatech.models.Tick;

public class AdvancedRiskManager {
    private final VWAPAnalyzer vwap;

    public AdvancedRiskManager() {
        this.vwap = new VWAPAnalyzer();
    }

    public boolean validateShort(Tick tick) {
        // Prevent shorting if too far below VWAP
        double vwapDistance = (vwap.getVWAP() - tick.getLastTradedPrice()) / vwap.getVWAP();
        return vwapDistance < 0.03; // Max 3% below VWAP
    }

    public boolean isShortSqueezeRisk(Tick tick) {
        double volumeRatio = tick.getLastTradedQuantity() / tick.getVolumeTradedToday();
        return volumeRatio > 3.0 && tick.getLastTradedPrice() > tick.getOpenPrice();
    }
}
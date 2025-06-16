package com.ibkr.risk;


import com.zerodhatech.models.Tick;
import com.ibkr.models.TradingSignal;
import com.ibkr.models.TradingPosition;

public class RiskManager {
    public RiskManager(LiquidityMonitor liquidityMonitor, VolatilityAnalyzer volatilityAnalyzer) {
        this.liquidityMonitor = liquidityMonitor;
        this.volatilityAnalyzer = volatilityAnalyzer;
    }

    private final LiquidityMonitor liquidityMonitor;
    private final VolatilityAnalyzer volatilityAnalyzer;

    public boolean validateTrade(TradingSignal signal, Tick currentTick) {
        // Reject if spread is too wide
        if (!liquidityMonitor.isSpreadAcceptable(currentTick)) {
            return false;
        }

        // Reject if volatility exceeds threshold
        if (volatilityAnalyzer.isTooVolatile(currentTick)) {
            return false;
        }

        // Additional risk checks
        return true;
    }

    public double calculateMaxPositionSize(Tick tick) {
        double volatilityFactor = 1 / volatilityAnalyzer.getCurrentVolatility(tick);
        double liquidityFactor = liquidityMonitor.getLiquidityScore(tick);
        return 1000 * volatilityFactor * liquidityFactor; // Base 1000 shares
    }

    public boolean validateShortSell(Tick tick) {
        // Additional checks for short selling
        return tick.getLastTradedPrice() > tick.getClosePrice() && // Avoid shorting below prev close
                liquidityMonitor.getShortAvailability(tick) > 0.7; // 70% shares available to borrow
    }

    public double getMaxShortPosition(Tick tick) {
        return calculateMaxPositionSize(tick) * 0.8; // 80% of normal size
    }
}
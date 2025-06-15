package com.ibkr.signal;


import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.indicators.VWAPAnalyzer;
import com.ibkr.indicators.VolumeAnalyzer;
import com.ibkr.models.TradeAction;
import com.ibkr.models.TradingSignal;
import com.zerodhatech.models.Tick;
import com.ibkr.models.TradingPosition;
import com.ibkr.risk.VolatilityAnalyzer;


public class BreakoutSignalGenerator {
    private final VolatilityAnalyzer volatilityAnalyzer;
    private double breakoutThreshold = 0.02; // 2%
    private double breakdownThreshold = -0.015; // -1.5% (more sensitive for shorts)
    private final VWAPAnalyzer vwapAnalyzer;
    private final VolumeAnalyzer volumeAnalyzer;
    private final SectorStrengthAnalyzer sectorAnalyzer;
    public BreakoutSignalGenerator() {
        volatilityAnalyzer =  new VolatilityAnalyzer();
        vwapAnalyzer = new VWAPAnalyzer();
        volumeAnalyzer = new VolumeAnalyzer();
        sectorAnalyzer = new SectorStrengthAnalyzer();
    }

    public TradingSignal generateSignal(Tick tick, TradingPosition currentPosition) {
        // LONG Signal: Price breaks above upper threshold
        if (!currentPosition.isInPosition() && isUpwardBreakout(tick)) {
            return createSignal(tick, TradeAction.BUY);
        }

        // SHORT Signal: Price breaks below lower threshold
        if (!currentPosition.isInPosition() && isDownwardBreakdown(tick)) {
            return createSignal(tick, TradeAction.SELL);
        }

        // Exit Conditions
        if (currentPosition.isInPosition()) {
            return handlePositionExit(tick, currentPosition);
        }

        return TradingSignal.hold();
    }

    private boolean isUpwardBreakout(Tick tick) {
        double movement = (tick.getLastTradedPrice() - tick.getOpenPrice()) / tick.getOpenPrice();
        double dynamicThreshold = breakoutThreshold * volatilityAnalyzer.getCurrentVolatility(tick);
        return movement > dynamicThreshold;
    }

    private boolean isDownwardBreakdown(Tick tick) {
        double movement = (tick.getLastTradedPrice() - tick.getOpenPrice()) / tick.getOpenPrice();
        double dynamicThreshold = breakdownThreshold * volatilityAnalyzer.getCurrentVolatility(tick);
        return movement < dynamicThreshold;
    }

    private TradingSignal handlePositionExit(Tick tick, TradingPosition position) {
        boolean isLongPosition = position.getEntryPrice() < tick.getLastTradedPrice();
        double volatilityFactor = position.getVolatilityAtEntry();

        // Profit targets
        double longProfitPrice = position.getEntryPrice() * (1 + (1.5 * volatilityFactor));
        double shortProfitPrice = position.getEntryPrice() * (1 - (1.2 * volatilityFactor)); // Tighter profit for shorts

        // Stop losses
        double longStopPrice = position.getEntryPrice() * (1 - (0.8 * volatilityFactor));
        double shortStopPrice = position.getEntryPrice() * (1 + (0.6 * volatilityFactor)); // Tighter stop for shorts

        if ((isLongPosition && (tick.getLastTradedPrice() >= longProfitPrice ||
                tick.getLastTradedPrice() <= longStopPrice)) ||
                (!isLongPosition && (tick.getLastTradedPrice() <= shortProfitPrice ||
                        tick.getLastTradedPrice() >= shortStopPrice))) {
            return createSignal(tick, isLongPosition ? TradeAction.SELL : TradeAction.BUY);
        }
        return TradingSignal.hold();
    }

    private TradingSignal createSignal(Tick tick, TradeAction action) {
        return new TradingSignal.Builder()
                .instrumentToken(tick.getInstrumentToken())
                .action(action)
                .price(tick.getLastTradedPrice())
                .quantity(calculatePositionSize(tick, action))
                .build();
    }

    private int calculatePositionSize(Tick tick, TradeAction action) {
        double riskAmount = 10000 * 0.01; // 1% of $10k capital
        double riskPerShare = tick.getLastTradedPrice() * 0.01;
        if (action == TradeAction.SELL) {
            riskPerShare *= 0.8; // Smaller size for shorts
        }
        return (int) (riskAmount / riskPerShare);
    }

    public TradingSignal generateShortSignal(Tick tick, String sectorId) {
        // Multi-condition short entry
        boolean vwapConfirmed = vwapAnalyzer.isBelowVWAP(tick);
        boolean volumeSpike = volumeAnalyzer.isBreakdownWithSpike(tick);
        boolean sectorWeakness = sectorAnalyzer.isUnderperforming(tick, sectorId);

        if (vwapConfirmed && volumeSpike && sectorWeakness) {
            return new TradingSignal.Builder()
                    .instrumentToken(tick.getInstrumentToken())
                    .action(TradeAction.SELL)
                    .price(tick.getLastTradedPrice())
                    .quantity(calculateShortSize(tick))
                    //.metadata("SHORT_VWAP_VOLUME_SECTOR")
                    .build();
        }
        return TradingSignal.hold();
    }

    private int calculateShortSize(Tick tick) {
        double risk = Math.min(
                vwapAnalyzer.getVWAP() - tick.getLastTradedPrice(),
                tick.getLastTradedPrice() * 0.02 // Max 2% risk
        );
        return (int) (10000 * 0.01 / risk); // 1% capital risk
    }
}
package com.ibkr.core;

import com.ibkr.analysis.MarketSentimentAnalyzer;
import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.indicators.*;
import com.ibkr.safeguards.*;
import com.ibkr.liquidity.*;
import com.zerodhatech.models.Tick;
import com.ibkr.models.TradingSignal;
import com.ibkr.models.TradingPosition;
import com.ibkr.models.TradeAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TradingEngine {
    private final VWAPAnalyzer vwap = new VWAPAnalyzer();
    private final VolumeAnalyzer volume = new VolumeAnalyzer();
    private final CircuitBreakerMonitor cbMonitor = new CircuitBreakerMonitor();
    private final DarkPoolScanner dpScanner = new DarkPoolScanner();
    private final MarketSentimentAnalyzer sentimentAnalyzer = new MarketSentimentAnalyzer(loadTop200Stocks());
    private final SectorStrengthAnalyzer sectorAnalyzer = new SectorStrengthAnalyzer();

    public TradingSignal generateSignal(Tick tick, TradingPosition position) {
        // Update all analyzers
        updateAnalyzers(tick);

        // Safeguard checks
        if (cbMonitor.isTradingHalted(tick.getSymbol())) {
            return TradingSignal.halt();
        }

        // Generate signals
        if (shouldEnterLong(tick, position)) {
            return buildSignal(tick, TradeAction.BUY, position);
        } else if (shouldEnterShort(tick, position)) {
            return buildSignal(tick, TradeAction.SELL, position);
        } else if (shouldExitPosition(tick, position)) {
            return buildExitSignal(tick, position);
        }

        return TradingSignal.hold();
    }

    private void updateAnalyzers(Tick tick) {
        vwap.update(tick);
        volume.update(tick);
        sectorAnalyzer.updateSectorPerformance(tick.getSymbol(),
                (tick.getLastTradedPrice() - tick.getOpenPrice()) / tick.getOpenPrice());
        cbMonitor.updateStatus(tick.getSymbol(),
                Math.abs(tick.getLastTradedPrice() - tick.getOpenPrice()) / tick.getOpenPrice());
        dpScanner.analyzeTick(tick);
    }

    private boolean shouldEnterLong(Tick tick, TradingPosition position) {
        return !position.isInPosition() &&
                vwap.isAboveVWAP(tick) &&
                volume.isBreakoutWithSpike(tick) &&
                sectorAnalyzer.isOutperforming(tick, "TECH") &&
                cbMonitor.allowAggressiveOrders(tick.getSymbol());
    }

    private boolean shouldEnterShort(Tick tick, TradingPosition position) {
        return !position.isInPosition() &&
                vwap.isBelowVWAP(tick) &&
                volume.isBreakdownWithSpike(tick) &&
                sectorAnalyzer.isUnderperforming(tick, "TECH") &&
                cbMonitor.allowShortSelling(tick.getSymbol()) &&
                !dpScanner.hasDarkPoolSupport(tick.getSymbol());
    }

    private boolean shouldExitPosition(Tick tick, TradingPosition position) {
        if (!position.isInPosition()) {
            return false;
        }

        // Take profit or stop loss check
        double currentReturn = (tick.getLastTradedPrice() - position.getEntryPrice()) / position.getEntryPrice();
        double volatility = vwap.getVolatility();

        if (position.isLong()) {
            return currentReturn >= (1.5 * volatility) ||  // Take profit
                    currentReturn <= (-0.8 * volatility);   // Stop loss
        } else {
            return currentReturn <= (-1.2 * volatility) || // Take profit (short)
                    currentReturn >= (0.6 * volatility);    // Stop loss (short)
        }
    }

    private TradingSignal buildSignal(Tick tick, TradeAction action, TradingPosition position) {
        return new TradingSignal.Builder()
                .symbol(tick.getSymbol())
                .action(action)
                .price(tick.getLastTradedPrice())
                .quantity(calculateSize(tick, action))
                .darkPoolAllowed(dpScanner.hasDarkPoolSupport(tick.getSymbol()))
                .strategyId("VWAP_VOL_SECTOR")
                .build();
    }

    private TradingSignal buildExitSignal(Tick tick, TradingPosition position) {
        TradeAction action = position.isLong() ? TradeAction.SELL : TradeAction.BUY;
        return new TradingSignal.Builder()
                .symbol(position.getSymbol())
                .action(action)
                .price(tick.getLastTradedPrice())
                .quantity(position.getQuantity())
                .strategyId("EXIT_" + position.getInstrumentToken())
                .build();
    }

    private int calculateSize(Tick tick, TradeAction action) {
        double baseSize = 1000; // Shares
        double volatilityAdjustment = 1 / Math.max(0.01, vwap.getVolatility()); // Prevent division by zero
        double liquidityAdjustment = dpScanner.getLiquidityScore(tick.getSymbol());
        double circuitBreakerAdjustment = cbMonitor.getSizeMultiplier(tick.getSymbol());
        double shortingReduction = action == TradeAction.SELL ? 0.8 : 1.0; // Reduce short positions

        return (int) (baseSize * volatilityAdjustment * liquidityAdjustment * circuitBreakerAdjustment * shortingReduction);
    }

    // Additional helper methods
    private boolean isMarketOpen() {
        // Implement market hours check
        return true; // Simplified for example
    }

    private boolean isHighVolatility(Tick tick) {
        return vwap.getVolatility() > (tick.getLastTradedPrice() * 0.05); // 5% threshold
    }

    public List<TradingSignal> generateOpeningSignals(Tick tick) {
        List<TradingSignal> signals = new ArrayList<>();

        if (!sentimentAnalyzer.isInAnalysisWindow()) {
            return signals;
        }

        MarketSentimentAnalyzer.MarketSentiment sentiment = sentimentAnalyzer.getMarketSentiment();

        switch (sentiment) {
            case STRONG_UP:
                signals.addAll(generateBullishSignals(tick));
                break;
            case STRONG_DOWN:
                signals.addAll(generateBearishSignals(tick));
                break;
        }

        return signals;
    }

    private List<TradingSignal> generateBullishSignals(Tick tick) {
        List<TradingSignal> signals = new ArrayList<>();

        // Buy top 5 performing stocks in strongest sectors
        List<String> topSectors = sectorAnalyzer.getTopSectors(3);
        List<String> topStocks = sentimentAnalyzer.getTopPerformers(20, true);

        topStocks.stream()
                .filter(symbol -> topSectors.contains(sectorAnalyzer.getSector(symbol)))
                .limit(5)
                .forEach(symbol -> {
                    signals.add(new TradingSignal.Builder()
                            .symbol(symbol)
                            .action(TradeAction.BUY)
                            .quantity(calculateSize(tick, TradeAction.BUY))
                            .build());
                });

        return signals;
    }

    private List<TradingSignal> generateBearishSignals(Tick tick) {
        List<TradingSignal> signals = new ArrayList<>();

        // Short bottom 5 performing stocks in weakest sectors
        List<String> weakSectors = sectorAnalyzer.getBottomSectors(3);
        List<String> bottomStocks = sentimentAnalyzer.getTopPerformers(20, false);

        bottomStocks.stream()
                .filter(symbol -> weakSectors.contains(sectorAnalyzer.getSector(symbol)))
                .limit(5)
                .forEach(symbol -> {
                    signals.add(new TradingSignal.Builder()
                            .symbol(symbol)
                            .action(TradeAction.SELL)
                            .quantity(calculateSize(tick, TradeAction.SELL))
                            .build());
                });

        return signals;

    }

    private Set<String> loadTop200Stocks() {
        // Load from config/database
        return Set.of("AAPL", "MSFT", "GOOGL"); // Top 200 symbols
    }
}
package com.ibkr.core;

import com.ibkr.analysis.MarketSentimentAnalyzer;
import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.indicators.*;
import com.ibkr.safeguards.*;
import com.ibkr.liquidity.*;
import com.ibkr.data.InstrumentRegistry; // Added import
import com.zerodhatech.models.Tick;
import com.ibkr.models.TradingSignal;
import com.ibkr.models.TradingPosition;
import com.ibkr.models.TradeAction;
import com.ibkr.models.OpeningMarketTrend; // New import
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added

import java.util.ArrayList;
import java.util.List;
import java.util.Map; // New import
import java.util.Set;

public class TradingEngine {
    private static final Logger logger = LoggerFactory.getLogger(TradingEngine.class); // Added

    private final VWAPAnalyzer vwapAnalyzer;
    private final VolumeAnalyzer volumeAnalyzer;
    private final CircuitBreakerMonitor circuitBreakerMonitor;
    private final DarkPoolScanner darkPoolScanner;
    private final MarketSentimentAnalyzer marketSentimentAnalyzer;
    private final SectorStrengthAnalyzer sectorStrengthAnalyzer;
    private final InstrumentRegistry instrumentRegistry; // Added

    public TradingEngine(VWAPAnalyzer vwapAnalyzer, VolumeAnalyzer volumeAnalyzer,
                         CircuitBreakerMonitor circuitBreakerMonitor, DarkPoolScanner darkPoolScanner,
                         MarketSentimentAnalyzer marketSentimentAnalyzer, SectorStrengthAnalyzer sectorStrengthAnalyzer,
                         InstrumentRegistry instrumentRegistry) {
        this.vwapAnalyzer = vwapAnalyzer;
        this.volumeAnalyzer = volumeAnalyzer;
        this.circuitBreakerMonitor = circuitBreakerMonitor;
        this.darkPoolScanner = darkPoolScanner;
        this.marketSentimentAnalyzer = marketSentimentAnalyzer;
        this.sectorStrengthAnalyzer = sectorStrengthAnalyzer;
        this.instrumentRegistry = instrumentRegistry; // Added
    }

    public TradingSignal generateSignal(Tick tick, TradingPosition position) {
        logger.debug("Generating signal for symbol: {}, tick: {}, position: {}", tick.getSymbol(), tick, position);
        // Update all analyzers
        updateAnalyzers(tick);

        // Safeguard checks
        if (circuitBreakerMonitor.isTradingHalted(tick.getSymbol())) {
            logger.warn("Trading halted for symbol: {}. Returning HALT signal.", tick.getSymbol());
            return TradingSignal.halt(tick.getSymbol());
        }

        // Generate signals
        if (shouldEnterLong(tick, position)) {
            TradingSignal signal = buildSignal(tick, TradeAction.BUY, position);
            logger.info("Generated BUY signal for {}: {}", tick.getSymbol(), signal);
            return signal;
        } else if (shouldEnterShort(tick, position)) {
            TradingSignal signal = buildSignal(tick, TradeAction.SELL, position);
            logger.info("Generated SELL signal for {}: {}", tick.getSymbol(), signal);
            return signal;
        } else if (shouldExitPosition(tick, position)) {
            TradingSignal signal = buildExitSignal(tick, position);
            logger.info("Generated EXIT signal for {}: {}", tick.getSymbol(), signal);
            return signal;
        }
        logger.debug("No entry or exit conditions met for {}. Returning HOLD signal.", tick.getSymbol());
        return TradingSignal.hold();
    }

    private void updateAnalyzers(Tick tick) {
        logger.debug("Updating analyzers for symbol: {}, LTP: {}", tick.getSymbol(), tick.getLastTradedPrice());
        vwapAnalyzer.update(tick);
        volumeAnalyzer.update(tick);
        sectorStrengthAnalyzer.updateSectorPerformance(tick.getSymbol(),
                (tick.getLastTradedPrice() - tick.getOpenPrice()) / tick.getOpenPrice());
        circuitBreakerMonitor.updateStatus(tick.getSymbol(),
                Math.abs(tick.getLastTradedPrice() - tick.getOpenPrice()) / tick.getOpenPrice());
        darkPoolScanner.analyzeTick(tick);
    }

    private boolean shouldEnterLong(Tick tick, TradingPosition position) {
        String currentSymbolSector = sectorStrengthAnalyzer.getSector(tick.getSymbol());
        boolean sectorCheckPass = false;
        if (currentSymbolSector == null) {
            logger.warn("No sector defined for symbol: {}. Sector performance check will be skipped/failed.", tick.getSymbol());
            // Strategy Decision: if sector is unknown, do we proceed or not? For now, let's say sector check fails.
            sectorCheckPass = false;
        } else {
            sectorCheckPass = sectorStrengthAnalyzer.isOutperforming(tick, currentSymbolSector);
        }

        boolean enterLong = !position.isInPosition() &&
                vwapAnalyzer.isAboveVWAP(tick) &&
                volumeAnalyzer.isBreakoutWithSpike(tick) &&
                sectorCheckPass &&
                circuitBreakerMonitor.allowAggressiveOrders(tick.getSymbol());
        logger.debug("shouldEnterLong for {}: Conditions - InPosition: {}, AboveVWAP: {}, VolumeSpike: {}, SectorCheckPass (Sector: {}): {}, AggressiveOrdersAllowed: {}. Result: {}",
                tick.getSymbol(), position.isInPosition(), vwapAnalyzer.isAboveVWAP(tick), volumeAnalyzer.isBreakoutWithSpike(tick),
                currentSymbolSector, sectorCheckPass, circuitBreakerMonitor.allowAggressiveOrders(tick.getSymbol()), enterLong);
        return enterLong;
    }

    private boolean shouldEnterShort(Tick tick, TradingPosition position) {
        String currentSymbolSector = sectorStrengthAnalyzer.getSector(tick.getSymbol());
        boolean sectorCheckPass = false;
        if (currentSymbolSector == null) {
            logger.warn("No sector defined for symbol: {}. Sector performance check will be skipped/failed.", tick.getSymbol());
            sectorCheckPass = false;
        } else {
            sectorCheckPass = sectorStrengthAnalyzer.isUnderperforming(tick, currentSymbolSector);
        }

        boolean enterShort = !position.isInPosition() &&
                vwapAnalyzer.isBelowVWAP(tick) &&
                volumeAnalyzer.isBreakdownWithSpike(tick) &&
                sectorCheckPass &&
                circuitBreakerMonitor.allowShortSelling(tick.getSymbol()) &&
                !darkPoolScanner.hasDarkPoolSupport(tick.getSymbol());
        logger.debug("shouldEnterShort for {}: Conditions - InPosition: {}, BelowVWAP: {}, VolumeSpike: {}, SectorCheckPass (Sector: {}): {}, ShortSellingAllowed: {}, NoDarkPool: {}. Result: {}",
                tick.getSymbol(), position.isInPosition(), vwapAnalyzer.isBelowVWAP(tick), volumeAnalyzer.isBreakdownWithSpike(tick),
                currentSymbolSector, sectorCheckPass, circuitBreakerMonitor.allowShortSelling(tick.getSymbol()),
                !darkPoolScanner.hasDarkPoolSupport(tick.getSymbol()), enterShort);
        return enterShort;
    }

    private boolean shouldExitPosition(Tick tick, TradingPosition position) {
        if (!position.isInPosition()) {
            return false;
        }
        double currentReturn = (tick.getLastTradedPrice() - position.getEntryPrice()) / position.getEntryPrice();
        double volatility = vwapAnalyzer.getVolatility();
        boolean exit = false;
        if (position.isLong()) {
            exit = currentReturn >= (1.5 * volatility) || currentReturn <= (-0.8 * volatility);
        } else { // Short position
            exit = currentReturn <= (-1.2 * volatility) || currentReturn >= (0.6 * volatility);
        }
        logger.debug("shouldExitPosition for {}: InPosition: {}, IsLong: {}, CurrentReturn: {:.4f}, Volatility: {:.4f}. Result: {}",
                tick.getSymbol(), position.isInPosition(), position.isLong(), currentReturn, volatility, exit);
        return exit;
    }

    private TradingSignal buildSignal(Tick tick, TradeAction action, TradingPosition position) {
        TradingSignal signal = new TradingSignal.Builder()
                .symbol(tick.getSymbol())
                .action(action)
                .price(tick.getLastTradedPrice())
                .quantity(calculateSize(tick, action))
                .darkPoolAllowed(darkPoolScanner.hasDarkPoolSupport(tick.getSymbol()))
                .strategyId("VWAP_VOL_SECTOR")
                .build();
        logger.debug("Built signal for {}: {}", tick.getSymbol(), signal);
        return signal;
    }

    private TradingSignal buildExitSignal(Tick tick, TradingPosition position) {
        TradeAction action = position.isLong() ? TradeAction.SELL : TradeAction.BUY;
        TradingSignal signal = new TradingSignal.Builder()
                .symbol(position.getSymbol())
                .action(action)
                .price(tick.getLastTradedPrice())
                .quantity(position.getQuantity())
                .strategyId("EXIT_" + position.getInstrumentToken())
                .build();
        logger.debug("Built exit signal for {}: {}", position.getSymbol(), signal);
        return signal;
    }

    private int calculateSize(Tick tick, TradeAction action) {
        if (tick == null) {
            logger.warn("Calculating default size as tick is null (likely for general opening signal list).");
            return 10; // Default placeholder size
        }
        double baseSize = 1000; // Shares
        double vwapVolatility = vwapAnalyzer.getVolatility();
        double volatilityAdjustment = 1 / Math.max(0.01, vwapVolatility); // Prevent division by zero
        double liquidityScore = darkPoolScanner.getLiquidityScore(tick.getSymbol());
        double cbSizeMultiplier = circuitBreakerMonitor.getSizeMultiplier(tick.getSymbol());
        double shortingReduction = action == TradeAction.SELL ? 0.8 : 1.0; // Reduce short positions

        int calculatedQuantity = (int) (baseSize * volatilityAdjustment * liquidityScore * cbSizeMultiplier * shortingReduction);
        logger.trace("calculateSize for {}: Action: {}, BaseSize: {}, Volatility: {:.4f}, VolAdj: {:.2f}, LiqScore: {:.2f}, CB Multiplier: {:.2f}, ShortReduction: {:.2f}. Calculated Quantity: {}",
                tick.getSymbol(), action, baseSize, vwapVolatility, volatilityAdjustment, liquidityScore, cbSizeMultiplier, shortingReduction, calculatedQuantity);
        return calculatedQuantity;
    }

    // Additional helper methods
    private boolean isMarketOpen() {
        // Implement market hours check
        return true; // Simplified for example
    }

    private boolean isHighVolatility(Tick tick) {
        return vwapAnalyzer.getVolatility() > (tick.getLastTradedPrice() * 0.05); // 5% threshold
    }

    public List<TradingSignal> generateOpeningSignals(
            OpeningMarketTrend overallOpeningTrend,
            Map<String, Double> stockOpeningPrices,
            Set<String> monitoredSymbols,
            Map<String, Tick> currentTickData) {

        List<TradingSignal> signals = new ArrayList<>();
        logger.info("Generating opening signals. Overall Trend: {}, Monitored Symbols: {}", overallOpeningTrend, monitoredSymbols.size());

        if (overallOpeningTrend == OpeningMarketTrend.OBSERVATION_PERIOD ||
            overallOpeningTrend == OpeningMarketTrend.OUTSIDE_ANALYSIS_WINDOW ||
            overallOpeningTrend == OpeningMarketTrend.TREND_NEUTRAL) {
            logger.debug("Not generating opening signals due to overall trend: {}", overallOpeningTrend);
            return signals;
        }

        double individualStockMoveThreshold = 0.005; // 0.5% move from open
        int maxSignalsToGenerate = 3; // Limit opening trades

        for (String symbol : monitoredSymbols) {
            if (signals.size() >= maxSignalsToGenerate) break;

            Tick tick = currentTickData.get(symbol);
            Double openPrice = stockOpeningPrices.get(symbol);

            if (tick == null || openPrice == null || openPrice == 0) {
                // logger.trace("Skipping symbol {} for opening signal: missing tick or openPrice.", symbol);
                continue;
            }

            boolean stockMovingWithTrend = false;
            if (overallOpeningTrend == OpeningMarketTrend.TREND_UP && tick.getLastTradedPrice() > openPrice * (1 + individualStockMoveThreshold)) {
                stockMovingWithTrend = true;
            } else if (overallOpeningTrend == OpeningMarketTrend.TREND_DOWN && tick.getLastTradedPrice() < openPrice * (1 - individualStockMoveThreshold)) {
                stockMovingWithTrend = true; // For potential short/avoid long
            }

            if (stockMovingWithTrend) {
                // Further filtering by sector can be added here if SectorStrengthAnalyzer is enhanced
                // String sector = sectorStrengthAnalyzer.getSector(symbol);
                // if (sector != null && sectorStrengthAnalyzer.isSectorAlignedWithTrend(sector, overallOpeningTrend))

                TradeAction action = (overallOpeningTrend == OpeningMarketTrend.TREND_UP) ? TradeAction.BUY : TradeAction.SELL;
                // Basic check: only BUY on TREND_UP for now, no SELL on TREND_DOWN yet.
                if (action == TradeAction.BUY) {
                     TradingSignal signal = new TradingSignal.Builder()
                          .symbol(symbol)
                          .action(action)
                          .quantity(calculateSize(tick, action)) // Now passing the specific tick
                          .price(tick.getLastTradedPrice()) // Use current price for signal
                          .strategyId("OPENING_MARKET_TREND")
                          .build();
                     signals.add(signal);
                     logger.info("Generated opening signal for {}: {} based on market trend {} and stock movement.", symbol, action, overallOpeningTrend);
                }
            }
        }
        return signals;
    }

    // Removed generateBullishSignals and generateBearishSignals methods
}
package com.ibkr.core;

import com.ibkr.analysis.MarketSentimentAnalyzer;
import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.analysis.VolumeSpikeAnalyzer;
import com.ibkr.data.HistoricalVolumeService;
import com.ibkr.indicators.*;
import com.ibkr.safeguards.*;
import com.ibkr.liquidity.*;
import com.ibkr.data.InstrumentRegistry;
import com.ibkr.data.TickAggregator; // +OrbStrategy
import com.ibkr.strategy.orb.OrbStrategy; // +OrbStrategy
import com.ibkr.strategy.common.OrbStrategyParameters; // +OrbStrategy
import com.ibkr.strategy.common.VolumeSpikeStrategyParameters;
import com.ibkr.AppContext; // +OrbStrategy
import com.ibkr.IBKRApiService;
import com.ibkr.analysis.IntradayPriceActionAnalyzer; // +PriceAction
import com.ibkr.models.PriceActionSignal; // +PriceAction
import com.ibkr.alert.TradeAlertLogger; // +Alerts
import com.ibkr.strategy.orb.OrbStrategyState;
import com.zerodhatech.models.Depth;
import com.zerodhatech.models.Tick;
import com.zerodhatech.models.OHLC; // +OrbStrategy
import com.ibkr.models.TradingSignal;
import com.ibkr.models.TradingPosition;
import com.ibkr.models.PreviousDayData; // +OrbStrategy
import com.ibkr.models.TradeAction;
import com.ibkr.models.OpeningMarketTrend; // New import
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added

import java.util.ArrayList;
import java.util.List;
import java.util.Map; // New import
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TradingEngine {
    private static final Logger logger = LoggerFactory.getLogger(TradingEngine.class); // Added

    private final VWAPAnalyzer vwapAnalyzer;
    private final VolumeAnalyzer volumeAnalyzer;
    private final CircuitBreakerMonitor circuitBreakerMonitor;
    private final DarkPoolScanner darkPoolScanner;
    private final MarketSentimentAnalyzer marketSentimentAnalyzer;
    private final SectorStrengthAnalyzer sectorStrengthAnalyzer;
    private final InstrumentRegistry instrumentRegistry;
    private final AppContext appContext; // +OrbStrategy
    private final TickAggregator tickAggregator; // +OrbStrategy
    private final OrbStrategyParameters orbStrategyParameters; // +OrbStrategy
    private final OrbStrategy orbStrategy; // +OrbStrategy
    private final IntradayPriceActionAnalyzer intradayPriceActionAnalyzer; // +PriceAction
    private final HistoricalVolumeService historicalVolumeService;
    private final VolumeSpikeAnalyzer volumeSpikeAnalyzer;

    // Data structures for ORB Strategy support
    /** Inner public static class to hold OHLC and Volume for a bar, accessible by DataUtils */
    public static class BarData {
        public final OHLC ohlc;
        public final long volume;
        public final long timestamp; // Start timestamp of the bar

        public BarData(OHLC ohlc, long volume, long timestamp) {
            this.ohlc = ohlc;
            this.volume = volume;
            this.timestamp = timestamp;
        }
    }
    private final Map<String, List<BarData>> oneMinuteBarsHistory = new ConcurrentHashMap<>(); // Stores OHLCV
    private final Map<String, PreviousDayData> dailyPdhCache = new ConcurrentHashMap<>(); // +OrbStrategy


    public TradingEngine(AppContext appContext, VWAPAnalyzer vwapAnalyzer, VolumeAnalyzer volumeAnalyzer,
                         CircuitBreakerMonitor circuitBreakerMonitor, DarkPoolScanner darkPoolScanner,
                         MarketSentimentAnalyzer marketSentimentAnalyzer, SectorStrengthAnalyzer sectorStrengthAnalyzer,
                         InstrumentRegistry instrumentRegistry, TickAggregator tickAggregator) {
        this.appContext = appContext;
        this.vwapAnalyzer = vwapAnalyzer;
        this.volumeAnalyzer = volumeAnalyzer;
        this.circuitBreakerMonitor = circuitBreakerMonitor;
        this.darkPoolScanner = darkPoolScanner;
        this.marketSentimentAnalyzer = marketSentimentAnalyzer;
        this.sectorStrengthAnalyzer = sectorStrengthAnalyzer;
        this.instrumentRegistry = instrumentRegistry;
        this.tickAggregator = tickAggregator;

        // Initialize ORB Strategy components
        this.orbStrategyParameters = new OrbStrategyParameters(); // Using default parameters for now
        this.orbStrategy = new OrbStrategy(this.appContext, this.instrumentRegistry, this.orbStrategyParameters, "America/New_York"); // Assuming ET for IBKR

        // Initialize Intraday Price Action Analyzer
        this.intradayPriceActionAnalyzer = new IntradayPriceActionAnalyzer(this.appContext); // +PriceAction

        // Initialize Volume Spike Analysis components
        IBKRApiService ibkrApiService = new IBKRApiService();
        ibkrApiService.connect("127.0.0.1", 7497, 0); // Make sure the port is correct
        this.historicalVolumeService = new HistoricalVolumeService(ibkrApiService);
        this.volumeSpikeAnalyzer = new VolumeSpikeAnalyzer(this.historicalVolumeService);

        // Load historical volume data at startup
        Set<String> symbolsToMonitor = instrumentRegistry.getAllSymbols();
        if (symbolsToMonitor != null && !symbolsToMonitor.isEmpty()) {
            historicalVolumeService.calculateAverageVolumes(new ArrayList<>(symbolsToMonitor));
        } else {
            logger.warn("No symbols found in InstrumentRegistry at startup. Historical volume data will not be pre-loaded.");
        }

        logger.info("TradingEngine initialized, including OrbStrategy, IntradayPriceActionAnalyzer, and VolumeSpikeAnalyzer.");
    }

    /**
     * Initializes strategies for a given symbol at the start of a trading day.
     * This includes resetting daily state for strategies like ORB and caching necessary daily data.
     * @param symbol The stock symbol to initialize.
     * @param pdhData The PreviousDayData for the symbol.
     */
    public void initializeStrategyForSymbol(String symbol, PreviousDayData pdhData) {
        if (symbol == null || symbol.isEmpty() || pdhData == null) {
            logger.warn("Cannot initialize strategy for symbol: {} with pdhData: {}. Invalid input.", symbol, pdhData);
            return;
        }

        // Cache PDH
        dailyPdhCache.put(symbol, pdhData);
        logger.info("Cached PDH for {}: {}", symbol, pdhData.getPreviousHigh());

        // Reset ORB strategy daily state and set PDH
        if (orbStrategy != null) {
            orbStrategy.resetDailyState(symbol);
            orbStrategy.setPreviousDayHigh(symbol, pdhData.getPreviousHigh());
            logger.info("ORB Strategy daily state reset and PDH set for symbol: {}", symbol);
        }

        // Reset other strategy states here if needed
        // e.g., clear oneMinuteBarsHistory for the symbol if it's not automatically managed by size
        List<BarData> history = oneMinuteBarsHistory.get(symbol);
        if (history != null) {
            history.clear(); // Clears history for the new day
            logger.debug("Cleared 1-minute bar history for {}", symbol);
        }

        // Initialize IntradayPriceActionAnalyzer state for the symbol
        if (intradayPriceActionAnalyzer != null) {
            // intradayOpen will be properly set by the first bar in IntradayPriceActionAnalyzer.processBar
            intradayPriceActionAnalyzer.initializeSymbol(symbol, pdhData.getPreviousHigh(), pdhData.getPreviousClose());
        }

        logger.info("Daily strategy initialization complete for symbol: {}", symbol);
    }

    // Helper method to calculate average volume from the stored 1-minute bar history
    private double calculateAverageVolume(String symbol, int lookbackPeriods) {
        List<BarData> history = oneMinuteBarsHistory.get(symbol);
        if (history == null || history.isEmpty() || lookbackPeriods <= 0) {
            logger.warn("Cannot calculate average volume for {}: No history or invalid lookback {}.", symbol, lookbackPeriods);
            return 0.0;
        }

        int actualLookback = Math.min(lookbackPeriods, history.size());
        double sumVolume = 0;
        // Iterate from end of list backwards (most recent `actualLookback` bars)
        // The 'history' list contains bars *before* the current one being processed.
        for (int i = 0; i < actualLookback; i++) {
            // history.size() - 1 is the latest bar in history (which is previous to current newBar)
            // history.size() - 1 - i gets elements going backwards
            sumVolume += history.get(history.size() - 1 - i).volume;
        }

        if (actualLookback == 0) return 0.0;
        double avgVol = sumVolume / actualLookback;
        logger.debug("Calculated avg volume for {}: Lookback={}, ActualLookback={}, SumVol={}, AvgVol={}",
            symbol, lookbackPeriods, actualLookback, sumVolume, avgVol);
        return avgVol;
    }


    /**
     * Processes a newly closed 1-minute bar for a symbol to generate ORB strategy signals.
     * @param symbol The stock symbol.
     * @param ohlcPortion The OHLC part of the newly closed 1-minute bar.
     * @param volumeForBar The volume for this newly closed 1-minute bar.
     * @param barTimestamp The epoch millisecond starting timestamp of this newBar.
     * @param latestTickForDepth The latest available Tick object for this symbol (to get depth).
     * @return TradingSignal from ORB strategy, or null if no signal.
     */
    public TradingSignal onOneMinuteBarClose(String symbol, OHLC ohlcPortion, long volumeForBar, long barTimestamp, Tick latestTickForDepth) {
        if (symbol == null || ohlcPortion == null || latestTickForDepth == null) {
            logger.warn("Invalid arguments for onOneMinuteBarClose for symbol: {}", symbol);
            return null;
        }

        logger.debug("Processing 1-min bar for {}: O={}, H={}, L={}, C={}, V={}, Time={}",
                symbol, ohlcPortion.getOpen(), ohlcPortion.getHigh(), ohlcPortion.getLow(), ohlcPortion.getClose(), volumeForBar, barTimestamp);

        // Retrieve PDH from cache
        PreviousDayData pdhData = dailyPdhCache.get(symbol);
        if (pdhData == null) {
            logger.warn("PDH data not found in cache for symbol: {}. ORB strategy may not run correctly.", symbol);
            // Depending on OrbStrategy's internal handling of missing PDH, this might be problematic.
            // OrbStrategy.setPreviousDayHigh should have been called during daily init.
            // If we absolutely need it here and it's missing, could try to fetch from appContext,
            // but it's better if it's pre-loaded.
            // For now, OrbStrategy has its own pdhFetched check.
        }
        // double previousDayHigh = (pdhData != null) ? pdhData.getPreviousHigh() : 0.0; // OrbStrategy handles its own PDH state

        // Prepare VolumeData
        // Note: calculateAverageVolume needs history *before* adding the new BarData.
        double averageLastNVols = calculateAverageVolume(symbol, orbStrategyParameters.getVolumeAverageLookbackCandles());
        OrbStrategy.VolumeData volumeData = new OrbStrategy.VolumeData(volumeForBar, averageLastNVols);

        // Update history *after* calculating average for the current bar's context
        List<BarData> history = oneMinuteBarsHistory.computeIfAbsent(symbol, k -> new ArrayList<>());
        history.add(new BarData(ohlcPortion, volumeForBar, barTimestamp)); // Add current bar to history

        // Optional: Trim history if it grows too large
        int maxHistorySize = Math.max(20, orbStrategyParameters.getVolumeAverageLookbackCandles() + 5);
        while (history.size() > maxHistorySize) {
            history.remove(0);
        }

        // Extract Market Depth
        List<Depth> bidDepth = new ArrayList<>();
        List<Depth> askDepth = new ArrayList<>();
        if (latestTickForDepth.getMarketDepth() != null) {
            bidDepth = latestTickForDepth.getMarketDepth().getOrDefault("buy", new ArrayList<>());
            askDepth = latestTickForDepth.getMarketDepth().getOrDefault("sell", new ArrayList<>());
        } else {
            logger.warn("Market depth is null in latestTickForDepth for symbol: {}", symbol);
        }

        // Call OrbStrategy
        if (orbStrategy != null) {
            // Pass the OHLC part of the new bar to the strategy
            TradingSignal orbSignal = orbStrategy.processBar(symbol, ohlcPortion, barTimestamp, bidDepth, askDepth, volumeData);
            if (orbSignal != null && orbSignal.getAction() != TradeAction.HOLD) {
                logger.info("TradingEngine: ORB Strategy generated signal for {}: {}", symbol, orbSignal);
                // Potentially return orbSignal here if it should take precedence immediately,
                // or let PriceActionAnalyzer run and decide later.
                // For now, let ORB signal take precedence if generated.
                return orbSignal;
            }
        }

        // Process with IntradayPriceActionAnalyzer
        if (intradayPriceActionAnalyzer != null) {
            // historyForVolumeAvg for IntradayPriceActionAnalyzer might be different if it needs a different lookback
            // For now, using the same oneMinuteBarsHistory.get(symbol)
            List<BarData> currentHistory = oneMinuteBarsHistory.get(symbol);
            if (currentHistory == null) { // Should have been initialized by computeIfAbsent earlier
                currentHistory = new ArrayList<>(); // Avoid NPE if somehow null
            }
            // Create a new BarData for the current bar to pass to processBar
            BarData currentBarData = new BarData(ohlcPortion, volumeForBar, barTimestamp);
            intradayPriceActionAnalyzer.processBar(symbol, currentBarData, new ArrayList<>(currentHistory)); // Pass a copy of history

            PriceActionSignal priceActionSignal = intradayPriceActionAnalyzer.getSignal(symbol);
            if (priceActionSignal != null &&
                (priceActionSignal == PriceActionSignal.SIGNIFICANT_POSITIVE_CHANGE || priceActionSignal == PriceActionSignal.SIGNIFICANT_NEGATIVE_CHANGE)) {

                com.ibkr.analysis.IntradayPriceActionState priceActionState = intradayPriceActionAnalyzer.getState(symbol); // Assuming getState is public in analyzer
                String alertMessage = String.format("Signal: %s, TriggerPrice: %.2f, CurrentHigh: %.2f, CurrentLow: %.2f, BarClose: %.2f",
                        priceActionSignal.name(),
                        priceActionState.initialTriggerPrice,
                        priceActionState.highestPriceAfterTrigger,
                        priceActionState.lowestPriceAfterTrigger,
                        ohlcPortion.getClose());

                TradeAlertLogger.logSystemAlert(
                        "PRICE_ACTION",
                        symbol,
                        alertMessage,
                        ohlcPortion.getClose() // Context price for the alert
                );
                logger.info("TradingEngine: Alert logged for {} - {}", symbol, alertMessage);
                // TODO: Convert PriceActionSignal to TradingSignal for execution if needed.
            }
        }

        // If ORB signal was generated and returned, this part is skipped.
        // If no ORB signal, and PriceActionAnalyzer is just for logging/events now, return null.
        return null;
    }

    public TradingSignal generateSignal(Tick tick, TradingPosition position) {
        logger.debug("Generating signal for symbol: {}, tick: {}, position: {}", tick.getSymbol(), tick, position);

        // Check ORB strategy state first
        if (orbStrategy != null) {
            OrbStrategyState orbState = orbStrategy.getState(tick.getSymbol()); // Assumes OrbStrategy has a public getState or similar
            if (orbState.tradeTakenToday) {
                logger.info("ORB trade already taken for {} today. Tick-based strategy will not generate new entry signals.", tick.getSymbol());
                // Still allow tick-based logic to manage exits if position was taken by ORB
                // and if the existing exit logic is compatible.
                // For now, if ORB trade is done, we only check for exits from existing positions.
                if (shouldExitPosition(tick, position)) { // position here would be the one from ORB
                    return buildExitSignal(tick, position);
                }
                return TradingSignal.hold(); // No new entries from tick-strategy
            }
        }

        // Update tick-based analyzers
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
        volumeSpikeAnalyzer.update(tick, tick.getSymbol()); // Update our new analyzer
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
                volumeSpikeAnalyzer.isHappening(tick.getSymbol()) && // New volume spike check
                sectorCheckPass &&
                circuitBreakerMonitor.allowAggressiveOrders(tick.getSymbol());
        logger.debug("shouldEnterLong for {}: Conditions - InPosition: {}, AboveVWAP: {}, VolumeSpike: {}, Happening: {}, SectorCheckPass (Sector: {}): {}, AggressiveOrdersAllowed: {}. Result: {}",
                tick.getSymbol(), position.isInPosition(), vwapAnalyzer.isAboveVWAP(tick), volumeAnalyzer.isBreakoutWithSpike(tick),
                volumeSpikeAnalyzer.isHappening(tick.getSymbol()), currentSymbolSector, sectorCheckPass,
                circuitBreakerMonitor.allowAggressiveOrders(tick.getSymbol()), enterLong);
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
                volumeSpikeAnalyzer.isHappening(tick.getSymbol()) && // New volume spike check
                sectorCheckPass &&
                circuitBreakerMonitor.allowShortSelling(tick.getSymbol()) &&
                !darkPoolScanner.hasDarkPoolSupport(tick.getSymbol());
        logger.debug("shouldEnterShort for {}: Conditions - InPosition: {}, BelowVWAP: {}, VolumeSpike: {}, Happening: {}, SectorCheckPass (Sector: {}): {}, ShortSellingAllowed: {}, NoDarkPool: {}. Result: {}",
                tick.getSymbol(), position.isInPosition(), vwapAnalyzer.isBelowVWAP(tick), volumeAnalyzer.isBreakdownWithSpike(tick),
                volumeSpikeAnalyzer.isHappening(tick.getSymbol()), currentSymbolSector, sectorCheckPass,
                circuitBreakerMonitor.allowShortSelling(tick.getSymbol()),
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

    /**
     * Retrieves the most recently completed 1-minute bar data for a symbol.
     * @param symbol The stock symbol.
     * @return The last BarData object, or null if no history exists for the symbol.
     */
    public BarData getLastCompletedBarData(String symbol) {
        List<BarData> history = oneMinuteBarsHistory.get(symbol);
        if (history != null && !history.isEmpty()) {
            // The last element in the list is the most recently added (completed) bar
            return history.get(history.size() - 1);
        }
        logger.debug("No 1-minute bar history found for symbol {} when requesting last completed bar.", symbol);
        return null;
    }
}
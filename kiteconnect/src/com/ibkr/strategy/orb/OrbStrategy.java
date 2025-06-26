package com.ibkr.strategy.orb;

import com.ibkr.AppContext;
import com.ibkr.data.InstrumentRegistry; // Assuming this is needed for symbol lookup if not provided directly
import com.ibkr.models.TradingSignal;
import com.ibkr.models.TradeAction; // Assuming this enum exists in com.ibkr.models
import com.ibkr.strategy.common.OrbStrategyParameters;
import com.zerodhatech.models.OHLC; // Using Zerodha's OHLC model as per existing structures
import com.zerodhatech.models.Depth; // Using Zerodha's Depth model

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements a 15-minute Opening Range Breakout (ORB) trading strategy.
 *
 * Strategy Conditions:
 * 1. ORB Definition: First 15 mins of trading (e.g., 9:15 AM - 9:30 AM) define ORB high/low.
 * 2. Breakout: Bullish breakout (price crosses ORB high) only after ORB definition time.
 * 3. Volume Spike: Breakout candle's volume > X times average volume of last N candles.
 * 4. PDH Filter: Breakout candle's close must be above Previous Day's High.
 * 5. Bid-Ask Ratio: Total Bid Quantity / Total Ask Quantity > Y.
 * 6. Consolidation Filter: Between ORB end and a defined time (e.g., 9:30 AM - 9:45 AM),
 *    price must stay in a tight range (e.g., max 0.5%). If fails, no trades after this period.
 * 7. Retest: After breakout, wait for a pullback to ORB high (or very close)
 *    and then a close above ORB high before entering.
 */
public class OrbStrategy {
    private static final Logger logger = LoggerFactory.getLogger(OrbStrategy.class);

    private final AppContext appContext;
    private final InstrumentRegistry instrumentRegistry; // May not be directly needed if symbol is passed in
    private final OrbStrategyParameters params;
    private final Map<String, OrbStrategyState> stateBySymbol = new ConcurrentHashMap<>();
    private final ZoneId marketTimeZone; // e.g., ZoneId.of("America/New_York")

    // Helper structure for volume data input
    public static class VolumeData {
        public final double currentCandleVolume;
        public final double averageVolumeLastN;

        public VolumeData(double currentCandleVolume, double averageVolumeLastN) {
            this.currentCandleVolume = currentCandleVolume;
            this.averageVolumeLastN = averageVolumeLastN;
        }
    }

    public OrbStrategy(AppContext appContext, InstrumentRegistry instrumentRegistry, OrbStrategyParameters params, String marketTimeZoneId) {
        this.appContext = appContext;
        this.instrumentRegistry = instrumentRegistry;
        this.params = params;
        this.marketTimeZone = ZoneId.of(marketTimeZoneId);
        logger.info("OrbStrategy initialized with parameters. Market Timezone: {}", marketTimeZoneId);
    }

    private OrbStrategyState getState(String symbol) {
        return stateBySymbol.computeIfAbsent(symbol, s -> {
            logger.info("Creating new OrbStrategyState for symbol: {}", s);
            return new OrbStrategyState(s);
        });
    }

    // Call this at the start of each trading day for all relevant symbols
    public void resetDailyState(String symbol) {
        OrbStrategyState state = getState(symbol);
        state.resetForNewDay();
        logger.info("ORB daily state reset for symbol: {}", symbol);
    }

    public void setPreviousDayHigh(String symbol, double pdh) {
        OrbStrategyState state = getState(symbol);
        state.previousDayHigh = pdh;
        state.pdhFetched = true;
        logger.debug("PDH set for {}: {}", symbol, pdh);
    }


    /**
     * Processes a new market data bar (e.g., 1-minute OHLC) for a given symbol.
     *
     * @param symbol          The stock symbol.
     * @param bar             The OHLC data for the current bar/candle.
     * @param barTimestamp    The starting timestamp of the bar (epoch milliseconds).
     * @param bidDepth        Current top bid depth levels.
     * @param askDepth        Current top ask depth levels.
     * @param volumeData      Volume data for the current candle and recent average.
     * @return A TradingSignal if entry conditions are met, otherwise null.
     */
    public TradingSignal processBar(String symbol, OHLC bar, long barTimestamp,
                                   List<Depth> bidDepth, List<Depth> askDepth,
                                   VolumeData volumeData) {

        OrbStrategyState state = getState(symbol);
        LocalTime currentMarketTime = LocalTime.ofInstant(java.time.Instant.ofEpochMilli(barTimestamp), marketTimeZone);

        // Ensure PDH is fetched; this would ideally be done once at strategy initialization for the day
        if (!state.pdhFetched) {
            // In a real system, PDH might be pre-loaded or fetched via AppContext
            // For this structure, assuming it's passed via setPreviousDayHigh or fetched here if AppContext allows
            // If AppContext.getPreviousDayData(symbol) is available:
            // PreviousDayData pdData = appContext.getPreviousDayData(symbol);
            // if (pdData != null) { setPreviousDayHigh(symbol, pdData.getPreviousHigh()); }
            // else { logger.warn("PDH not available for {}", symbol); return null; } // Cannot proceed without PDH
            if(state.previousDayHigh == 0.0) { // check if it was set externally
                 logger.warn("PDH not fetched for {}. Strategy cannot proceed effectively.", symbol);
                 return null; // Or handle as per strategy requirements if PDH is optional initially
            }
        }

        // 1. ORB Definition (e.g., 9:15 AM - 9:30 AM)
        if (!state.orbDefined) {
            if (currentMarketTime.isAfter(params.getOrbStartTime().minusSeconds(1)) &&
                currentMarketTime.isBefore(params.getOrbEndTime().plusSeconds(1))) {
                state.orbHigh = Math.max(state.orbHigh, bar.getHigh());
                state.orbLow = Math.min(state.orbLow, bar.getLow());
                logger.debug("ORB Def for {}: Time {}, Bar H/L: {}/{}, ORB H/L: {}/{}",
                        symbol, currentMarketTime, bar.getHigh(), bar.getLow(), state.orbHigh, state.orbLow);
            }
            // Check if ORB period has just ended
            if (currentMarketTime.isAfter(params.getOrbEndTime().minusSeconds(1)) && state.orbCalculationEndTime == null) {
                if (state.orbHigh > 0 && state.orbLow < Double.MAX_VALUE) {
                    state.orbDefined = true;
                    state.orbCalculationEndTime = currentMarketTime; // Mark time when ORB was set
                    logger.info("ORB Defined for {}: High={}, Low={}. Time: {}",
                            symbol, state.orbHigh, state.orbLow, currentMarketTime);
                } else {
                    logger.warn("ORB period ended for {} but ORB values are invalid (High={}, Low={}). ORB not defined.",
                            symbol, state.orbHigh, state.orbLow);
                    // Potentially disable strategy for the day for this symbol if ORB isn't valid
                    state.consolidationRangeValid = false; // Mark as invalid to prevent trades
                }
            }
        }

        // If ORB is not yet defined, or trade already taken, or consolidation failed, do nothing further for trading.
        if (!state.orbDefined || state.tradeTakenToday) {
            return null;
        }

        // If consolidation was invalidated earlier, and we are past that period, block trades.
        if (!state.consolidationRangeValid && currentMarketTime.isAfter(params.getConsolidationEndTime())) {
             // logger.info("Consolidation range failed for {}, no ORB trades allowed after {}.", symbol, params.getConsolidationEndTime());
             return null;
        }


        // 2. Consolidation Filter (e.g., 9:30 AM - 9:45 AM)
        // This check applies only if current time is within the consolidation window.
        if (currentMarketTime.isAfter(params.getConsolidationStartTime().minusSeconds(1)) &&
            currentMarketTime.isBefore(params.getConsolidationEndTime().plusSeconds(1))) {

            if (!state.inConsolidationPeriod) { // First bar in consolidation
                state.inConsolidationPeriod = true;
                state.consolidationPeriodHigh = bar.getHigh();
                state.consolidationPeriodLow = bar.getLow();
                logger.info("Consolidation period started for {}: Initial H/L: {}/{}", symbol, state.consolidationPeriodHigh, state.consolidationPeriodLow);
            } else { // Subsequent bars in consolidation
                state.consolidationPeriodHigh = Math.max(state.consolidationPeriodHigh, bar.getHigh());
                state.consolidationPeriodLow = Math.min(state.consolidationPeriodLow, bar.getLow());
            }

            if (state.consolidationPeriodLow > 0) { // Ensure low is valid
                double currentRange = (state.consolidationPeriodHigh - state.consolidationPeriodLow) / state.consolidationPeriodLow;
                if (currentRange > params.getConsolidationMaxRangePercent()) {
                    if (state.consolidationRangeValid) { // Log only once
                       logger.info("Consolidation FAILED for {}: Range {}% > {}%. High={}, Low={}",
                                symbol, String.format("%.2f", currentRange * 100),
                                String.format("%.2f", params.getConsolidationMaxRangePercent() * 100),
                                state.consolidationPeriodHigh, state.consolidationPeriodLow);
                    }
                    state.consolidationRangeValid = false;
                } else {
                     logger.debug("Consolidation check for {}: Current Range {}%, Max Range {}%. High={}, Low={}",
                                symbol, String.format("%.2f", currentRange * 100),
                                String.format("%.2f", params.getConsolidationMaxRangePercent() * 100),
                                state.consolidationPeriodHigh, state.consolidationPeriodLow);
                }
            }
        } else if (state.inConsolidationPeriod) { // Consolidation period just ended
            state.inConsolidationPeriod = false;
            if (state.consolidationRangeValid) {
                logger.info("Consolidation PASSED for {}. Valid range maintained.", symbol);
            } else {
                 logger.info("Consolidation period ended for {}. Range was INVALID. No ORB trades after {}.", symbol, params.getConsolidationEndTime());
            }
        }


        // Trading logic only after trade start time (e.g., 9:30 AM)
        if (currentMarketTime.isBefore(params.getTradeStartTime())) {
            return null;
        }

        // If consolidation failed and we are after consolidation period, no trades.
        if (!state.consolidationRangeValid && currentMarketTime.isAfter(params.getConsolidationEndTime())) {
            return null;
        }

        // 3. Breakout Condition
        if (!state.breakoutOccurred && bar.getClose() > state.orbHigh) {
            logger.debug("Potential Breakout for {}: Close {} > ORB High {}", symbol, bar.getClose(), state.orbHigh);

            // Volume Spike Filter
            boolean volumeSpike = volumeData.currentCandleVolume > (params.getVolumeSpikeMultiplier() * volumeData.averageVolumeLastN);
            if (!volumeSpike) {
                logger.info("Breakout REJECTED for {}: Volume spike condition failed. CurrentVol={}, AvgVol={}, Multiplier={}",
                        symbol, volumeData.currentCandleVolume, volumeData.averageVolumeLastN, params.getVolumeSpikeMultiplier());
                return null;
            }
            logger.debug("Breakout for {}: Volume Spike PASSED", symbol);

            // PDH Filter
            if (bar.getClose() <= state.previousDayHigh) {
                logger.info("Breakout REJECTED for {}: PDH filter failed. Close {} <= PDH {}",
                        symbol, bar.getClose(), state.previousDayHigh);
                return null;
            }
            logger.debug("Breakout for {}: PDH PASSED", symbol);

            // Bid-Ask Ratio Filter
            long totalBidQty = 0;
            for (Depth d : bidDepth) {
                totalBidQty += d.getQuantity();
            }
            long totalAskQty = 0;
            for (Depth d : askDepth) {
                totalAskQty += d.getQuantity();
            }

            if (totalAskQty == 0) { // Avoid division by zero, implies very illiquid ask side
                logger.warn("Breakout REJECTED for {}: Total Ask Qty is 0. Bid/Ask Ratio cannot be calculated.", symbol);
                return null;
            }
            double bidAskRatio = (double) totalBidQty / totalAskQty;
            if (bidAskRatio <= params.getMinBidAskRatio()) {
                logger.info("Breakout REJECTED for {}: Bid-Ask Ratio filter failed. Ratio {} <= Threshold {}",
                        symbol, String.format("%.2f", bidAskRatio), params.getMinBidAskRatio());
                return null;
            }
            logger.debug("Breakout for {}: Bid-Ask Ratio PASSED (Ratio: {})", symbol, String.format("%.2f", bidAskRatio));

            // All breakout conditions met
            state.breakoutOccurred = true;
            state.breakoutLevel = state.orbHigh; // Breakout level is the ORB high
            logger.info("BREAKOUT CONFIRMED for {}: Price {} broke ORB High {} with all filters passed.",
                    symbol, bar.getClose(), state.breakoutLevel);
            // Do not generate signal yet, wait for retest.
        }

        // 4. Retest Condition
        if (state.breakoutOccurred && !state.tradeTakenToday) {
            // Price pulled back to ORB high (or very close)
            boolean pulledBackToOrbHigh = bar.getLow() <= state.breakoutLevel * (1 + params.getRetestProximityPercent()) &&
                                          bar.getLow() >= state.breakoutLevel * (1 - params.getRetestProximityPercent());

            // And then closed above ORB high
            boolean closedAboveOrbHigh = bar.getClose() > state.breakoutLevel;

            if (pulledBackToOrbHigh && closedAboveOrbHigh) {
                logger.info("RETEST SUCCESSFUL for {}: Low {} retested ORB High {}, Close {} above. Generating BUY signal.",
                        symbol, bar.getLow(), state.breakoutLevel, bar.getClose());

                state.tradeTakenToday = true; // Mark trade as taken for the day

                // TODO: Determine quantity for the signal
                int quantity = 1; // Placeholder - implement proper position sizing

                return new TradingSignal.Builder()
                        .symbol(symbol)
                        // .instrumentToken(...) // Requires lookup or passing it through
                        .action(TradeAction.BUY)
                        .price(bar.getClose()) // Entry at the close of the retest candle
                        .quantity(quantity)
                        .strategyName("ORB_15Min_Retest")
                        .triggeredAt(System.currentTimeMillis()) // Add timestamp
                        .build();
            } else {
                logger.debug("Retest check for {}: Low {}, Close {}. ORB High {}. Proximity: {}%, PulledBack: {}, ClosedAbove: {}",
                    symbol, bar.getLow(), bar.getClose(), state.breakoutLevel, params.getRetestProximityPercent()*100,
                    pulledBackToOrbHigh, closedAboveOrbHigh);
            }
        }
        return null; // No signal
    }

    // --- Optional: Suggestions for Entry/Exit and SL placement ---
    // These would typically be part of the TradingSignal or handled by an execution manager.

    /**
     * Suggestion for Stop-Loss:
     * - Below the low of the retest candle.
     * - Or, a fixed percentage/ATR below the ORB High (entry level).
     * - Or, below the ORB Low for a wider, more conservative stop.
     *
     * Suggestion for Take Profit:
     * - Fixed Risk-Reward Ratio (e.g., 1.5:1 or 2:1 based on SL distance).
     * - Target based on ATR multiples from entry.
     * - Trailing stop-loss initiated after price moves X% in profit.
     *
     * Entry:
     * - On the close of the valid retest candle, as implemented.
     */
}

package com.ibkr.analysis;

import com.ibkr.AppContext;
import com.ibkr.core.TradingEngine; // For BarData
import com.ibkr.models.PriceActionSignal;
import com.ibkr.models.PreviousDayData; // For getting PDH
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IntradayPriceActionAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(IntradayPriceActionAnalyzer.class);

    private final AppContext appContext;
    private final Map<String, IntradayPriceActionState> stateBySymbol = new ConcurrentHashMap<>();

    // Configuration Parameters (TODO: Move to a dedicated config class or app.properties)
    private static final double MIN_RANGE_PERCENT = 2.0; // Minimum intraday range (H-L)/L * 100
    private static final long MIN_ACCUMULATED_VOLUME_AT_HIGHS = 100000; // Example threshold
    private static final double VOLUME_SPIKE_MULTIPLIER = 2.0; // Current bar volume vs. average
    private static final int VOLUME_AVG_LOOKBACK_PERIOD = 10; // For N-bar average volume
    private static final double SIGNIFICANT_POSITIVE_CHANGE_THRESHOLD = 1.5; // %
    private static final double SIGNIFICANT_NEGATIVE_CHANGE_THRESHOLD = 1.5; // % (drawdown)
    private static final int BARS_TO_KEEP_SPIKE_FLAG = 3; // How many bars to keep recentVolumeSpikeOccurred = true

    public IntradayPriceActionAnalyzer(AppContext appContext) {
        this.appContext = appContext;
    }

    private IntradayPriceActionState getState(String symbol) {
        return stateBySymbol.computeIfAbsent(symbol, k -> {
            logger.info("Creating new IntradayPriceActionState for symbol: {}", k);
            return new IntradayPriceActionState(k);
        });
    }

    public void initializeSymbol(String symbol, double pdh, double dayOpenPrice) {
        IntradayPriceActionState state = getState(symbol);
        state.resetForNewDay(pdh, dayOpenPrice);
        logger.info("Initialized IntradayPriceActionState for {}: PDH={}, DayOpen={}", symbol, pdh, dayOpenPrice);
    }

    public void resetSymbol(String symbol) {
        IntradayPriceActionState state = getState(symbol);
        PreviousDayData pdhData = appContext.getPreviousDayData(symbol);
        double pdh = (pdhData != null) ? pdhData.getPreviousHigh() : 0.0;
        // Assuming open price needs to be re-initialized perhaps from the first bar of the day if available
        // For a full reset, it might be better to remove from stateBySymbol map or re-initialize with known open.
        // For now, re-initializing with potentially stale open if not updated by first bar logic.
        state.resetForNewDay(pdh, state.intradayOpen);
        logger.info("Reset IntradayPriceActionState for symbol: {}", symbol);
    }


    public void processBar(String symbol, TradingEngine.BarData bar, List<TradingEngine.BarData> historyForVolumeAvg) {
        IntradayPriceActionState state = getState(symbol);

        if (state.intradayOpen == 0.0 && bar.ohlc.getOpen() != 0.0) { // Set intraday open from first bar if not set
            state.intradayOpen = bar.ohlc.getOpen();
            state.intradayHigh = bar.ohlc.getHigh(); // Initialize H/L with first bar's data
            state.intradayLow = bar.ohlc.getLow();
            logger.info("Set intradayOpen for {} from first bar: {}", symbol, state.intradayOpen);
        }

        // Update intraday high/low
        if (bar.ohlc.getHigh() > state.intradayHigh) {
            state.intradayHigh = bar.ohlc.getHigh();
            state.volumeAccumulatedAtHighs += bar.volume; // Accumulate volume on new highs
            state.timeOfLastIntradayHigh = bar.timestamp;
        }
        state.intradayLow = Math.min(state.intradayLow, bar.ohlc.getLow());

        // Volume Spike Logic
        if (state.recentVolumeSpikeOccurred) {
            state.barsProcessedSinceLastSpikeReset++;
            if (state.barsProcessedSinceLastSpikeReset > BARS_TO_KEEP_SPIKE_FLAG) {
                state.recentVolumeSpikeOccurred = false;
                state.barsProcessedSinceLastSpikeReset = 0;
            }
        }

        if (historyForVolumeAvg != null && !historyForVolumeAvg.isEmpty()) {
            long sumVolume = 0;
            int count = 0;
            // Get last N volumes from history (excluding current bar)
            for (int i = Math.max(0, historyForVolumeAvg.size() - VOLUME_AVG_LOOKBACK_PERIOD); i < historyForVolumeAvg.size(); i++) {
                sumVolume += historyForVolumeAvg.get(i).volume;
                count++;
            }
            if (count > 0) {
                double avgVolume = (double) sumVolume / count;
                if (avgVolume > 0 && bar.volume > (avgVolume * VOLUME_SPIKE_MULTIPLIER)) {
                    state.recentVolumeSpikeOccurred = true;
                    state.barsProcessedSinceLastSpikeReset = 0; // Reset counter on new spike
                    logger.debug("Volume spike detected for {}: BarVol={}, AvgVol={}", symbol, bar.volume, avgVolume);
                }
            }
        }

        // Primary Condition Check
        if (!state.primaryConditionMet) {
            boolean pdhCondition = state.pdh < state.intradayHigh && state.pdh != 0.0;
            double range = (state.intradayLow > 0) ? ((state.intradayHigh - state.intradayLow) / state.intradayLow * 100.0) : 0.0;
            boolean rangeCondition = range > MIN_RANGE_PERCENT;
            boolean openCondition = state.intradayOpen < state.intradayHigh && state.intradayOpen != 0.0;
            boolean volumeAccCondition = state.volumeAccumulatedAtHighs > MIN_ACCUMULATED_VOLUME_AT_HIGHS;
            // For volume spike, consider if it should be active *during* the bar that makes the new high or *on* the breakout bar
            boolean volSpikeCondition = state.recentVolumeSpikeOccurred;

            if (pdhCondition && rangeCondition && openCondition && volumeAccCondition && volSpikeCondition) {
                state.primaryConditionMet = true;
                state.initialTriggerPrice = bar.ohlc.getClose(); // Price when condition is met
                state.lowestPriceAfterTrigger = bar.ohlc.getClose();
                state.highestPriceAfterTrigger = bar.ohlc.getClose();
                logger.info("Primary condition MET for {}: PDH<IH ({}), Range>{}({}), Open<IH ({}), VolAcc>{}({}), VolSpike ({}). TriggerPrice: {}",
                        symbol, pdhCondition, MIN_RANGE_PERCENT, String.format("%.2f",range), openCondition, MIN_ACCUMULATED_VOLUME_AT_HIGHS, state.volumeAccumulatedAtHighs, volSpikeCondition, state.initialTriggerPrice);
            }
        }

        // If primary condition met, update post-trigger highs and lows
        if (state.primaryConditionMet) {
            state.lowestPriceAfterTrigger = Math.min(state.lowestPriceAfterTrigger, bar.ohlc.getLow());
            state.highestPriceAfterTrigger = Math.max(state.highestPriceAfterTrigger, bar.ohlc.getHigh());
        }
    }

    public PriceActionSignal getSignal(String symbol) {
        IntradayPriceActionState state = getState(symbol);

        if (!state.primaryConditionMet) {
            return PriceActionSignal.CONDITION_NOT_MET_YET;
        }

        if (state.initialTriggerPrice == 0) { // Should not happen if primaryConditionMet is true
            logger.warn("getSignal called for {} but initialTriggerPrice is 0 despite primaryConditionMet=true.", symbol);
            return PriceActionSignal.NEUTRAL_OR_NO_ACTION;
        }

        double positiveChangePercent = (state.highestPriceAfterTrigger - state.initialTriggerPrice) / state.initialTriggerPrice * 100.0;
        double negativeChangePercent = (state.initialTriggerPrice - state.lowestPriceAfterTrigger) / state.initialTriggerPrice * 100.0; // Based on initial price

        logger.debug("Signal Check for {}: InitialTriggerPrice={}, HighestAfter={}, LowestAfter={}, PosChg%={}, NegChg%={}",
                symbol, state.initialTriggerPrice, state.highestPriceAfterTrigger, state.lowestPriceAfterTrigger,
                String.format("%.2f", positiveChangePercent), String.format("%.2f", negativeChangePercent));

        if (negativeChangePercent > SIGNIFICANT_NEGATIVE_CHANGE_THRESHOLD) {
            logger.info("Significant NEGATIVE change detected for {}: {}%", symbol, String.format("%.2f", negativeChangePercent));
            return PriceActionSignal.SIGNIFICANT_NEGATIVE_CHANGE;
        } else if (positiveChangePercent > SIGNIFICANT_POSITIVE_CHANGE_THRESHOLD) {
            // Add your sample's profit < 20% condition if needed:
            // if (positiveChangePercent < MAX_POSITIVE_CHANGE_THRESHOLD)
            logger.info("Significant POSITIVE change detected for {}: {}%", symbol, String.format("%.2f", positiveChangePercent));
            return PriceActionSignal.SIGNIFICANT_POSITIVE_CHANGE;
        }

        return PriceActionSignal.NO_SIGNIFICANT_CHANGE_POST_TRIGGER;
    }
}

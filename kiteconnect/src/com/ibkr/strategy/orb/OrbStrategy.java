package com.ibkr.strategy.orb;

import com.ibkr.AppContext;
import com.ibkr.models.OrderType;
import com.ibkr.models.TradeAction;
import com.ibkr.models.TradingSignal;
import com.ibkr.strategy.common.OrbStrategyParameters;
import com.zerodhatech.models.OHLC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the revised Opening Range Breakout (ORB) strategy.
 * This strategy identifies the opening range, determines the directional bias,
 * and places a single stop order to enter a trade on a breakout.
 */
public class OrbStrategy {
    private static final Logger logger = LoggerFactory.getLogger(OrbStrategy.class);

    private final AppContext appContext;
    private final OrbStrategyParameters params;
    private final Map<String, OrbStrategyState> stateBySymbol = new ConcurrentHashMap<>();
    private final Map<String, List<OHLC>> oneMinuteBarBuffer = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> oneMinuteVolumeBuffer = new ConcurrentHashMap<>();
    private final ZoneId marketTimeZone;

    public OrbStrategy(AppContext appContext, OrbStrategyParameters params, String marketTimeZoneId) {
        this.appContext = appContext;
        this.params = params;
        this.marketTimeZone = ZoneId.of(marketTimeZoneId);
        logger.info("OrbStrategy initialized. Timeframe: {} mins, SL ATR %: {}, Risk %: {}",
                params.getOrbTimeframeMinutes(), params.getStopLossAtrPercentage(), params.getRiskPerTrade());
    }

    public OrbStrategyState getState(String symbol) {
        return stateBySymbol.computeIfAbsent(symbol, s -> new OrbStrategyState(s));
    }

    public void resetDailyState(String symbol) {
        getState(symbol).resetForNewDay();
        oneMinuteBarBuffer.remove(symbol);
        oneMinuteVolumeBuffer.remove(symbol);
        logger.info("ORB daily state and buffers reset for symbol: {}", symbol);
    }

    public ZoneId getMarketTimeZone() {
        return marketTimeZone;
    }


    /**
     * Processes a new 1-minute bar for a given symbol.
     * It aggregates bars to define the opening range and then generates a trading signal.
     *
     * @param symbol       The stock symbol.
     * @param bar          The 1-minute OHLC data.
     * @param volume       The volume for the 1-minute bar.
     * @param barTimestamp The starting timestamp of the bar.
     * @return A TradingSignal if entry conditions are met, otherwise null.
     */
    public TradingSignal processBar(String symbol, OHLC bar, long volume, long barTimestamp) {
        OrbStrategyState state = getState(symbol);

        if (state.rangeDefined || state.stopOrderPlaced) {
            return null; // Opening range already defined and processed for today.
        }

        // Buffer the 1-minute bars and volumes
        List<OHLC> barBuffer = oneMinuteBarBuffer.computeIfAbsent(symbol, k -> new ArrayList<>());
        List<Long> volumeBuffer = oneMinuteVolumeBuffer.computeIfAbsent(symbol, k -> new ArrayList<>());
        barBuffer.add(bar);
        volumeBuffer.add(volume);

        logger.debug("Buffered 1-min bar {}/{} for {}. Time: {}", barBuffer.size(), params.getOrbTimeframeMinutes(), symbol, LocalTime.ofInstant(java.time.Instant.ofEpochMilli(barTimestamp), marketTimeZone));

        // Check if we have enough bars to define the opening range
        if (barBuffer.size() == params.getOrbTimeframeMinutes()) {
            defineOpeningRange(symbol, barBuffer, volumeBuffer);
            return generateTradingSignal(symbol);
        }

        return null;
    }

    /**
     * Aggregates the buffered 1-minute bars into a single opening range candle.
     */
    private void defineOpeningRange(String symbol, List<OHLC> barBuffer, List<Long> volumeBuffer) {
        OrbStrategyState state = getState(symbol);
        if (state.rangeDefined) return;

        double open = barBuffer.get(0).getOpen();
        double high = barBuffer.stream().mapToDouble(OHLC::getHigh).max().orElse(0.0);
        double low = barBuffer.stream().mapToDouble(OHLC::getLow).min().orElse(Double.MAX_VALUE);
        double close = barBuffer.get(barBuffer.size() - 1).getClose();
        long totalVolume = volumeBuffer.stream().mapToLong(Long::longValue).sum();

        OHLC openingRangeCandle = new OHLC();
        openingRangeCandle.setOpen(open);
        openingRangeCandle.setHigh(high);
        openingRangeCandle.setLow(low);
        openingRangeCandle.setClose(close);

        state.openingRangeCandle = openingRangeCandle;
        state.openingRangeVolume = totalVolume;
        state.rangeDefined = true;

        if (state.avgOpeningRangeVolume14day > 0) {
            state.relativeVolume = totalVolume / state.avgOpeningRangeVolume14day;
        }


        // Determine candle direction
        if (close > open) {
            state.candleDirection = OrbStrategyState.CandleDirection.BULLISH;
        } else if (close < open) {
            state.candleDirection = OrbStrategyState.CandleDirection.BEARISH;
        } else {
            state.candleDirection = OrbStrategyState.CandleDirection.DOJI;
        }

        logger.info("Opening Range Defined for {}: O={}, H={}, L={}, C={}, V={}. Direction: {}",
                symbol, open, high, low, close, totalVolume, state.candleDirection);
    }

    /**
     * Generates the trading signal after the opening range is defined.
     */
    public TradingSignal generateTradingSignal(String symbol) {
        OrbStrategyState state = getState(symbol);
        if (!state.rangeDefined || state.stopOrderPlaced) {
            return null; // Should not happen if called correctly, but as a safeguard.
        }

        // Mark as processed to prevent duplicate orders
        state.stopOrderPlaced = true;

        if (state.candleDirection == OrbStrategyState.CandleDirection.DOJI) {
            logger.info("No trade for {}: Opening range candle was a Doji.", symbol);
            return null;
        }

        if (state.atr14day <= 0) {
            logger.error("Cannot generate signal for {}: 14-day ATR is not set or is zero.", symbol);
            return null;
        }

        // Determine order parameters
        TradeAction action;
        OrderType orderType;
        double triggerPrice;
        double stopLossPrice;

        if (state.candleDirection == OrbStrategyState.CandleDirection.BULLISH) {
            action = TradeAction.BUY;
            orderType = OrderType.BUY_STOP;
            triggerPrice = state.openingRangeCandle.getHigh();
            stopLossPrice = triggerPrice - (state.atr14day * params.getStopLossAtrPercentage());
        } else { // BEARISH
            action = TradeAction.SELL;
            orderType = OrderType.SELL_STOP;
            triggerPrice = state.openingRangeCandle.getLow();
            stopLossPrice = triggerPrice + (state.atr14day * params.getStopLossAtrPercentage());
        }

        int quantity = calculatePositionSize(symbol, triggerPrice, stopLossPrice);
        if (quantity == 0) {
            logger.warn("Position size for {} is zero. No trade will be placed.", symbol);
            return null;
        }

        logger.info("Generating Signal for {}: Action={}, OrderType={}, TriggerPrice={}, StopLoss={}, Quantity={}",
                symbol, action, orderType, triggerPrice, stopLossPrice, quantity);

        return new TradingSignal.Builder()
                .symbol(symbol)
                .action(action)
                .orderType(orderType) // Assuming OrderType can be set
                .price(triggerPrice) // For stop orders, this is the trigger price
                .stopLossPrice(stopLossPrice) // Assuming SL can be set
                .quantity(quantity)
                .strategyId("ORB_Revised")
                .relativeVolume(state.relativeVolume)
                .build();
    }

    /**
     * Calculates position size based on 1% capital risk and max leverage.
     */
    private int calculatePositionSize(String symbol, double entryPrice, double stopLossPrice) {
        double accountCapital = appContext.getAccountCapital(); // Assuming this method exists
        if (accountCapital <= 0) {
            logger.error("Account capital is not available. Cannot calculate position size.");
            return 0;
        }

        double stopLossDistance = Math.abs(entryPrice - stopLossPrice);
        if (stopLossDistance <= 0) {
            logger.error("Stop loss distance is zero or negative for {}. Cannot calculate position size.", symbol);
            return 0;
        }

        double riskAmountPerTrade = accountCapital * params.getRiskPerTrade();
        int quantity = (int) (riskAmountPerTrade / stopLossDistance);

        // Check against max leverage
        double positionValue = quantity * entryPrice;
        double maxPositionValue = accountCapital * params.getMaxLeverage();

        if (positionValue > maxPositionValue) {
            quantity = (int) (maxPositionValue / entryPrice);
            logger.warn("Position size for {} reduced to {} due to max leverage constraint.", symbol, quantity);
        }

        return quantity;
    }
}

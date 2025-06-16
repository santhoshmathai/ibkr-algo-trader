package com.ibkr.strategy;

import com.ibkr.AppContext; // Added
// Removed MarketSentimentAnalyzer import as it's not directly used here
import com.ibkr.core.TradingEngine;
import com.ibkr.IBOrderExecutor;
import com.ibkr.models.Order;
import com.ibkr.models.TradeAction;
import com.ibkr.models.TradingPosition;
import com.ibkr.models.TradingSignal;
import com.ibkr.risk.RiskManager;
import com.ibkr.signal.BreakoutSignalGenerator;
import com.zerodhatech.models.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List; // Added
import java.util.Map;
// Removed Set import as it's not directly used here
import java.util.concurrent.ConcurrentHashMap;

public class TickProcessor {
    private static final Logger logger = LoggerFactory.getLogger(TickProcessor.class); // Added
    private final BreakoutSignalGenerator signalGenerator;
    private final RiskManager riskManager;
    private final TradingEngine tradingEngine;
    private final IBOrderExecutor orderExecutor;
    private final AppContext appContext; // Added

    public TickProcessor(BreakoutSignalGenerator signalGenerator, RiskManager riskManager,
                         TradingEngine tradingEngine, IBOrderExecutor orderExecutor, AppContext appContext) { // Added appContext
        this.signalGenerator = signalGenerator;
        this.riskManager = riskManager;
        this.tradingEngine = tradingEngine;
        this.orderExecutor = orderExecutor;
        this.appContext = appContext; // Added
    }

    private final Map<Long, TradingPosition> positions = new ConcurrentHashMap<>();

    public void process(Tick tick) {
        logger.debug("Processing tick for {}: LTP = {}", tick.getSymbol(), tick.getLastTradedPrice());
        TradingPosition currentPosition = positions.getOrDefault(tick.getInstrumentToken(),
                new TradingPosition(tick.getInstrumentToken(), tick.getSymbol(), 0, 0, 0.0, true, null));
        logger.debug("Current position for {}: {}", tick.getSymbol(), currentPosition);

        // Stop-loss check
        if (currentPosition.isInPosition()) {
            double stopLossPercent = 0.01; // 1%
            try {
                double stopLossPrice = currentPosition.getStopLossPrice(stopLossPercent);
                logger.debug("Symbol: {}, Entry: {}, Current LTP: {}, Calculated SL: {} for {}%",
                    tick.getSymbol(), currentPosition.getEntryPrice(), tick.getLastTradedPrice(), stopLossPrice, stopLossPercent * 100);

                boolean stopLossHit = false;
                if (currentPosition.isLong() && tick.getLastTradedPrice() <= stopLossPrice) {
                    stopLossHit = true;
                }
                // else if (!currentPosition.isLong() && tick.getLastTradedPrice() >= stopLossPrice) {
                //     // Logic for short position stop-loss, if shorting is implemented
                //     // stopLossHit = true;
                // }

                if (stopLossHit) {
                    logger.warn("STOP-LOSS HIT for symbol {}: LTP {} breached SL price {}. Position: {}",
                        tick.getSymbol(), tick.getLastTradedPrice(), stopLossPrice, currentPosition);

                    // Create immediate exit signal
                    TradeAction exitAction = currentPosition.isLong() ? TradeAction.SELL : TradeAction.BUY; // BUY to cover short
                    TradingSignal exitSignal = new TradingSignal.Builder()
                        .instrumentToken(currentPosition.getInstrumentToken())
                        .symbol(currentPosition.getSymbol())
                        .action(exitAction)
                        .quantity(currentPosition.getQuantity())
                        .price(tick.getLastTradedPrice()) // Use last traded price for exit
                        .strategyId("STOP_LOSS")
                        .build();

                    logger.info("Executing STOP-LOSS trade for {}: {}", tick.getSymbol(), exitSignal);
                    executeTrade(exitSignal);
                    updatePosition(exitSignal, tick);

                    return; // Exit further processing for this tick
                }
            } catch (IllegalStateException e) {
                logger.error("IllegalStateException while calculating stop-loss for {}. This should not happen for an active position. Position: {}", tick.getSymbol(), currentPosition, e);
            } catch (IllegalArgumentException e) {
                logger.error("IllegalArgumentException while calculating stop-loss for {}. Stop-loss percentage {} is invalid.", tick.getSymbol(), stopLossPercent, e);
            }
        }

        boolean openingWindow = appContext.isMarketInOpeningWindow(); // Changed to use appContext
        boolean openingSignalActedUpon = false;

        if (openingWindow) {
            logger.debug("Market is in opening window for {}. Generating opening signals.", tick.getSymbol());
            List<TradingSignal> openingSignals = tradingEngine.generateOpeningSignals(tick);
            if (openingSignals != null && !openingSignals.isEmpty()) {
                logger.info("Received {} opening signals for {}.", openingSignals.size(), tick.getSymbol());
                for (TradingSignal openingSignal : openingSignals) {
                    if (openingSignal.getAction() != TradeAction.HOLD) {
                        logger.debug("Processing opening signal: {}", openingSignal);
                        if (riskManager.validateTrade(openingSignal, tick)) {
                            logger.info("Opening trade validated for {}. Signal: {}", openingSignal.getSymbol(), openingSignal.getAction());
                            executeTrade(openingSignal);
                            updatePosition(openingSignal, tick); // Assuming opening signals might establish new positions
                            openingSignalActedUpon = true;
                            // Decide if we process multiple opening signals or just the first valid one
                            // For now, let's assume we can act on multiple (e.g. basket)
                        } else {
                            logger.warn("Opening trade validation FAILED for {}. Signal: {}", openingSignal.getSymbol(), openingSignal.getAction());
                        }
                    }
                }
            } else {
                logger.debug("No opening signals generated for {}.", tick.getSymbol());
            }
        }

        if (openingSignalActedUpon && !allowMultipleSignalsPerTick()) { // allowMultipleSignalsPerTick() is a hypothetical method
             logger.info("Opening signal acted upon for {}. Skipping regular signal processing for this tick.", tick.getSymbol());
             return;
        }

        logger.debug("Proceeding to regular signal generation for {}.", tick.getSymbol());
        TradingSignal breakoutSignal = signalGenerator.generateSignal(tick, currentPosition);
        logger.debug("Breakout signal for {}: {}", tick.getSymbol(), breakoutSignal.getAction());
        TradingSignal engineSignal = tradingEngine.generateSignal(tick, currentPosition);
        logger.debug("TradingEngine signal for {}: {}", tick.getSymbol(), engineSignal.getAction());

        TradingSignal finalSignal = breakoutSignal; // Prioritize breakout by default

        if (breakoutSignal.getAction() == TradeAction.HOLD || breakoutSignal.getAction() == null) {
            finalSignal = engineSignal;
        } else if (engineSignal.getAction() != TradeAction.HOLD && engineSignal.getAction() != null) {
            // Simple prioritization: if both are active, breakout might be preferred.
            // Or, if engine signal is stronger or different type, could choose it.
            // For now, breakout takes precedence if it's not HOLD.
            logger.debug("Both Breakout and Engine signals are active for {}. Prioritizing Breakout: {}", tick.getSymbol(), breakoutSignal.getAction());
        }

        logger.info("Final signal for {}: {}", tick.getSymbol(), finalSignal.getAction());

        if (finalSignal.getAction() != TradeAction.HOLD && finalSignal.getAction() != null) {
            if (currentPosition.isInPosition() && finalSignal.getAction() == currentPosition.getTradeAction()) {
                logger.debug("Signal {} for {} is same as current position action. No new trade.", finalSignal.getAction(), tick.getSymbol());
            } else if (riskManager.validateTrade(finalSignal, tick)) {
                logger.info("Final trade validated for {}. Signal: {}", tick.getSymbol(), finalSignal.getAction());
                executeTrade(finalSignal);
                updatePosition(finalSignal, tick);
            } else {
                logger.warn("Final trade validation FAILED for {}. Signal: {}", tick.getSymbol(), finalSignal.getAction());
            }
        } else {
            logger.debug("Final signal is HOLD for {}. No action taken.", tick.getSymbol());
        }
    }

    // Removed placeholder isMarketInOpeningWindow()

    private boolean allowMultipleSignalsPerTick() {
        // Placeholder: configuration if multiple signals (e.g. opening + regular) can be acted upon for the same tick.
        // Typically false to avoid conflicting orders.
        return false;
    }

    private void executeTrade(TradingSignal signal) {
        if (orderExecutor == null) {
            logger.error("OrderExecutor is null. Cannot execute trade for signal: {}", signal);
            return;
        }
        try {
            com.ibkr.models.Order order = new com.ibkr.models.Order();
            order.setSymbol(signal.getSymbol());
            order.setAction(signal.getAction());
            order.setQuantity(signal.getQuantity());
            order.setPrice(signal.getPrice()); // LTP from signal, used as LMT price by IBOrderExecutor if LMT
            order.setDarkPoolAllowed(signal.isDarkPoolAllowed());
            // order.setOrderType(...); // If TradingSignal were to specify MKT/LMT

            logger.info("Placing order for signal: Symbol={}, Action={}, Qty={}, Price={}, DarkPoolOK={}",
                order.getSymbol(), order.getAction(), order.getQuantity(), order.getPrice(), order.isDarkPoolAllowed());
            orderExecutor.placeOrder(order);
        } catch (Exception e) {
            logger.error("Error executing trade for signal {}: {}", signal, e.getMessage(), e);
        }
    }

    private void updatePosition(TradingSignal signal, Tick tick) {
        if (signal.getAction() == TradeAction.BUY) {
            // TODO: Re-evaluate how volatility is set for TradingPosition.
            // VolatilityAnalyzer was removed from TickProcessor.
            // Setting to 0.0 as a placeholder.
            double volatility = 0.0; // Placeholder
            logger.warn("Using placeholder volatility {} for new position on {}", volatility, signal.getSymbol());
            TradingPosition newPosition = new TradingPosition(
                    signal.getInstrumentToken(),
                    signal.getSymbol(),
                    signal.getPrice(),
                    signal.getQuantity(),
                    volatility, // Placeholder volatility
                    true, // Assuming it's a long position
                    signal.getAction() // Pass the TradeAction from the signal
            );
            positions.put(signal.getInstrumentToken(), newPosition);
            logger.info("Position UPDATED for {}: New position: {}", signal.getSymbol(), newPosition);
        } else if (signal.getAction() == TradeAction.SELL) { // Assuming SELL here means closing a long position
            TradingPosition removedPosition = positions.remove(signal.getInstrumentToken());
            if (removedPosition != null) {
                logger.info("Position REMOVED for {}: Details: {}", signal.getSymbol(), removedPosition);
            } else {
                logger.warn("Attempted to remove position for {}, but no active position found.", signal.getSymbol());
            }
        }
    }

}
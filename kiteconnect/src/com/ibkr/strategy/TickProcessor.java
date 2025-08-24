package com.ibkr.strategy;

import com.ibkr.AppContext;
import com.ibkr.analysis.MarketSentimentAnalyzer; // New import
import com.ibkr.core.TradingEngine;
import com.ibkr.data.TickAggregator; // New import
import com.ibkr.service.OrderService;
import com.ibkr.models.OpeningMarketTrend; // New import
import com.ibkr.models.Order;
import com.ibkr.models.TradeAction;
import com.ibkr.models.TradingPosition;
import com.ibkr.models.TradingSignal;
import com.ibkr.risk.RiskManager;
import com.ibkr.signal.BreakoutSignalGenerator;
import com.zerodhatech.models.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set; // New import
import java.util.HashMap; // New import
import java.util.concurrent.ConcurrentHashMap;

public class TickProcessor {
    private static final Logger logger = LoggerFactory.getLogger(TickProcessor.class); // Added
    private final BreakoutSignalGenerator signalGenerator;
    private final RiskManager riskManager;
    private final TradingEngine tradingEngine;
    private final OrderService orderService;
    private final AppContext appContext; // Added

    public TickProcessor(BreakoutSignalGenerator signalGenerator, RiskManager riskManager,
                         TradingEngine tradingEngine, OrderService orderService, AppContext appContext) { // Added appContext
        this.signalGenerator = signalGenerator;
        this.riskManager = riskManager;
        this.tradingEngine = tradingEngine;
        this.orderService = orderService;
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
                else if (!currentPosition.isLong() && tick.getLastTradedPrice() >= stopLossPrice) {
                    logger.info("Short position for {} stop-loss check: Current LTP {} >= SL Price {}",
                        tick.getSymbol(), tick.getLastTradedPrice(), stopLossPrice);
                    stopLossHit = true;
                }

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

        boolean openingWindow = appContext.getMarketSentimentAnalyzer().isInAnalysisWindow();
        boolean openingSignalActedUpon = false;

        MarketSentimentAnalyzer sentimentAnalyzer = appContext.getMarketSentimentAnalyzer();

        if (openingWindow) {
            logger.debug("Tick for {} in opening window. Updating opening tick analysis.", tick.getSymbol());
            sentimentAnalyzer.updateOpeningTickAnalysis(tick);

            OpeningMarketTrend currentOpeningTrend = sentimentAnalyzer.getDeterminedOpeningTrend();
            logger.debug("Current Opening Market Trend for {}: {}", tick.getSymbol(), currentOpeningTrend);

            if (currentOpeningTrend == OpeningMarketTrend.TREND_UP || currentOpeningTrend == OpeningMarketTrend.TREND_DOWN) {
                logger.info("Opening market trend determined as {}. Checking for opening signals.", currentOpeningTrend);

                Map<String, Double> stockOpeningPrices = sentimentAnalyzer.getStockOpeningPrices();
                Set<String> monitoredSymbols = appContext.getTop100USStocks();
                Map<String, Tick> currentTickData = new HashMap<>();
                TickAggregator tickAggregator = appContext.getTickAggregator();

                for (String symbol : monitoredSymbols) {
                    Integer tickerId = appContext.getInstrumentRegistry().getTickerId(symbol);
                    if (tickerId != null) {
                        Tick currentSymbolTick = tickAggregator.getTick(tickerId);
                        if (currentSymbolTick != null) {
                            currentTickData.put(symbol, currentSymbolTick);
                        }
                    }
                }
                currentTickData.put(tick.getSymbol(), tick);

                if (currentTickData.size() < monitoredSymbols.size() * 0.5) {
                    logger.warn("Insufficient fresh tick data for monitored symbols (got {} of {}). Skipping opening signal generation for this cycle.", currentTickData.size(), monitoredSymbols.size());
                } else {
                    List<TradingSignal> openingSignals = tradingEngine.generateOpeningSignals(
                        currentOpeningTrend,
                        stockOpeningPrices,
                        monitoredSymbols,
                        currentTickData
                    );

                    if (openingSignals != null && !openingSignals.isEmpty()) {
                        logger.info("Received {} opening signals from TradingEngine based on trend {}.", openingSignals.size(), currentOpeningTrend);
                        for (TradingSignal openingSignal : openingSignals) {
                            if (!tick.getSymbol().equals(openingSignal.getSymbol())) {
                                logger.debug("Opening signal for {} ignored because current tick is for {}", openingSignal.getSymbol(), tick.getSymbol());
                                continue;
                            }

                            TradingPosition signalSymbolPosition = positions.getOrDefault(openingSignal.getInstrumentToken(),
                                new TradingPosition(openingSignal.getInstrumentToken(), openingSignal.getSymbol(), 0, 0, 0.0, true, null));

                            if (signalSymbolPosition.isInPosition()) {
                                 logger.info("Already in position for opening signal symbol {}. Skipping duplicate opening trade.", openingSignal.getSymbol());
                                 continue;
                            }

                            if (openingSignal.getAction() != TradeAction.HOLD) {
                                logger.debug("Processing opening signal: {}", openingSignal);
                                Tick signalTick = currentTickData.get(openingSignal.getSymbol());
                                if (signalTick == null) {
                                     logger.warn("No current tick data for signal symbol {} during risk validation. Skipping.", openingSignal.getSymbol());
                                     continue;
                                }

                                if (riskManager.validateTrade(openingSignal, signalTick)) {
                                    logger.info("Opening trade validated for {}. Signal: {}", openingSignal.getSymbol(), openingSignal.getAction());
                                    executeTrade(openingSignal);
                                    updatePosition(openingSignal, signalTick);
                                    openingSignalActedUpon = true;
                                } else {
                                    logger.warn("Opening trade validation FAILED for {}. Signal: {}", openingSignal.getSymbol(), openingSignal.getAction());
                                }
                            }
                             if(openingSignalActedUpon && !allowMultipleSignalsPerTick()){
                                 break;
                             }
                        }
                    } else {
                        logger.debug("No opening signals generated by TradingEngine for trend {}.", currentOpeningTrend);
                    }
                }
            } else if (currentOpeningTrend == OpeningMarketTrend.OBSERVATION_PERIOD) {
                logger.debug("Market in opening observation period. No trend-based opening signals generated yet for tick {}.", tick.getSymbol());
            } else {
                logger.debug("Opening market trend is {} (e.g., Neutral or Outside Window). No specific opening signals generated for tick {}.", currentOpeningTrend, tick.getSymbol());
            }
        }

        if (openingSignalActedUpon && !allowMultipleSignalsPerTick()) {
             logger.info("Opening signal acted upon for this tick cycle. Skipping regular signal processing for tick {}.", tick.getSymbol());
             return;
        }

        logger.debug("Proceeding to regular signal generation for {}.", tick.getSymbol());
        TradingSignal breakoutSignal = signalGenerator.generateSignal(tick, currentPosition);
        logger.debug("Breakout signal for {}: {}", tick.getSymbol(), breakoutSignal.getAction());
        TradingSignal engineSignal = tradingEngine.generateSignal(tick, currentPosition);
        logger.debug("TradingEngine signal for {}: {}", tick.getSymbol(), engineSignal.getAction());

        TradingSignal finalSignal;
        if (engineSignal.getAction() == TradeAction.HALT) {
            finalSignal = engineSignal; // HALT signal takes absolute precedence
            logger.debug("Engine signal is HALT for {}. Overriding any breakout signal.", tick.getSymbol());
        } else if (breakoutSignal.getAction() != TradeAction.HOLD && breakoutSignal.getAction() != null) {
            // If breakout is active (BUY/SELL) and engine is not HALT, prioritize breakout.
            finalSignal = breakoutSignal;
            logger.debug("Breakout signal is active for {}. Prioritizing Breakout (Engine was not HALT). Engine: {}", tick.getSymbol(), engineSignal.getAction());
        } else {
            // If breakout is HOLD or null, and engine is not HALT, use engine's signal.
            finalSignal = engineSignal;
            logger.debug("Breakout signal is passive for {}. Using Engine signal: {}", tick.getSymbol(), engineSignal.getAction());
        }

        logger.info("Final signal for {}: {}", tick.getSymbol(), finalSignal.getAction());

        if (finalSignal.getAction() != TradeAction.HOLD && finalSignal.getAction() != TradeAction.HALT && finalSignal.getAction() != null) {
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
            logger.debug("Final signal is {} for {}. No trade action taken.", finalSignal.getAction(), tick.getSymbol());
        }
    }

    // Removed placeholder isMarketInOpeningWindow()

    private boolean allowMultipleSignalsPerTick() {
        // Placeholder: configuration if multiple signals (e.g. opening + regular) can be acted upon for the same tick.
        // Typically false to avoid conflicting orders.
        return false;
    }

    private void executeTrade(TradingSignal signal) {
        if (orderService == null) {
            logger.error("OrderService is null. Cannot execute trade for signal: {}", signal);
            return;
        }
        try {
            Order order = new Order();
            order.setSymbol(signal.getSymbol());
            order.setAction(signal.getAction());
            order.setQuantity(signal.getQuantity());
            order.setPrice(signal.getPrice());
            order.setOrderType(com.ibkr.models.OrderType.MARKET);
            order.setDarkPoolAllowed(signal.isDarkPoolAllowed());

            logger.info("Placing order for signal: Symbol={}, Action={}, Qty={}, Price={}",
                signal.getSymbol(), signal.getAction(), signal.getQuantity(), signal.getPrice());
            orderService.placeOrder(order);
        } catch (Exception e) {
            logger.error("Error executing trade for signal {}: {}", signal, e.getMessage(), e);
        }
    }

    private void updatePosition(TradingSignal signal, Tick tick) {
        if (signal == null || tick == null) {
            logger.warn("updatePosition called with null signal or tick. Signal: {}, Tick: {}", signal, tick);
            return;
        }

        long instrumentToken = signal.getInstrumentToken();
        if (instrumentToken == 0 && signal.getSymbol() != null) {
            Integer token = appContext.getInstrumentRegistry().getTickerId(signal.getSymbol());
            if (token != null) {
                instrumentToken = token;
            } else {
                logger.error("Cannot update position for signal {}: Instrument token is 0 and symbol {} not found in registry.", signal, signal.getSymbol());
                return;
            }
        } else if (instrumentToken == 0) {
             logger.error("Cannot update position for signal {}: Instrument token is 0 and no symbol provided in signal to look up.", signal);
             return;
        }

        TradingPosition existingPosition = positions.get(instrumentToken);
        String symbol = signal.getSymbol() != null ? signal.getSymbol() : (existingPosition != null ? existingPosition.getSymbol() : tick.getSymbol());

        double entryVolatility = 0.0;
        if (tradingEngine.getVwapAnalyzer() != null && tick != null) {
            entryVolatility = tradingEngine.getVwapAnalyzer().getVolatility();
        } else {
            logger.warn("Could not calculate entry volatility for {}: VolatilityAnalyzer or Tick is null during updatePosition. Using 0.0.", symbol);
        }

        if (signal.getAction() == TradeAction.BUY) {
            if (existingPosition != null && existingPosition.isInPosition()) {
                if (existingPosition.isLong()) {
                    logger.warn("Received BUY signal for {} which is already long. No action taken (pyramiding not implemented). Position: {}", symbol, existingPosition);
                } else {
                    logger.info("BUY signal to cover existing short position for {}. Old Position: {}", symbol, existingPosition);
                    positions.remove(instrumentToken);
                    logger.info("Short position for {} closed by BUY signal.", symbol);
                }
            } else {
                TradingPosition newPosition = new TradingPosition(
                        instrumentToken,
                        symbol,
                        signal.getPrice(),
                        signal.getQuantity(),
                        entryVolatility,
                        true,
                        signal.getAction()
                );
                positions.put(instrumentToken, newPosition);
                logger.info("New LONG position opened for {}: {}", symbol, newPosition);
            }
        } else if (signal.getAction() == TradeAction.SELL) {
            if (existingPosition != null && existingPosition.isInPosition()) {
                if (!existingPosition.isLong()) {
                    logger.warn("Received SELL signal for {} which is already short. No action taken (pyramiding not implemented). Position: {}", symbol, existingPosition);
                } else {
                    logger.info("SELL signal to close existing long position for {}. Old Position: {}", symbol, existingPosition);
                    positions.remove(instrumentToken);
                    logger.info("Long position for {} closed by SELL signal.", symbol);
                }
            } else {
                TradingPosition newPosition = new TradingPosition(
                        instrumentToken,
                        symbol,
                        signal.getPrice(),
                        signal.getQuantity(),
                        entryVolatility,
                        false,
                        signal.getAction()
                );
                positions.put(instrumentToken, newPosition);
                logger.info("New SHORT position opened for {}: {}", symbol, newPosition);
            }
        } else if (signal.getAction() == TradeAction.HOLD || signal.getAction() == TradeAction.HALT) {
            logger.debug("HOLD or HALT signal received for {}. No position change.", symbol);
        } else {
            logger.warn("Unhandled TradeAction {} in updatePosition for symbol {}", signal.getAction(), symbol);
        }
    }

}
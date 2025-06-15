package com.ibkr.strategy;

import com.ibkr.analysis.MarketSentimentAnalyzer;
import com.ibkr.core.TradingEngine;
import com.ibkr.models.TradeAction;
import com.ibkr.models.TradingPosition;
import com.ibkr.models.TradingSignal;
import com.ibkr.risk.RiskManager;
import com.ibkr.risk.VolatilityAnalyzer;
import com.ibkr.signal.BreakoutSignalGenerator;
import com.zerodhatech.models.Tick;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TickProcessor {
    private final BreakoutSignalGenerator signalGenerator;
    private final RiskManager riskManager;
    private final VolatilityAnalyzer volatilityAnalyzer;
    private final MarketSentimentAnalyzer sentimentAnalyzer;
    private final TradingEngine tradingEngine;

    public TickProcessor(BreakoutSignalGenerator signalGenerator, RiskManager riskManager, VolatilityAnalyzer volatilityAnalyzer, MarketSentimentAnalyzer sentimentAnalyzer,  TradingEngine tradingEngine) {
        this.signalGenerator = signalGenerator;
        this.riskManager = riskManager;
        this.volatilityAnalyzer = volatilityAnalyzer;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.tradingEngine = tradingEngine;
    }

    public TickProcessor() {
        this.signalGenerator = new BreakoutSignalGenerator();
        this.riskManager = new RiskManager();
        this.volatilityAnalyzer = new VolatilityAnalyzer();
        this.sentimentAnalyzer = new MarketSentimentAnalyzer(loadTop200Stocks());
        this.tradingEngine = new TradingEngine();
    }

    private final Map<Long, TradingPosition> positions = new ConcurrentHashMap<>();

    public void process(Tick tick) {
        TradingPosition currentPosition = positions.getOrDefault(tick.getInstrumentToken(),
                new TradingPosition(tick.getInstrumentToken(), "", 0, 0, 0, true));

        TradingSignal signal = signalGenerator.generateSignal(tick, currentPosition);

        if (signal.getAction() != TradeAction.HOLD &&
                riskManager.validateTrade(signal, tick)) {

            executeTrade(signal);
            updatePosition(signal, tick);
        }
    }

    private void executeTrade(TradingSignal signal) {
        // Your existing order execution logic
    }

    private void updatePosition(TradingSignal signal, Tick tick) {
        if (signal.getAction() == TradeAction.BUY) {
            double volatility = volatilityAnalyzer.getCurrentVolatility(tick);
            positions.put(signal.getInstrumentToken(),
                    new TradingPosition(
                            signal.getInstrumentToken(),
                            signal.getSymbol(),
                            signal.getPrice(),
                            signal.getQuantity(),
                            volatility,
                            true
                    ));
        } else if (signal.getAction() == TradeAction.SELL) {
            positions.remove(signal.getInstrumentToken());
        }
    }

    private Set<String> loadTop200Stocks() {
        // Load from config/database
        return Set.of("AAPL", "MSFT", "GOOGL"); // Top 200 symbols
    }
}
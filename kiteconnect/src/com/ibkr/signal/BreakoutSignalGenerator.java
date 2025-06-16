package com.ibkr.signal;


import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.indicators.VWAPAnalyzer;
import com.ibkr.indicators.VolumeAnalyzer;
import com.ibkr.models.TradeAction;
import com.ibkr.models.TradingSignal;
import com.zerodhatech.models.Tick;
import com.ibkr.models.TradingPosition;
import com.ibkr.risk.VolatilityAnalyzer;
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added


public class BreakoutSignalGenerator {
    private static final Logger logger = LoggerFactory.getLogger(BreakoutSignalGenerator.class); // Added
    private final VolatilityAnalyzer volatilityAnalyzer;
    private double breakoutThreshold = 0.02; // 2%
    private double breakdownThreshold = -0.015; // -1.5% (more sensitive for shorts)
    private final VWAPAnalyzer vwapAnalyzer;
    private final VolumeAnalyzer volumeAnalyzer;
    private final SectorStrengthAnalyzer sectorAnalyzer;

    public BreakoutSignalGenerator(VolatilityAnalyzer volatilityAnalyzer, VWAPAnalyzer vwapAnalyzer,
                                   VolumeAnalyzer volumeAnalyzer, SectorStrengthAnalyzer sectorAnalyzer) {
        this.volatilityAnalyzer = volatilityAnalyzer;
        this.vwapAnalyzer = vwapAnalyzer;
        this.volumeAnalyzer = volumeAnalyzer;
        this.sectorAnalyzer = sectorAnalyzer;
    }

    public TradingSignal generateSignal(Tick tick, TradingPosition currentPosition) {
        logger.debug("Generating signal for tick: {} and position: {}", tick, currentPosition);
        TradingSignal signalToReturn = TradingSignal.hold();

        // LONG Signal: Price breaks above upper threshold
        if (!currentPosition.isInPosition() && isUpwardBreakout(tick)) {
            signalToReturn = createSignal(tick, TradeAction.BUY);
            logger.info("Upward breakout detected for {}. Generated BUY signal: {}", tick.getSymbol(), signalToReturn);
        }
        // SHORT Signal: Price breaks below lower threshold
        else if (!currentPosition.isInPosition() && isDownwardBreakdown(tick)) {
            signalToReturn = createSignal(tick, TradeAction.SELL);
            logger.info("Downward breakdown detected for {}. Generated SELL signal: {}", tick.getSymbol(), signalToReturn);
        }
        // Exit Conditions
        else if (currentPosition.isInPosition()) {
            signalToReturn = handlePositionExit(tick, currentPosition);
            if (signalToReturn.getAction() != TradeAction.HOLD) {
                logger.info("Exit condition met for {}. Generated {} signal: {}", tick.getSymbol(), signalToReturn.getAction(), signalToReturn);
            }
        }

        if (signalToReturn.getAction() == TradeAction.HOLD) {
            logger.debug("No signal generated for {}. Conditions not met. Returning HOLD.", tick.getSymbol());
        }
        return signalToReturn;
    }

    private boolean isUpwardBreakout(Tick tick) {
        double movement = (tick.getLastTradedPrice() - tick.getOpenPrice()) / tick.getOpenPrice();
        double currentVolatility = volatilityAnalyzer.getCurrentVolatility(tick);
        double dynamicThreshold = breakoutThreshold * currentVolatility;
        boolean isBreakout = movement > dynamicThreshold;
        logger.trace("isUpwardBreakout for {}: Movement: {:.4f}, Volatility: {:.4f}, DynamicThreshold: {:.4f}. Result: {}",
                tick.getSymbol(), movement, currentVolatility, dynamicThreshold, isBreakout);
        return isBreakout;
    }

    private boolean isDownwardBreakdown(Tick tick) {
        double movement = (tick.getLastTradedPrice() - tick.getOpenPrice()) / tick.getOpenPrice();
        double currentVolatility = volatilityAnalyzer.getCurrentVolatility(tick);
        double dynamicThreshold = breakdownThreshold * currentVolatility; // breakdownThreshold is negative
        boolean isBreakdown = movement < dynamicThreshold;
        logger.trace("isDownwardBreakdown for {}: Movement: {:.4f}, Volatility: {:.4f}, DynamicThreshold: {:.4f}. Result: {}",
                tick.getSymbol(), movement, currentVolatility, dynamicThreshold, isBreakdown);
        return isBreakdown;
    }

    private TradingSignal handlePositionExit(Tick tick, TradingPosition position) {
        boolean isLongPosition = position.getEntryPrice() < tick.getLastTradedPrice(); // This logic for determining long/short might be flawed if entry price is LTP
        logger.debug("Handling position exit for {}. Position: {}, IsLong (heuristic): {}", tick.getSymbol(), position, isLongPosition);

        double volatilityFactor = position.getVolatilityAtEntry();
        if (volatilityFactor == 0) { // Handle case where volatility at entry was zero
            logger.warn("Volatility at entry for position {} is 0. Using a default small factor for exit calculation.", position.getSymbol());
            volatilityFactor = 0.01; // Default small factor to prevent division by zero or extreme targets
        }

        // Profit targets
        double longProfitPrice = position.getEntryPrice() * (1 + (1.5 * volatilityFactor));
        double shortProfitPrice = position.getEntryPrice() * (1 - (1.2 * volatilityFactor));

        // Stop losses
        double longStopPrice = position.getEntryPrice() * (1 - (0.8 * volatilityFactor));
        double shortStopPrice = position.getEntryPrice() * (1 + (0.6 * volatilityFactor));

        logger.debug("Exit params for {}: IsLong: {}, LTP: {}, Entry: {}, VolAtEntry: {:.4f}",
                tick.getSymbol(), position.isLong(), tick.getLastTradedPrice(), position.getEntryPrice(), position.getVolatilityAtEntry());
        logger.debug("Long Target/Stop: {:.2f}/{:.2f}. Short Target/Stop: {:.2f}/{:.2f}",
                longProfitPrice, longStopPrice, shortProfitPrice, shortStopPrice);

        boolean exitConditionMet = false;
        if (position.isLong()) { // Assuming TradingPosition has an isLong() method or similar flag
            exitConditionMet = (tick.getLastTradedPrice() >= longProfitPrice || tick.getLastTradedPrice() <= longStopPrice);
        } else { // Short position
            exitConditionMet = (tick.getLastTradedPrice() <= shortProfitPrice || tick.getLastTradedPrice() >= shortStopPrice);
        }

        if (exitConditionMet) {
            TradeAction exitAction = position.isLong() ? TradeAction.SELL : TradeAction.BUY;
            logger.info("Exit condition met for {}. Action: {}", tick.getSymbol(), exitAction);
            return createSignal(tick, exitAction);
        }
        return TradingSignal.hold();
    }

    private TradingSignal createSignal(Tick tick, TradeAction action) {
        TradingSignal signal = new TradingSignal.Builder()
                .instrumentToken(tick.getInstrumentToken())
                .symbol(tick.getSymbol()) // **** ADDED THIS LINE ****
                .action(action)
                .price(tick.getLastTradedPrice())
                .quantity(calculatePositionSize(tick, action))
                .build();
        logger.debug("Created signal: {}", signal);
        return signal;
    }

    private int calculatePositionSize(Tick tick, TradeAction action) {
        double riskAmount = 10000 * 0.01; // 1% of $10k capital
        double price = tick.getLastTradedPrice();
        if (price == 0) { // Avoid division by zero if price is not set (e.g. before market open)
            logger.warn("Price is 0 for {} in calculatePositionSize. Returning 0 quantity.", tick.getSymbol());
            return 0;
        }
        double riskPerShare = price * 0.01; // 1% of current price as risk per share (example)
        if (action == TradeAction.SELL) { // Assuming SELL here is for shorting, not exiting a long
            riskPerShare *= 0.8; // Smaller size for shorts
        }
        if (riskPerShare == 0) {
             logger.warn("Risk per share is 0 for {} in calculatePositionSize. Returning 0 quantity.", tick.getSymbol());
            return 0;
        }
        int quantity = (int) (riskAmount / riskPerShare);
        logger.trace("Calculated position size for {}: Action: {}, RiskAmount: {}, Price: {}, RiskPerShare: {}, Quantity: {}",
                tick.getSymbol(), action, riskAmount, price, riskPerShare, quantity);
        return quantity;
    }

    public TradingSignal generateShortSignal(Tick tick, String sectorId) {
        logger.debug("Generating short signal for {} in sector {}", tick.getSymbol(), sectorId);
        // Multi-condition short entry
        boolean vwapConfirmed = vwapAnalyzer.isBelowVWAP(tick);
        boolean volumeSpike = volumeAnalyzer.isBreakdownWithSpike(tick);
        boolean sectorWeakness = sectorAnalyzer.isUnderperforming(tick, sectorId);

        logger.debug("Short signal conditions for {}: VWAP confirmed: {}, Volume spike: {}, Sector weakness: {}",
                tick.getSymbol(), vwapConfirmed, volumeSpike, sectorWeakness);

        if (vwapConfirmed && volumeSpike && sectorWeakness) {
            TradingSignal signal = new TradingSignal.Builder()
                    .instrumentToken(tick.getInstrumentToken())
                    .symbol(tick.getSymbol()) // **** ADDED THIS LINE ****
                    .action(TradeAction.SELL)
                    .price(tick.getLastTradedPrice())
                    .quantity(calculateShortSize(tick))
                    //.metadata("SHORT_VWAP_VOLUME_SECTOR")
                    .build();
            logger.info("Generated SHORT signal for {}: {}", tick.getSymbol(), signal);
            return signal;
        }
        logger.debug("Short signal conditions not met for {}. Returning HOLD.", tick.getSymbol());
        return TradingSignal.hold();
    }

    private int calculateShortSize(Tick tick) {
        double vwap = vwapAnalyzer.getVWAP();
        double price = tick.getLastTradedPrice();
        if (price == 0 || vwap == 0) { // Avoid division by zero or meaningless calculation
            logger.warn("Price or VWAP is 0 for {} in calculateShortSize. Returning 0 quantity. Price: {}, VWAP: {}", tick.getSymbol(), price, vwap);
            return 0;
        }
        double riskDistance = vwap - price; // For shorts, risk is if price goes above VWAP
        double maxRiskPerShare = price * 0.02; // Max 2% of current price as risk

        double riskPerShare = Math.min(Math.abs(riskDistance), maxRiskPerShare); // Use absolute distance, ensure positive
        if (riskDistance <=0) { // If price is already above VWAP, this short setup might be invalid or very risky
            logger.warn("Price {} is already above or at VWAP {} for {}. Short signal risk calculation might be problematic.", price, vwap, tick.getSymbol());
            // Default to maxRiskPerShare if riskDistance is not favorable for short
            riskPerShare = maxRiskPerShare;
        }

        if (riskPerShare == 0) {
            logger.warn("Calculated risk per share is 0 for {} in calculateShortSize. Using max risk per share.", tick.getSymbol());
            riskPerShare = maxRiskPerShare; // Fallback if min resulted in zero
            if(riskPerShare == 0) {
                 logger.error("Max risk per share is also 0 for {}. Cannot calculate short size.", tick.getSymbol());
                 return 0;
            }
        }

        int quantity = (int) ( (10000 * 0.01) / riskPerShare); // 1% capital risk
        logger.trace("Calculated short size for {}: Price: {}, VWAP: {}, RiskDistance: {}, MaxRiskPerShare: {}, ActualRiskPerShare: {}, Quantity: {}",
                tick.getSymbol(), price, vwap, riskDistance, maxRiskPerShare, riskPerShare, quantity);
        return quantity;
    }
}
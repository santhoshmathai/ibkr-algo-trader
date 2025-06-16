package com.ibkr.data;

import com.zerodhatech.models.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zerodhatech.models.OHLC;
import com.zerodhatech.models.Depth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TickAggregator {
    private static final Logger logger = LoggerFactory.getLogger(TickAggregator.class);
    private final Map<Integer, Tick> instrumentTicks = new ConcurrentHashMap<>();
    private final InstrumentRegistry registry;

    // Fields for 1-minute aggregation
    private final Map<Integer, OHLC> currentMinuteOhlc = new ConcurrentHashMap<>();
    private final Map<Integer, Long> currentMinuteTimestamp = new ConcurrentHashMap<>();
    private final Map<Integer, Long> currentMinuteVolume = new ConcurrentHashMap<>();
    private final Map<Integer, Double> currentMinuteWapVolumeSum = new ConcurrentHashMap<>();

    // Fields for Market Depth
    // These store the full depth received from IBKR (up to MAX_DEPTH_LEVELS)
    private final Map<Integer, List<Quote>> currentBidDepthLevels = new ConcurrentHashMap<>();
    private final Map<Integer, List<Quote>> currentAskDepthLevels = new ConcurrentHashMap<>();
    private static final int MAX_DEPTH_LEVELS = 20;

    private static class Quote {
        private double price;
        private int quantity;

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }


    public TickAggregator(InstrumentRegistry registry) {
        this.registry = registry;
    }

    private void setTickMode(Tick tick, Tick.Mode newMode) {
        String currentMode = tick.getMode();
        // LTP -> QUOTE -> FULL. Do not downgrade.
        if (Tick.Mode.FULL.name().equals(currentMode)) {
            return; // Already at highest level
        }
        if (Tick.Mode.QUOTE.name().equals(currentMode) && Tick.Mode.LTP == newMode) {
            return; // Do not downgrade from QUOTE to LTP
        }
        if (currentMode == null && newMode == null) { // no data yet
            return;
        }
        tick.setMode(newMode.name());
    }


    public void processTickPrice(int tickerId, int field, double price) {
        logger.debug("Processing tick price for tickerId: {}, field: {}, price: {}", tickerId, field, price);
        Tick tick = getOrCreateTick(tickerId);

        switch (field) {
            case 1: // BID price (Best Bid Price)
                tick.setBestBidPrice(price);
                setTickMode(tick, Tick.Mode.QUOTE);
                break;
            case 2: // ASK price (Best Ask Price)
                tick.setBestAskPrice(price);
                setTickMode(tick, Tick.Mode.QUOTE);
                break;
            case 4: // LAST price
                tick.setLastTradedPrice(price);
                tick.setLastTradedTime(new Date(System.currentTimeMillis())); // Approx time
                if (tick.getMode() == null) setTickMode(tick, Tick.Mode.LTP);
                break;
            case 6: // HIGH
                tick.setHighPrice(price);
                 if (tick.getMode() == null || Tick.Mode.LTP.name().equals(tick.getMode())) setTickMode(tick, Tick.Mode.QUOTE);
                break;
            case 7: // LOW
                tick.setLowPrice(price);
                if (tick.getMode() == null || Tick.Mode.LTP.name().equals(tick.getMode())) setTickMode(tick, Tick.Mode.QUOTE);
                break;
            case 9: // CLOSE price (previous day's close)
                tick.setClosePrice(price);
                if (tick.getMode() == null || Tick.Mode.LTP.name().equals(tick.getMode())) setTickMode(tick, Tick.Mode.QUOTE);
                break;
            case 14: // OPEN price
                tick.setOpenPrice(price);
                if (tick.getMode() == null || Tick.Mode.LTP.name().equals(tick.getMode())) setTickMode(tick, Tick.Mode.QUOTE);
                break;
            case 21: // VWAP (Hypothetical IBKR field type for daily VWAP)
                logger.debug("Received Daily VWAP for {}: {}", tick.getSymbol(), price);
                tick.setAveragePrice(price);
                // Daily VWAP itself doesn't elevate mode beyond what OHLC/LTP/Bid/Ask imply.
                break;
        }
        tick.setTickTimestamp(new Date(System.currentTimeMillis()));
    }

    public void processTickSize(int tickerId, int field, int size) {
        logger.debug("Processing tick size for tickerId: {}, field: {}, size: {}", tickerId, field, size);
        Tick tick = getOrCreateTick(tickerId);

        switch (field) {
            case 0: // BID_SIZE (Best Bid Size)
                tick.setBestBidQuantity(size);
                setTickMode(tick, Tick.Mode.QUOTE);
                break;
            case 3: // ASK_SIZE (Best Ask Size)
                tick.setBestAskQuantity(size);
                setTickMode(tick, Tick.Mode.QUOTE);
                break;
            case 5: // LAST_SIZE
                tick.setLastTradedQuantity(size);
                break;
            case 8: // VOLUME
                tick.setVolumeTradedToday(size);
                break;
        }
        tick.setTickTimestamp(new Date(System.currentTimeMillis()));
    }

    public void processTickByTickAllLast(int reqId, int tickType, long time, double price, int size) {
        logger.debug("Processing tick-by-tick all last for reqId: {}, tickType: {}, time: {}, price: {}, size: {}",
                reqId, tickType, time, price, size);
        Tick tick = getOrCreateTick(reqId);
        tick.setLastTradedPrice(price);
        tick.setLastTradedQuantity(size);
        Date eventTime = new Date(time * 1000);
        tick.setLastTradedTime(eventTime);
        tick.setTickTimestamp(eventTime);

        if (tick.getMode() == null) {
            setTickMode(tick, Tick.Mode.LTP);
        }
    }

    public void processTickByTickBidAsk(int reqId, double bidPrice, double askPrice, int bidSize, int askSize) {
        logger.debug("Processing tick-by-tick bid/ask for reqId: {}, bidPrice: {}, askPrice: {}, bidSize: {}, askSize: {}",
                reqId, bidPrice, askPrice, bidSize, askSize);
        Tick tick = getOrCreateTick(reqId);
        tick.setBestBidPrice(bidPrice);
        tick.setBestBidQuantity(bidSize);
        tick.setBestAskPrice(askPrice);
        tick.setBestAskQuantity(askSize);
        tick.setTickTimestamp(new Date(System.currentTimeMillis()));

        setTickMode(tick, Tick.Mode.QUOTE);
    }

    private Tick getOrCreateTick(int instrumentToken) {
        return instrumentTicks.computeIfAbsent(instrumentToken, k -> {
            logger.info("Creating new Tick object for instrumentToken: {}", instrumentToken);
            Tick tick = new Tick();
            tick.setInstrumentToken(instrumentToken);
            tick.setTradable(true); // Assuming true by default
            String symbol = registry.getSymbol(instrumentToken);
            logger.debug("Setting symbol for instrumentToken {}: {}", instrumentToken, symbol);
            tick.setSymbol(symbol);
            tick.setMode(null); // Initialize mode to null; will be set by first data event
            return tick;
        });
    }

    public Tick getTick(int instrumentToken) {
        logger.debug("Retrieving tick for instrumentToken: {}", instrumentToken);
        return instrumentTicks.get(instrumentToken);
    }

    public void processRealTimeBar(int tickerId, long timeSeconds, double open, double high, double low, double close, long volume, double wap, int count) {
        logger.debug("processRealTimeBar: tickerId={}, timeSeconds={}, O={}, H={}, L={}, C={}, Vol={}, WAP={}, Count={}",
                tickerId, timeSeconds, open, high, low, close, volume, wap, count);

        Tick tick = getOrCreateTick(tickerId);
        long timeMillis = timeSeconds * 1000;
        long currentBarMinuteStart = (timeMillis / 60000) * 60000;

        Long previousBarMinuteStart = currentMinuteTimestamp.get(tickerId);

        if (previousBarMinuteStart == null || currentBarMinuteStart > previousBarMinuteStart) {
            if (previousBarMinuteStart != null) {
                OHLC completedOhlc = currentMinuteOhlc.get(tickerId);
                Long completedVolume = currentMinuteVolume.get(tickerId);
                Double completedWapVolumeSum = currentMinuteWapVolumeSum.get(tickerId);

                if (completedOhlc != null && completedVolume != null && completedWapVolumeSum != null) {
                    tick.setOhlc(new OHLC(completedOhlc)); // Store a copy
                    if (completedVolume > 0) {
                        double minuteVWAP = completedWapVolumeSum / completedVolume;
                        if(tick.getAveragePrice() == 0.0) { // Prioritize daily VWAP if already set
                           tick.setAveragePrice(minuteVWAP);
                        }
                        logger.info("Completed 1-min bar for {}: OHLC={}, VWAP={:.2f}, Volume={}", tick.getSymbol(), completedOhlc, minuteVWAP, completedVolume);
                    } else {
                         if(tick.getAveragePrice() == 0.0) tick.setAveragePrice(completedOhlc.getClose());
                         logger.info("Completed 1-min bar for {}: OHLC={}, Volume=0. VWAP not calculated.", tick.getSymbol(), completedOhlc);
                    }
                    tick.setLastTradedTime(new Date(previousBarMinuteStart + 59999)); // End of the completed minute
                }
            }

            OHLC newOhlc = new OHLC();
            newOhlc.setOpen(open); newOhlc.setHigh(high); newOhlc.setLow(low); newOhlc.setClose(close);
            currentMinuteOhlc.put(tickerId, newOhlc);
            currentMinuteTimestamp.put(tickerId, currentBarMinuteStart);
            currentMinuteVolume.put(tickerId, volume);
            currentMinuteWapVolumeSum.put(tickerId, wap * volume);
            logger.debug("New 1-minute aggregation started for {} at {}", tick.getSymbol(), new Date(currentBarMinuteStart));
        } else {
            OHLC ohlcToUpdate = currentMinuteOhlc.get(tickerId);
            if (ohlcToUpdate != null) {
                ohlcToUpdate.setHigh(Math.max(ohlcToUpdate.getHigh(), high));
                ohlcToUpdate.setLow(Math.min(ohlcToUpdate.getLow(), low));
                ohlcToUpdate.setClose(close);
                currentMinuteVolume.computeIfPresent(tickerId, (k, v) -> v + volume);
                currentMinuteWapVolumeSum.computeIfPresent(tickerId, (k, v) -> v + (wap * volume));
                logger.debug("Updated current 1-min bar for {}: OHLC C={}, H={}, L={}",
                             tick.getSymbol(), ohlcToUpdate.getClose(), ohlcToUpdate.getHigh(), ohlcToUpdate.getLow());
            } else {
                 logger.warn("ohlcToUpdate is null for tickerId {} during non-new minute aggregation. Re-initializing.", tickerId);
                 OHLC newOhlc = new OHLC();
                 newOhlc.setOpen(open); newOhlc.setHigh(high); newOhlc.setLow(low); newOhlc.setClose(close);
                 currentMinuteOhlc.put(tickerId, newOhlc);
                 currentMinuteTimestamp.put(tickerId, currentBarMinuteStart);
                 currentMinuteVolume.put(tickerId, volume);
                 currentMinuteWapVolumeSum.put(tickerId, wap * volume);
            }
        }

        tick.setLastTradedPrice(close);
        tick.setLastTradedQuantity(volume);
        tick.setLastTradedTime(new Date(timeMillis));

        OHLC formingOhlc = currentMinuteOhlc.get(tickerId);
        if (formingOhlc != null) {
            tick.setOhlc(new OHLC(formingOhlc)); // Store a copy for the current tick
            if (tick.getMode() == null || Tick.Mode.LTP.name().equals(tick.getMode())) {
                setTickMode(tick, Tick.Mode.QUOTE);
            }
        }
        Long formingVolume = currentMinuteVolume.get(tickerId);
        Double formingWapVolumeSum = currentMinuteWapVolumeSum.get(tickerId);
        if (formingVolume != null && formingWapVolumeSum != null && formingVolume > 0) {
            if(tick.getAveragePrice() == 0.0) { // Prioritize daily VWAP
                tick.setAveragePrice(formingWapVolumeSum / formingVolume);
            }
        } else if (formingOhlc != null && tick.getAveragePrice() == 0.0) {
            tick.setAveragePrice(formingOhlc.getClose());
        }
        tick.setTickTimestamp(new Date(System.currentTimeMillis()));
    }

    public void processMarketDepth(int tickerId, int position, int operation, int side, double price, int size) {
        logger.debug("processMarketDepth: tickerId={}, pos={}, op={}, side={}, price={}, size={}",
                tickerId, position, operation, side, price, size);

        Tick tick = getOrCreateTick(tickerId);
        // Use currentBidDepthLevels and currentAskDepthLevels
        List<Quote> depthLevelsList = (side == 1) ?
                currentBidDepthLevels.computeIfAbsent(tickerId, k -> new ArrayList<>()) :
                currentAskDepthLevels.computeIfAbsent(tickerId, k -> new ArrayList<>());

        switch (operation) {
            case 0: // Insert
                Quote newQuote = new Quote();
                newQuote.setPrice(price); newQuote.setQuantity(size);
                if (position < depthLevelsList.size()) {
                    depthLevelsList.add(position, newQuote);
                } else {
                    depthLevelsList.add(newQuote);
                }
                while (depthLevelsList.size() > MAX_DEPTH_LEVELS) {
                    depthLevelsList.remove(depthLevelsList.size() - 1);
                }
                break;
            case 1: // Update
                if (position < depthLevelsList.size()) {
                    Quote quoteToUpdate = depthLevelsList.get(position);
                    quoteToUpdate.setPrice(price); quoteToUpdate.setQuantity(size);
                } else {
                     logger.warn("Market depth update for {} at position {} out of bounds (size {}). Treating as insert.", tick.getSymbol(), position, depthLevelsList.size());
                     Quote updateAsNewQuote = new Quote();
                     updateAsNewQuote.setPrice(price); updateAsNewQuote.setQuantity(size);
                     depthLevelsList.add(updateAsNewQuote);
                     while (depthLevelsList.size() > MAX_DEPTH_LEVELS) {
                        depthLevelsList.remove(depthLevelsList.size() - 1);
                    }
                }
                break;
            case 2: // Delete
                if (position < depthLevelsList.size()) {
                    depthLevelsList.remove(position);
                } else {
                    logger.warn("Market depth delete for {} at position {} out of bounds (size {}). Ignored.", tick.getSymbol(), position, depthLevelsList.size());
                }
                break;
            default:
                logger.warn("Unknown market depth operation: {}", operation);
                return;
        }

        if (side == 1) {
            depthLevelsList.sort(Comparator.comparingDouble(Quote::getPrice).reversed());
        } else {
            depthLevelsList.sort(Comparator.comparingDouble(Quote::getPrice));
        }

        Map<String, ArrayList<Depth>> newMarketDepthMap = new HashMap<>();
        ArrayList<Depth> bids = new ArrayList<>();
        List<Quote> currentBids = currentBidDepthLevels.getOrDefault(tickerId, new ArrayList<>());
        for (int i = 0; i < Math.min(5, currentBids.size()); i++) {
            Quote q = currentBids.get(i);
            Depth d = new Depth();
            d.setPrice(q.getPrice()); d.setQuantity(q.getQuantity()); d.setOrders(0);
            bids.add(d);
        }
        newMarketDepthMap.put("buy", bids);

        ArrayList<Depth> asks = new ArrayList<>();
        List<Quote> currentAsks = currentAskDepthLevels.getOrDefault(tickerId, new ArrayList<>());
        for (int i = 0; i < Math.min(5, currentAsks.size()); i++) {
            Quote q = currentAsks.get(i);
            Depth d = new Depth();
            d.setPrice(q.getPrice()); d.setQuantity(q.getQuantity()); d.setOrders(0);
            asks.add(d);
        }
        newMarketDepthMap.put("sell", asks);

        tick.setMarketDepth(newMarketDepthMap);

        if (!bids.isEmpty() || !asks.isEmpty()) {
            setTickMode(tick, Tick.Mode.FULL);
            logger.trace("Updated tick {} with market depth. Bids: {}, Asks: {}. Mode set to FULL.", tick.getSymbol(), bids.size(), asks.size());
        } else {
            String currentMode = tick.getMode();
            if (Tick.Mode.FULL.name().equals(currentMode)) {
                 if (tick.getOhlc() != null && tick.getOhlc().getOpen() != 0.0) {
                     setTickMode(tick, Tick.Mode.QUOTE);
                 } else if (tick.getLastTradedPrice() != 0.0) {
                     setTickMode(tick, Tick.Mode.LTP);
                 } else {
                     tick.setMode(null);
                 }
                 logger.trace("Market depth for {} is now empty. Mode reverted based on other data to: {}", tick.getSymbol(), tick.getMode());
            }
        }
        tick.setTickTimestamp(new Date(System.currentTimeMillis()));
    }
}
package com.ibkr.data;

import com.zerodhatech.models.Tick;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TickAggregator {
    private final Map<Integer, Tick> instrumentTicks = new ConcurrentHashMap<>();
    private final InstrumentRegistry registry;

    public TickAggregator(InstrumentRegistry registry) {
        this.registry = registry;
    }

    public void processTickPrice(int tickerId, int field, double price) {
        Tick tick = getOrCreateTick(tickerId);

        switch (field) {
            case 1: // BID
                tick.setMode("bid");
                break;
            case 2: // ASK
                tick.setMode("ask");
                break;
            case 4: // LAST
                tick.setLastTradedPrice(price);
                break;
            case 6: // HIGH
                tick.setHighPrice(price);
                break;
            case 7: // LOW
                tick.setLowPrice(price);
                break;
            case 9: // CLOSE
                tick.setClosePrice(price);
                break;
            case 14: // OPEN
                tick.setOpenPrice(price);
                break;
        }
    }

    public void processTickSize(int tickerId, int field, int size) {
        Tick tick = getOrCreateTick(tickerId);

        switch (field) {
            case 0: // BID_SIZE
                tick.setTotalBuyQuantity(size);
                break;
            case 3: // ASK_SIZE
                tick.setTotalSellQuantity(size);
                break;
            case 5: // LAST_SIZE
                tick.setLastTradedQuantity(size);
                break;
            case 8: // VOLUME
                tick.setVolumeTradedToday(size);
                break;
        }
    }

    public void processTickByTickAllLast(int reqId, int tickType, long time, double price, int size) {
        Tick tick = getOrCreateTick(reqId);
        tick.setLastTradedPrice(price);
        tick.setLastTradedQuantity(size);
        tick.setLastTradedTime(new Date(time * 1000));
        tick.setTickTimestamp(new Date(time * 1000));
        tick.setMode(tickType == 1 ? "ask" : "bid");
    }

    public void processTickByTickBidAsk(int reqId, double bidPrice, double askPrice, int bidSize, int askSize) {
        Tick tick = getOrCreateTick(reqId);
        tick.setTotalBuyQuantity(bidSize);
        tick.setTotalSellQuantity(askSize);
    }

    private Tick getOrCreateTick(int instrumentToken) {
        return instrumentTicks.computeIfAbsent(instrumentToken, k -> {
            Tick tick = new Tick();
            tick.setInstrumentToken(instrumentToken);
            tick.setTradable(true);
            // Set symbol from your registry
            tick.setSymbol(registry.getSymbol(instrumentToken));
            return tick;
        });
    }

    public Tick getTick(int instrumentToken) {
        return instrumentTicks.get(instrumentToken);
    }
}
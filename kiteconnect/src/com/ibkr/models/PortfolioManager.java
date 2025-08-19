package com.ibkr.models;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PortfolioManager {
    private final Map<String, TradingPosition> positions = new ConcurrentHashMap<>();

    public void updatePosition(String symbol, int quantity, double price, TradeAction action) {
        TradingPosition position = positions.get(symbol);
        if (position == null) {
            position = new TradingPosition(symbol, 0, 0);
            positions.put(symbol, position);
        }

        if (action == TradeAction.BUY) {
            position.setQuantity(position.getQuantity() + quantity);
            position.setEntryPrice(price); // This is a simplification. A real implementation would average the price.
        } else { // SELL
            position.setQuantity(position.getQuantity() - quantity);
            if (position.getQuantity() == 0) {
                positions.remove(symbol);
            }
        }
    }

    public Collection<TradingPosition> getOpenPositions() {
        return positions.values();
    }
}

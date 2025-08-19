package com.ibkr.models;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PortfolioManager {
    private final Map<String, TradingPosition> positions = new ConcurrentHashMap<>();

    public void updatePosition(String symbol, int quantity, double price, TradeAction action) {
        TradingPosition currentPosition = positions.get(symbol);
        if (currentPosition == null) {
            // New position
            boolean isLong = action == TradeAction.BUY;
            // volatilityAtEntry is not available here. I will use 0 for now.
            // instrumentToken is not available here. I will use 0 for now.
            TradingPosition newPosition = new TradingPosition(0, symbol, price, quantity, 0, isLong, action);
            positions.put(symbol, newPosition);
        } else {
            // Update existing position
            int newQuantity = currentPosition.getQuantity();
            if (action == TradeAction.BUY) {
                newQuantity += quantity;
            } else { // SELL
                newQuantity -= quantity;
            }

            if (newQuantity == 0) {
                positions.remove(symbol);
            } else {
                // Create a new position object with updated values
                // Again, volatilityAtEntry and instrumentToken are not available.
                // Averaging entry price is also a simplification.
                TradingPosition updatedPosition = new TradingPosition(
                        currentPosition.getInstrumentToken(),
                        symbol,
                        price, // Using the new price, not averaging. This is a simplification.
                        newQuantity,
                        currentPosition.getVolatilityAtEntry(),
                        currentPosition.isLong(),
                        action);
                positions.put(symbol, updatedPosition);
            }
        }
    }

    public Collection<TradingPosition> getOpenPositions() {
        return positions.values();
    }
}

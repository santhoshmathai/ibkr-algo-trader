package com.ibkr.models;


public class TradingPosition {
    private final long instrumentToken;
    private final String symbol;

    private final double entryPrice;
    private final int quantity;
    private final double volatilityAtEntry;
    private final boolean isLong;

    public TradingPosition(long instrumentToken, String symbol, double entryPrice,
                           int quantity, double volatilityAtEntry, boolean isLong) {
        this.instrumentToken = instrumentToken;
        this.symbol = symbol;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.volatilityAtEntry = volatilityAtEntry;
        this.isLong = isLong;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isInPosition() {
        return quantity != 0;
    }

    public boolean isLong() {
        return isLong;
    }


    // Getters
    public long getInstrumentToken() {
        return instrumentToken;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getVolatilityAtEntry() {
        return volatilityAtEntry;
    }
}
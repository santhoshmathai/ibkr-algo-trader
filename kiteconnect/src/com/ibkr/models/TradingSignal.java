package com.ibkr.models;

public class TradingSignal {
    //public enum Action { BUY, SELL, HOLD, HALT }
    private final long instrumentToken;
    private final String symbol;
    private final TradeAction action;
    private final double price;
    private final int quantity;
    private final boolean darkPoolAllowed;
    private final String strategyId;

    private TradingSignal(Builder builder) {
        this.instrumentToken = builder.instrumentToken;
        this.symbol = builder.symbol;
        this.action = builder.action;
        this.price = builder.price;
        this.quantity = builder.quantity;
        this.darkPoolAllowed = builder.darkPoolAllowed;
        this.strategyId = builder.strategyId;
    }

    public static class Builder {
        private long instrumentToken;
        private String symbol;
        private TradeAction action = TradeAction.HOLD;
        private double price;
        private int quantity;
        private boolean darkPoolAllowed = false;
        private String strategyId = "DEFAULT";

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder action(TradeAction action) {
            this.action = action;
            return this;
        }

        public Builder price(double price) {
            this.price = price;
            return this;
        }

        public Builder instrumentToken(long instrumentToken) {
            this.instrumentToken = instrumentToken;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder darkPoolAllowed(boolean allowed) {
            this.darkPoolAllowed = allowed;
            return this;
        }

        public Builder strategyId(String id) {
            this.strategyId = id;
            return this;
        }

        public TradingSignal build() {
            return new TradingSignal(this);
        }
    }

    public static TradingSignal halt(String symbol) {
        return new Builder()
                .symbol(symbol) // Include the symbol for which trading is halted
                .action(TradeAction.HALT)
                .strategyId("CIRCUIT_BREAKER") // Or a more generic "SYSTEM_HALT"
                .build();
    }

    public static TradingSignal hold() {
        return new Builder().action(TradeAction.HOLD).build();
    }

    // Getters
    public String getSymbol() { return symbol; }
    public TradeAction getAction() { return action; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public boolean isDarkPoolAllowed() { return darkPoolAllowed; }
    public String getStrategyId() { return strategyId; }
    public long getInstrumentToken() {
        return instrumentToken;
    }

}
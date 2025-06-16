package com.ibkr.models;

public class PreviousDayData {
    private final String symbol;
    private double previousHigh;
    private double previousClose;

    public PreviousDayData(String symbol, double previousHigh, double previousClose) {
        this.symbol = symbol;
        this.previousHigh = previousHigh;
        this.previousClose = previousClose;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPreviousHigh() {
        return previousHigh;
    }

    public void setPreviousHigh(double previousHigh) {
        this.previousHigh = previousHigh;
    }

    public double getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(double previousClose) {
        this.previousClose = previousClose;
    }

    @Override
    public String toString() {
        return "PreviousDayData{" +
                "symbol='" + symbol + '\'' +
                ", previousHigh=" + previousHigh +
                ", previousClose=" + previousClose +
                '}';
    }
}

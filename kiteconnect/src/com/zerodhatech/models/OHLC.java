package com.zerodhatech.models;

import com.google.gson.annotations.SerializedName;

/**
 * A wrapper for open, high, low, close.
 */
public class OHLC {

    @SerializedName("high")
    private double high;
    @SerializedName("low")
    private double low;
    @SerializedName("close")
    private double close;
    @SerializedName("open")
    private double open;

    public OHLC() {
    }

    public OHLC(OHLC other) {
        this.open = other.getOpen();
        this.high = other.getHigh();
        this.low = other.getLow();
        this.close = other.getClose();
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }
}

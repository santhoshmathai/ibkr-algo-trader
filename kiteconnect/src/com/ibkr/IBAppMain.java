package com.ibkr;

import com.ibkr.core.IBClient;

public class IBAppMain {
    public static void main(String[] args) {
        IBClient client = new IBClient();
        client.connect("127.0.0.1", 7496, 0);
        //client.startMessageProcessing();
        // Subscribe to instruments
        client.subscribeToSymbol("AAPL", "SMART");
        client.subscribeToSymbol("MSFT", "SMART");
        client.subscribeToSymbol("GOOGL", "SMART");
    }
}
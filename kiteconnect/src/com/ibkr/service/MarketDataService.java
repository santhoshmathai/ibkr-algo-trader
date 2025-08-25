package com.ibkr.service;

import com.ib.client.Contract;
import com.zerodhatech.models.HistoricalData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import java.util.Map;

public interface MarketDataService {
    CompletableFuture<List<HistoricalData>> requestHistoricalData(
            Contract contract, String endDateTime, String durationStr, String barSizeSetting,
            String whatToShow, int useRTH, int formatDate);

    CompletableFuture<Map<String, Long>> getOpeningRangeVolumeHistory(String symbol, int days, int timeframeMinutes, String time);

    CompletableFuture<List<HistoricalData>> getDailyHistoricalData(String symbol, int days);

    CompletableFuture<Double> getAverageDailyVolume(String symbol);

    void connect(String host, int port, int clientId);
    void disconnect();
    boolean isConnected();
}

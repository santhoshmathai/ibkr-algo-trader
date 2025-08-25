package com.ibkr.service;

import com.ib.client.Contract;
import com.ibkr.marketdata.reader.CsvMarketDataReader;
import com.ibkr.marketdata.reader.StockDataRecord;
import com.zerodhatech.models.HistoricalData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BacktestMarketDataService implements MarketDataService {

    private final CsvMarketDataReader csvMarketDataReader;

    public BacktestMarketDataService(String filePath) {
        this.csvMarketDataReader = new CsvMarketDataReader();
        try {
            this.csvMarketDataReader.loadData(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load backtest data from " + filePath, e);
        }
    }

    @Override
    public CompletableFuture<List<HistoricalData>> requestHistoricalData(Contract contract, String endDateTime, String durationStr, String barSizeSetting, String whatToShow, int useRTH, int formatDate) {
        List<HistoricalData> historicalDataList = new ArrayList<>();
        for (StockDataRecord record : csvMarketDataReader.getRecords()) {
            if (record.getString(StockDataRecord.SYMBOL).equals(contract.symbol())) {
                HistoricalData historicalData = new HistoricalData();
                historicalData.open = record.getDouble(StockDataRecord.OPEN);
                historicalData.high = record.getDouble(StockDataRecord.HIGH);
                historicalData.low = record.getDouble(StockDataRecord.LOW);
                historicalData.close = record.getDouble(StockDataRecord.LTP);
                historicalData.volume = record.getLong(StockDataRecord.VOLUME_SHARES);
                historicalDataList.add(historicalData);
            }
        }
        return CompletableFuture.completedFuture(historicalDataList);
    }

    @Override
    public CompletableFuture<Map<String, Long>> getOpeningRangeVolumeHistory(String symbol, int days, int timeframeMinutes, String time) {
        return CompletableFuture.completedFuture(new HashMap<>());
    }

    @Override
    public CompletableFuture<Double> getAverageDailyVolume(String symbol) {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public void connect(String host, int port, int clientId) {
        // No-op for backtesting
    }

    @Override
    public void disconnect() {
        // No-op for backtesting
    }

    @Override
    public CompletableFuture<List<HistoricalData>> getDailyHistoricalData(String symbol, int days) {
        return null;
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}

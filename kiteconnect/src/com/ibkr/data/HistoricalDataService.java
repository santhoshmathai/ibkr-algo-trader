package com.ibkr.data;

import com.ib.client.Contract;
import com.ibkr.core.IBClient;
import com.zerodhatech.models.HistoricalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service to fetch historical data required for strategies.
 * This includes daily bars for ATR and average volume calculations,
 * and historical opening range data for Relative Volume calculations.
 */
public class HistoricalDataService {

    private static final Logger logger = LoggerFactory.getLogger(HistoricalDataService.class);
    private final IBClient ibClient;
    private final InstrumentRegistry instrumentRegistry;
    private static final DateTimeFormatter IB_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");


    public HistoricalDataService(IBClient ibClient, InstrumentRegistry instrumentRegistry) {
        this.ibClient = ibClient;
        this.instrumentRegistry = instrumentRegistry;
    }

    /**
     * Fetches historical daily bars for a given symbol for a specified number of trading days.
     *
     * @param symbol The stock symbol.
     * @param days   The number of past trading days to fetch (e.g., 14 for 14 days).
     * @return A CompletableFuture that will complete with a list of HistoricalData objects.
     */
    public CompletableFuture<List<HistoricalData>> getDailyHistoricalData(String symbol, int days) {
        Contract contract = instrumentRegistry.getContractBySymbol(symbol);
        if (contract == null) {
            logger.error("No contract found for symbol: {}. Cannot fetch daily historical data.", symbol);
            return CompletableFuture.failedFuture(new IllegalArgumentException("No contract for symbol: " + symbol));
        }

        String endDateTime = ""; // Empty string gets data up to the previous day's close
        String durationStr = days + " D";
        String barSizeSetting = "1 day";
        String whatToShow = "TRADES";
        int useRTH = 1; // Use regular trading hours
        int formatDate = 2; // 2 for epoch seconds, easier to parse

        logger.info("Requesting daily historical data for {}: {} days", symbol, days);
        return ibClient.requestHistoricalData(contract, endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, formatDate);
    }

    /**
     * Fetches historical opening range bars for a symbol for the last N days.
     * For example, it can fetch the first 5 minutes of data for the last 14 days.
     * Note: This implementation fetches 1-minute bars for the last N days and then filters them.
     * A more optimized approach might be possible depending on exact IB API capabilities.
     *
     * @param symbol           The stock symbol.
     * @param days             The number of past trading days to fetch.
     * @param timeframeMinutes The duration of the opening range in minutes (e.g., 5).
     * @param marketOpenTime   The time the market opens (e.g., "09:30:00").
     * @return A CompletableFuture that will complete with a map where the key is the date string
     *         and the value is the aggregated volume for the opening range of that date.
     */
    public CompletableFuture<Map<String, Long>> getOpeningRangeVolumeHistory(String symbol, int days, int timeframeMinutes, String marketOpenTime) {
        Contract contract = instrumentRegistry.getContractBySymbol(symbol);
        if (contract == null) {
            logger.error("No contract found for symbol: {}. Cannot fetch opening range history.", symbol);
            return CompletableFuture.failedFuture(new IllegalArgumentException("No contract for symbol: " + symbol));
        }

        String endDateTime = ""; // Fetch up to now/previous day
        String durationStr = days + " D";
        String barSizeSetting = "1 min"; // Fetch 1-minute bars to aggregate
        String whatToShow = "TRADES";
        int useRTH = 1; // RTH only
        int formatDate = 2; // epoch seconds

        logger.info("Requesting 1-min historical data for {} to calculate opening range volume for last {} days.", symbol, days);

        CompletableFuture<List<HistoricalData>> futureBars = ibClient.requestHistoricalData(contract, endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, formatDate);

        return futureBars.thenApply(bars -> {
            logger.debug("Processing {} 1-min bars for {} to extract opening range volume.", bars.size(), symbol);
            // Group bars by date
            Map<String, List<HistoricalData>> barsByDate = bars.stream()
                    .collect(Collectors.groupingBy(bar -> {
                        // Extract date part from timestamp, assuming epoch seconds
                        LocalDateTime dt = LocalDateTime.ofEpochSecond(Long.parseLong(bar.timeStamp), 0, java.time.ZoneOffset.UTC);
                        return dt.toLocalDate().toString();
                    }));

            Map<String, Long> dailyOpeningVolume = new java.util.HashMap<>();

            barsByDate.forEach((date, dailyBars) -> {
                // Sort bars by time just in case
                dailyBars.sort((b1, b2) -> Long.compare(Long.parseLong(b1.timeStamp), Long.parseLong(b2.timeStamp)));

                // Find the market open bar
                final long[] openingBarTimestamp = {-1L};
                for(HistoricalData bar : dailyBars) {
                    LocalDateTime dt = LocalDateTime.ofEpochSecond(Long.parseLong(bar.timeStamp), 0, java.time.ZoneOffset.UTC);
                    // This logic needs to be timezone aware. Assuming UTC for now, but needs to be market time.
                    if (dt.toLocalTime().toString().contains(marketOpenTime)) {
                        openingBarTimestamp[0] = Long.parseLong(bar.timeStamp);
                        break;
                    }
                }

                if (openingBarTimestamp[0] != -1) {
                    long endTime = openingBarTimestamp[0] + (timeframeMinutes * 60);
                    long totalVolume = dailyBars.stream()
                            .filter(bar -> Long.parseLong(bar.timeStamp) >= openingBarTimestamp[0] && Long.parseLong(bar.timeStamp) < endTime)
                            .mapToLong(bar -> bar.volume)
                            .sum();
                    dailyOpeningVolume.put(date, totalVolume);
                    logger.debug("Date: {}, Opening Range ({}-min) Volume: {}", date, timeframeMinutes, totalVolume);
                } else {
                    logger.warn("Could not find opening bar for date {} for symbol {}. Cannot calculate OR volume.", date, symbol);
                }
            });

            return dailyOpeningVolume;
        });
    }
}

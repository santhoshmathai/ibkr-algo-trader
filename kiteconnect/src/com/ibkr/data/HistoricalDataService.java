package com.ibkr.data;

import com.ib.client.Contract;
import com.ibkr.service.MarketDataService;
import com.ibkr.service.MarketDataService;
import com.zerodhatech.models.HistoricalData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service to fetch historical data required for strategies.
 * This includes daily bars for ATR and average volume calculations,
 * and historical opening range data for Relative Volume calculations.
 */
public class HistoricalDataService {

    private static final Logger logger = LoggerFactory.getLogger(HistoricalDataService.class);
    private final MarketDataService marketDataService;
    private final InstrumentRegistry instrumentRegistry;
    private final MeterRegistry meterRegistry;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York"); // Adjust based on your market

    // Metrics
    private final Counter dailyDataRequestsCounter;
    private final Counter openingRangeRequestsCounter;
    private final Counter failedRequestsCounter;
    private final Timer dailyDataTimer;
    private final Timer openingRangeTimer;

    public HistoricalDataService(MarketDataService marketDataService, InstrumentRegistry instrumentRegistry, MeterRegistry meterRegistry) {
        this.marketDataService = marketDataService;
        this.instrumentRegistry = instrumentRegistry;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.dailyDataRequestsCounter = Counter.builder("historical.data.requests")
                .tag("type", "daily")
                .register(meterRegistry);

        this.openingRangeRequestsCounter = Counter.builder("historical.data.requests")
                .tag("type", "opening_range")
                .register(meterRegistry);

        this.failedRequestsCounter = Counter.builder("historical.data.failures")
                .register(meterRegistry);

        this.dailyDataTimer = Timer.builder("historical.data.duration")
                .tag("type", "daily")
                .register(meterRegistry);

        this.openingRangeTimer = Timer.builder("historical.data.duration")
                .tag("type", "opening_range")
                .register(meterRegistry);
    }

    /**
     * Validates contract existence for a given symbol
     */
    private Contract validateAndGetContract(String symbol) {
        Contract contract = instrumentRegistry.getContractBySymbol(symbol);
        if (contract == null) {
            String errorMsg = "No contract found for symbol: " + symbol;
            logger.error(errorMsg);
            failedRequestsCounter.increment();
            throw new IllegalArgumentException(errorMsg);
        }
        return contract;
    }

    /**
     * Fetches historical daily bars for a given symbol for a specified number of trading days.
     */
    public CompletableFuture<List<HistoricalData>> getDailyHistoricalData(String symbol, int days) {
        long startTime = System.nanoTime();
        dailyDataRequestsCounter.increment();

        try {
            Contract contract = validateAndGetContract(symbol);

            String endDateTime = ""; // Empty string gets data up to the previous day's close
            String durationStr = days + " D";
            String barSizeSetting = "1 day";
            String whatToShow = "TRADES";
            int useRTH = 1; // Use regular trading hours
            int formatDate = 2; // 2 for epoch seconds

            logger.info("Requesting daily historical data for {}: {} days", symbol, days);

            CompletableFuture<List<HistoricalData>> result = marketDataService.requestHistoricalData(
                    contract, endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, formatDate);

            // Record success metrics when completed
            result.whenComplete((data, ex) -> {
                long duration = System.nanoTime() - startTime;
                dailyDataTimer.record(duration, TimeUnit.NANOSECONDS);

                if (ex != null) {
                    failedRequestsCounter.increment();
                    logger.error("Failed to fetch daily data for {}: {}", symbol, ex.getMessage());
                } else {
                    logger.info("Successfully fetched {} daily bars for {}", data.size(), symbol);
                    // Record additional success metric
                    meterRegistry.counter("historical.data.success", "type", "daily").increment();
                }
            });

            return result;

        } catch (IllegalArgumentException e) {
            long duration = System.nanoTime() - startTime;
            dailyDataTimer.record(duration, TimeUnit.NANOSECONDS);
            failedRequestsCounter.increment();
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fetches historical opening range bars for a symbol for the last N days.
     */
    public CompletableFuture<Map<String, Long>> getOpeningRangeVolumeHistory(String symbol, int days, int timeframeMinutes, String marketOpenTime) {
        long startTime = System.nanoTime();
        openingRangeRequestsCounter.increment();

        try {
            Contract contract = validateAndGetContract(symbol);

            String endDateTime = "";
            String durationStr = days + " D";
            String barSizeSetting = "1 min";
            String whatToShow = "TRADES";
            int useRTH = 1;
            int formatDate = 2;

            logger.info("Requesting 1-min historical data for {} to calculate opening range volume for last {} days", symbol, days);

            CompletableFuture<Map<String, Long>> result = marketDataService.requestHistoricalData(
                    contract, endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, formatDate)
                    .thenApply(bars -> processOpeningRangeBars(bars, timeframeMinutes, marketOpenTime, symbol));

            // Record success metrics when completed
            result.whenComplete((data, ex) -> {
                long duration = System.nanoTime() - startTime;
                openingRangeTimer.record(duration, TimeUnit.NANOSECONDS);

                if (ex != null) {
                    failedRequestsCounter.increment();
                    logger.error("Failed to fetch opening range data for {}: {}", symbol, ex.getMessage());
                } else {
                    logger.info("Successfully processed opening range data for {} days for {}", data.size(), symbol);
                    // Record additional success metric
                    meterRegistry.counter("historical.data.success", "type", "opening_range").increment();
                }
            });

            return result;

        } catch (IllegalArgumentException e) {
            long duration = System.nanoTime() - startTime;
            openingRangeTimer.record(duration, TimeUnit.NANOSECONDS);
            failedRequestsCounter.increment();
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Processes 1-minute bars to extract opening range volume data
     */
    private Map<String, Long> processOpeningRangeBars(List<HistoricalData> bars, int timeframeMinutes, String marketOpenTime, String symbol) {
        if (bars == null || bars.isEmpty()) {
            logger.warn("No bars received for symbol: {}", symbol);
            return Collections.emptyMap();
        }

        logger.debug("Processing {} 1-min bars for {} to extract opening range volume", bars.size(), symbol);

        // Parse market open time once
        LocalTime marketOpen = LocalTime.parse(marketOpenTime);

        Map<String, List<HistoricalData>> barsByDate = bars.stream()
                .collect(Collectors.groupingBy(bar -> extractDateFromEpoch(bar.timeStamp)));

        Map<String, Long> dailyOpeningVolume = new HashMap<>();

        barsByDate.forEach((date, dailyBars) -> {
            long openingVolume = calculateDailyOpeningVolume(dailyBars, marketOpen, timeframeMinutes, date, symbol);
            if (openingVolume >= 0) {
                dailyOpeningVolume.put(date, openingVolume);
            }
        });

        return dailyOpeningVolume;
    }

    /**
     * Extracts date from epoch timestamp with proper timezone handling
     */
    private String extractDateFromEpoch(String epochSeconds) {
        long epoch = Long.parseLong(epochSeconds);
        Instant instant = Instant.ofEpochSecond(epoch);
        ZonedDateTime marketTime = instant.atZone(MARKET_TIME_ZONE);
        return marketTime.format(DATE_FORMATTER);
    }

    /**
     * Calculates opening range volume for a single day
     */
    private long calculateDailyOpeningVolume(List<HistoricalData> dailyBars, LocalTime marketOpen,
                                           int timeframeMinutes, String date, String symbol) {
        // Sort by timestamp
        dailyBars.sort(Comparator.comparingLong(bar -> Long.parseLong(bar.timeStamp)));

        // Find market open bar with proper timezone handling
        Optional<HistoricalData> openingBar = dailyBars.stream()
                .filter(bar -> isMarketOpenTime(bar.timeStamp, marketOpen))
                .findFirst();

        if (!openingBar.isPresent()) {
            logger.warn("Could not find opening bar for date {} for symbol {}", date, symbol);
            return -1;
        }

        long openingTimestamp = Long.parseLong(openingBar.get().timeStamp);
        long endTimestamp = openingTimestamp + (timeframeMinutes * 60);

        return dailyBars.stream()
                .filter(bar -> {
                    long barTimestamp = Long.parseLong(bar.timeStamp);
                    return barTimestamp >= openingTimestamp && barTimestamp < endTimestamp;
                })
                .mapToLong(bar -> bar.volume)
                .sum();
    }

    /**
     * Checks if a bar timestamp matches market open time with proper timezone
     */
    private boolean isMarketOpenTime(String epochSeconds, LocalTime marketOpen) {
        long epoch = Long.parseLong(epochSeconds);
        Instant instant = Instant.ofEpochSecond(epoch);
        ZonedDateTime marketTime = instant.atZone(MARKET_TIME_ZONE);
        return marketTime.toLocalTime().equals(marketOpen);
    }
}

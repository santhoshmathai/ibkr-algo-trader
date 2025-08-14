package com.ibkr.data;

import org.supercsv.io.CsvMapReader;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.prefs.CsvPreference;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to calculate the average DAILY trading volume for instruments
 * based on historical tick data stored in CSV files.
 */
import com.ibkr.strategy.common.VolumeSpikeStrategyParameters;

public class HistoricalVolumeService {

    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final Map<String, Double> averageDailyVolume = new ConcurrentHashMap<>();
    private final VolumeSpikeStrategyParameters params;

    public HistoricalVolumeService(VolumeSpikeStrategyParameters params) {
        this.params = params;
    }

    /**
     * Calculates and caches the average daily volume for the given symbols.
     *
     * @param symbols The list of stock symbols to process.
     */
    public void calculateAverageVolumes(List<String> symbols) {
        int daysToAnalyze = params.getDaysToAnalyze();
        System.out.println("Starting historical daily volume calculation for " + symbols.size() + " symbols over " + daysToAnalyze + " days.");
        List<LocalDate> tradingDays = getPastTradingDays(daysToAnalyze);

        for (String symbol : symbols) {
            try {
                List<Long> dailyTotals = new ArrayList<>();
                for (LocalDate day : tradingDays) {
                    Path filePath = Paths.get(params.getDataDirectory(), symbol + "_" + day.format(FILE_DATE_FORMATTER) + ".csv");
                    if (Files.exists(filePath)) {
                        long totalVolumeForDay = getTotalVolumeForDay(filePath);
                        if (totalVolumeForDay > 0) {
                            dailyTotals.add(totalVolumeForDay);
                        }
                    }
                }

                if (!dailyTotals.isEmpty()) {
                    double avgVolume = dailyTotals.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    if (avgVolume > 0) {
                        averageDailyVolume.put(symbol, avgVolume);
                        System.out.println("Cached average DAILY volume for " + symbol + ": " + String.format("%.2f", avgVolume));
                    }
                }
            } catch (IOException e) {
                System.err.println("Error processing symbol " + symbol + " for daily volume: " + e.getMessage());
            }
        }
        System.out.println("Historical daily volume calculation complete.");
    }

    /**
     * Retrieves the cached average daily volume for a symbol.
     *
     * @param symbol The stock symbol.
     * @return The average daily volume, or 0.0 if not found.
     */
    public double getAverageDailyVolume(String symbol) {
        return averageDailyVolume.getOrDefault(symbol, 0.0);
    }

    private long getTotalVolumeForDay(Path filePath) throws IOException {
        try (ICsvMapReader mapReader = new CsvMapReader(new FileReader(filePath.toFile()), CsvPreference.STANDARD_PREFERENCE)) {
            final String[] header = mapReader.getHeader(true);
            Map<String, String> row;
            String lastVolumeStr = "0";

            // Read the entire file to get the last row's volume
            while ((row = mapReader.read(header)) != null) {
                lastVolumeStr = row.get("VolumeTradedToday");
            }

            if (lastVolumeStr != null && !lastVolumeStr.isEmpty()) {
                return Long.parseLong(lastVolumeStr);
            }
        }
        return 0;
    }

    protected List<LocalDate> getPastTradingDays(int daysToAnalyze) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int count = 0;
        while (days.size() < daysToAnalyze) {
            LocalDate day = today.minusDays(++count); // Start from yesterday
            // Assuming Monday to Friday are trading days
            if (day.getDayOfWeek().getValue() >= 1 && day.getDayOfWeek().getValue() <= 5) {
                days.add(day);
            }
        }
        return days;
    }

    // Main method for self-contained testing
    public static void main(String[] args) {
        System.out.println("Running HistoricalVolumeService self-test...");
        try {
            // 1. Setup
            com.ibkr.strategy.common.VolumeSpikeStrategyParameters params = new com.ibkr.strategy.common.VolumeSpikeStrategyParameters();
            params.setDataDirectory("src/test/resources/market_data_test"); // Assuming test data is available
            params.setDaysToAnalyze(2);

            HistoricalVolumeService service = new HistoricalVolumeService(params) {
                @Override
                protected List<LocalDate> getPastTradingDays(int daysToAnalyze) {
                    return java.util.Collections.singletonList(LocalDate.of(2025, 8, 13));
                }
            };
            List<String> symbols = java.util.Collections.singletonList("TEST_SYMBOL");

            // 2. Execute
            service.calculateAverageVolumes(symbols);
            double result = service.getAverageDailyVolume("TEST_SYMBOL");

            // 3. Assert
            double expected = 500000.0;
            if (result != expected) {
                throw new AssertionError("Expected " + expected + ", but got " + result);
            }
            System.out.println("Self-test PASSED! Average daily volume is " + result);

        } catch (Exception e) {
            System.err.println("Self-test FAILED!");
            e.printStackTrace();
        }
    }
}

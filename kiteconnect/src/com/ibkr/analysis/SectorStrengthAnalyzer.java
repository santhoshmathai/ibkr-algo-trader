package com.ibkr.analysis;


import com.zerodhatech.models.Tick;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SectorStrengthAnalyzer {
    private final Map<String, List<String>> sectorToStocks = new HashMap<>();
    private final Map<String, Double> sectorReturns = new ConcurrentHashMap<>();
    private final Map<String, String> symbolToSector = new HashMap<>();

    public SectorStrengthAnalyzer() {
        // Initialize with sample data (in production, load from DB/API)
        symbolToSector.put("AAPL", "TECH");
        symbolToSector.put("MSFT", "TECH");
        symbolToSector.put("GOOGL", "TECH");
        symbolToSector.put("AMZN", "CONSUMER");
        symbolToSector.put("TSLA", "AUTOMOTIVE");
    }
    public void updateSectorPerformance(String symbol, double priceChange) {
        String sector = getSector(symbol);
        sectorReturns.merge(sector, priceChange, (old, change) -> (old + change) / 2);
    }

    public List<String> getTopSectors(int count) {
        return sectorReturns.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .toList();
    }

    public List<String> getBottomSectors(int count) {
        return sectorReturns.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(count)
                .map(Map.Entry::getKey)
                .toList();
    }

    public String getSector(String symbol) {
        // In production: Map symbols to sectors (e.g., AAPL → "Technology")
        return "Technology"; // Simplified
    }

    public boolean isOutperforming(Tick tick, String benchmarkSector) {
        String sector = symbolToSector.get(tick.getSymbol());
        if (sector == null) return false;

        double sectorReturn = sectorReturns.getOrDefault(sector, 0.0);
        double benchmarkReturn = sectorReturns.getOrDefault(benchmarkSector, 0.0);
        return sectorReturn > benchmarkReturn + 0.005; // 0.5% outperformance
    }

    public boolean isUnderperforming(Tick tick, String benchmarkSector) {
        String sector = symbolToSector.get(tick.getSymbol());
        if (sector == null) return false;

        double sectorReturn = sectorReturns.getOrDefault(sector, 0.0);
        double benchmarkReturn = sectorReturns.getOrDefault(benchmarkSector, 0.0);
        return sectorReturn < benchmarkReturn - 0.005; // 0.5% underperformance
    }
}
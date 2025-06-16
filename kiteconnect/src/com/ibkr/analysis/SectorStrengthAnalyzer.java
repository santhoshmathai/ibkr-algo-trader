package com.ibkr.analysis;


import com.zerodhatech.models.Tick;
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors; // Added for .toList() which might not be available in older Java versions if not directly supported by the collection's stream()

public class SectorStrengthAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(SectorStrengthAnalyzer.class); // Added
    private final Map<String, List<String>> sectorToStocks;
    private final Map<String, Double> sectorReturns = new ConcurrentHashMap<>();
    private final Map<String, String> symbolToSector;

    public SectorStrengthAnalyzer(Map<String, List<String>> sectorToStocks, Map<String, String> symbolToSector) {
        this.sectorToStocks = new HashMap<>(sectorToStocks != null ? sectorToStocks : Collections.emptyMap());
        this.symbolToSector = new HashMap<>(symbolToSector != null ? symbolToSector : Collections.emptyMap());
        logger.info("SectorStrengthAnalyzer initialized with {} sectors and {} symbol mappings.",
                this.sectorToStocks.size(), this.symbolToSector.size());
    }

    public void updateSectorPerformance(String symbol, double priceChange) {
        String sector = getSector(symbol);
        if (sector != null) {
            double newReturn = sectorReturns.merge(sector, priceChange, (oldVal, newPriceChange) -> (oldVal + newPriceChange) / 2.0);
            logger.debug("Updated sector performance for {}: Symbol: {}, PriceChange: {:.4f}, New SectorReturn: {:.4f}",
                    sector, symbol, priceChange, newReturn);
        } else {
            logger.warn("Cannot update sector performance for symbol {}: Sector not found.", symbol);
        }
    }

    // Removed duplicated empty getTopSectors method

    public List<String> getTopSectors(int count) {
        logger.debug("Getting top {} sectors.", count);
        List<String> topSectors = sectorReturns.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()); // Ensure compatibility
        logger.debug("Top sectors found: {}", topSectors);
        return topSectors;
    }

    public List<String> getBottomSectors(int count) {
        logger.debug("Getting bottom {} sectors.", count);
        List<String> bottomSectors = sectorReturns.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()); // Ensure compatibility
        logger.debug("Bottom sectors found: {}", bottomSectors);
        return bottomSectors;
    }

    public String getSector(String symbol) {
        String sector = symbolToSector.get(symbol);
        logger.trace("getSector for symbol {}: {}", symbol, sector);
        return sector;
    }

    public boolean isOutperforming(Tick tick, String benchmarkSector) {
        String symbol = tick.getSymbol();
        String sector = getSector(symbol);
        if (sector == null) {
            logger.warn("Cannot check outperformance for {}: Sector not found.", symbol);
            return false;
        }

        double sectorReturn = sectorReturns.getOrDefault(sector, 0.0);
        double benchmarkReturn = sectorReturns.getOrDefault(benchmarkSector, 0.0);
        boolean outperforming = sectorReturn > benchmarkReturn + 0.005; // 0.5% outperformance
        logger.debug("isOutperforming check for {}: Sector: {}, SectorReturn: {:.4f}, BenchmarkSector: {}, BenchmarkReturn: {:.4f}. Result: {}",
                symbol, sector, sectorReturn, benchmarkSector, benchmarkReturn, outperforming);
        return outperforming;
    }

    public boolean isUnderperforming(Tick tick, String benchmarkSector) {
        String symbol = tick.getSymbol();
        String sector = getSector(symbol);
        if (sector == null) {
            logger.warn("Cannot check underperformance for {}: Sector not found.", symbol);
            return false;
        }

        double sectorReturn = sectorReturns.getOrDefault(sector, 0.0);
        double benchmarkReturn = sectorReturns.getOrDefault(benchmarkSector, 0.0);
        boolean underperforming = sectorReturn < benchmarkReturn - 0.005; // 0.5% underperformance
        logger.debug("isUnderperforming check for {}: Sector: {}, SectorReturn: {:.4f}, BenchmarkSector: {}, BenchmarkReturn: {:.4f}. Result: {}",
                symbol, sector, sectorReturn, benchmarkSector, benchmarkReturn, underperforming);
        return underperforming;
    }
}
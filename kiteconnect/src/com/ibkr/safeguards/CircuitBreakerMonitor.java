package com.ibkr.safeguards;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerMonitor {
    private final Map<String, CBStatus> statusMap = new ConcurrentHashMap<>();

    public enum CBStatus { NORMAL, LEVEL1, LEVEL2, HALTED }

    public void updateStatus(String symbol, double priceChangePct) {
        CBStatus status = CBStatus.NORMAL;
        if (priceChangePct >= 0.07) status = CBStatus.LEVEL1;
        if (priceChangePct >= 0.13) status = CBStatus.LEVEL2;
        if (priceChangePct >= 0.20) status = CBStatus.HALTED;
        statusMap.put(symbol, status);
    }

    public boolean isTradingHalted(String symbol) {
            return statusMap.getOrDefault(symbol, CBStatus.NORMAL) == CBStatus.HALTED;
    }

    public boolean allowAggressiveOrders(String symbol) {
        return statusMap.getOrDefault(symbol, CBStatus.NORMAL) == CBStatus.NORMAL;
    }

    public boolean allowShortSelling(String symbol) {
        CBStatus status = statusMap.getOrDefault(symbol, CBStatus.NORMAL);
        return status == CBStatus.NORMAL || status == CBStatus.LEVEL1;
    }

    public double getSizeMultiplier(String symbol) {
        return switch (statusMap.getOrDefault(symbol, CBStatus.NORMAL)) {
            case LEVEL2 -> 0.3;
            case LEVEL1 -> 0.7;
            default -> 1.0;
        };
    }
}
package com.ibkr.execution;

import com.ib.client.*;
import com.ibkr.liquidity.DarkPoolScanner;
import com.ibkr.models.TradeAction;
import com.ibkr.models.TradingSignal;
import com.ibkr.safeguards.CircuitBreakerMonitor;

import java.util.ArrayList;

public class SmartOrderRouter {
    private final CircuitBreakerMonitor cbMonitor;
    private final DarkPoolScanner dpScanner;

    public SmartOrderRouter(CircuitBreakerMonitor cbMonitor, DarkPoolScanner dpScanner) {
        this.cbMonitor = cbMonitor;
        this.dpScanner = dpScanner;
    }

    public Order routeOrder(TradingSignal signal) {
        if (signal.getAction() == TradeAction.HOLD ||
                signal.getAction() == TradeAction.HALT) {
            return null;
        }

        Order order = new Order();

        // Basic order parameters
        order.action(signal.getAction() == TradeAction.BUY ? "BUY" : "SELL");
        order.totalQuantity(signal.getQuantity());
        order.lmtPrice(signal.getPrice());
        order.orderType("LMT");
        order.tif("DAY");

        // Advanced routing logic
        if (signal.isDarkPoolAllowed() && dpScanner.hasDarkPoolSupport(signal.getSymbol())) {
            configureDarkPoolOrder(order);
        } else if (cbMonitor.allowAggressiveOrders(signal.getSymbol())) {
            configureAggressiveOrder(order);
        } else {
            configurePassiveOrder(order);
        }

        return order;
    }

    private void configureDarkPoolOrder(Order order) {
        order.algoStrategy("DarkPool");
        order.algoParams(new ArrayList<>());
        order.algoParams().add(new TagValue("DarkPoolOnly", "1"));
        order.algoParams().add(new TagValue("Stealth", "1")); // Add stealth mode
    }

    private void configureAggressiveOrder(Order order) {
        order.orderType("MKT");
        order.tif("IOC"); // Immediate or Cancel
    }

    private void configurePassiveOrder(Order order) {
        order.orderType("LMT");
        order.tif("GTC"); // Good Till Cancel
    }
}
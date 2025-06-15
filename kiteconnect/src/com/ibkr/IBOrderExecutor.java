package com.ibkr;

import com.ib.client.*;
import com.ibkr.models.Order;  // Our custom Order model
import com.ibkr.models.TradeAction;
import com.ibkr.safeguards.CircuitBreakerMonitor;
import com.ibkr.liquidity.DarkPoolScanner;

import java.util.HashMap;
import java.util.Map;

public class IBOrderExecutor {
    private final EClientSocket client;
    private final CircuitBreakerMonitor cbMonitor;
    private final DarkPoolScanner dpScanner;
    private int nextOrderId = 1;
    private final Map<String, Integer> symbolToOrderId = new HashMap<>();

    public IBOrderExecutor(EClientSocket client,
                           CircuitBreakerMonitor cbMonitor,
                           DarkPoolScanner dpScanner) {
        this.client = client;
        this.cbMonitor = cbMonitor;
        this.dpScanner = dpScanner;
    }

    public void placeOrder(Order order) {
        Contract contract = createContract(order.getSymbol());
        com.ib.client.Order ibOrder = createIBOrder(order);

        // Generate unique order ID
        int orderId = nextOrderId++;
        symbolToOrderId.put(order.getSymbol(), orderId);

        client.placeOrder(orderId, contract, ibOrder);
        System.out.println("Placed IB order #" + orderId + " for " + order.getSymbol());
    }

    private Contract createContract(String symbol) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");
        return contract;
    }

    private com.ib.client.Order createIBOrder(Order order) {
        com.ib.client.Order ibOrder = new com.ib.client.Order();

        // Convert our TradeAction to IB action string
        String action = order.getAction() == TradeAction.BUY ? "BUY" : "SELL";
        ibOrder.action(action);

        // Set smart routing based on market conditions
        if (dpScanner.hasDarkPoolSupport(order.getSymbol()) && order.isDarkPoolAllowed()) {
            configureDarkPoolOrder(ibOrder);
        } else if (cbMonitor.allowAggressiveOrders(order.getSymbol())) {
            configureAggressiveOrder(ibOrder);
        } else {
            configurePassiveOrder(ibOrder);
        }

        ibOrder.totalQuantity(order.getQuantity());
        ibOrder.tif("GTC");
        return ibOrder;
    }

    private void configureDarkPoolOrder(com.ib.client.Order order) {
        order.orderType("LMT");
        order.lmtPrice(order.lmtPrice());  // Use original limit price
        order.algoStrategy("DarkPool");
        order.algoParams(new java.util.ArrayList<>());
        order.algoParams().add(new TagValue("DarkPoolOnly", "1"));
        order.algoParams().add(new TagValue("Stealth", "1"));
    }

    private void configureAggressiveOrder(com.ib.client.Order order) {
        order.orderType("MKT");
        order.tif("IOC");  // Immediate or Cancel
    }

    private void configurePassiveOrder(com.ib.client.Order order) {
        order.orderType("LMT");
        order.lmtPrice(order.lmtPrice());  // Use original limit price
        order.tif("DAY");
    }

    // Callback for order status updates
    public void onOrderStatus(int orderId, String status, double filled,
                              double remaining, double avgFillPrice) {
        System.out.printf("Order %d: %s (Filled: %.0f, Price: %.2f)%n",
                orderId, status, filled, avgFillPrice);
    }

    // Callback for next valid order ID
    public void setNextValidOrderId(int orderId) {
        this.nextOrderId = orderId;
    }
}
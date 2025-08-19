package com.ibkr;

import com.ib.client.*;
import com.ibkr.models.Order;  // Our custom Order model
import com.ibkr.models.PortfolioManager;
import com.ibkr.models.TradeAction;
import com.ibkr.models.OrderType;
import com.ibkr.safeguards.CircuitBreakerMonitor;
import com.ibkr.liquidity.DarkPoolScanner;
import com.ibkr.data.InstrumentRegistry;
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory;

import java.util.ArrayList; // Added
import java.util.HashMap;
import java.util.List; // Added
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // Added
import java.util.concurrent.atomic.AtomicInteger;

public class IBOrderExecutor {
    private static final Logger logger = LoggerFactory.getLogger(IBOrderExecutor.class);
    private EClientSocket clientSocket;
    private final CircuitBreakerMonitor cbMonitor;
    private final DarkPoolScanner dpScanner;
    private final InstrumentRegistry instrumentRegistry;
    private final PortfolioManager portfolioManager;
    private final AtomicInteger nextOrderId = new AtomicInteger(0);
    private final Map<String, Integer> symbolToOrderId = new HashMap<>(); // This map might need review for long-running apps

    // Internal storage for order states and executions
    private final Map<Integer, String> orderIdToStatusMap = new ConcurrentHashMap<>();
    private final Map<Integer, List<Execution>> orderIdToExecutionsMap = new ConcurrentHashMap<>();
    private final Map<Integer, Order> orderIdToOrderMap = new ConcurrentHashMap<>();
    private final Map<Integer, TradingSignal> orderIdToSignalMap = new ConcurrentHashMap<>();


    public IBOrderExecutor(CircuitBreakerMonitor cbMonitor,
                           DarkPoolScanner dpScanner,
                           InstrumentRegistry instrumentRegistry,
                           PortfolioManager portfolioManager) {
        this.cbMonitor = cbMonitor;
        this.dpScanner = dpScanner;
        this.instrumentRegistry = instrumentRegistry;
        this.portfolioManager = portfolioManager;
        // nextOrderId is initialized when TWS calls nextValidId via IBClient
    }

    public void setClientSocket(EClientSocket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public int placeOrder(Order order) {
        if (this.nextOrderId.get() == 0) {
            logger.error("Cannot place order for {}. NextValidId has not been received from TWS yet.", order.getSymbol());
            // Optionally, throw an exception or return an order rejection status
            return -1;
        }
        Contract contract = createContract(order.getSymbol());
        com.ib.client.Order ibOrder = createIBOrder(order);

        int orderId = this.nextOrderId.getAndIncrement(); // Use AtomicInteger
        symbolToOrderId.put(order.getSymbol() + "_" + orderId, orderId); // Make key more unique if needed
        orderIdToOrderMap.put(orderId, order);

        if (this.clientSocket == null) {
            logger.error("EClientSocket is not set. Cannot place order for symbol: {}", order.getSymbol());
            return -1;
        }
        this.clientSocket.placeOrder(orderId, contract, ibOrder);
        logger.info("Placed IB order #{} for {}", orderId, order.getSymbol());
        return orderId;
    }

    public void placeOrder(TradingSignal signal) {
        Order order = new Order();
        order.setSymbol(signal.getSymbol());
        order.setAction(signal.getAction());
        order.setQuantity(signal.getQuantity());
        order.setPrice(signal.getPrice());
        order.setOrderType(signal.getOrderType());
        order.setDarkPoolAllowed(signal.isDarkPoolAllowed());

        int orderId = placeOrder(order);

        if (orderId != -1) {
            // Store the signal for stop loss placement
            orderIdToSignalMap.put(orderId, signal);
        }
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
        if (order.getOrderType() == OrderType.MARKET) {
            ibOrder.orderType("MKT");
        } else if (order.getOrderType() == OrderType.LIMIT) {
            ibOrder.orderType("LMT");
            ibOrder.lmtPrice(order.getPrice());
        } else if (order.getOrderType() == OrderType.STOP) {
            ibOrder.orderType("STP");
            ibOrder.auxPrice(order.getPrice());
        } else if (order.getOrderType() == OrderType.BUY_STOP || order.getOrderType() == OrderType.SELL_STOP) {
            ibOrder.orderType("STP");
            ibOrder.auxPrice(order.getPrice());
        } else {
            if (dpScanner.hasDarkPoolSupport(order.getSymbol()) && order.isDarkPoolAllowed()) {
                configureDarkPoolOrder(ibOrder);
            } else if (cbMonitor.allowAggressiveOrders(order.getSymbol())) {
                configureAggressiveOrder(ibOrder);
            } else {
                configurePassiveOrder(ibOrder);
            }
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

    // Method called by IBClient's orderStatus callback
    public void updateOrderStatus(int orderId, String status, double filled,
                                  double remaining, double avgFillPrice, double lastFillPrice) {
        logger.info("Order ID {}: Status={}, Filled={}, Remaining={}, AvgFillPrice={}, LastFillPrice={}",
                orderId, status, filled, remaining, avgFillPrice, lastFillPrice);
        orderIdToStatusMap.put(orderId, status);

        if (status.equals("Filled")) {
            Order order = orderIdToOrderMap.get(orderId);
            if (order != null) {
                portfolioManager.updatePosition(order.getSymbol(), (int) filled, avgFillPrice, order.getAction());

                TradingSignal signal = orderIdToSignalMap.get(orderId);
                if (signal != null && signal.getStopLossPrice() > 0) {
                    placeStopLossOrder(signal);
                }
            }
        }
    }

    private void placeStopLossOrder(TradingSignal signal) {
        Order stopLossOrder = new Order();
        stopLossOrder.setSymbol(signal.getSymbol());
        stopLossOrder.setAction(signal.getAction() == TradeAction.BUY ? TradeAction.SELL : TradeAction.BUY);
        stopLossOrder.setQuantity(signal.getQuantity());
        stopLossOrder.setPrice(signal.getStopLossPrice());
        stopLossOrder.setOrderType(com.ibkr.models.OrderType.STOP);
        placeOrder(stopLossOrder);
        logger.info("Placed stop loss order for signal: {}", signal);
    }

    // Method called by IBClient's openOrder callback
    public void handleOpenOrder(int orderId, Contract contract, com.ib.client.Order ibOrder, com.ib.client.OrderState orderState) {
        logger.info("Received open order details for Order ID {}: Symbol={}, Action={}, Type={}, Status={}",
                orderId, contract.symbol(), ibOrder.action(), ibOrder.orderType(), orderState.getStatus());
        orderIdToStatusMap.put(orderId, orderState.getStatus());
        // TODO: Store more details from ibOrder or contract if needed for internal state reconciliation
    }

    // Method called by IBClient's openOrderEnd callback
    public void handleOpenOrderEnd() {
        logger.debug("IBOrderExecutor: Open order batch processing finished.");
        // TODO: Any logic to perform after all open orders are received
    }

    // Method called by IBClient's execDetails callback
    public void handleExecutionDetails(int reqId, Contract contract, Execution execution) {
        logger.info("Execution for Order ID {}: Symbol={}, Side={}, Shares={}, Price={}, Time={}, ExecId={}",
                execution.orderId(), contract.symbol(), execution.side(), execution.shares(), execution.price(), execution.time(), execution.execId());

        orderIdToExecutionsMap.computeIfAbsent(execution.orderId(), k -> new ArrayList<>()).add(execution);
        // TODO: Update portfolio/positions based on execution details
    }

    // Method called by IBClient's execDetailsEnd callback
    public void handleExecutionDetailsEnd(int reqId) {
        logger.debug("IBOrderExecutor: Execution details batch processing finished for reqId: {}", reqId);
        // TODO: Any logic to perform after all executions for a request are received
    }

    // Callback for next valid order ID
    public synchronized void setNextValidOrderId(int orderId) {
        this.nextOrderId.set(orderId);
        logger.info("Next valid order ID set to: {}", orderId);
    }
}
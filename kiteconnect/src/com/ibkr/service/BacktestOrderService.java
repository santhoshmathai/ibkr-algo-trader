package com.ibkr.service;

import com.ibkr.models.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BacktestOrderService implements OrderService {

    private static final Logger logger = LoggerFactory.getLogger(BacktestOrderService.class);
    private int nextOrderId = 1;

    @Override
    public int placeOrder(Order order) {
        int orderId = nextOrderId++;
        logger.info("Simulating placing order: " + order + " with orderId: " + orderId);
        return orderId;
    }

    @Override
    public void cancelOrder(int orderId) {
        logger.info("Simulating canceling order: " + orderId);
    }
}

package com.ibkr.service;

import com.ibkr.IBOrderExecutor;
import com.ibkr.models.Order;

public class IbkrOrderService implements OrderService {

    private final IBOrderExecutor orderExecutor;

    public IbkrOrderService(IBOrderExecutor orderExecutor) {
        this.orderExecutor = orderExecutor;
    }

    @Override
    public int placeOrder(Order order) {
        return orderExecutor.placeOrder(order);
    }

    @Override
    public void cancelOrder(int orderId) {
        orderExecutor.cancelOrder(orderId);
    }
}

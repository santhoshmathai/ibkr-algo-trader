package com.ibkr.service;

import com.ibkr.models.Order;

public interface OrderService {
    int placeOrder(Order order);
    void cancelOrder(int orderId);
}

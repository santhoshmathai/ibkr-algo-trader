package com.ibkr.models;



public class Order {
    private String orderId;
    private String symbol;
    private TradeAction action;
    private OrderType orderType;
    private boolean isDarkPoolAllowed;

    public boolean isDarkPoolAllowed() {
        return isDarkPoolAllowed;
    }

    public String getOrderId() {
        return orderId;
    }

    private double price;

    public void setDarkPoolAllowed(boolean darkPoolAllowed) {
        isDarkPoolAllowed = darkPoolAllowed;
    }

    private int quantity;

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setAction(TradeAction action) {
        this.action = action;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeAction getAction() {
        return action;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    private OrderStatus status;
    private String rejectionReason;

    // Getters, setters, builder
}

enum OrderStatus {
    PENDING, FILLED, REJECTED, CANCELLED
}
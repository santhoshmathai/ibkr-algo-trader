package com.ibkr.risk;


import com.ibkr.models.TradingSignal;

public interface RiskEvaluator {
    boolean validateTrade(TradingSignal signal);
}
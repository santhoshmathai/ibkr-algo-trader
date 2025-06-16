package com.ibkr.models;

public enum OpeningMarketTrend {
    TREND_UP,               // Market has shown a clear upward trend after observation
    TREND_DOWN,             // Market has shown a clear downward trend after observation
    TREND_NEUTRAL,          // Market is mixed or flat after observation
    OBSERVATION_PERIOD,     // Currently within the initial observation window, trend not yet determined
    OUTSIDE_ANALYSIS_WINDOW // Not within the market opening analysis window (e.g., market closed, or past the initial period)
}

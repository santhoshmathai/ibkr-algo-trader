<a href="https://zerodha.tech"><img src="https://zerodha.tech/static/images/github-badge.svg" align="right" /></a>
# Detailed logic for each class:

##  BreakoutSignalGenerator.java

**Logic**: It generates BUY/SELL signals when price breaks support/resistance, confirmed by VWAP and volume. For BUYs, price must break above resistance, be above VWAP, and have a volume spike. For SELLs (shorts), price must break below support, be below VWAP, and have a volume spike. An alternative short signal also checks for sector weakness.

**Integration**: It relies on VWAPAnalyzer, VolumeAnalyzer, SectorStrengthAnalyzer, and SupportResistanceAnalyzer to get the necessary data for its decisions.

**Handling Existing Positions**: If a TradingPosition object indicates an active trade (currentPosition.isInPosition() is true), it switches to exit logic. Exits (profit taking or stop-loss) are determined by volatility-based price targets calculated using the volatilityAtEntry stored in the TradingPosition.

**Risk Management:** Risk is managed through:

1. Requiring multiple confirmations (price, S/R, VWAP, volume) before entry.
2. Using volatility-adjusted stop-losses for exits.
3. Calculating position size to limit potential loss on a single trade to a predefined capital percentage (e.g., 1% of $10k).


### VWAPAnalyzer
Calculates the Volume Weighted Average Price. BreakoutSignalGenerator uses it to confirm if the price is above VWAP (for buys) or below VWAP (for shorts), ensuring the trade aligns with short-term momentum.

**VWAPAnalyzer Detailed Logic:**
Core Function: Calculates VWAP over a rolling 50-tick window.

update(Tick tick):

1. Calculates Typical Price for the tick: (High + Low + LTP) / 3.
2. Uses tick.getVolumeTradedToday() as the volume for the tick. Note: This is cumulative daily volume. If interval volume is desired for a classic intraday VWAP, this is a key point.
3. Adds the tick to the window. Updates cumulativeVolume and cumulativeValue (sum of TypicalPrice * Volume).
4. If window exceeds 50 ticks, removes the oldest tick and subtracts its contribution from cumulativeVolume and cumulativeValue.

**getVWAP()**: Returns cumulativeValue / cumulativeVolume.

**getVolatility():** Calculates the standard deviation of typical prices in the window around the current VWAP.

**isAboveVWAP(Tick tick) / isBelowVWAP(Tick tick):** Checks if LTP is >0.5% above or <0.5% below VWAP, respectively. This 0.5% acts as a buffer.

**getDistanceToVWAP(Tick tick):** Returns the percentage difference between LTP and VWAP.

**Key Considerations for Fine-Tuning:**

* VWAP Window Size (50): Adjust based on desired sensitivity.
* Volume Data (tick.getVolumeTradedToday()): Understand its impact. Using cumulative daily volume means later ticks (with higher volume numbers) can have a larger effect if earlier ticks are still in the window. The fixed window helps normalize this over time.
* VWAP Buffer (0.5%): Evaluate if this fixed percentage is suitable across all price ranges or if an adaptive buffer (e.g., ATR-based) would be better.

### VolumeAnalyzer
Detects if the current trade volume represents a spike compared to recent average volume. BreakoutSignalGenerator requires this volume spike to confirm the strength and validity of a breakout/breakdown, filtering out moves on low conviction.

**VolumeAnalyzer Detailed Logic:**

**Core Function:** Detects if the current tick's trading volume constitutes a significant spike compared to recent average volume.

**update(Tick tick):**

1. Uses tick.getLastTradedQuantity() as the volume for the current tick (this is the volume of the last trade or within the tick's interval).
2. Ignores ticks with zero or negative lastTradedQuantity.
3. Adds this lastTradedQuantity to a rolling window of 20 volume readings.
4. Maintains a rollingSum of volumes in the window. If the window size is exceeded, the oldest volume is removed and subtracted from rollingSum.

**isBreakoutWithSpike(Tick tick) / isBreakdownWithSpike(Tick tick):**

1. Calculate avgVolume (simple moving average of volumes in the 20-period window).
2. A volume spike is defined as tick.getLastTradedQuantity() > avgVolume * 2.5.
3. The logic is identical for both methods; the context of breakout or breakdown is determined by the calling code (BreakoutSignalGenerator) based on price action, not by different volume calculations here.

**Key Considerations for Fine-Tuning:**

* **Volume Window Size (20):** Adjust for desired sensitivity of the average volume.
* **Spike Multiplier (2.5):** This is a key parameter. Lower for more signals, higher for fewer, stronger signals. Test and optimize.
* **Source of Volume (getLastTradedQuantity()):** This is good for immediate spike detection. Contrast with VWAPAnalyzer's use of getVolumeTradedToday().
* **Uniform Spike Definition:** The 2.5x threshold is the same for upside and downside moves. Consider if directional sensitivity is needed.

### SectorStrengthAnalyzer.java

Calculates the average performance of stocks within predefined sectors. BreakoutSignalGenerator uses its isUnderperforming method in generateShortSignal to check if the stock's sector is weak compared to a benchmark, adding a layer of confirmation for short trades.

#### SectorStrengthAnalyzer Detailed Logic:
**Core Function:** Calculates and compares the performance of different market sectors.

**Initialization:** Requires two maps:

1. sectorToStocks: Defines which stocks belong to which sector.
2. symbolToSector: Maps individual stock symbols to their sector.

**updateSectorPerformance(String symbol, double priceChange):**

1. Identifies the sector for the given symbol.
2. Updates the sector's performance in sectorReturns using: newReturn = (oldSectorReturn + stockPriceChange) / 2.0. This is a simple rolling average.
3. **Crucial:** The nature of priceChange (absolute, percentage, period of change) greatly influences what sectorReturns represents.

**getTopSectors(int count) / getBottomSectors(int count):** Ranks sectors by the values in sectorReturns.

**isOutperforming(Tick tick, String benchmarkSector) / isUnderperforming(Tick tick, String benchmarkSector):**

1. Compares the performance of the stock's sector to a benchmarkSector.
2. Returns true if the stock's sector return is >0.5% better (outperforming) or <0.5% worse (underperforming) than the benchmark.

**Key Considerations for Fine-Tuning:**

* **Nature of priceChange input:** Is it absolute, percentage? What period does it cover? This is fundamental.
* **Averaging Method:** The (old + new) / 2 method is simple. Consider if weighting by market cap or a different smoothing technique is needed.
* **Benchmark Choice:** The relevance of the benchmarkSector is key for comparison.
* **Performance Threshold (0.5%):** May need adjustment based on market conditions or desired sensitivity.

### SupportResistanceAnalyzer.java

Identifies support and resistance levels, primarily using the previous day's high and low. BreakoutSignalGenerator relies heavily on this to define the actual price levels that need to be broken for a breakout or breakdown signal to be generated. It's the core of the 'breakout' premise.

#### SupportResistanceAnalyzer Detailed Logic:

**Core Function:** Identifies and stores support and resistance (S/R) levels for stocks, primarily based on previous day's data.

**Initialization:** Requires an AppContext to fetch PreviousDayData.

**calculateDailyLevels(String symbol):**

1. Fetches PreviousDayData (previous high, low, close) for the symbol via AppContext.
2. Clears any existing S/R levels for that symbol before adding new daily ones.
3. If pdData.getPreviousHigh() is valid, it's added as a RESISTANCE level with LevelStrength.MODERATE and method "DailyHighLow".
4. If pdData.getPreviousLow() is valid, it's added as a SUPPORT level with LevelStrength.MODERATE and method "DailyHighLow".
5. Logic for using previous day's close is present but commented out.

**getLevels(String symbol):** Returns the list of stored SupportResistanceLevel objects for the symbol.

**clearLevels(String symbol) / clearAllLevels():** Utility methods to remove S/R levels.

**Key Considerations for Fine-Tuning:**

* Source of S/R Levels: Currently limited to Previous Day High/Low. Consider adding other types: Pivot Points, Fibonacci levels, moving averages, historical swing points, round numbers.
* LevelStrength: Currently assigned as MODERATE. More advanced logic could vary strength based on number of touches, volume at level, etc. BreakoutSignalGenerator doesn't currently use this strength in its logic.
* Static Levels: These are static for the day. Dynamic S/R levels (like intraday pivots or MAs) would require different update mechanisms.
* Data Dependency: Relies on accurate PreviousDayData from AppContext.
* Clearing Strategy: The current clear() in calculateDailyLevels is absolute for the symbol. If mixing S/R calculation methods, a more targeted clear might be needed.

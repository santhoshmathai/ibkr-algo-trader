# System Logic Explanation

This document details the logic of various components within the `com.ibkr.analysis`, `com.ibkr.risk`, and `com.ibkr.data` packages.

## Package: `com.ibkr.analysis`

### `MarketSentimentAnalyzer.java` Detailed Logic:

This class performs two main analyses: determining the **Opening Market Trend** and calculating a more general **Market Sentiment**.

**I. Initialization & Configuration:**
*   Takes `AppContext` (for previous day data), `symbolsToMonitor`, `openingObservationMinutes` (e.g., 5-15 mins after open), and `actualMarketOpenTime`.
*   Defines an `openingObservationEndTime` and a fixed 30-minute `openingAnalysisWindowEndTime` from market open.

**II. Opening Market Trend Logic (Calculated once after `openingObservationMinutes`):**
1.  `updateOpeningTickAnalysis(Tick tick)`:
    *   Records the open price for each symbol (`tick.getOpenPrice()` or LTP).
    *   During the observation period, it stores the `lastTradedPrice` for each symbol.
2.  `calculateDeterminedOpeningTrend()` (called after observation period ends):
    *   For each monitored stock, compares its `lastObservedPrice` to its `openPrice`.
    *   Stock is UP if `lastObservedPrice > openPrice * 1.001` (0.1% threshold).
    *   Stock is DOWN if `lastObservedPrice < openPrice * 0.999` (0.1% threshold).
    *   **Trend UP:** If >= 70% of stocks are UP.
    *   **Trend DOWN:** If >= 70% of stocks are DOWN.
    *   **Trend NEUTRAL:** Otherwise.
    *   Result stored in `determinedOpeningTrend`.
3.  `getDeterminedOpeningTrend()`: Returns the calculated opening trend.

**III. General Market Sentiment Logic (Calculated during the 30-minute `openingAnalysisWindowEndTime`):**
1.  `update(Tick tick)`:
    *   Initializes opening price for a symbol if not already done (uses LTP).
    *   Determines `direction` (1 for up, -1 for down, 0 for neutral) for the symbol:
        *   Primary: `direction = 1` if `Current > Open AND Current > PreviousDayClose`.
        *   Primary: `direction = -1` if `Current < Open AND Current < PreviousDayClose`.
        *   Fallback (if not strictly meeting above): `direction = 1` if `Current > Open`, or `-1` if `Current < Open`.
    *   Stores this `direction` in `tickerDirection` map.
2.  `getMarketSentiment()`:
    *   Counts UP and DOWN symbols from `tickerDirection`.
    *   **STRONG_UP:** If >= 60% of symbols are UP.
    *   **STRONG_DOWN:** If >= 60% of symbols are DOWN.
    *   **NEUTRAL:** Otherwise.

**IV. Key Considerations for Fine-Tuning:**
*   **Distinct Analyses:** Understand the difference between the very short-term 'Opening Trend' and the 30-min 'General Sentiment'.
*   **Opening Price Consistency:** Ensure the method of determining 'open price' is consistent and accurate for both analyses.
*   **Thresholds:** The 0.1% price move & 70% stock agreement for Opening Trend, and 60% stock agreement for General Sentiment are critical tuning parameters.
*   **Time Windows:** `openingObservationMinutes` and the 30-minute analysis window define the operational periods.
*   **Direction Logic:** The two-tiered direction logic (strict criteria then fallback) in general sentiment impacts how many stocks are classified as strictly up/down vs. neutral.

### `SectorStrengthAnalyzer.java` Detailed Logic:

**Core Function:** Calculates and compares the performance of different market sectors.

**Initialization:** Requires two maps:
1.  `sectorToStocks`: Defines which stocks belong to which sector.
2.  `symbolToSector`: Maps individual stock symbols to their sector.

**`updateSectorPerformance(String symbol, double priceChange)`:**
1.  Identifies the sector for the given `symbol`.
2.  Updates the sector's performance in `sectorReturns` using: `newReturn = (oldSectorReturn + stockPriceChange) / 2.0`. This is a simple rolling average.
3.  **Crucial:** The nature of `priceChange` (absolute, percentage, period of change) greatly influences what `sectorReturns` represents.

**`getTopSectors(int count)` / `getBottomSectors(int count)`:** Ranks sectors by the values in `sectorReturns`.

**`isOutperforming(Tick tick, String benchmarkSector)` / `isUnderperforming(Tick tick, String benchmarkSector)`:**
1.  Compares the performance of the stock's sector to a `benchmarkSector`.
2.  Returns true if the stock's sector return is >0.5% better (outperforming) or <0.5% worse (underperforming) than the benchmark.

**Key Considerations for Fine-Tuning:**
*   **Nature of `priceChange` input:** Is it absolute, percentage? What period does it cover? This is fundamental.
*   **Averaging Method:** The `(old + new) / 2` method is simple. Consider if weighting by market cap or a different smoothing technique is needed.
*   **Benchmark Choice:** The relevance of the `benchmarkSector` is key for comparison.
*   **Performance Threshold (0.5%):** May need adjustment based on market conditions or desired sensitivity.

### `SupportResistanceAnalyzer.java` Detailed Logic:

**Core Function:** Identifies and stores support and resistance (S/R) levels for stocks, primarily based on previous day's data.

**Initialization:** Requires an `AppContext` to fetch `PreviousDayData`.

**`calculateDailyLevels(String symbol)`:**
1.  Fetches `PreviousDayData` (previous high, low, close) for the `symbol` via `AppContext`.
2.  **Clears any existing S/R levels for that symbol** before adding new daily ones.
3.  If `pdData.getPreviousHigh()` is valid, it's added as a `RESISTANCE` level with `LevelStrength.MODERATE` and method "DailyHighLow".
4.  If `pdData.getPreviousLow()` is valid, it's added as a `SUPPORT` level with `LevelStrength.MODERATE` and method "DailyHighLow".
5.  Logic for using previous day's close is present but commented out.

**`getLevels(String symbol)`:** Returns the list of stored `SupportResistanceLevel` objects for the symbol.

**`clearLevels(String symbol)` / `clearAllLevels()`:** Utility methods to remove S/R levels.

**Key Considerations for Fine-Tuning:**
*   **Source of S/R Levels:** Currently limited to Previous Day High/Low. Consider adding other types: Pivot Points, Fibonacci levels, moving averages, historical swing points, round numbers.
*   **`LevelStrength`:** Currently assigned as `MODERATE`. More advanced logic could vary strength based on number of touches, volume at level, etc. `BreakoutSignalGenerator` doesn't currently use this strength in its logic.
*   **Static Levels:** These are static for the day. Dynamic S/R levels (like intraday pivots or MAs) would require different update mechanisms.
*   **Data Dependency:** Relies on accurate `PreviousDayData` from `AppContext`.
*   **Clearing Strategy:** The current `clear()` in `calculateDailyLevels` is absolute for the symbol. If mixing S/R calculation methods, a more targeted clear might be needed.

## Package: `com.ibkr.risk`

### `com.ibkr.risk.VolatilityAnalyzer.java` Detailed Logic:

**Core Function:** Calculates price volatility using the standard deviation of the last traded prices over a rolling window and checks if volatility is excessive.

**`getCurrentVolatility(Tick tick)`:**
1.  Adds the `tick.getLastTradedPrice()` to a `priceHistory` (a Deque of Doubles) with a fixed `lookbackPeriod` of 20.
2.  If `priceHistory` exceeds 20, removes the oldest price.
3.  Calls `calculateStandardDeviation()` on the prices in `priceHistory`.

**`calculateStandardDeviation()` (Private):**
1.  Calculates the mean (average) of prices in `priceHistory`.
2.  Calculates the variance (average of squared differences from the mean).
3.  Returns `Math.sqrt(variance)`.

**`isTooVolatile(Tick tick)`:**
1.  Calculates current volatility using `getCurrentVolatility(tick)`.
2.  Returns `true` if this volatility is `> (tick.getLastTradedPrice() * 0.05)` (i.e., if standard deviation is greater than 5% of the current price).

**Key Considerations for Fine-Tuning:**
*   **Lookback Period (20):** Adjust for sensitivity. Shorter = more reactive; longer = smoother.
*   **Volatility Threshold (5% of LTP):** This is a key risk parameter. Evaluate if 5% is appropriate for your strategy and instruments.
*   **Usage:** The `BreakoutSignalGenerator` constructor includes it, but its `isTooVolatile` method might be used by a higher-level system to pause trading during extreme volatility. The volatility for stops in `BreakoutSignalGenerator` comes from `position.getVolatilityAtEntry()`, which could be populated by this analyzer at trade entry time.
*   **Data Points (LTP only):** Uses only LTP. Consider ATR if a more comprehensive volatility measure (including gaps, highs/lows) is needed.

### `RiskManager.java` Detailed Logic:

**Core Function:** Centralizes pre-trade risk checks and dynamic position sizing, using `LiquidityMonitor` and `VolatilityAnalyzer`.

**Constructor:** Takes `LiquidityMonitor` and `VolatilityAnalyzer` as dependencies.

**`validateTrade(TradingSignal signal, Tick currentTick)`:**
1.  Calls `liquidityMonitor.isSpreadAcceptable(currentTick)`: Rejects trade if bid-ask spread is too wide.
2.  Calls `volatilityAnalyzer.isTooVolatile(currentTick)`: Rejects trade if price volatility is too high (e.g., std dev > 5% of LTP).
3.  Returns `true` if both checks pass (placeholder for more checks).

**`calculateMaxPositionSize(Tick tick)`:**
1.  `volatilityFactor = 1 / volatilityAnalyzer.getCurrentVolatility(tick)` (higher volatility = smaller factor).
2.  `liquidityFactor = liquidityMonitor.getLiquidityScore(tick)` (higher score = better liquidity).
3.  Returns `1000 * volatilityFactor * liquidityFactor` (base of 1000 units, adjusted by factors). **Caution:** Inverse volatility can lead to very large sizes if volatility is near zero.

**`validateShortSell(Tick tick)`:**
1.  Checks if `LTP > PreviousDayClose` (avoids shorting below previous close).
2.  Checks if `liquidityMonitor.getShortAvailability(tick) > 0.7` (e.g., >70% shares available to borrow).

**`getMaxShortPosition(Tick tick)`:** Returns `calculateMaxPositionSize(tick) * 0.8` (shorts are 80% of normal calculated size).

**Key Considerations for Fine-Tuning:**
*   **Completeness of `validateTrade`:** Add checks for max exposure, daily loss limits, etc.
*   **Position Sizing Formula:** The `1/volatility` can be sensitive. Consider caps or linking base size to % of capital. Reconcile with any sizing logic in signal generators.
*   **Short Selling Rules:** The `LTP > PDC` rule might be too restrictive for some strategies.
*   **Parameterization:** Hardcoded thresholds (0.7, 0.8, base 1000) should ideally be configurable.

### `LiquidityMonitor.java` Detailed Logic:

**Core Function:** Assesses instrument liquidity through spread, a general score, and short-selling availability.

**`isSpreadAcceptable(Tick tick)`:**
1.  Calculates `spread = tick.getTotalSellQuantity() - tick.getTotalBuyQuantity();` **This is an order book quantity imbalance, NOT a price spread.**
2.  Returns `true` if this quantity `spread < (tick.getLastTradedPrice() * 0.01)` (i.e., imbalance is less than 1% of LTP). **This comparison (shares vs. price) is dimensionally unusual and needs review.** A price spread would use `bestAsk - bestBid`.

**`getLiquidityScore(Tick tick)`:**
1.  `avgVolume = (tick.getTotalBuyQuantity() + tick.getTotalSellQuantity()) / 2` (average order book depth in shares).
2.  Returns `Math.min(1.0, avgVolume / 1000)`. Score is normalized against 1000 shares, capped at 1.0. Higher score = better liquidity.

**`getShortAvailability(Tick tick)`:**
1.  **Explicitly a MOCK IMPLEMENTATION.** States it should be replaced with actual shortable shares data.
2.  Current mock logic: `Math.min(1.0, 0.9 - ( (tick.getVolumeTradedToday() / 1000000.0) * 0.2) )`. This implies availability *decreases* with higher daily volume, which is likely not realistic.

**Key Considerations for Fine-Tuning:**
*   **`isSpreadAcceptable` Logic:** The current quantity-based spread and its comparison to price % is highly unconventional. If a price spread check is intended, this method needs a rewrite using best bid/ask prices.
*   **`getLiquidityScore` Normalization (1000 shares):** The 1000-share base is arbitrary and may not suit all instruments. Consider making it relative to average trade size or price.
*   **`getShortAvailability`:** **MUST BE REPLACED** with actual broker data for live short selling. The current mock logic is not reliable.
*   **Data Source:** Assumes `Tick` object contains relevant order book depth data (`getTotalBuyQuantity`, etc.).
*   **Parameterization:** Hardcoded thresholds (1%, 1000) should be configurable.

### `RiskEvaluator.java` Detailed Logic:

**Core Function:** This file defines an **interface**, not a concrete class. It specifies a contract for any class that validates trading signals based on risk.

**Interface Definition:**
```java
public interface RiskEvaluator {
    boolean validateTrade(TradingSignal signal);
}
```
*   Any class implementing `RiskEvaluator` must provide a `validateTrade` method.
*   This method takes a `TradingSignal` object as input.
*   It returns `true` if the trade is acceptable from a risk perspective, `false` otherwise.

**Usage:**
*   Concrete classes will implement this interface, each containing specific risk validation logic (e.g., checking exposure, loss limits, symbol-specific rules).
*   The system would then use instances of these implementing classes to validate trades.

**Key Considerations for Fine-Tuning:**
*   **Find Implementations:** The actual risk rules are in the classes that implement this interface. These need to be identified and reviewed.
*   **Information Scope:** Implementers of this specific interface can only use data available within the `TradingSignal` object for their validation, unless they access more context through other means.
*   The `RiskManager` class has a `validateTrade` method with a different signature (`TradingSignal signal, Tick currentTick`), suggesting it might not directly implement this specific interface or that multiple types of risk validation exist.

### `AdvancedRiskManager.java` Detailed Logic:

**Core Function:** Provides specialized risk checks, particularly for short selling and potential short squeezes. Uses an internal `VWAPAnalyzer`.

**Constructor:** Initializes its own internal `VWAPAnalyzer` instance.
**CRITICAL FLAW:** This internal `VWAPAnalyzer` is never explicitly updated with ticks within the `AdvancedRiskManager`'s methods. For its calculations to be meaningful, `this.vwap.update(tick)` must be called with the relevant instrument's ticks before `vwap.getVWAP()` is used.

**`validateShort(Tick tick)`:**
1.  Calculates `vwapDistance = (vwap.getVWAP() - LTP) / vwap.getVWAP()`.
2.  Returns `true` (short is okay) if `vwapDistance < 0.03` (i.e., price is not more than 3% below VWAP). Aims to prevent shorting a stock that is already significantly extended downwards from VWAP.

**`isShortSqueezeRisk(Tick tick)`:**
1.  Calculates `volumeRatio = tick.getLastTradedQuantity() / tick.getVolumeTradedToday()`.
    *   **LOGIC FLAW:** This ratio is problematic. `getLastTradedQuantity` (last trade/interval volume) divided by `getVolumeTradedToday` (cumulative daily volume) will almost always be very small. A `volumeRatio > 3.0` is mathematically highly improbable under normal interpretations of these fields.
2.  Returns `true` if `volumeRatio > 3.0` AND `LTP > OpenPrice`.
    *   The `LTP > OpenPrice` part is logical for a squeeze (price moving up).

**Key Considerations for Fine-Tuning:**
*   **VWAPAnalyzer Updates:** The internal `VWAPAnalyzer` **must** be updated with the correct tick stream for the instrument being checked. This is a major gap in the current code as presented.
*   **`isShortSqueezeRisk` Volume Logic:** The `volumeRatio` calculation is flawed and needs to be redesigned to accurately detect volume spikes indicative of a squeeze (e.g., by comparing to a moving average of volume, similar to `VolumeAnalyzer`).
*   **Thresholds (3% for VWAP, 3.0 for volume ratio):** Hardcoded; should be configurable.

## Package: `com.ibkr.data`

### `InstrumentRegistry.java` Detailed Logic:

**Core Function:** Manages mappings between financial instruments (as IB `Contract` objects), their string symbols, and unique integer `tickerId`s used for API communication (especially with Interactive Brokers).

**Constructor:** Takes an `AppContext` instance, which is used to obtain unique `tickerId`s via `appContext.getNextRequestId()`.

**Data Structures (ConcurrentHashMaps for thread safety):**
*   `tickerIdToContract`: Maps `tickerId` (int) to `Contract` object.
*   `tickerIdToSymbol`: Maps `tickerId` (int) to `symbol` (String).
*   `symbolToTickerId`: Maps `symbol` (String) to `tickerId` (int).

**`registerInstrument(Contract contract)` (synchronized method):**
1.  Extracts `symbol` from the `contract`.
2.  **Checks if `symbol` is already registered:** If yes, returns the existing `tickerId`.
3.  If new, gets a unique `tickerId` from `appContext.getNextRequestId()`.
4.  Stores the new `contract`, `symbol`, and `tickerId` in all three maps.
5.  Returns the `tickerId`.

**Getter Methods:**
*   `getSymbol(int tickerId)`: Returns symbol for a tickerId.
*   `getContract(int tickerId)`: Returns Contract object for a tickerId.
*   `getTickerId(String symbol)`: Returns tickerId for a symbol.

**Key Considerations for Fine-Tuning:**
*   **Uniqueness of `appContext.getNextRequestId()`:** Critical for proper IB API interaction.
*   **Contract Uniqueness for Registration:** Currently uses `contract.symbol()`. If greater specificity is needed (e.g., for options or multi-exchange listings of the same symbol), a more complex key or using the `Contract` object itself as a map key might be necessary.
*   **Null Handling:** Getter methods can return `null` if a mapping isn't found; calling code must handle this.
*   **Lifecycle:** This is an in-memory registry. Data is lost on application restart unless persistence is added.

### `MarketDataHandler.java` Detailed Logic:

**Core Function:** Asynchronously persists incoming market data (specifically `com.zerodhatech.models.Tick` objects) to daily CSV files, one file per symbol per day.

**Initialization & Configuration:**
*   `PERSIST_TO_DISK`: Flag to enable/disable writing (default `true`).
*   `DATA_DIRECTORY`: "market_data".
*   Uses a single-thread `ExecutorService` (`persistenceExecutor`) for asynchronous file writes.
*   Caches `BufferedWriter` instances per symbol in `symbolWriters` to avoid re-opening files.
*   Timestamps in CSV are formatted to `yyyy-MM-dd HH:mm:ss.SSS` in "IST" timezone (configurable).

**`persistTick(com.zerodhatech.models.Tick zerodhaTick)` (Public Method):**
1.  Checks `PERSIST_TO_DISK` and if tick/symbol is null.
2.  Delegates to `persistTickAsync` for non-blocking operation.

**`persistTickAsync` (Private Method):**
1.  Submits a task to `persistenceExecutor`.
2.  Task gets/creates a `BufferedWriter` for the symbol using `symbolWriters.computeIfAbsent(symbol, this::createWriterForSymbol)`.
3.  Converts tick to CSV string using `toCsvString(tick)` and writes it, followed by a newline.

**`createWriterForSymbol(String symbol)` (Private Method):**
1.  Generates filename: `DATA_DIRECTORY/SYMBOL_YYYYMMDD.csv`.
2.  Opens file in **append mode**. If file is new, writes a header row from `getCsvHeader()`.

**`toCsvString(com.zerodhatech.models.Tick tick)` (Private Method):**
1.  Constructs a comma-separated string from various fields of the `Tick` object (LTP, volume, OHLC from `tick.getOhlc()`, simplified market depth from `tick.getMarketDepth()`).
2.  Uses `escapeCsv` helper for strings containing commas, quotes, or newlines.

**`shutdown()` (Public Method):**
1.  Gracefully shuts down the `persistenceExecutor`.
2.  Closes all open `BufferedWriter` instances.

**Key Considerations for Fine-Tuning:**
*   **Tick Object Type:** Designed for `com.zerodhatech.models.Tick`. If using other data sources (e.g., IB's native objects), an adapter or different handler is needed.
*   **Timestamp Timezone:** Ensure "IST" is appropriate; UTC is often preferred for raw data storage.
*   **Error Handling:** Failure to create a writer in `createWriterForSymbol` results in silent data loss for that symbol for the day. Consider more robust error reporting or retries.
*   **Disk Space:** Tick-by-tick CSVs can be very large. Consider compression, binary formats, or data sampling.
*   **Shutdown Reliability:** Ensure `shutdown()` is called (e.g., via JVM shutdown hook) to prevent data loss.

### `TickAggregator.java` Detailed Logic:

**Core Function:** Receives raw Interactive Brokers (IB) market data (prices, sizes, real-time bars, depth) and aggregates it into a standardized `com.zerodhatech.models.Tick` object for each instrument. Also performs 1-minute OHLCV/VWAP aggregation.

**Initialization:** Takes an `InstrumentRegistry` to map IB `tickerId` to symbols.

**Main Data Structure:** `instrumentTicks` (Map<Integer, com.zerodhatech.models.Tick>) stores the latest aggregated `Tick` object for each `tickerId`.

**Key Processing Methods:**
*   **`getOrCreateTick(int instrumentToken)`:** Retrieves or creates a `Tick` object for the given `tickerId`.
*   **`setTickMode(Tick tick, Tick.Mode newMode)`:** Manages the `Tick.mode` (LTP, QUOTE, FULL) to indicate data richness, preventing downgrades.
*   **`processTickPrice(tickerId, field, price)` & `processTickSize(tickerId, field, size)`:** Handle discrete field updates from IB (e.g., BID_PRICE, LAST_PRICE, VOLUME) and map them to the `Tick` object. Update `Tick.mode`.
*   **`processTickByTickAllLast(...)` & `processTickByTickBidAsk(...)`:** Handle IB's tick-by-tick data streams for last trades and bid/ask updates, populating the `Tick` object.
*   **`processRealTimeBar(tickerId, timeSeconds, o, h, l, c, vol, wap, count)`:**
    1.  Receives 5-second bars from IB.
    2.  Aggregates these into 1-minute OHLC data stored in `currentMinuteOhlc`.
    3.  Calculates 1-minute VWAP using `wap` (VWAP of the 5s bar) and `volume`, stored in `currentMinuteWapVolumeSum`.
    4.  When a minute completes, the aggregated 1-min OHLC is set in `tick.setOhlc()`. The 1-min VWAP is set in `tick.setAveragePrice()` *if a daily VWAP isn't already present*.
    5.  The main `Tick` object's LTP, volume, etc., are also updated with the latest 5s bar data.
*   **`processMarketDepth(tickerId, position, operation, side, price, size)`:**
    1.  Manages full market depth (up to 20 levels) in internal lists (`currentBidDepthLevels`, `currentAskDepthLevels`).
    2.  Updates these lists based on IB depth operations (Insert, Update, Delete).
    3.  Populates the `tick.setMarketDepth()` field with the **top 5 levels** of bid and ask depth in the `com.zerodhatech.models.Depth` format.
    4.  Sets `Tick.mode` to `FULL` if depth data is present.

**Key Considerations for Fine-Tuning:**
*   **Data Model Mapping:** The class translates IB data to `com.zerodhatech.models.Tick`. Ensure this mapping is accurate and complete for your needs.
*   **Timestamping:** Uses system time for some updates in `processTickPrice/Size`. Prefer exchange-provided timestamps (as in `processTickByTickAllLast`) for accuracy.
*   **1-Min VWAP vs. Daily VWAP:** Logic correctly prioritizes an actual daily VWAP feed over calculated 1-min VWAP for `tick.averagePrice`.
*   **Market Depth:** Stores up to 20 levels but only exposes top 5 in the `Tick` object. Adjust if strategies need deeper views.
*   **IB Tick Types:** Ensure all necessary IB `TickType` fields are handled by `processTickPrice` and `processTickSize` if your strategy depends on them.

## Stock Data Handling for OrbStrategy and Role of `com.ibkr.data`

The `OrbStrategy` requires specific data inputs (1-minute OHLC bars, Previous Day High, live market depth, and volume information) to make its decisions. This data is sourced and processed through several components, with the `com.ibkr.data` package playing a key role, particularly the `TickAggregator`.

**Data Flow for `OrbStrategy` (via `TradingEngine`):**

1.  **1-Minute OHLC Bars (`OHLC newBar`) and `barTimestamp`:**
    *   **Source:** These are expected to be generated by the `com.ibkr.data.TickAggregator`.
    *   **Mechanism:** `TickAggregator.processRealTimeBar` method receives 5-second bars from Interactive Brokers and internally aggregates them into 1-minute OHLC data. This 1-minute OHLC data (along with its starting timestamp) is then made available (e.g., by being stored in the `com.zerodhatech.models.Tick` object's `ohlc` field or through an event/callback).
    *   `TradingEngine.onOneMinuteBarClose` receives this 1-minute bar and its timestamp to pass to `OrbStrategy`.

2.  **Previous Day High (`previousDayHigh`):**
    *   **Source:** This data is typically fetched once at the start of the trading day.
    *   **Mechanism:** The `IBClient` (using `reqHistoricalData` for the previous day's daily bar) would fetch this. It would then likely be stored in `AppContext.previousDayDataMap`.
    *   `TradingEngine.initializeStrategyForSymbol` retrieves this from `AppContext` (or is passed it) and calls `orbStrategy.setPreviousDayHigh()` to provide it to the strategy state.

3.  **Market Depth (`List<Depth> bidDepth`, `List<Depth> askDepth`):**
    *   **Source:** Live Level II market depth data from Interactive Brokers.
    *   **Mechanism:** `com.ibkr.data.TickAggregator.processMarketDepth` receives raw depth updates from IB. It maintains internal lists of bid/ask depth levels (up to 20 levels).
    *   It then populates the `marketDepth` field of the `com.zerodhatech.models.Tick` object with the top 5 levels of bid and ask depth.
    *   `TradingEngine.onOneMinuteBarClose` retrieves the `latestTickForDepth` (which is a `com.zerodhatech.models.Tick` object managed by `TickAggregator`) and extracts these top 5 bid/ask depth lists to pass to `OrbStrategy`.

4.  **Volume Data (`OrbStrategy.VolumeData` - `currentCandleVolume`, `averageVolumeLastN`):**
    *   `currentCandleVolume`: This is the volume of the 1-minute `newBar` itself, sourced from `newBar.getVolume()`. This volume is part of the 1-minute aggregation done by `TickAggregator`.
    *   `averageVolumeLastN`: This is calculated by `TradingEngine.calculateAverageVolume` method. This method uses a history of recent 1-minute bars (`oneMinuteBarsHistory` stored in `TradingEngine`), which are themselves the `OHLC` objects originating from `TickAggregator`'s 1-minute aggregation.

**Roles of `com.ibkr.data` Classes:**

*   **`com.ibkr.data.TickAggregator`:**
    *   **Central and Critical Role:** This class is the primary processor of raw IB market data.
    *   It aggregates various IB data types (tick-by-tick, real-time bars, depth updates) into the standardized `com.zerodhatech.models.Tick` object.
    *   Crucially for `OrbStrategy`, it performs the 1-minute OHLCV aggregation (from 5-second bars) and structures the market depth data.
    *   The `TradingEngine` heavily relies on the output of `TickAggregator` (the `Tick` objects and the bars derived from them) to feed `OrbStrategy`.

*   **`com.ibkr.data.InstrumentRegistry`:**
    *   **Supportive Role:** Manages the mapping between IB `Contract` objects, string symbols, and internal `tickerId`s.
    *   While `OrbStrategy` primarily deals with symbol strings, `InstrumentRegistry` is vital for the underlying system (`TickAggregator`, `IBClient`) to correctly associate incoming data streams with the right instruments and `Tick` objects.

*   **`com.ibkr.data.MarketDataHandler`:**
    *   **Archival Role:** Responsible for persisting `com.zerodhatech.models.Tick` data to CSV files for offline analysis or logging.
    *   It does **not** serve as a real-time data source for `OrbStrategy`'s decision-making process. Its operation is asynchronous and for storage purposes.

In essence, `TickAggregator` is the workhorse that prepares and structures the raw market data into a format (1-minute bars, consolidated tick objects with depth) that `TradingEngine` can then use to supply the `OrbStrategy` with the specific inputs it needs.

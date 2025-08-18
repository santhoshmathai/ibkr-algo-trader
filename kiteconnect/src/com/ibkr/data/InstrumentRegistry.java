package com.ibkr.data;

import com.ib.client.Contract;
import com.ibkr.AppContext; // Added for AppContext
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class InstrumentRegistry {
    private final Map<Integer, Contract> tickerIdToContract = new ConcurrentHashMap<>();
    private final Map<Integer, String> tickerIdToSymbol = new ConcurrentHashMap<>();
    private final Map<String, Integer> symbolToTickerId = new ConcurrentHashMap<>();
    private final AppContext appContext; // Added

    public InstrumentRegistry(AppContext appContext) { // Added constructor
        this.appContext = appContext;
    }

    public synchronized int registerInstrument(Contract contract) {
        String symbol = contract.symbol();
        if (symbolToTickerId.containsKey(symbol)) {
            return symbolToTickerId.get(symbol);
        }

        int tickerId = appContext.getNextRequestId(); // Changed
        tickerIdToContract.put(tickerId, contract);
        tickerIdToSymbol.put(tickerId, symbol);
        symbolToTickerId.put(symbol, tickerId);
        return tickerId;
    }

    public String getSymbol(int tickerId) {
        return tickerIdToSymbol.get(tickerId);
    }

    public Contract getContract(int tickerId) {
        return tickerIdToContract.get(tickerId);
    }

    public Integer getTickerId(String symbol) {
        return symbolToTickerId.get(symbol);
    }

    public Contract getContractBySymbol(String symbol) {
        Integer tickerId = getTickerId(symbol);
        if (tickerId != null) {
            return getContract(tickerId);
        }
        return null;
    }

    /**
     * Returns an unmodifiable set of all registered symbols.
     *
     * @return A set of all symbol strings.
     */
    public Set<String> getAllSymbols() {
        return Collections.unmodifiableSet(symbolToTickerId.keySet());
    }
}
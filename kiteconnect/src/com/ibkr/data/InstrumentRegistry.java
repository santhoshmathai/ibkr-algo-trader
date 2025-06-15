package com.ibkr.data;

import com.ib.client.Contract;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InstrumentRegistry {
    private final Map<Integer, Contract> tickerIdToContract = new ConcurrentHashMap<>();
    private final Map<Integer, String> tickerIdToSymbol = new ConcurrentHashMap<>();
    private final Map<String, Integer> symbolToTickerId = new ConcurrentHashMap<>();
    private int nextTickerId = 1000;

    public synchronized int registerInstrument(Contract contract) {
        String symbol = contract.symbol();
        if (symbolToTickerId.containsKey(symbol)) {
            return symbolToTickerId.get(symbol);
        }

        int tickerId = nextTickerId++;
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
}
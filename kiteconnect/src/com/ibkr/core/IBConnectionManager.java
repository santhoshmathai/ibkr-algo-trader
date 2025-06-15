package com.ibkr.core;

import com.ib.client.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IBConnectionManager {
    private final EClientSocket clientSocket;
    private final EReaderSignal signal;
    private final ExecutorService executor;
    private boolean connected = false;

    public IBConnectionManager(EWrapper wrapper) {
        this.signal = new EJavaSignal();
        this.clientSocket = new EClientSocket(wrapper, signal);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public synchronized void connect(String host, int port, int clientId) {
        if (!connected) {
            clientSocket.eConnect(host, port, clientId);
            if (clientSocket.isConnected()) {
                connected = true;
                startMessageProcessing();
            }
        }
    }

    private void startMessageProcessing() {
        final EReader reader = new EReader(clientSocket, signal);
        reader.start();

        executor.execute(() -> {
            while (connected) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    System.err.println("Error processing messages: " + e.getMessage() + e.getClass());
                }
            }
        });
    }

    public synchronized void disconnect() {
        if (connected) {
            clientSocket.eDisconnect();
            connected = false;
            executor.shutdown();
        }
    }

    public EClientSocket getClientSocket() {
        return clientSocket;
    }

    public boolean isConnected() {
        return connected;
    }
}
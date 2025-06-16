package com.ibkr.core;

import com.ib.client.EClientSocket;
// EReaderSignal, ExecutorService, Executors, EWrapper are no longer needed here directly
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added

public class IBConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(IBConnectionManager.class); // Added
    private final EClientSocket clientSocket;
    // private final EReaderSignal signal; // Removed
    // private final ExecutorService executor; // Removed
    private boolean connected = false; // This status might still be useful locally or could be derived from clientSocket.isConnected()

    public IBConnectionManager(EClientSocket clientSocket) { // Modified constructor
        this.clientSocket = clientSocket;
    }

    public synchronized void connect(String host, int port, int clientId) {
        if (!clientSocket.isConnected()) { // Check actual socket status
            logger.info("Connecting to {}:{} with clientId: {}", host, port, clientId);
            clientSocket.eConnect(host, port, clientId);
            // Connection status will be updated via EWrapper callbacks (connectAck, error)
            // No need to manage 'connected' state here explicitly if IBClient handles it via EWrapper
            // For simplicity, we can assume eConnect is synchronous enough for this check, or rely on EWrapper.
            if (clientSocket.isConnected()) { // Re-check after attempting connect
                 logger.info("Successfully connected (according to isConnected()). Awaiting connectAck.");
                 this.connected = true; // Local status
            } else {
                 logger.warn("Connection attempt made, but isConnected() is false. Check TWS logs and ensure EWrapper.connectAck is received.");
            }
        } else {
            logger.info("Already connected or connection attempt in progress.");
        }
    }

    // Removed startMessageProcessing() as it's handled by IBClient

    public synchronized void disconnect() {
        if (clientSocket.isConnected()) {
            logger.info("Disconnecting...");
            clientSocket.eDisconnect();
            // Connection status will be updated via EWrapper.connectionClosed()
            this.connected = false; // Local status
            logger.info("Disconnected.");
        } else {
            logger.info("Already disconnected.");
        }
    }

    public EClientSocket getClientSocket() { // This might not be needed if IBClient manages its own socket
        return clientSocket;
    }

    public boolean isConnected() {
        return connected;
    }
}
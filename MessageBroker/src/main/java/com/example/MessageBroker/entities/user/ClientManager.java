package com.example.MessageBroker.entities.user;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages client connections and heartbeat monitoring in the Message Broker system.
 * This class provides thread-safe client tracking and implements a robust heartbeat
 * mechanism to detect and handle disconnected clients.
 *
 * Key features:
 * - Thread-safe client tracking using ConcurrentHashMap
 * - Periodic heartbeat checks for all connected clients
 * - Automatic detection and removal of disconnected clients
 * - Configurable heartbeat intervals and failure thresholds
 * - Graceful handling of client disconnections
 */
public class ClientManager {
    /** Map of active client IDs to their socket connections */
    private static final ConcurrentHashMap<Long, Socket> activeClients = new ConcurrentHashMap<>();

    /** Map of client IDs to their last heartbeat timestamp */
    private static final ConcurrentHashMap<Long, Long> lastHeartbeatTime = new ConcurrentHashMap<>();

    /** Interval between heartbeat checks in seconds */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;

    /** Maximum time a client can be inactive before being considered disconnected */
    private static final int CLIENT_TIMEOUT_SECONDS = 30;

    /** Flag indicating if the heartbeat service is currently running */
    private static boolean heartbeatServiceRunning = false;

    /** Scheduler for periodic heartbeat checks */
    private static ScheduledExecutorService heartbeatScheduler;

    /** Map tracking failed heartbeat attempts for each client */
    private static final ConcurrentHashMap<Long, Integer> failedHeartbeats = new ConcurrentHashMap<>();

    /** Maximum number of missed heartbeats before removing a client */
    private static final int MAX_MISSED_HEARTBEATS = 3;

    /**
     * Adds a new client to the active clients map and initializes heartbeat tracking.
     * Also starts the heartbeat service if it's not already running.
     *
     * @param clientId The unique identifier of the client
     * @param socket The socket connection to the client
     */
    public static void addClient(Long clientId, Socket socket) {
        activeClients.put(clientId, socket);
        lastHeartbeatTime.put(clientId, System.currentTimeMillis());
        failedHeartbeats.remove(clientId); // Reset failed heartbeats counter

        // Start heartbeat service if not already running
        startHeartbeatService();
    }

    /**
     * Retrieves the socket connection for a specific client.
     *
     * @param clientId The ID of the client
     * @return The socket connection if the client is active, null otherwise
     */
    public static Socket getClientSocket(Long clientId) {
        return activeClients.get(clientId);
    }

    /**
     * Removes a client from all tracking maps and closes their socket connection.
     *
     * @param clientId The ID of the client to remove
     */
    public static void removeClient(Long clientId) {
        Socket socket = activeClients.remove(clientId);
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket for client " + clientId + ": " + e.getMessage());
            }
        }
        lastHeartbeatTime.remove(clientId);
        failedHeartbeats.remove(clientId);
    }

    /**
     * Prints all active client connections to the console.
     * Used for debugging and monitoring purposes.
     */
    public static void getHashMap() {
        activeClients.forEach((k, v) -> System.out.println(k + " -> " + v));
    }

    /**
     * Retrieves the ID of the first active client in the map.
     *
     * @return The ID of the first client, or null if no clients are active
     */
    public static Long getFirst() {
        if (activeClients.isEmpty()) {
            return null;
        }
        Map.Entry<Long, Socket> entry = activeClients.entrySet().iterator().next();
        return entry.getKey();
    }

    /**
     * Gets the current count of active clients.
     *
     * @return The number of currently connected clients
     */
    public static int getActiveClientCount() {
        return activeClients.size();
    }

    /**
     * Updates the last heartbeat time for a client.
     * This method is called whenever any activity is received from the client.
     *
     * @param clientId The ID of the client whose heartbeat should be updated
     */
    public static void updateClientHeartbeat(Long clientId) {
        if (activeClients.containsKey(clientId)) {
            lastHeartbeatTime.put(clientId, System.currentTimeMillis());
            failedHeartbeats.remove(clientId); // Reset failed heartbeats when we get activity
            System.out.println("Updated heartbeat time for client: " + clientId);
        }
    }

    /**
     * Starts the heartbeat service if it's not already running.
     * The service periodically checks all connected clients for activity
     * and removes those that haven't responded.
     */
    private static synchronized void startHeartbeatService() {
        if (heartbeatServiceRunning) {
            return;
        }

        heartbeatServiceRunning = true;
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            pingAllClients();
            checkForDisconnectedClients();
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        System.out.println("Started client heartbeat service. Checking clients every "
                + HEARTBEAT_INTERVAL_SECONDS + " seconds");
    }

    /**
     * Stops the heartbeat service and releases its resources.
     * This method should be called when shutting down the server.
     */
    public static synchronized void stopHeartbeatService() {
        if (!heartbeatServiceRunning) {
            return;
        }

        heartbeatServiceRunning = false;
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        System.out.println("Stopped client heartbeat service");
    }

    /**
     * Sends a PING message to all connected clients to check their status.
     * Clients that fail to respond will be marked for potential removal.
     */
    private static void pingAllClients() {
        if (activeClients.isEmpty()) {
            return;
        }

        System.out.println("Pinging " + activeClients.size() + " active clients...");

        Set<Map.Entry<Long, Socket>> clientEntries = activeClients.entrySet();
        for (Map.Entry<Long, Socket> entry : clientEntries) {
            Long clientId = entry.getKey();
            Socket socket = entry.getValue();

            if (socket.isClosed() || !socket.isConnected()) {
                markClientForRemoval(clientId);
                continue;
            }

            try {
                // Send ping message
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("PING");

                // We don't wait for response here - client should respond with PONG
                // UserHandler will call updateClientHeartbeat when response received

                // Track failed pings
                if (!failedHeartbeats.containsKey(clientId)) {
                    failedHeartbeats.put(clientId, 1);
                } else {
                    failedHeartbeats.put(clientId, failedHeartbeats.get(clientId) + 1);
                }
            } catch (IOException e) {
                System.out.println("Failed to ping client " + clientId + ": " + e.getMessage());
                markClientForRemoval(clientId);
            }
        }
    }

    /**
     * Marks a client for potential removal due to connection issues.
     * A client is removed after failing the maximum number of heartbeat checks.
     *
     * @param clientId The ID of the client to mark for removal
     */
    private static void markClientForRemoval(Long clientId) {
        int failCount = failedHeartbeats.getOrDefault(clientId, 0) + 1;
        failedHeartbeats.put(clientId, failCount);

        if (failCount >= MAX_MISSED_HEARTBEATS) {
            System.out.println("Client " + clientId + " failed " + failCount +
                    " heartbeats - removing from active clients");
            removeClient(clientId);
        } else {
            System.out.println("Client " + clientId + " failed heartbeat " + failCount +
                    " of " + MAX_MISSED_HEARTBEATS + " allowed");
        }
    }

    /**
     * Checks for clients that haven't had a heartbeat in too long.
     * Clients exceeding the timeout period are removed from the active clients list.
     */
    private static void checkForDisconnectedClients() {
        if (lastHeartbeatTime.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        Set<Map.Entry<Long, Long>> heartbeatEntries = lastHeartbeatTime.entrySet();

        for (Map.Entry<Long, Long> entry : heartbeatEntries) {
            Long clientId = entry.getKey();
            Long lastHeartbeat = entry.getValue();

            long timeSinceHeartbeat = (currentTime - lastHeartbeat) / 1000;

            if (timeSinceHeartbeat > CLIENT_TIMEOUT_SECONDS) {
                System.out.println("Client " + clientId + " hasn't responded in " +
                        timeSinceHeartbeat + " seconds - removing");
                removeClient(clientId);
            }
        }
    }
}
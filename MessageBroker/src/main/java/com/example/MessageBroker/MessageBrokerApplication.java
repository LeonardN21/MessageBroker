package com.example.MessageBroker;

import com.example.MessageBroker.entities.user.ClientManager;
import com.example.MessageBroker.entities.user.UserHandler;
import com.example.MessageBroker.utilities.ClusterManager;
import com.example.MessageBroker.utilities.DatabaseService;
import com.example.MessageBroker.utilities.MessageRetryService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main application class for the Message Broker system.
 * This class initializes and manages the core components of the message broker:
 * - Server socket for client connections
 * - Thread pool for handling client connections
 * - Database service for persistent storage
 * - Cluster manager for distributed operation
 * - Message retry service for reliable message delivery
 *
 * The application supports both standalone and clustered operation modes,
 * with configurable parameters for message delivery reliability and clustering.
 */
public class MessageBrokerApplication {

    /** Default port for the server socket */
    private static final int DEFAULT_PORT = 8080;

    /** Default maximum number of delivery attempts for messages */
    private static final int DEFAULT_MAX_DELIVERY_ATTEMPTS = 3;

    /** Default interval in seconds between message redelivery attempts */
    private static final int DEFAULT_REDELIVERY_INTERVAL_SECONDS = 15;

    /** Default interval in seconds between checking for failed messages */
    private static final int DEFAULT_CHECK_INTERVAL_SECONDS = 30;

    /** Thread pool for handling client connections */
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    /** Service for database operations */
    private static final DatabaseService DATABASE_SERVICE = new DatabaseService();

    /** Application start time for uptime tracking */
    private static final Instant startTime = Instant.now();

    /** Manager for cluster operations */
    private ClusterManager clusterManager;

    /** Service for retrying failed message deliveries */
    private MessageRetryService messageRetryService;

    /** Port number the server is listening on */
    private int serverPort;

    /** Whether clustering is enabled */
    private boolean enableClustering = true;

    /** Maximum number of delivery attempts for messages */
    private int maxDeliveryAttempts = DEFAULT_MAX_DELIVERY_ATTEMPTS;

    /** Interval in seconds between message redelivery attempts */
    private int redeliveryIntervalSeconds = DEFAULT_REDELIVERY_INTERVAL_SECONDS;

    /** Interval in seconds between checking for failed messages */
    private int checkIntervalSeconds = DEFAULT_CHECK_INTERVAL_SECONDS;

    /**
     * Main entry point for the Message Broker application.
     * Parses command line arguments and initializes the broker with the specified configuration.
     *
     * Command line arguments:
     * --port &lt;number&gt;              Server port (default: 8080)
     * --no-cluster                    Disable clustering
     * --max-delivery-attempts &lt;number&gt; Maximum delivery attempts (default: 3)
     * --redelivery-interval &lt;number&gt;   Redelivery interval in seconds (default: 15)
     * --check-interval &lt;number&gt;       Check interval in seconds (default: 30)
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean clustering = true;
        int maxAttempts = DEFAULT_MAX_DELIVERY_ATTEMPTS;
        int redeliveryInterval = DEFAULT_REDELIVERY_INTERVAL_SECONDS;
        int checkInterval = DEFAULT_CHECK_INTERVAL_SECONDS;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                    i++;  // Skip the next arg since we consumed it
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number: " + args[i + 1]);
                }
            } else if (args[i].equals("--no-cluster")) {
                clustering = false;
            } else if (args[i].equals("--max-delivery-attempts") && i + 1 < args.length) {
                try {
                    maxAttempts = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid max delivery attempts: " + args[i + 1]);
                }
            } else if (args[i].equals("--redelivery-interval") && i + 1 < args.length) {
                try {
                    redeliveryInterval = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid redelivery interval: " + args[i + 1]);
                }
            } else if (args[i].equals("--check-interval") && i + 1 < args.length) {
                try {
                    checkInterval = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid check interval: " + args[i + 1]);
                }
            }
        }

        MessageBrokerApplication app = new MessageBrokerApplication();
        app.serverPort = port;
        app.enableClustering = clustering;
        app.maxDeliveryAttempts = maxAttempts;
        app.redeliveryIntervalSeconds = redeliveryInterval;
        app.checkIntervalSeconds = checkInterval;

        DATABASE_SERVICE.initBrokerConfig();

        System.out.println("Message Broker initialized. Starting server on port " + port + "...");
        if (clustering) {
            System.out.println("Clustering enabled - this node will discover and coordinate with other nodes");
        } else {
            System.out.println("Clustering disabled - this node will operate in standalone mode");
        }

        System.out.println("Message reliability: max attempts=" + maxAttempts +
                ", redelivery interval=" + redeliveryInterval + "s, check interval=" + checkInterval + "s");

        app.startServer();
    }

    /**
     * Starts the Message Broker server.
     * This method:
     * 1. Initializes clustering if enabled
     * 2. Starts the message retry service
     * 3. Binds to the configured port (or finds an available port)
     * 4. Starts accepting client connections
     * 5. Handles client connections using the thread pool
     *
     * The server will continue running until it encounters a fatal error
     * or is shut down externally. When shutting down, it will:
     * - Stop the cluster manager
     * - Stop the message retry service
     * - Stop the client heartbeat service
     * - Shutdown the thread pool
     */
    public void startServer() {
        try {
            // Initialize clustering if enabled
            if (enableClustering) {
                try {
                    clusterManager = new ClusterManager(serverPort, DATABASE_SERVICE);
                    clusterManager.start();
                    System.out.println("Cluster manager started. Looking for other nodes...");
                } catch (IOException e) {
                    System.err.println("Error initializing cluster manager: " + e.getMessage());
                    System.out.println("Continuing in standalone mode");
                    enableClustering = false;
                }
            }

            // Initialize and start the event queue processor
            messageRetryService = new MessageRetryService(
                    DATABASE_SERVICE,
                    maxDeliveryAttempts,
                    redeliveryIntervalSeconds,
                    checkIntervalSeconds
            );
            messageRetryService.start();

            // Try to bind to the requested port, if fails try alternative ports
            ServerSocket serverSocket = null;
            int attemptedPort = serverPort;
            int maxPortAttempts = 10; // Try up to 10 ports starting from serverPort

            for (int attempt = 0; attempt < maxPortAttempts; attempt++) {
                try {
                    serverSocket = new ServerSocket(attemptedPort);
                    System.out.println("Message Broker Server running on port " + attemptedPort);
                    // Successfully bound to a port
                    break;
                } catch (IOException e) {
                    if (attempt == 0) {
                        System.out.println("Port " + attemptedPort + " is already in use.");
                    }
                    attemptedPort++;
                    System.out.println("Trying alternative port: " + attemptedPort);
                }
            }

            // If we couldn't bind to any port
            if (serverSocket == null) {
                System.err.println("Failed to bind to any port. Exiting.");
                System.exit(1);
            }

            // If we're using a different port than originally requested, update the cluster manager
            if (attemptedPort != serverPort) {
                serverPort = attemptedPort;
                // If we have a cluster manager, update its port
                if (clusterManager != null) {
                    clusterManager.updateLocalPort(serverPort);
                }
            }

            startClientCountUpdater();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected at port: " + clientSocket.getPort());
                UserHandler userHandler = new UserHandler(clientSocket, DATABASE_SERVICE, clusterManager);
                pool.submit(userHandler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown the cluster manager if it was started
            if (clusterManager != null) {
                clusterManager.shutdown();
            }

            // Shutdown the message retry service
            if (messageRetryService != null) {
                messageRetryService.stop();
            }

            // Shutdown the client heartbeat service
            ClientManager.stopHeartbeatService();

            // Shutdown the thread pool
            pool.shutdown();
        }
    }

    /**
     * Starts a background thread that periodically updates the client count
     * in the database. This count is used for monitoring and statistics.
     * The thread runs every 5 seconds and updates the count of currently
     * connected clients in the broker_config table.
     */
    private void startClientCountUpdater() {
        new Thread(() -> {
            while (true) {
                try {
                    int clientCount = ClientManager.getActiveClientCount();
                    DATABASE_SERVICE.updateClientCount(clientCount);

                    // If clustering is enabled, update the node info to show this node is active
                    if (enableClustering && clusterManager != null) {
                        DATABASE_SERVICE.updateNodeHeartbeat(clusterManager.getNodeId());
                    }

                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error updating client count: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Get the server uptime in seconds
     */
    public static long getUptimeSeconds() {
        return Instant.now().getEpochSecond() - startTime.getEpochSecond();
    }
}

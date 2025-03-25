package com.example.MessageBroker.entities.user;

import com.example.MessageBroker.entities.event.Event;
import com.example.MessageBroker.entities.event.EventType;
import com.example.MessageBroker.entities.subscription.Subscription;
import com.example.MessageBroker.utilities.ClusterManager;
import com.example.MessageBroker.utilities.DatabaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Handles communication with connected clients in the message broker system.
 * This class is responsible for processing client messages, managing subscriptions,
 * delivering events, and maintaining client state.
 *
 * It uses a command pattern approach to handle different types of client messages,
 * which provides better maintainability and extensibility compared to traditional
 * if-else chains.
 */
public class UserHandler implements Runnable {

    /** Socket connection to the client */
    private final Socket clientSocket;

    /** Service for database interactions */
    private final DatabaseService databaseService;

    /** Manager for cluster operations in distributed mode */
    private final ClusterManager clusterManager;

    /** ID of the current user connected to this handler */
    private Long currentUserId;

    /** Jackson ObjectMapper for JSON serialization/deserialization */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Functional interface for message handlers.
     * Each handler processes a specific type of client message.
     */
    private interface MessageHandler {
        /**
         * Handles a client message.
         *
         * @param message The client message to process
         * @param writer PrintWriter for sending responses to the client
         */
        void handle(String message, PrintWriter writer);
    }

    /**
     * Map of message predicates to their handlers.
     * Using LinkedHashMap ensures handlers are evaluated in insertion order.
     */
    private final Map<Predicate<String>, MessageHandler> messageHandlers = new LinkedHashMap<>();

    /**
     * Creates a new UserHandler instance.
     *
     * @param clientSocket Socket connection to the client
     * @param databaseService Service for database interactions
     * @param clusterManager Manager for cluster operations
     */
    public UserHandler(Socket clientSocket, DatabaseService databaseService, ClusterManager clusterManager) {
        this.clientSocket = clientSocket;
        this.databaseService = databaseService;
        this.clusterManager = clusterManager;

        // Initialize command handlers
        initializeMessageHandlers();
    }

    /**
     * Initializes the map of message handlers for different command types.
     * Order matters - handlers are evaluated in the order they are added.
     */
    private void initializeMessageHandlers() {
        // Exact matches
        addHandler(msg -> msg.equals("PONG"), (msg, writer) -> {/* No action for heartbeat */});
        addHandler(msg -> msg.equals("PING"), (msg, writer) -> writer.println("PONG"));

        // Prefix-based handlers (startsWith)
        addHandler(msg -> msg.startsWith("LOGOUT"), this::handleLogout);
        addHandler(msg -> msg.startsWith("SUBSCRIBE"), this::subscribeToEventType);
        addHandler(msg -> msg.startsWith("UNSUBSCRIBE"), this::unsubscribeFromEventType);
        addHandler(msg -> msg.startsWith("GET SUBSCRIBED EVENT TYPES"), this::sendSubscribedEventTypes);
        addHandler(msg -> msg.startsWith("ACK:"), this::processMessageAcknowledgment);

        // Keyword-based handlers (contains) - order matters for overlapping patterns
        addHandler(msg -> msg.contains("CREATE EVENT TYPE"), this::createEventType);
        addHandler(msg -> msg.contains("CREATE EVENT"), this::createEvent);
        addHandler(msg -> msg.contains("GET EVENT TYPE LIST"), (msg, writer) -> sendEventTypeList(writer));
        addHandler(msg -> msg.contains("GET SYSTEM STATUS"), (msg, writer) -> sendSystemStatus(writer));
        addHandler(msg -> msg.contains("CLIENTID"), this::handleClientId);
        addHandler(msg -> msg.contains("REGISTER"), this::validateMessageForRegister);
    }

    /**
     * Helper method to add a handler to the messageHandlers map.
     *
     * @param predicate Condition to match the message
     * @param handler Handler to process the message
     */
    private void addHandler(Predicate<String> predicate, MessageHandler handler) {
        messageHandlers.put(predicate, handler);
    }

    /**
     * Special handler for client ID that handles both saving ID and delivering pending messages.
     *
     * @param message The client ID message
     * @param writer PrintWriter for sending responses
     */
    private void handleClientId(String message, PrintWriter writer) {
        saveUserId(message, writer);
        deliverPendingMessages(writer);
    }

    /**
     * Main execution method for the handler thread.
     * Reads client messages, updates heartbeats, and processes messages
     * until the connection is closed.
     */
    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("Received from client: " + message);

                // Update client heartbeat on any activity
                if (currentUserId != null) {
                    ClientManager.updateClientHeartbeat(currentUserId);
                }

                // Process the message using the message handlers
                processClientMessage(message, writer);
            }
        } catch (SocketException e) {
            System.out.println("Client disconnected: " + clientSocket.getPort());
            handleClientDisconnect();
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            handleClientDisconnect();
        }
    }

    /**
     * Process a client message by finding and executing the appropriate handler.
     *
     * @param message The message received from client
     * @param writer PrintWriter for sending responses
     */
    private void processClientMessage(String message, PrintWriter writer) {
        // Find a matching handler for the message
        boolean handled = false;

        for (Map.Entry<Predicate<String>, MessageHandler> entry : messageHandlers.entrySet()) {
            if (entry.getKey().test(message)) {
                entry.getValue().handle(message, writer);
                handled = true;
                break;
            }
        }

        // Handle unknown commands
        if (!handled) {
            writer.println("Unknown command: " + message);
        }
    }

    /**
     * Handle client disconnection by removing from active clients.
     * Called when a socket exception occurs or the connection is closed.
     */
    private void handleClientDisconnect() {
        if (currentUserId != null) {
            ClientManager.removeClient(currentUserId);
        }
    }

    /**
     * Process a message acknowledgment from the client.
     * Updates the message delivery status in the database.
     *
     * @param message The acknowledgment message in format ACK:{messageId}
     * @param writer PrintWriter for responses (not used in this handler)
     */
    private void processMessageAcknowledgment(String message, PrintWriter writer) {
        try {
            String messageId = message.substring(4).trim();
            if (currentUserId != null && messageId.length() > 0) {
                databaseService.acknowledgeMessage(messageId, currentUserId);
            }
        } catch (Exception e) {
            System.err.println("Error processing acknowledgment: " + e.getMessage());
        }
    }

    /**
     * Registers a new client user in the system.
     *
     * @param username The username for the new client
     * @param password The password for the new client
     * @param role The role for the new client (CLIENT or ADMIN)
     */
    private void registerClient(String username, String password, String role) {
        User user = new User(username, password, Role.valueOf(role));
        databaseService.saveUser(user);
    }

    /**
     * Validates and processes a registration message.
     * Expected format: "REGISTER,username,password,role"
     *
     * @param message The registration message to process
     * @param writer PrintWriter for sending responses
     */
    public void validateMessageForRegister(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 4) {
            String username = parts[1].trim();
            String password = parts[2].trim();
            String role = parts[3].trim();
            registerClient(username, password, role);
            writer.println("Client registered successfully");
        } else {
            writer.println("Invalid registration format. Use: REGISTER,username,password,role");
        }
    }

    /**
     * Saves the user ID from a CLIENTID message and registers the client in the active clients map.
     * Expected format: "CLIENTID,userId"
     *
     * @param message The CLIENTID message containing the user ID
     * @param writer PrintWriter for sending responses
     */
    private void saveUserId(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 2) {
            String userIdStr = parts[1].trim();
            try {
                Long userId = Long.parseLong(userIdStr);
                this.currentUserId = userId;
                ClientManager.addClient(userId, clientSocket);
                System.out.println(userId + " -> " + clientSocket);
                System.out.println("Saving socket for user ID: " + userId + " Socket: " + clientSocket);
            } catch (NumberFormatException e) {
                writer.println("Invalid user ID format");
            }
        } else {
            writer.println("Invalid CLIENTID message format");
        }
    }

    /**
     * Sends system status information to the client.
     * Includes client count, event count, system status, and uptime.
     *
     * @param writer PrintWriter for sending responses
     */
    public void sendSystemStatus(PrintWriter writer) {
        Object[] stats = databaseService.getBrokerStats();
        writer.println("BEGIN STATUS");
        writer.println("CLIENT_COUNT:" + stats[0]);
        writer.println("EVENT_COUNT:" + stats[1]);
        writer.println("STATUS:" + stats[2]);
        writer.println("UPTIME:" + stats[3]);
        writer.println("END STATUS");
    }

    /**
     * Creates and publishes a new event based on the message.
     * Expected format: "CREATE EVENT,eventTypeId,payload"
     *
     * @param message The create event message
     * @param writer PrintWriter for responses
     */
    public void createEvent(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 3) {
            try {
                Long eventTypeId = Long.valueOf(parts[1].trim());
                String payload = parts[2].trim();
                EventType eventType = databaseService.findEventTypeById(eventTypeId);

                if (eventType == null) {
                    writer.println("Event type not found: " + eventTypeId);
                    return;
                }

                Event event = new Event(eventType, payload);
                databaseService.createEvent(event);
                sendPublishedEvent(eventType.getId(), event);

                // Forward the event to other nodes in the cluster
                if (clusterManager != null) {
                    clusterManager.forwardEventToCluster(event, eventTypeId);
                }

                writer.println("Event published successfully");
            } catch (NumberFormatException e) {
                writer.println("Invalid event type ID format");
            } catch (Exception e) {
                writer.println("Error creating event: " + e.getMessage());
            }
        } else {
            writer.println("Invalid format for creating event");
        }
    }

    /**
     * Sends a published event to all subscribers.
     * Handles both online and offline subscribers appropriately.
     *
     * @param eventTypeId The ID of the event type
     * @param event The event to send to subscribers
     */
    public void sendPublishedEvent(Long eventTypeId, Event event) {
        List<Long> userIdList = databaseService.getAllSubscribers(eventTypeId);
        boolean allSuccessful = true;

        for (Long userId : userIdList) {
            Socket clientSocket = ClientManager.getClientSocket(userId);

            // Check if user is online and can receive messages directly
            if (clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed()) {
                try {
                    PrintWriter clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);

                    // Send event with message ID for tracking
                    String eventMessage = String.format("EVENT %s:%s:%s",
                            event.getEventType().getEventTypeName(),
                            event.getMessageId(),
                            event.getPayload());

                    clientWriter.println(eventMessage);
                    System.out.println("Delivered event to user ID: " + userId);

                    // Track delivery status
                    databaseService.trackMessageDelivery(event.getMessageId(), userId, "DELIVERED");

                } catch (IOException e) {
                    System.err.println("Error delivering event to client: " + e.getMessage());
                    allSuccessful = false;

                    // Store as pending if delivery fails
                    databaseService.storePendingMessage(userId, eventTypeId, event.getPayload());
                    databaseService.trackMessageDelivery(event.getMessageId(), userId, "PENDING");
                    System.out.println("Stored pending message for user ID: " + userId + " (delivery error)");
                }
            } else {
                // User is offline, store as pending message
                databaseService.storePendingMessage(userId, eventTypeId, event.getPayload());
                databaseService.trackMessageDelivery(event.getMessageId(), userId, "PENDING");
                System.out.println("Stored pending message for user ID: " + userId + " (offline)");
                allSuccessful = false;
            }
        }

        if (!allSuccessful) {
            System.out.println("Some subscribers were offline. Messages will be delivered when they reconnect.");
        }
    }

    /**
     * Creates a new event type based on the message.
     * Expected format: "CREATE EVENT TYPE,eventTypeName,description"
     *
     * @param message The create event type message
     * @param writer PrintWriter for responses
     */
    public void createEventType(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 3) {
            String eventTypeName = parts[1].trim();
            String description = parts[2].trim();

            try {
                // Check if event type already exists by name
                Long existingEventTypeId = databaseService.findEventTypeIdByEventTypeName(eventTypeName);

                if (existingEventTypeId != null) {
                    writer.println("Event type already exists: " + eventTypeName);
                    return;
                }

                EventType eventType = new EventType(eventTypeName, description);
                databaseService.createEventType(eventType);
                writer.println("Event type created successfully");
                System.out.println("Created new event type: " + eventTypeName);
            } catch (Exception e) {
                writer.println("Error creating event type: " + e.getMessage());
                System.err.println("Error creating event type: " + e.getMessage());
            }
        } else {
            writer.println("Invalid format for creating event type");
        }
    }

    /**
     * Delivers any pending messages to the client when they connect.
     * Retrieves stored messages and sends them to the newly connected client.
     *
     * @param writer PrintWriter for sending pending messages
     */
    private void deliverPendingMessages(PrintWriter writer) {
        if (currentUserId != null) {
            // Track messages already delivered to avoid duplicates
            Map<String, Boolean> deliveredMessageHashes = new HashMap<>();

            // Get pending messages from the dedicated pending_message table
            List<Event> pendingMessages = databaseService.getPendingMessages(currentUserId);

            System.out.println("Retrieved " + pendingMessages.size() + " pending messages for user: " + currentUserId);

            if (!pendingMessages.isEmpty()) {
                writer.println("You have " + pendingMessages.size() + " pending messages:");

                for (Event event : pendingMessages) {
                    // Create a unique hash for this message to avoid duplicates
                    String messageHash = event.getEventType().getEventTypeName() + ":" + event.getPayload();

                    // Skip if we've already delivered this message
                    if (deliveredMessageHashes.containsKey(messageHash)) {
                        System.out.println("Skipping duplicate message: " + messageHash);
                        continue;
                    }

                    System.out.println("Delivering pending message ID: " + event.getMessageId() + " for event type: " +
                            event.getEventType().getEventTypeName());

                    // Format message with messageId for acknowledgment
                    String eventMessage = String.format("EVENT %s:%s:%s",
                            event.getEventType().getEventTypeName(),
                            event.getMessageId(),
                            event.getPayload());

                    writer.println(eventMessage);

                    // Mark as delivered to avoid duplicates
                    deliveredMessageHashes.put(messageHash, true);

                    // Update delivery tracking
                    databaseService.trackMessageDelivery(event.getMessageId(), currentUserId, "DELIVERED");

                    // Delete the pending message from the database
                    boolean deleted = databaseService.deletePendingMessage(event.getId());
                    if (deleted) {
                        System.out.println("Successfully deleted pending message: " + event.getId());
                    } else {
                        System.err.println("Failed to delete pending message: " + event.getId());
                    }
                }

                System.out.println("Delivered " + deliveredMessageHashes.size() + " unique pending messages to user: " + currentUserId);
            }
        }
    }

    /**
     * Sends the list of all available event types to the client.
     * Serializes event types to JSON format for transmission.
     *
     * @param writer PrintWriter for sending the event types list
     */
    public void sendEventTypeList(PrintWriter writer) {
        List<EventType> eventTypes = databaseService.getAllEventTypes();
        try {
            String jsonResponse = objectMapper.writeValueAsString(eventTypes);
            writer.println(jsonResponse);
            System.out.println("Sent event data as JSON to client: " + clientSocket.getPort());
        } catch (JsonProcessingException e) {
            writer.println("Error serializing event types: " + e.getMessage());
            System.err.println("Error serializing event types: " + e.getMessage());
        }
    }

    /**
     * Handles subscribing a user to an event type.
     * Expected format: "SUBSCRIBE,eventTypeId,userId"
     *
     * @param message The subscribe message
     * @param writer PrintWriter for responses
     */
    private void subscribeToEventType(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 3) {
            try {
                Long eventTypeId = Long.valueOf(parts[1].trim());
                Long userId = Long.valueOf(parts[2].trim());

                // Check if user has an active subscription
                if (databaseService.isAlreadySubscribed(userId, eventTypeId)) {
                    writer.println("You are already subscribed to this event type");
                    return;
                }

                // Check if there's an inactive subscription that can be reactivated
                if (databaseService.hasInactiveSubscription(userId, eventTypeId)) {
                    boolean success = databaseService.reactivateSubscription(userId, eventTypeId);
                    if (success) {
                        writer.println("Successfully resubscribed to event type ID: " + eventTypeId);
                    } else {
                        writer.println("Failed to reactivate subscription");
                    }
                    return;
                }

                // Create a new subscription
                Subscription subscription = new Subscription(userId, eventTypeId);
                databaseService.createSubscription(subscription);
                writer.println("Successfully subscribed to event type ID: " + eventTypeId);
            } catch (NumberFormatException e) {
                writer.println("Invalid ID format in subscription request");
            } catch (Exception e) {
                writer.println("Error processing subscription: " + e.getMessage());
            }
        } else {
            writer.println("Invalid format for subscribing. Use: SUBSCRIBE,eventTypeId,userId");
        }
    }

    /**
     * Handles unsubscribe request from client.
     * Expected format: "UNSUBSCRIBE,eventTypeId,userId"
     *
     * @param message The unsubscribe message
     * @param writer PrintWriter to send response to client
     */
    private void unsubscribeFromEventType(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 3) {
            try {
                Long eventTypeId = Long.valueOf(parts[1].trim());
                Long userId = Long.valueOf(parts[2].trim());

                boolean success = databaseService.unsubscribeFromEventType(userId, eventTypeId);

                if (success) {
                    writer.println("Successfully unsubscribed from event type ID: " + eventTypeId);
                    System.out.println("User ID: " + userId + " unsubscribed from event type ID: " + eventTypeId);
                } else {
                    writer.println("Failed to unsubscribe. You may not be subscribed to this event type.");
                    System.out.println("Failed to unsubscribe user ID: " + userId + " from event type ID: " + eventTypeId);
                }
            } catch (NumberFormatException e) {
                writer.println("Invalid event type ID or user ID format");
                System.err.println("Invalid ID format in unsubscribe message: " + message);
            }
        } else {
            writer.println("Invalid format for unsubscribing. Use: UNSUBSCRIBE,eventTypeId,userId");
        }
    }

    /**
     * Sends the event types that a user is subscribed to.
     * Expected format: "GET SUBSCRIBED EVENT TYPES,userId"
     *
     * @param message The request message
     * @param writer PrintWriter to send the response to
     */
    private void sendSubscribedEventTypes(String message, PrintWriter writer) {
        try {
            String[] parts = message.split(",");
            if (parts.length == 2) {
                Long userId = Long.valueOf(parts[1].trim());

                List<EventType> subscribedEventTypes = databaseService.getSubscribedEventTypes(userId);

                if (subscribedEventTypes.isEmpty()) {
                    writer.println("[]"); // Empty JSON array
                    System.out.println("User ID: " + userId + " has no active subscriptions");
                } else {
                    String jsonResponse = objectMapper.writeValueAsString(subscribedEventTypes);
                    writer.println(jsonResponse);
                    System.out.println("Sent " + subscribedEventTypes.size() + " subscribed event types to user ID: " + userId);
                }
            } else {
                writer.println("[]"); // Empty JSON array for invalid request
                System.err.println("Invalid format for GET SUBSCRIBED EVENT TYPES request: " + message);
            }
        } catch (Exception e) {
            writer.println("[]"); // Empty JSON array on error
            System.err.println("Error sending subscribed event types: " + e.getMessage());
        }
    }

    /**
     * Handles logout request from client.
     * Expected format: "LOGOUT" or "LOGOUT,userId"
     *
     * @param message The logout message that may contain user ID
     * @param writer PrintWriter to send response to client
     */
    private void handleLogout(String message, PrintWriter writer) {
        try {
            // Try to extract user ID from message if provided
            Long userIdToRemove = null;

            // Check if message contains user ID (format: LOGOUT,userId)
            String[] parts = message.split(",");
            if (parts.length > 1) {
                try {
                    userIdToRemove = Long.parseLong(parts[1].trim());
                } catch (NumberFormatException e) {
                    System.err.println("Invalid user ID in logout message: " + parts[1]);
                }
            }

            // If no valid user ID in message, use the current user ID
            if (userIdToRemove == null) {
                userIdToRemove = this.currentUserId;
            }

            if (userIdToRemove != null) {
                // Remove client from manager
                ClientManager.removeClient(userIdToRemove);
                System.out.println("User " + userIdToRemove + " logged out successfully");

                // Log the logout action in the database if needed
                try {
                    databaseService.addLog("User ID: " + userIdToRemove + " logged out", "INFO", null);
                } catch (Exception e) {
                    System.err.println("Error logging logout: " + e.getMessage());
                }
            }

            // Send logout confirmation
            writer.println("LOGOUT_CONFIRMED");
        } catch (Exception e) {
            System.err.println("Error during logout: " + e.getMessage());
        }
    }
}

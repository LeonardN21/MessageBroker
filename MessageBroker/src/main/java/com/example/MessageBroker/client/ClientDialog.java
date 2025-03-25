package com.example.MessageBroker.client;

import com.example.MessageBroker.entities.event.EventType;
import com.example.MessageBroker.entities.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Handles client-side communication with the message broker server.
 * This class manages user interactions, event subscriptions, and message processing.
 */
public class ClientDialog {

    /** Writer for sending messages to the server */
    private final PrintWriter printWriter;

    /** Reader for receiving messages from the server */
    private final BufferedReader reader;

    /** Scanner for user input */
    private Scanner scanner;

    /** Currently authenticated user */
    private final User user;

    /** List of all available event types */
    private List<EventType> allEventTypes;

    /** List of event types the user is subscribed to */
    private List<EventType> subscribedEventTypes;

    /** Message received from server */
    private String message;

    /** Flag to control message listener thread */
    private volatile boolean running = true;

    /** Flag to track what type of request was last made */
    private boolean lastRequestWasForSubscribedTypes = false;

    /**
     * Constructs a new ClientDialog instance.
     *
     * @param printWriter Writer for sending messages to the server
     * @param reader Reader for receiving messages from the server
     * @param user The authenticated user
     */
    public ClientDialog(PrintWriter printWriter, BufferedReader reader, User user) {
        this.printWriter = printWriter;
        this.reader = reader;
        this.user = user;
    }

    /**
     * Starts the client dialog by initializing the message listener thread
     * and displaying the user menu.
     */
    public void startClientDialog() {
        startMessageListenerThread();
        runMenu();
    }

    /**
     * Starts a background thread that listens for incoming server messages.
     * The thread handles different types of messages including heartbeat pings,
     * event notifications, and server responses.
     */
    private void startMessageListenerThread() {
        new Thread(() -> {
            try {
                while (running && (message = reader.readLine()) != null) {
                    // Handle PING message specifically with automatic PONG response
                    if (message.equals("PING")) {
                        printWriter.println("PONG");
                        System.out.println("Responded to server heartbeat ping");
                        continue;
                    }

                    // Only print the received message if it's not part of an event types list response
                    if (!message.contains("EVENT TYPES") && !message.equals("END")) {
                        System.out.println("Received from server: " + message);
                    }

                    processMessage(message);
                }
            } catch (IOException e) {
                // Only print error if we're still supposed to be running
                if (running) {
                    System.err.println("Connection to server lost: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Process incoming messages based on their type.
     *
     * @param message The message to process
     */
    private void processMessage(String message) {
        MessageType messageType = determineMessageType(message);

        switch (messageType) {
            case EVENT_TYPES_LIST:
                if (!message.equals("END")) {  // Don't process the END marker
                    handleEventTypesList(message, false);
                }
                break;
            case SUBSCRIBED_EVENT_TYPES_LIST:
                if (!message.equals("END")) {  // Don't process the END marker
                    handleEventTypesList(message, true);
                }
                break;
            case EVENT:
                processEventMessage(message);
                break;
            case LOGOUT_CONFIRMED:
                System.out.println("Server confirmed logout. Closing connection.");
                break;
            case UNKNOWN:
                if (!message.equals("END")) {  // Don't print the END marker
                    System.out.println(message);
                }
                break;
        }
    }

    /**
     * Determines the type of message received from the server.
     *
     * @param message The message to analyze
     * @return The type of message as a MessageType enum value
     */
    private MessageType determineMessageType(String message) {
        if (message.contains("EVENT TYPES")) {
            return MessageType.EVENT_TYPES_LIST;
        } else if (message.startsWith("[") && message.endsWith("]")) {
            return lastRequestWasForSubscribedTypes ?
                    MessageType.SUBSCRIBED_EVENT_TYPES_LIST :
                    MessageType.EVENT_TYPES_LIST;
        } else if (message.startsWith("EVENT ")) {
            return MessageType.EVENT;
        } else if (message.equals("LOGOUT_CONFIRMED")) {
            return MessageType.LOGOUT_CONFIRMED;
        }
        return MessageType.UNKNOWN;
    }

    /**
     * Processes an event message received from the server.
     * Parses the message format "EVENT type:messageId:payload" and sends an acknowledgment.
     *
     * @param message The event message to process
     */
    private void processEventMessage(String message) {
        try {
            // Parse message format: "EVENT type:messageId:payload"
            String content = message.substring(6); // Remove "EVENT " prefix
            String[] parts = content.split(":", 3);

            if (parts.length == 3) {
                String eventType = parts[0];
                String messageId = parts[1];
                String payload = parts[2];

                // Display the event to the user
                System.out.println("Received event of type '" + eventType + "': " + payload);

                // Send acknowledgment
                sendAcknowledgment(messageId);
            } else {
                System.out.println("Received malformed event: " + message);
            }
        } catch (Exception e) {
            System.err.println("Error handling event: " + e.getMessage());
        }
    }

    /**
     * Sends an acknowledgment to the server for a received message.
     *
     * @param messageId The message ID to acknowledge
     */
    private void sendAcknowledgment(String messageId) {
        if (printWriter != null) {
            printWriter.println("ACK:" + messageId);
            System.out.println("Sent acknowledgment for message: " + messageId);
        }
    }

    /**
     * Displays the main menu and handles user input for different operations.
     * Options include creating/publishing events, subscribing to events,
     * unsubscribing from events, and exiting the application.
     */
    private void runMenu() {
        scanner = new Scanner(System.in);
        int choice = 0;

        while (choice != 4) {
            System.out.println("Hello, choose one option");
            System.out.println("1. Create and publish event\n" +
                    "2. Subscribe\n" +
                    "3. Unsubscribe\n" +
                    "4. Exit");
            System.out.print("Enter your choice: ");

            // Use more robust input handling
            try {
                String input = scanner.nextLine().trim();
                try {
                    choice = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a number between 1 and 4.");
                    continue;
                }
            } catch (Exception e) {
                System.out.println("Error reading input: " + e.getMessage());
                choice = 0;
                continue;
            }

            if (choice == 1) {
                lastRequestWasForSubscribedTypes = false;
                waitForEventTypes(false);
                chooseEventTypeAndCreate();
            } else if (choice == 2) {
                lastRequestWasForSubscribedTypes = false;
                waitForEventTypes(false);
                chooseEventTypeAndSubscribe();
            } else if (choice == 3) {
                lastRequestWasForSubscribedTypes = true;
                waitForEventTypes(true);
                chooseEventTypeAndUnsubscribe();
            } else if (choice == 4) {
                exitApplication();
                break;
            } else {
                System.out.println("Invalid choice. Please enter a number between 1 and 4.");
            }
        }
    }

    /**
     * Properly exits the application by closing all resources and terminating.
     * Sends a logout message to the server, closes all I/O resources, and
     * exits with the appropriate status code.
     */
    private void exitApplication() {
        System.out.println("Exiting application...");
        try {
            // Signal the message listener thread to stop
            running = false;

            // Send a logout message to the server if needed
            if (printWriter != null && user != null) {
                // Include user ID so server can identify which user to remove
                printWriter.println("LOGOUT," + user.getId());
                System.out.println("Logout message sent to server");

                // Wait briefly for logout confirmation (max 2 seconds)
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            // Close resources
            if (printWriter != null) {
                printWriter.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (scanner != null) {
                scanner.close();
            }

            System.out.println("All connections closed. Goodbye!");

            // Exit with success status
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Error during exit: " + e.getMessage());
            // Force exit if there was an error
            System.exit(1);
        }
    }

    /**
     * Displays available event types and prompts the user to select one for creating and publishing an event.
     * Validates user input and calls createEvent with the selected event type.
     */
    private void chooseEventTypeAndCreate() {
        if (allEventTypes == null || allEventTypes.isEmpty()) {
            System.out.println("No event types available.");
            return;
        }

        System.out.println("Choose an event type: ");
        for (int i = 0; i < allEventTypes.size(); i++) {
            System.out.println((i + 1) + ". " + allEventTypes.get(i).getEventTypeName());
        }
        System.out.print("Enter your choice: ");

        // Use a more robust input handling method with error recovery
        int eventChoice = -1;
        try {
            // Don't consume another line - the nextLine from the main menu is sufficient
            String input = scanner.nextLine().trim();
            try {
                eventChoice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
                return;
            }
        } catch (Exception e) {
            System.out.println("Error reading input: " + e.getMessage());
            return;
        }

        if (eventChoice > 0 && eventChoice <= allEventTypes.size()) {
            createEvent(eventChoice);
        } else {
            System.out.println("Invalid choice. Please select a number between 1 and " + allEventTypes.size());
        }
    }

    /**
     * Displays available event types and prompts the user to select one for subscription.
     * Validates user input and calls subscribeToEvent with the selected event type.
     */
    private void chooseEventTypeAndSubscribe() {
        if (allEventTypes == null || allEventTypes.isEmpty()) {
            System.out.println("No event types available.");
            return;
        }

        System.out.println("Choose an event type: ");
        for (int i = 0; i < allEventTypes.size(); i++) {
            System.out.println((i + 1) + ". " + allEventTypes.get(i).getEventTypeName());
        }
        System.out.print("Enter your choice: ");

        // Use a more robust input handling method with error recovery
        int eventChoice = -1;
        try {
            String input = scanner.nextLine().trim();
            try {
                eventChoice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
                return;
            }
        } catch (Exception e) {
            System.out.println("Error reading input: " + e.getMessage());
            return;
        }

        if (eventChoice > 0 && eventChoice <= allEventTypes.size()) {
            subscribeToEvent(eventChoice);
        } else {
            System.out.println("Invalid choice. Please select a number between 1 and " + allEventTypes.size());
        }
    }

    /**
     * Displays the event types the user is currently subscribed to and prompts
     * the user to select one for unsubscription.
     * Validates user input and calls unsubscribeFromEvent with the selected event type.
     */
    private void chooseEventTypeAndUnsubscribe() {
        if (subscribedEventTypes == null || subscribedEventTypes.isEmpty()) {
            System.out.println("You are not subscribed to any event types.");
            return;
        }

        System.out.println("Choose an event type to unsubscribe from: ");
        for (int i = 0; i < subscribedEventTypes.size(); i++) {
            System.out.println((i + 1) + ". " + subscribedEventTypes.get(i).getEventTypeName());
        }
        System.out.print("Enter your choice: ");

        // Use a more robust input handling method with error recovery
        int eventChoice = -1;
        try {
            String input = scanner.nextLine().trim();
            try {
                eventChoice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
                return;
            }
        } catch (Exception e) {
            System.out.println("Error reading input: " + e.getMessage());
            return;
        }

        if (eventChoice > 0 && eventChoice <= subscribedEventTypes.size()) {
            unsubscribeFromEvent(eventChoice);
        } else {
            System.out.println("Invalid choice. Please select a number between 1 and " + subscribedEventTypes.size());
        }
    }

    /**
     * Creates and publishes an event of the selected type.
     * Requests a payload from the user and sends the event creation request to the server.
     *
     * @param eventChoice The index of the selected event type (1-based)
     */
    public void createEvent(int eventChoice) {
        try {
            String payload = createEventPayload();
            printWriter.println("CREATE EVENT," + allEventTypes.get(eventChoice - 1).getId() + "," + payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Requests the list of all available event types from the server.
     * Sends a "GET EVENT TYPE LIST" command to the server.
     */
    public void requestAllEventTypes() {
        if (printWriter != null) {
            printWriter.println("GET EVENT TYPE LIST");
            System.out.println("Get list request sent to server.");
        }
    }

    /**
     * Requests the list of event types that the current user is subscribed to.
     * Sends a "GET SUBSCRIBED EVENT TYPES" command with the user ID to the server.
     */
    public void requestSubscribedEventTypes() {
        if (printWriter != null) {
            printWriter.println("GET SUBSCRIBED EVENT TYPES," + user.getId());
            System.out.println("Requesting subscribed event types from server...");
        }
    }

    /**
     * Prompts the user to enter a payload for the event being created.
     *
     * @return The payload text entered by the user
     */
    public String createEventPayload() {
        System.out.print("Enter payload: ");
        return scanner.nextLine();
    }

    /**
     * Handles event types list response from server by parsing the JSON data.
     * Attempts to extract and parse the JSON array of event types from the message.
     * Includes error handling and recovery mechanisms for malformed JSON.
     *
     * @param message JSON message with event types
     * @param isSubscribedList true if this is a list of subscribed event types, false for all event types
     */
    public void handleEventTypesList(String message, boolean isSubscribedList) {
        try {
            String jsonText;

            if (message.startsWith("[")) {
                // Direct JSON response
                jsonText = message;
            } else {
                // Extract JSON from a message containing other text
                int jsonStart = message.indexOf("[");
                if (jsonStart == -1) {
                    System.err.println("Warning: JSON array not found in message: " + message);
                    // If we can't parse as JSON, initialize as empty list to avoid null issues
                    initializeEmptyList(isSubscribedList);
                    return;
                }
                jsonText = message.substring(jsonStart);
            }

            // Trim any potential whitespace or trailing characters
            jsonText = jsonText.trim();
            if (!jsonText.startsWith("[") || !jsonText.endsWith("]")) {
                System.err.println("Warning: Malformed JSON - trimming to proper bounds");
                int start = jsonText.indexOf("[");
                int end = jsonText.lastIndexOf("]") + 1;
                if (start >= 0 && end > start) {
                    jsonText = jsonText.substring(start, end);
                } else {
                    System.err.println("Error: Cannot extract valid JSON array");
                    initializeEmptyList(isSubscribedList);
                    return;
                }
            }

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                EventType[] eventArray = objectMapper.readValue(jsonText, EventType[].class);

                if (isSubscribedList) {
                    subscribedEventTypes = new ArrayList<>(Arrays.asList(eventArray));
                    System.out.println("Subscribed event types received: " + subscribedEventTypes.size() + " types");
                } else {
                    allEventTypes = new ArrayList<>(Arrays.asList(eventArray));
                    System.out.println("All event types received: " + allEventTypes.size() + " types");
                }
            } catch (Exception jsonEx) {
                System.err.println("Error parsing JSON data: " + jsonEx.getMessage());
                System.err.println("Received JSON: " + jsonText);
                initializeEmptyList(isSubscribedList);

                // Try again with a more robust approach if the first attempt failed
                tryRecoveryParsing(jsonText, isSubscribedList);
            }
        } catch (Exception e) {
            System.err.println("General error processing event types list: " + e.getMessage());
            e.printStackTrace();
            initializeEmptyList(isSubscribedList);
        }
    }

    /**
     * Attempts to recover from a JSON parsing error by trying to find valid JSON
     * within the provided text.
     *
     * @param jsonText The potentially malformed JSON text
     * @param isSubscribedList Indicates if we're processing subscribed event types
     */
    private void tryRecoveryParsing(String jsonText, boolean isSubscribedList) {
        try {
            // Sometimes the JSON might have extra characters, try to find valid JSON
            if (jsonText.contains("[") && jsonText.contains("]")) {
                int start = jsonText.indexOf("[");
                int end = jsonText.lastIndexOf("]") + 1;
                String cleanJson = jsonText.substring(start, end);

                ObjectMapper objectMapper = new ObjectMapper();
                EventType[] eventArray = objectMapper.readValue(cleanJson, EventType[].class);

                if (isSubscribedList) {
                    subscribedEventTypes = new ArrayList<>(Arrays.asList(eventArray));
                    System.out.println("Recovered subscribed event types: " + subscribedEventTypes.size() + " types");
                } else {
                    allEventTypes = new ArrayList<>(Arrays.asList(eventArray));
                    System.out.println("Recovered all event types: " + allEventTypes.size() + " types");
                }
            }
        } catch (Exception retryEx) {
            System.err.println("Recovery attempt failed: " + retryEx.getMessage());
        }
    }

    /**
     * Initializes the appropriate list as empty to avoid null reference issues.
     *
     * @param isSubscribedList Indicates whether to initialize the subscribed list or all events list
     */
    private void initializeEmptyList(boolean isSubscribedList) {
        if (isSubscribedList) {
            subscribedEventTypes = new ArrayList<>();
            System.out.println("Initialized empty subscribed event types list due to processing error");
        } else {
            allEventTypes = new ArrayList<>();
            System.out.println("Initialized empty all event types list due to processing error");
        }
    }

    /**
     * Waits for event types to be received from the server.
     * Requests the appropriate type of event list and waits for a response,
     * with retry logic in case of failures or timeouts.
     *
     * @param waitingForSubscribed true to wait for subscribed event types, false for all event types
     */
    private void waitForEventTypes(boolean waitingForSubscribed) {
        if (waitingForSubscribed) {
            // Always start with a fresh empty list to ensure a refresh
            subscribedEventTypes = new ArrayList<>();

            // Send the request
            requestSubscribedEventTypes();

            // Wait for the response with a more robust approach
            System.out.println("Waiting for subscribed event types...");

            // Use a more robust waiting approach
            int attempts = 0;
            final int maxAttempts = 10;
            final int waitTimePerAttempt = 500; // milliseconds

            while ((subscribedEventTypes == null || subscribedEventTypes.isEmpty()) && attempts < maxAttempts) {
                try {
                    Thread.sleep(waitTimePerAttempt);
                    attempts++;
                    System.out.println("Still waiting for subscribed event types... (attempt " + attempts + "/" + maxAttempts + ")");

                    // Every 2 attempts, try sending the request again
                    if (attempts % 2 == 0) {
                        System.out.println("Re-requesting subscribed event types...");
                        requestSubscribedEventTypes();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted while waiting for event types");
                    break;
                }
            }

            // Report the outcome
            if (subscribedEventTypes != null && !subscribedEventTypes.isEmpty()) {
                System.out.println("Successfully received " + subscribedEventTypes.size() + " subscribed event types after " + attempts + " attempts");
            } else {
                // If still empty after all attempts, initialize to avoid NullPointerException
                if (subscribedEventTypes == null) {
                    subscribedEventTypes = new ArrayList<>();
                }
                System.out.println("No subscribed event types received after " + attempts + " attempts, continuing with empty list");
            }
        } else {
            // Always start with a fresh empty list to ensure a refresh
            allEventTypes = new ArrayList<>();

            // Send the request
            requestAllEventTypes();

            // Use a more robust waiting approach
            System.out.println("Waiting for event types...");
            int attempts = 0;
            final int maxAttempts = 10;
            final int waitTimePerAttempt = 500; // milliseconds

            while ((allEventTypes == null || allEventTypes.isEmpty()) && attempts < maxAttempts) {
                try {
                    Thread.sleep(waitTimePerAttempt);
                    attempts++;
                    System.out.println("Still waiting for event types... (attempt " + attempts + "/" + maxAttempts + ")");

                    // Every 2 attempts, try sending the request again
                    if (attempts % 2 == 0) {
                        System.out.println("Re-requesting event types...");
                        requestAllEventTypes();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted while waiting for event types");
                    break;
                }
            }

            // Report the outcome
            if (allEventTypes != null && !allEventTypes.isEmpty()) {
                System.out.println("Successfully received " + allEventTypes.size() + " event types after " + attempts + " attempts");
            } else {
                // If still empty after all attempts, initialize to avoid NullPointerException
                if (allEventTypes == null) {
                    allEventTypes = new ArrayList<>();
                }
                System.out.println("No event types received after " + attempts + " attempts, continuing with empty list");
            }
        }
    }

    /**
     * Subscribes the user to an event type.
     * Sends a subscription request to the server and updates the local list of subscribed event types.
     * Also requests an updated list of subscribed event types after a delay to ensure
     * proper synchronization with the server.
     *
     * @param choice The index of the selected event type (1-based)
     */
    public void subscribeToEvent(int choice) {
        if (allEventTypes == null || choice < 1 || choice > allEventTypes.size()) {
            System.out.println("Invalid event type selection");
            return;
        }

        EventType selectedType = allEventTypes.get(choice - 1);
        printWriter.println("SUBSCRIBE," + selectedType.getId() + "," + user.getId());
        System.out.println("Subscribe request sent for event type: " + selectedType.getEventTypeName());

        // Wait for the server response without immediately requesting updated list
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for subscription response");
        }

        // Add the subscribed item locally to avoid double JSON processing
        // First check if it's already in the list
        if (subscribedEventTypes == null) {
            subscribedEventTypes = new ArrayList<>();
        }

        // Only add if not already subscribed
        boolean alreadySubscribed = false;
        for (EventType et : subscribedEventTypes) {
            if (et.getId() == selectedType.getId()) {
                alreadySubscribed = true;
                break;
            }
        }

        if (!alreadySubscribed) {
            subscribedEventTypes.add(selectedType);
        }

        // Request an updated list in a separate thread to not block the user
        new Thread(() -> {
            try {
                // Wait longer for cluster synchronization
                Thread.sleep(1500);

                // Now request an updated list for future operations
                if (printWriter != null) {
                    lastRequestWasForSubscribedTypes = true;
                    printWriter.println("GET SUBSCRIBED EVENT TYPES," + user.getId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while requesting updated subscribed event types");
            }
        }).start();
    }

    /**
     * Unsubscribes the user from an event type.
     * Sends an unsubscription request to the server and updates the local list of subscribed event types.
     * Also requests an updated list of subscribed event types after a delay to ensure
     * proper synchronization with the server.
     *
     * @param choice The index of the selected event type (1-based)
     */
    public void unsubscribeFromEvent(int choice) {
        if (subscribedEventTypes == null || subscribedEventTypes.isEmpty()) {
            System.out.println("No subscribed event types available.");
            return;
        }

        if (choice < 1 || choice > subscribedEventTypes.size()) {
            System.out.println("Invalid choice: " + choice + ". Valid range is 1-" + subscribedEventTypes.size());
            return;
        }

        EventType eventType = subscribedEventTypes.get(choice - 1);
        if (printWriter != null) {
            printWriter.println("UNSUBSCRIBE," + eventType.getId() + "," + user.getId());
            System.out.println("Unsubscribe request sent for event type: " + eventType.getEventTypeName());

            // Wait for the server response without immediately requesting updated list
            try {
                Thread.sleep(1000); // Allow time for server to process
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for unsubscription response");
            }

            // Instead of requesting a new list, just remove the unsubscribed item locally
            // This prevents double JSON processing
            subscribedEventTypes.removeIf(et -> et.getId() == eventType.getId());

            // Only request an updated list after a longer delay to ensure cluster synchronization
            // but do this in a separate thread to not block the user
            new Thread(() -> {
                try {
                    // Wait longer for cluster synchronization
                    Thread.sleep(2000);

                    // Now request an updated list for future operations
                    if (printWriter != null) {
                        lastRequestWasForSubscribedTypes = true;
                        printWriter.println("GET SUBSCRIBED EVENT TYPES," + user.getId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted while requesting updated subscribed event types");
                }
            }).start();
        }
    }

    /**
     * Enum representing different types of messages that can be received from the server.
     * Used to categorize and process messages based on their content and purpose.
     */
    private enum MessageType {
        /** List of all available event types */
        EVENT_TYPES_LIST,

        /** List of event types the user is subscribed to */
        SUBSCRIBED_EVENT_TYPES_LIST,

        /** An event notification */
        EVENT,

        /** Confirmation of user logout */
        LOGOUT_CONFIRMED,

        /** Any other message type not specifically categorized */
        UNKNOWN
    }
}

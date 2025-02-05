package com.example.MessageBroker.client;

import com.example.MessageBroker.entities.event.EventType;
import com.example.MessageBroker.entities.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class ClientDialog {

    private PrintWriter printWriter;
    private Scanner scanner;
    private BufferedReader reader;

    private List<EventType> eventTypeList;

    private User user;


    public ClientDialog(PrintWriter printWriter, BufferedReader reader, User user) {
        this.printWriter = printWriter;
        this.reader = reader;
        this.user = user;
    }

    // Method to start the client dialog
    public void startClientDialog() {
        // Start a background thread to listen for messages from the server
        startMessageListenerThread();

        // Main thread for menu interaction
        runMenu();
    }

    // Thread to listen for incoming server messages
    private void startMessageListenerThread() {
        new Thread(() -> {
            try {
                String message;
                // Read and print message from the server
                while ((message = reader.readLine()) != null) {
                    if (message.contains("EVENT TYPES")) {
                        getEventTypeList(message);
                    } else if (message.equals("END")) {

                    } else{
                        System.out.println(message);
                    }
                    System.out.println("Received from server: " + message);

                }

            } catch (IOException e) {
                e.printStackTrace();

            }
        }).start();
    }

    // Method for running the main menu in the client
    private void runMenu() {
        scanner = new Scanner(System.in);  // Only one Scanner instance in main thread
        int choice = 0;

        while (choice != -1) {
            System.out.println("Hello, choose one option");
            System.out.println("1. Create and publish event\n" +
                    "2. Subscribe\n" +
                    "3. Exit");
            choice = scanner.nextInt();
            scanner.nextLine(); // Consume the leftover newline

            if (choice == 1) {
                requestEventTypes();
                waitForEventTypes();
                chooseEventTypeAndCreate();
            } else if (choice == 2) {
                requestEventTypes();
                waitForEventTypes();
                chooseEventTypeAndSubscribe();
            } else if (choice == 3) {
                System.out.println("Exiting...");
                break;
            }
        }
    }

    private void chooseEventTypeAndCreate() {
        System.out.println("Choose an event type: ");
        for (int i = 0; i < eventTypeList.size(); i++) {
            System.out.println((i + 1) + ". " + eventTypeList.get(i).getEventTypeName());
        }
        int eventChoice = scanner.nextInt();

        if (eventChoice > 0 && eventChoice <= eventTypeList.size()) {
            createEvent(eventChoice);
        } else {
            System.out.println("Invalid choice.");
        }
    }

    private void chooseEventTypeAndSubscribe() {
        System.out.println("Choose an event type: ");
        for (int i = 0; i < eventTypeList.size(); i++) {
            System.out.println((i + 1) + ". " + eventTypeList.get(i).getEventTypeName());
        }
        int eventChoice = scanner.nextInt();

        if (eventChoice > 0 && eventChoice <= eventTypeList.size()) {
            subscribeToEvent(eventChoice);
        } else {
            System.out.println("Invalid choice.");
        }
    }

    // Method to create an event
    public void createEvent(int eventchoice) {
        try {
            String payload = createEventPayload();
            printWriter.println("CREATE EVENT," + eventTypeList.get(eventchoice - 1).getId() + "," + payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void requestEventTypes() {
        if (printWriter != null) {
            printWriter.println("GET EVENT TYPE LIST");
            System.out.println("Get list request sent to server.");
        }
    }

    // Method to create the payload for the event
    public String createEventPayload() {
        scanner.nextLine();  // Consume the newline
        System.out.println("Payload:");
        return scanner.nextLine();
    }

    // Method to get the list of event types from the server
    public void getEventTypeList(String message) {
        try {
            int jsonStart = message.indexOf("[");
            if (jsonStart == -1) {
                throw new RuntimeException("Invalid response: JSON array not found.");
            }

            String jsonText = message.substring(jsonStart);
            System.out.println("Raw response from server: " + jsonText);

            ObjectMapper objectMapper = new ObjectMapper();
            EventType[] eventArray = objectMapper.readValue(jsonText, EventType[].class);
            eventTypeList = Arrays.asList(eventArray);

            System.out.println("Event types received.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void waitForEventTypes() {
        while (eventTypeList == null || eventTypeList.isEmpty()) {
            System.out.println("Waiting for event types...");
            try {
                Thread.sleep(1000);  // Wait for data to arrive
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to subscribe to an event type
    public void subscribeToEvent(int choice) {
        printWriter.println("SUBSCRIBE," + eventTypeList.get(choice - 1).getId() + "," + user.getId());
    }
}

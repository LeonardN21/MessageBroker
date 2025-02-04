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

    private boolean flag;

    public ClientDialog(PrintWriter printWriter, BufferedReader reader, User user) {
        this.printWriter = printWriter;
        this.reader = reader;
        this.user = user;
    }

    // Method to start the client dialog
    public void startClientDialog() {
        flag = false;
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
                while (true) {
                    if (!flag) {
                        // Read and print message from the server
                        while ((message = reader.readLine()) != null) {
                            System.out.println("Received from server: " + message);
                            break;
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();

            }
        }).start();
    }

    // Method for running the main menu in the client
    private void runMenu() {
        scanner = new Scanner(System.in);
        int choice = 0;

        while (choice != -1) {
            System.out.println("Hello, choose one option");
            System.out.println("1. Create and publish event\n" +
                    "2. Subscribe\n" +
                    "3. Exit");
            choice = scanner.nextInt();

            if (choice == 1) {
                createEvent();
            } else if (choice == 2) {
                subscribeToEvent();
            } else if (choice == 3) {
                System.out.println("Exiting...");
                break;
            }
        }
    }

    // Method to create an event
    public void createEvent() {
        scanner.nextLine();  // Consume the newline
        try {
            int choice = getEventTypeList();
            String payload = createEventPayload();
            printWriter.println("CREATE EVENT," + eventTypeList.get(choice - 1).getId() + "," + payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to create the payload for the event
    public String createEventPayload() {
        scanner.nextLine();  // Consume the newline
        System.out.println("Payload:");
        return scanner.nextLine();
    }

    // Method to get the list of event types from the server
    public int getEventTypeList() {
        int choice = 0;
        try {
            if (printWriter != null) {
                printWriter.println("GET EVENT TYPE LIST");
                System.out.println("Get list request sent to server.");
            }

            StringBuilder jsonResponse = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END")) break;
                jsonResponse.append(line);
            }

            flag = false;
            startMessageListenerThread();

            String jsonText = jsonResponse.toString();
            int jsonStart = jsonText.indexOf("[");

            if (jsonStart != -1) {
                jsonText = jsonText.substring(jsonStart);
            } else {
                throw new RuntimeException("Invalid response: JSON array not found.");
            }
            System.out.println("Raw response from server: " + jsonText);

            ObjectMapper objectMapper = new ObjectMapper();
            EventType[] eventArray = objectMapper.readValue(jsonText, EventType[].class);
            eventTypeList = Arrays.asList(eventArray);

            for (int i = 0; i < eventTypeList.size(); i++) {
                System.out.println((i + 1) + ". " + eventTypeList.get(i).getEventTypeName());
            }

            System.out.println("Choose one option: ");
            choice = scanner.nextInt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return choice;
    }

    // Method to subscribe to an event type
    public void subscribeToEvent() {
        int choice = getEventTypeList();
        printWriter.println("SUBSCRIBE," + eventTypeList.get(choice - 1).getId() + "," + user.getId());
    }
}

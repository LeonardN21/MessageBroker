package com.example.MessageBroker.entities.user;

import com.example.MessageBroker.entities.event.Event;
import com.example.MessageBroker.entities.event.EventType;
import com.example.MessageBroker.entities.subscription.Subscription;
import com.example.MessageBroker.utilities.DatabaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class UserHandler implements Runnable {

    private final Socket clientSocket;
    private final DatabaseService databaseService;

    public boolean sendEvents;

    private static final ObjectMapper objectMapper = new ObjectMapper(); // JSON serializer

    public UserHandler(Socket clientSocket, DatabaseService databaseService) {
        this.clientSocket = clientSocket;
        this.databaseService = databaseService;
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);


            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("Received from client: " + message);
                if (message.contains("CLIENTID")) {
                    saveUserId(message, writer);
                } else if (message.contains("REGISTER")) {
                    validateMessageForRegister(message, writer);
                } else if (message.contains("CREATE EVENT TYPE")) {
                    //ADMIN
                } else if (message.contains("CREATE EVENT")) {
                    createEvent(message);
                } else if (message.contains("GET EVENT TYPE LIST")) {
                    sendEventTypeList(writer);
                } else if (message.contains("SUBSCRIBE")) {
                    subscribeToEventType(message, writer);
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
    }

    private void registerClient(String username, String password, String role) {
        User user = new User(username, password, Role.valueOf(role));
        databaseService.saveUser(user);
    }

    public void validateMessageForRegister(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 4) {
            String username = parts[1].trim();
            String password = parts[2].trim();
            String role = parts[3].trim();
            registerClient(username, password, role);
            writer.println("Client registered successfully");
        }
    }

    public void saveUserId(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 2) {
            Long id = Long.valueOf(parts[1].trim());
            ClientManager.addClient(id, clientSocket);
            ClientManager.getHashMap();
            ClientManager.getClientSocket(id);
            System.out.println("Saving socket for user ID: " + id + " Socket: " + clientSocket);
            writer.println("User saved in  map");
        }
    }

    public void sendEventTypeList(PrintWriter writer) throws JsonProcessingException {
        List<EventType> eventTypeList = databaseService.getAllEventTypes();
        String eventJson = objectMapper.writeValueAsString(eventTypeList);
        writer.println("EVENT TYPES " + eventJson);
        writer.println("END");
        System.out.println("Sent event data as JSON to client: " + clientSocket.getPort());
    }


    public void createEvent(String message) {
        String[] parts = message.split(",");
        if (parts.length == 3) {
            Long eventTypeId = Long.valueOf(parts[1].trim());
            String payload = parts[2].trim();
            EventType eventType = databaseService.findEventTypeById(eventTypeId);
            Event event = new Event(eventType, payload);
            databaseService.createEvent(event);
            sendPublishedEvent(eventType.getId(), event);
        }
    }

    public void sendPublishedEvent(Long id, Event event) {
        List<Long> userIdList = databaseService.getAllSubscribers(id);
        List<Socket> socketList = new ArrayList<>();
        ClientManager.getHashMap();
        System.out.println(ClientManager.getClientSocket(1617L));

        try {
            for (Long userId : userIdList) {
                socketList.add(ClientManager.getClientSocket(userId));
                PrintWriter printWriter2 = new PrintWriter(ClientManager.getClientSocket(userId).getOutputStream(), true);
                printWriter2.println("Printing event" + event.getPayload());
            }
        }catch (Exception e){

        }
    }

    public void subscribeToEventType(String message, PrintWriter writer) {
        String[] parts = message.split(",");
        if (parts.length == 3) {
            Long eventTypeId = Long.valueOf(parts[1].trim());
            Long userId = Long.valueOf(parts[2].trim());
            Subscription subscription = new Subscription(userId, eventTypeId);
            databaseService.createSubscription(subscription);
        }
    }
}

package com.example.MessageBroker.entities.user;

import com.example.MessageBroker.utilities.DatabaseService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class UserHandler implements Runnable {

    private final Socket clientSocket;
    private final DatabaseService databaseService;

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

                if (message.contains("REGISTER")) {
                    validateMessageForRegister(message, writer);
                } else {
                    writer.println("Message received: " + message);
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
}

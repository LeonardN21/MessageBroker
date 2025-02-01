package com.example.MessageBroker.client;

import com.example.MessageBroker.entities.user.Role;
import com.example.MessageBroker.entities.user.User;
import com.example.MessageBroker.entities.user.UserService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final UserService userService;

    public ClientHandler(Socket clientSocket, UserService userService) {
        this.clientSocket = clientSocket;
        this.userService = userService;
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

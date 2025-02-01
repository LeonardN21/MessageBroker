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
            System.out.println("TESTTTTTTTTTT");
            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("TEST");
                System.out.println("Received from client: " + message);

                // Example: Handle a registration request by saving user to the database
                if (message.startsWith("REGISTER:")) {
                    String[] parts = message.split(":");
                    if (parts.length == 3) {
                        String username = parts[1];
                        String password = parts[2];
                        registerClient(username, password);  // Register the client
                        writer.println("Client registered successfully");
                    }
                } else {
                    writer.println("Message received: " + message);  // Echo the message back
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }   catch (IOException e){

        }
    }

    private void registerClient(String username, String password) {
        // Create a new User object and set its properties
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(Role.CLIENT);

        // Save user to the database using UserService
        userService.saveUser(user);
    }
}

package com.example.MessageBroker;

import com.example.MessageBroker.client.ClientHandler;
import com.example.MessageBroker.entities.user.UserService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageBrokerApplication {

    private static final int PORT = 8080;
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);
    private static final UserService userService = new UserService();

    public static void main(String[] args) {
        new MessageBrokerApplication().startServer();
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Message Broker Server running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected");
                ClientHandler clientHandler = new ClientHandler(clientSocket, userService);
                pool.submit(clientHandler);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error starting server: " + e.getMessage(), e);
        }
    }
}

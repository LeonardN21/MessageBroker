package com.example.MessageBroker;

import com.example.MessageBroker.entities.user.UserHandler;
import com.example.MessageBroker.entities.user.UserService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageBrokerApplication {

    /*
        S1. Anfragen an den Message Broker müssen parallel in eigenen Prozessen oder
            Threads behandelt werden.
    ??? S5. Nachrichten müssen auch dann zugestellt werden, wenn ein Client vorrübergehend
            nicht erreichbar ist.

     */

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
                UserHandler userHandler = new UserHandler(clientSocket, userService);
                pool.submit(userHandler);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error starting server: " + e.getMessage(), e);
        }
    }
}

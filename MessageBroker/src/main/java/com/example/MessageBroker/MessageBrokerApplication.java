package com.example.MessageBroker;

import com.example.MessageBroker.client.ClientHandler;
import com.example.MessageBroker.entities.user.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootApplication
public class MessageBrokerApplication{
    @Autowired
    private TestClass testClass;

    @Autowired
    private UserService userService;
    private static final int PORT = 8080;

    private static ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args){
        SpringApplication.run(MessageBrokerApplication.class, args);
    }

    @PostConstruct
    public void startServe() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Message Broker Server running on port " + PORT);
            String message;
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected");
                ClientHandler clientHandler = new ClientHandler(clientSocket, userService);
                pool.submit(clientHandler); //
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

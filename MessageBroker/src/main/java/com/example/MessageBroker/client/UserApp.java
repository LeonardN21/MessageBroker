package com.example.MessageBroker.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class UserApp {

    private Socket socket;
    private Scanner scanner;

    public static void main(String[] args) {
        UserApp userApp = new UserApp();
        System.out.println("TEST");
        userApp.startClient();
    }

    public void startClient() {
        try {
            socket = new Socket("localhost", 8080);
            PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
            printWriter.flush();
            UserDialog userDialog = new UserDialog(printWriter);
            userDialog.startDialog();
        } catch (IOException e) {
            throw new RuntimeException("Error connecting to server: " + e.getMessage(), e);
        }
    }



}

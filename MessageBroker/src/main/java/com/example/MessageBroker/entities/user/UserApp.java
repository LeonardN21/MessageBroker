package com.example.MessageBroker.entities.user;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class UserApp {

    private Socket socket;

    public static void main(String[] args) {
        UserApp userApp = new UserApp();
        System.out.println("TEST");
        userApp.startClient();
    }

    public void startClient() {
        try {
            socket = new Socket("localhost", 8080);
            PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));            printWriter.flush();
            System.out.println(socket.getLocalPort());
            UserDialog userDialog = new UserDialog(printWriter, reader);
            userDialog.startDialog();
        } catch (IOException e) {
            throw new RuntimeException("Error connecting to server: " + e.getMessage(), e);
        }
    }



}

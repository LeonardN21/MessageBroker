package com.example.MessageBroker.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientApp {

    private Socket socket;

    public static void main(String[] args) {


        ClientApp clientApp = new ClientApp();
        clientApp.startClient();
    }

    public void startClient() {
        try {
            socket = new Socket("localhost", 8080);
            PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
            printWriter.flush();
            ClientDialog clientDialog = new ClientDialog(printWriter);
            clientDialog.startDialog();
        } catch (IOException e) {
            throw new RuntimeException("Error connecting to server: " + e.getMessage(), e);
        }
    }
}

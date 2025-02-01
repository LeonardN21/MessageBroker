package com.example.MessageBroker.client;

import com.example.MessageBroker.entities.user.Role;

import java.io.PrintWriter;
import java.util.Scanner;

public class ClientDialog {

    private PrintWriter printWriter;
    Scanner scanner = new Scanner(System.in);

    public ClientDialog(PrintWriter printWriter){
        this.printWriter = printWriter;
    }

    public void startDialog(){
        int choice = 0;
        while (choice != -1){
            System.out.println("Hello Client, choose one option");
            System.out.println("1. Register Client\n" +
                    "2.Log in\n");
            choice = scanner.nextInt();
            if(choice == 1){
                registerClient();
            }else{
                login();
            }
        }

    }

    public void registerClient(){
        scanner.nextLine();
        System.out.println("Choose a username:");
        String username = scanner.nextLine();
        System.out.println("Choose a password:");
        String password = scanner.nextLine();

        if (printWriter != null) {
            // Sends the registration request directly to the server
            printWriter.println("REGISTER, " + username + ", " + password + ", " + Role.CLIENT);
            printWriter.flush();
            System.out.println("Registration request sent to server.");
        }
    }

    public void login(){

    }

}

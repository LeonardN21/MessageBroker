package com.example.MessageBroker.entities.user;

import com.example.MessageBroker.admin.AdminDialog;
import com.example.MessageBroker.client.ClientDialog;
import com.example.MessageBroker.utilities.DatabaseService;
import com.example.MessageBroker.utilities.Encryptor;

import java.io.PrintWriter;
import java.util.Scanner;

public class UserDialog {

    private PrintWriter printWriter;
    Scanner scanner = new Scanner(System.in);
    DatabaseService databaseService;

    public UserDialog(PrintWriter printWriter){
        this.printWriter = printWriter;
    }

    public void startDialog(){
        databaseService = new DatabaseService();
        int choice = 0;
        while (choice != -1){
            System.out.println("Hello, choose one option");
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
        Encryptor encryptor = new Encryptor(password);
        password = encryptor.getPassword();
        if (printWriter != null) {
            // Sends the registration request directly to the server
            printWriter.println("REGISTER, " + username + ", " + password + ", " + Role.CLIENT);
            printWriter.flush();
            System.out.println("Registration request sent to server.");
        }
    }

    public void login(){
        scanner.nextLine();
        System.out.println("Username:");
        String username = scanner.nextLine();
        System.out.println("Password:");
        String password = scanner.nextLine();
        Encryptor encryptor = new Encryptor(password);
        User user = databaseService.findByUsernameAndPassword(username, encryptor.getPassword());
        System.out.println(user.getUsername() + " " + user.getPassword() + " " + user.getRole());
        if(user.getRole() == Role.CLIENT){
            ClientDialog clientDialog = new ClientDialog();
            clientDialog.testmethod();
        }else{
            AdminDialog adminDialog = new AdminDialog();
            adminDialog.testmethod();
        }
    }

}

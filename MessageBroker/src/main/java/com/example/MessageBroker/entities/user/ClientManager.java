package com.example.MessageBroker.entities.user;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientManager {
    private static final ConcurrentHashMap<Long, Socket> activeClients = new ConcurrentHashMap<>();

    public static void addClient(Long clientId, Socket socket) {
        activeClients.put(clientId, socket);
    }

    public static Socket getClientSocket(Long clientId) {
        return activeClients.get(clientId);
    }

    public static void removeClient(String clientId) {
        activeClients.remove(clientId);
    }

    public static void getHashMap(){
        activeClients.forEach((k, v) ->  System.out.println(k + " -> " + v));
    }

    public static Long getFirst(){
        Map.Entry<Long, Socket> entry = activeClients.entrySet().iterator().next();
        return entry.getKey();
    }
}
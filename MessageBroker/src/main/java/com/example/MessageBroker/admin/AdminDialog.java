package com.example.MessageBroker.admin;

import com.example.MessageBroker.entities.event.EventType;
import com.example.MessageBroker.utilities.DatabaseService;

public class AdminDialog {

    /*{
    M4. Über einen Admin-Client müssen Administratoren das System konfigurieren und
        administrieren können.
    M5. Administratoren müssen in der Lage sein Event-Typen zu definieren

    S3. Über den Admin-Client muss es möglich sein den Status des Brokers zu überwachen
        und Statistiken abzurufen.


     */

    public void testmethod() {
        System.out.println("hi admin");

        DatabaseService databaseService = new DatabaseService();
        EventType eventType = new EventType("checkTemperature4", "checks temperature");
        databaseService.createEventType(eventType);
        System.out.println("...");
        EventType eventType2 = databaseService.findEventTypeById(1L);
        System.out.println(eventType2.getEventTypeName() + eventType2.getDescription() + eventType2.getId());
    }
}

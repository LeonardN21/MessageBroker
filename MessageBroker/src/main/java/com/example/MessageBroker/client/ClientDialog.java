package com.example.MessageBroker.client;

import com.example.MessageBroker.entities.event.Event;
import com.example.MessageBroker.entities.event.EventType;
import com.example.MessageBroker.utilities.DatabaseService;

import java.util.Date;

public class ClientDialog {

    /*
          M2. Über eine API muss es möglich sein, dass Client-Anwendungen sich an dem Service
            registrieren und Events publishen und subscriben.
//        M3. Es muss eine Client-Anwendung geben, über die Nutzer manuell Events publishen
            und subscriben können.
     */

    public void testmethod() {
        System.out.println("hi client");
        DatabaseService databaseService = new DatabaseService();
        Long eventTypeId = databaseService.findEventTypeIdByEventTypeName("checkTemperature3");
        EventType eventType = databaseService.findEventTypeById(eventTypeId);
        Event event = new Event(eventType, "test");
        databaseService.createEvent(event);
    }

}

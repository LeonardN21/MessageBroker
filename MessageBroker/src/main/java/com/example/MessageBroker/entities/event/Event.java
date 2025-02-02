package com.example.MessageBroker.entities.event;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Event {

    private Long id;
    private EventType eventType;
    private String payload;
    private Date timestamp;

    public Event(EventType eventType, String payload){
        this.eventType = eventType;
        this.payload = payload;
    }

}

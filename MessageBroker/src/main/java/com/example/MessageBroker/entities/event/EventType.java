package com.example.MessageBroker.entities.event;

import com.mysql.cj.util.SaslPrep;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventType {

    private Long id;
    private String eventTypeName;
    private String description;

    public EventType(String eventTypeName, String description) {
        this.eventTypeName = eventTypeName;
        this.description = description;
    }

    public EventType() {
    }


}

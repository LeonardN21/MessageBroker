package com.example.MessageBroker.entities.subscription;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Subscription {
    private Long id;
    private Long eventTypeId;
    private Long userId;

    public Subscription(Long userId, Long eventTypeId){
        this.eventTypeId = eventTypeId;
        this.userId = userId;
    }

}

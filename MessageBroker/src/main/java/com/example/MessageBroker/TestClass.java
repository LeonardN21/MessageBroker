package com.example.MessageBroker;

import com.example.MessageBroker.entities.user.Role;
import com.example.MessageBroker.entities.user.User;
import com.example.MessageBroker.entities.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestClass {

   @Autowired
   private UserService userService;

    public void saveUser(){

    }

}

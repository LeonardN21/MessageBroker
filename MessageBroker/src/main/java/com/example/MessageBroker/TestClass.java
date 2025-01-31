package com.example.MessageBroker;

import com.example.MessageBroker.entities.user.Role;
import com.example.MessageBroker.entities.user.User;
import com.example.MessageBroker.entities.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestClass {

   @Autowired
   UserService userService;

    public void saveUser(){
        int i = 0;
        while(i < 1000){
            User user = new User();
            user.setRole(Role.CLIENT);
            userService.saveUser(user);
            i++;
        }
    }

}

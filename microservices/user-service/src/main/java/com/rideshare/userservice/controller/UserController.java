package com.rideshare.userservice.controller;

import com.rideshare.userservice.dto.UpdateUserRequest;
import com.rideshare.userservice.dto.UserResponse;
import com.rideshare.userservice.service.UserServices;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserServices userServices;

    public UserController(UserServices userServices) {
        this.userServices = userServices;
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id){
        System.out.println("GET/ users/ " + id + " arrived");
        return userServices.getUser(id);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id,
                                   @RequestBody UpdateUserRequest updateUserRequest){
        System.out.println("POST/ users/ " + id + " arrived");
        return userServices.updateUser(id, updateUserRequest);
    }
}

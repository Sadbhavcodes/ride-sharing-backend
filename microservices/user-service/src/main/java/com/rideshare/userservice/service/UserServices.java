package com.rideshare.userservice.service;

import com.rideshare.userservice.dto.UpdateUserRequest;
import com.rideshare.userservice.dto.UserResponse;
import com.rideshare.userservice.entity.User;
import com.rideshare.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class UserServices {

    private final UserRepository userRepository;

    public UserServices(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getUser(Long id){
        System.out.println("Checking if user exists...");
        Optional<User> user = userRepository.findById(id);
        if(user.isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
            );
        }

        User foundUser = user.get();
        return new UserResponse(
                foundUser.getId(),
                foundUser.getUsername(),
                foundUser.getEmail(),
                foundUser.getPhoneNumber(),
                foundUser.getRole()
        );
    }

    public UserResponse updateUser(Long id, UpdateUserRequest request){
        System.out.println("Checking if user exists...");
        Optional<User> user = userRepository.findById(id);
        if(user.isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
            );
        }
        System.out.println("Checking if email already exists...");
        if(userRepository.existsByEmail(request.email())){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email already exists"
            );
        }
        User foundUser = user.get();
        foundUser.setEmail(request.email());
        foundUser.setUsername(request.username());
        foundUser = userRepository.save(foundUser);
        return new UserResponse(
                foundUser.getId(),
                foundUser.getUsername(),
                foundUser.getEmail(),
                foundUser.getPhoneNumber(),
                foundUser.getRole()
        );
    }
}

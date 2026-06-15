package com.rideshare.userservice.service;

import com.rideshare.userservice.dto.LoginRequest;
import com.rideshare.userservice.dto.LoginResponse;
import com.rideshare.userservice.dto.RegisterRequest;
import com.rideshare.userservice.dto.RegisterResponse;
import com.rideshare.userservice.entity.User;
import com.rideshare.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public RegisterResponse register(RegisterRequest request){
        System.out.println("Checking if email exists...");
        if(userRepository.existsByEmail(request.email())){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email already exists"
            );
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(request.password());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(request.role());
        user = userRepository.save(user);
        System.out.println("User saved in database");

        return new RegisterResponse(
                user.getId(),
                user.getUsername(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRole()
        );
    }
    public LoginResponse login(LoginRequest loginRequest){
        System.out.println("Checking if email already exists...");
        Optional<User> existByEmail = userRepository.findByEmail(loginRequest.email());
        if(existByEmail.isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid email or password"
            );
        }

        User user = existByEmail.get();

        System.out.println("Checking if password matches...");
        if(!user.getPassword().equals(loginRequest.password())){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid email or password"
            );
        }
        return new LoginResponse(
                "Login Success"
        );
    }
}

package com.rideshare.userservice.controller;

import com.rideshare.userservice.dto.LoginRequest;
import com.rideshare.userservice.dto.LoginResponse;
import com.rideshare.userservice.dto.RegisterRequest;
import com.rideshare.userservice.dto.RegisterResponse;
import com.rideshare.userservice.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request){
        System.out.println("Register request arrived safely at endpoint");
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request){
        System.out.println("Login request arrived safely at endpoint");
        return authService.login(request);
    }
}

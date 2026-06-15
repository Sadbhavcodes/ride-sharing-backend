package com.rideshare.userservice.dto;

public record LoginRequest(
        String email,
        String password
) {
}

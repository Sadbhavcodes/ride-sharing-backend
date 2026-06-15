package com.rideshare.userservice.dto;

public record UpdateUserRequest(
        String username,
        String email
) {
}

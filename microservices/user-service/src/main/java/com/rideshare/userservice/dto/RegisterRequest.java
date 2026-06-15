package com.rideshare.userservice.dto;

import com.rideshare.userservice.entity.Role;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String phoneNumber,
        Role role
) {
}

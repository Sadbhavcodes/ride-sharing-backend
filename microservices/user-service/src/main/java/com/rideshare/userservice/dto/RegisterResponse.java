package com.rideshare.userservice.dto;

import com.rideshare.userservice.entity.Role;

public record RegisterResponse(
        Long id,
        String username,
        String phoneNumber,
        String email,
        Role role
) {
}

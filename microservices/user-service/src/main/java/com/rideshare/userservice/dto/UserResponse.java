package com.rideshare.userservice.dto;

import com.rideshare.userservice.entity.Role;

public record UserResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        Role role
) {
}

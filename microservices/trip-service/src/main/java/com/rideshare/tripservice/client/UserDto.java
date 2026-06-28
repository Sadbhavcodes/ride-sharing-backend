package com.rideshare.tripservice.client;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String phoneNumber;
    private String role;
}

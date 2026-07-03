package com.rideshare.matchingservice.dto;

public record NearestDriverRequest(
    Double longitude,
    Double latitude,
    Double radius
) {
}

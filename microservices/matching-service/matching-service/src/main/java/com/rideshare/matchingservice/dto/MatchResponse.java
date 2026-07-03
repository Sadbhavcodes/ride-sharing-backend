package com.rideshare.matchingservice.dto;

public record MatchResponse(
        Long tripId,
        Long driverId
) {
}

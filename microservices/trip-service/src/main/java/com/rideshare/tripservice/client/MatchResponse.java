package com.rideshare.tripservice.client;

public record MatchResponse(
        Long tripId,
        Long driverId
) {
}

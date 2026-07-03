package com.rideshare.tripservice.client;

import com.rideshare.tripservice.dto.Coordinate;

public record MatchRequest(
        Long tripId,
        Long riderId,
        Coordinate pickup,
        Coordinate destination
) {
}

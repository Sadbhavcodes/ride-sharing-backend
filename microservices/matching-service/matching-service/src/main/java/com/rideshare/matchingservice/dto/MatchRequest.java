package com.rideshare.matchingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MatchRequest(
        @NotNull Long tripId,
        @NotNull Long riderId,
        @Valid @NotNull Coordinate pickup,
        @Valid @NotNull Coordinate destination
) {
}

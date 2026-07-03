package com.rideshare.tripservice.dto;

import jakarta.validation.constraints.NotNull;

public record Coordinate(
        @NotNull Double latitude,
        @NotNull Double longitude
) {
}

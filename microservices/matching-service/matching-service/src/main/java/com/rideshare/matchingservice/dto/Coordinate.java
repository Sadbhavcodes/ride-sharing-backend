package com.rideshare.matchingservice.dto;

import jakarta.validation.constraints.NotNull;

public record Coordinate(
        @NotNull Double latitude,
        @NotNull Double longitude
) {
}
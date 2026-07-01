package com.rideshare.locationservice.dto;

public record NearestDriverRequest(
        Double longitude,
        Double latitude
) {
}

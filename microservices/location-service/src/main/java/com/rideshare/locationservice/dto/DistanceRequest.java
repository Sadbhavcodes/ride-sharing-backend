package com.rideshare.locationservice.dto;

public record DistanceRequest(
        Double fromLatitude,
        Double fromLongitude,
        Double toLatitude,
        Double toLongitude
) {
}

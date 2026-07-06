package com.rideshare.tripservice.client;

public record DistanceRequest(
        Double fromLatitude,
        Double fromLongitude,
        Double toLatitude,
        Double toLongitude
) {
}

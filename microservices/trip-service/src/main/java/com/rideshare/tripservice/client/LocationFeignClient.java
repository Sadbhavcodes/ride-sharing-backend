package com.rideshare.tripservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for location-service.
 *
 * Trip-service calls this when a trip is COMPLETED to obtain the straight-line
 * distance between pickup and drop. The location-service is the single authority
 * for all geo calculations — no distance logic lives inside trip-service.
 */
@FeignClient(name = "LOCATIONSERVICE", path = "/locations")
public interface LocationFeignClient {

    @PostMapping("/distance")
    DistanceResponse calculateDistance(@RequestBody DistanceRequest request);
}

package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.dto.DriverDto;
import com.rideshare.matchingservice.dto.DriverLocationResponse;
import com.rideshare.matchingservice.dto.MatchRequest;
import com.rideshare.matchingservice.dto.MatchResponse;
import com.rideshare.matchingservice.dto.NearestDriverRequest;
import com.rideshare.matchingservice.exception.NoDriverAvailableException;
import com.rideshare.matchingservice.feign.DriverFeignClient;
import com.rideshare.matchingservice.feign.LocationFeignClient;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MatchingService {
    private final LocationFeignClient locationFeignClient;
    private final DriverFeignClient driverFeignClient;

    public MatchingService(LocationFeignClient locationFeignClient,
                           DriverFeignClient driverFeignClient) {
        this.locationFeignClient = locationFeignClient;
        this.driverFeignClient = driverFeignClient;
    }

    public MatchResponse matchDriver(MatchRequest request) {
        double[] radii = {3000, 5000, 8000, 12000, 20000}; 

        for (double radius : radii) {
            NearestDriverRequest nearestDriverRequest = new NearestDriverRequest(
                    request.pickup().longitude(),
                    request.pickup().latitude(),
                    radius
            );

            List<DriverLocationResponse> nearbyDrivers = locationFeignClient.findNearbyDrivers(
                    nearestDriverRequest
            );

            if (nearbyDrivers.isEmpty()) {
                continue;
            }

            List<Long> nearbyDriverIds = nearbyDrivers.stream()
                    .map(DriverLocationResponse::driverId)
                    .toList();

            List<DriverDto> availableDrivers = driverFeignClient.getAvailableDrivers(nearbyDriverIds);

            if (availableDrivers.isEmpty()) {
                continue;
            }

            Set<Long> availableDriverIds = availableDrivers.stream()
                    .map(DriverDto::id)
                    .collect(Collectors.toSet());

            for (DriverLocationResponse candidate : nearbyDrivers) {
                Long candidateId = candidate.driverId();

                if (!availableDriverIds.contains(candidateId)) {
                    continue;
                }

                try {
                    driverFeignClient.claimDriver(candidateId);
                    return new MatchResponse(request.tripId(), candidateId);

                } catch (FeignException.Conflict e) {
                    continue;
                }
            }
        }
        throw new NoDriverAvailableException("No drivers available even in expanded radius");
    }
}
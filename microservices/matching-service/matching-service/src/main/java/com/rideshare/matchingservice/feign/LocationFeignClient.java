package com.rideshare.matchingservice.feign;

import com.rideshare.matchingservice.dto.DriverLocationResponse;
import com.rideshare.matchingservice.dto.NearestDriverRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "locationservice")
public interface LocationFeignClient {

    @PostMapping("/drivers/nearby")
    List<DriverLocationResponse> findNearbyDrivers(
            @RequestBody NearestDriverRequest request
            );
}

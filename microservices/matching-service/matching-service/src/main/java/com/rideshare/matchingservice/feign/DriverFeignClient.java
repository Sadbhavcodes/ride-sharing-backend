package com.rideshare.matchingservice.feign;

import com.rideshare.matchingservice.dto.DriverDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "driverservice")
public interface DriverFeignClient {

    @PostMapping("/drivers/available")
    List<DriverDto> getAvailableDrivers(@RequestBody List<Long> driverIds);

    @PostMapping("/drivers/{id}/claim")
    DriverDto claimDriver(@PathVariable("id") Long driverId);
}

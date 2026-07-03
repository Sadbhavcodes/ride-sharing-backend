package com.rideshare.tripservice.controller;

import com.rideshare.tripservice.dto.AssignDriverRequest;
import com.rideshare.tripservice.dto.CancelTripResponse;
import com.rideshare.tripservice.dto.CreateTripRequest;
import com.rideshare.tripservice.dto.UpdateTripStatusRequest;
import com.rideshare.tripservice.entity.Trip;
import com.rideshare.tripservice.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/trips")
public class TripController {
    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Trip createTrip(@Valid @RequestBody CreateTripRequest request){
        return tripService.createTrip(request);
    }

    @GetMapping("/{id}")
    public Trip getTripById(@PathVariable Long id){
        return tripService.getTripById(id);
    }

    @GetMapping("/rider/{riderId}")
    public List<Trip> getTripsByRiderId(@PathVariable Long riderId){
        return tripService.getTripsByRiderId(riderId);
    }

    @GetMapping("/driver/{driverId}")
    public List<Trip> getTripsByDriverId(@PathVariable Long driverId){
        return tripService.getTripsByDriverId(driverId);
    }

    @PatchMapping("/{id}/assign-driver")
    public Trip assignDriverToTrip
            (@PathVariable Long id, @RequestBody AssignDriverRequest request){
        return tripService.assignDriver(id, request);
    }

    @PatchMapping("/{id}/status")
    public Trip updateTripStatus
            (@PathVariable Long id, @RequestBody UpdateTripStatusRequest request){
        return tripService.updateTripStatus(id,request);
    }

    @PostMapping("/{id}/cancel")
    public CancelTripResponse cancelTrip(@PathVariable Long id){

    }
}

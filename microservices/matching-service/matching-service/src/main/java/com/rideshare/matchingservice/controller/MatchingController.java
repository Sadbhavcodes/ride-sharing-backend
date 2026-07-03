package com.rideshare.matchingservice.controller;

import com.rideshare.matchingservice.dto.MatchRequest;
import com.rideshare.matchingservice.dto.MatchResponse;
import com.rideshare.matchingservice.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matching")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/match")
    public MatchResponse matchDriver(@Valid @RequestBody MatchRequest request) {
        return matchingService.matchDriver(request);
    }
}

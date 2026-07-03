package com.rideshare.tripservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "matchingservice")
public interface MatchingFeignClient {

    @PostMapping("/matching/match")
    MatchResponse findMatch(@RequestBody MatchRequest request);
}

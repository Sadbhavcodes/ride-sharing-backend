package com.rideshare.driverservice.client;

import com.rideshare.driverservice.exception.UserNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

@Component
public class FeignClientErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {

        if (response.status() == 404 &&
                methodKey.contains("UserFeignClient")) {
            // Extract the user id from the request URL
            String url = response.request().url();
            String[] parts = url.split("/");
            Long userId = null;
            try {
                userId = Long.parseLong(parts[parts.length - 1]);
            } catch (NumberFormatException ignored) {}

            return new UserNotFoundException(userId);
        }

        return defaultDecoder.decode(methodKey, response);
    }
}

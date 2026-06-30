package dto;

import java.util.List;

public record NearestDriverResponse(
        List<Long> drivers
) {
}

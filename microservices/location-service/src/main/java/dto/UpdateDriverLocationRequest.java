package dto;

public record UpdateDriverLocationRequest(
        Long driverId,
        Double longitude,
        Double latitude
) {
}

package dto;

public record DriverLocationResponse(
        Long driverId,
        Double longitude,
        Double latitude
) {
}

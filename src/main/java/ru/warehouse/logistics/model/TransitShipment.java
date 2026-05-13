package ru.warehouse.logistics.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransitShipment(
        UUID          id,
        String        trackingNumber,
        String        carrier,
        UUID          supplierId,
        String        supplierName,
        String        origin,
        String        destination,
        LocalDate     departureDate,
        LocalDate     expectedArrival,
        LocalDate     actualArrival,
        Status        status,
        String        notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public enum Status { PLANNED, IN_TRANSIT, DELIVERED, DELAYED, CANCELLED }
}

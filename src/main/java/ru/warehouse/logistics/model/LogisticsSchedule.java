package ru.warehouse.logistics.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record LogisticsSchedule(
        UUID      id,
        String    driverName,
        String    vehicle,
        String    route,
        LocalDate workDate,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        Status    status,
        UUID      shipmentId,
        String    shipmentTracking,
        String    notes
) {
    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }
}

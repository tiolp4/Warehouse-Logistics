package ru.warehouse.logistics.service;

import ru.warehouse.logistics.model.LogisticsSchedule;
import ru.warehouse.logistics.model.Supplier;
import ru.warehouse.logistics.model.TransitShipment;
import ru.warehouse.logistics.util.ApiClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class LogisticsService {

    private final ApiClient api = ApiClient.getInstance();

    public List<Supplier> getAllSuppliers() throws IOException {
        return api.listSuppliers();
    }

    // ── Transit shipments ─────────────────────────────────────

    public List<TransitShipment> getAllShipments() throws IOException {
        return api.listShipments();
    }

    public UUID createShipment(TransitShipment s) throws IOException {
        if (s.trackingNumber() == null || s.trackingNumber().isBlank())
            throw new IllegalArgumentException("Tracking number required");
        if (s.expectedArrival().isBefore(s.departureDate()))
            throw new IllegalArgumentException("Ожидаемая дата прибытия раньше даты отправки");
        return api.createShipment(s).id();
    }

    public void changeStatus(UUID id, TransitShipment.Status status) throws IOException {
        api.changeShipmentStatus(id, status);
    }

    public void markArrived(UUID id, LocalDate actualDate) throws IOException {
        api.markShipmentArrived(id, actualDate);
    }

    public void deleteShipment(UUID id) throws IOException {
        api.deleteShipment(id);
    }

    // ── Schedules ─────────────────────────────────────────────

    public List<LogisticsSchedule> getAllSchedules() throws IOException {
        return api.listSchedules(null, null);
    }

    public List<LogisticsSchedule> getSchedulesBetween(LocalDate from, LocalDate to) throws IOException {
        return api.listSchedules(from, to);
    }

    public UUID createSchedule(LogisticsSchedule s) throws IOException {
        if (s.shiftEnd().isBefore(s.shiftStart()))
            throw new IllegalArgumentException("Окончание смены раньше начала");
        return api.createSchedule(s).id();
    }

    public void changeScheduleStatus(UUID id, LogisticsSchedule.Status status) throws IOException {
        api.changeScheduleStatus(id, status);
    }

    public void deleteSchedule(UUID id) throws IOException {
        api.deleteSchedule(id);
    }
}

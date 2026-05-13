package ru.warehouse.logistics.service;

import ru.warehouse.logistics.util.ApiClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Wraps API analytics endpoints with the same shape used by the UI. */
public class AnalyticsService {

    private final ApiClient api = ApiClient.getInstance();

    public record DailyFlow(LocalDate date, long planned, long arrived) {}
    public record KpiSummary(
            long shipmentsTotal,
            long shipmentsInTransit,
            long shipmentsDelivered,
            long shipmentsDelayed,
            long schedulesTotal,
            long schedulesToday,
            long schedulesCompleted,
            long schedulesActive) {}

    public KpiSummary kpiSummary() throws IOException {
        var k = api.kpi();
        return new KpiSummary(
                k.shipmentsTotal(), k.shipmentsInTransit(), k.shipmentsDelivered(),
                k.shipmentsDelayed(), k.schedulesTotal(), k.schedulesToday(),
                k.schedulesCompleted(), k.schedulesActive());
    }

    public Map<LocalDate, DailyFlow> dailyShipmentFlow(int days) throws IOException {
        Map<LocalDate, DailyFlow> out = new LinkedHashMap<>();
        for (var f : api.shipmentFlow(days)) {
            out.put(f.date(), new DailyFlow(f.date(), f.incoming(), f.outgoing()));
        }
        return out;
    }

    public Map<String, Long> transitStatusBreakdown() throws IOException {
        return countMap(api.transitStatus());
    }

    public Map<String, Long> scheduleStatusBreakdown() throws IOException {
        return countMap(api.scheduleStatus());
    }

    public Map<String, Long> topCarriers(int limit) throws IOException {
        return countMap(api.topCarriers(limit));
    }

    public Map<String, Long> topDrivers(int limit) throws IOException {
        return countMap(api.topDrivers(limit));
    }

    private Map<String, Long> countMap(java.util.List<ApiClient.CountEntry> list) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (var e : list) m.put(e.key(), e.count());
        return m;
    }
}

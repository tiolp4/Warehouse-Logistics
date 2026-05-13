package ru.warehouse.logistics.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.warehouse.logistics.model.LogisticsSchedule;
import ru.warehouse.logistics.model.Supplier;
import ru.warehouse.logistics.model.TransitShipment;
import ru.warehouse.logistics.model.User;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Thin HTTP wrapper over warehouse-api.
 * Handles JWT auth, JSON marshalling and DTO mapping.
 */
public final class ApiClient {

    public record LoginResult(String token, long expiresAt, User user) {}
    public record Kpi(long shipmentsTotal, long shipmentsInTransit,
                      long shipmentsDelivered, long shipmentsDelayed,
                      long schedulesTotal, long schedulesToday,
                      long schedulesCompleted, long schedulesActive) {}
    public record DailyFlow(LocalDate date, long incoming, long outgoing) {}
    public record CountEntry(String key, long count) {}

    private static volatile ApiClient instance;

    private volatile HttpClient http;
    private final ObjectMapper json;
    private String baseUrl;          // not final: override-able in tests via reflection
    private final Duration timeout;

    private HttpClient http() {
        HttpClient h = http;
        if (h == null) {
            synchronized (this) {
                h = http;
                if (h == null) {
                    h = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .version(HttpClient.Version.HTTP_1_1)
                            .build();
                    http = h;
                }
            }
        }
        return h;
    }

    private ApiClient() {
        Properties p = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/api.properties")) {
            if (is != null) p.load(is);
        } catch (IOException ignored) {}
        String overrideUrl = System.getenv("API_BASE_URL");
        this.baseUrl = (overrideUrl != null && !overrideUrl.isBlank())
                ? stripTrailingSlash(overrideUrl)
                : stripTrailingSlash(p.getProperty("api.base.url",
                        "http://127.0.0.1:8080/api"));
        this.timeout = Duration.ofSeconds(
                Long.parseLong(p.getProperty("api.timeout.seconds", "15")));
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static ApiClient getInstance() {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) instance = new ApiClient();
            }
        }
        return instance;
    }

    public String baseUrl() { return baseUrl; }

    // ── Auth ──────────────────────────────────────────────────

    public LoginResult login(String username, String password) throws IOException {
        Map<String, String> body = Map.of("username", username, "password", password);
        JsonNode root = postJsonRaw("/v1/auth/login", body, null);
        String token = root.get("token").asText();
        long exp = root.get("expiresAt").asLong();
        var u = root.get("user");
        User user = new User(
                UUID.fromString(u.get("id").asText()),
                u.get("username").asText(),
                null,
                User.Role.valueOf(u.get("role").asText()),
                u.get("fullName").asText(),
                LocalDateTime.now());
        return new LoginResult(token, exp, user);
    }

    // ── Suppliers ─────────────────────────────────────────────

    public List<Supplier> listSuppliers() throws IOException {
        return get("/v1/suppliers", new TypeReference<List<Map<String, Object>>>() {})
                .stream()
                .map(m -> new Supplier(
                        UUID.fromString((String) m.get("id")),
                        (String) m.get("name"),
                        (String) m.get("contact"),
                        (String) m.get("phone"),
                        (String) m.get("email")))
                .toList();
    }

    // ── Shipments ─────────────────────────────────────────────

    public List<TransitShipment> listShipments() throws IOException {
        return get("/v1/shipments", new TypeReference<List<Map<String, Object>>>() {})
                .stream().map(ApiClient::mapShipment).toList();
    }

    public TransitShipment createShipment(TransitShipment t) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("trackingNumber", t.trackingNumber());
        req.put("carrier", t.carrier());
        req.put("supplierId", t.supplierId() == null ? null : t.supplierId().toString());
        req.put("origin", t.origin());
        req.put("destination", t.destination());
        req.put("departureDate", t.departureDate().toString());
        req.put("expectedArrival", t.expectedArrival().toString());
        req.put("notes", t.notes());
        return mapShipment(postJson("/v1/shipments", req));
    }

    public void changeShipmentStatus(UUID id, TransitShipment.Status status) throws IOException {
        patchJson("/v1/shipments/" + id + "/status",
                Map.of("status", status.name()));
    }

    public void markShipmentArrived(UUID id, LocalDate date) throws IOException {
        patchJson("/v1/shipments/" + id + "/arrived",
                Map.of("actualArrival", date.toString()));
    }

    public void deleteShipment(UUID id) throws IOException {
        delete("/v1/shipments/" + id);
    }

    // ── Schedules ─────────────────────────────────────────────

    public List<LogisticsSchedule> listSchedules(LocalDate from, LocalDate to) throws IOException {
        String path = "/v1/schedules";
        if (from != null && to != null) {
            path += "?from=" + URLEncoder.encode(from.toString(), StandardCharsets.UTF_8)
                  + "&to="   + URLEncoder.encode(to.toString(),   StandardCharsets.UTF_8);
        }
        return get(path, new TypeReference<List<Map<String, Object>>>() {})
                .stream().map(ApiClient::mapSchedule).toList();
    }

    public LogisticsSchedule createSchedule(LogisticsSchedule s) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("driverName", s.driverName());
        req.put("vehicle",    s.vehicle());
        req.put("route",      s.route());
        req.put("workDate",   s.workDate().toString());
        req.put("shiftStart", s.shiftStart().toString());
        req.put("shiftEnd",   s.shiftEnd().toString());
        req.put("shipmentId", s.shipmentId() == null ? null : s.shipmentId().toString());
        req.put("notes",      s.notes());
        return mapSchedule(postJson("/v1/schedules", req));
    }

    public void changeScheduleStatus(UUID id, LogisticsSchedule.Status status) throws IOException {
        patchJson("/v1/schedules/" + id + "/status",
                Map.of("status", status.name()));
    }

    public void deleteSchedule(UUID id) throws IOException {
        delete("/v1/schedules/" + id);
    }

    // ── Analytics ─────────────────────────────────────────────

    public Kpi kpi() throws IOException {
        Map<String, Object> m = get("/v1/analytics/kpi", new TypeReference<>() {});
        return new Kpi(
                num(m, "shipmentsTotal"), num(m, "shipmentsInTransit"),
                num(m, "shipmentsDelivered"), num(m, "shipmentsDelayed"),
                num(m, "schedulesTotal"), num(m, "schedulesToday"),
                num(m, "schedulesCompleted"), num(m, "schedulesActive"));
    }

    public List<DailyFlow> shipmentFlow(int days) throws IOException {
        return get("/v1/analytics/shipment-flow?days=" + days,
                new TypeReference<List<Map<String, Object>>>() {})
                .stream()
                .map(m -> new DailyFlow(
                        LocalDate.parse((String) m.get("date")),
                        num(m, "incoming"), num(m, "outgoing")))
                .toList();
    }

    public List<CountEntry> transitStatus() throws IOException { return countList("/v1/analytics/transit-status"); }
    public List<CountEntry> scheduleStatus() throws IOException { return countList("/v1/analytics/schedule-status"); }
    public List<CountEntry> topCarriers(int limit) throws IOException { return countList("/v1/analytics/top-carriers?limit=" + limit); }
    public List<CountEntry> topDrivers(int limit) throws IOException { return countList("/v1/analytics/top-drivers?limit=" + limit); }

    private List<CountEntry> countList(String path) throws IOException {
        return get(path, new TypeReference<List<Map<String, Object>>>() {})
                .stream()
                .map(m -> new CountEntry((String) m.get("key"), num(m, "count")))
                .toList();
    }

    // ── HTTP plumbing ─────────────────────────────────────────

    private HttpRequest.Builder builder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Accept", "application/json");
        String token = Session.getToken();
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private <T> T get(String path, TypeReference<T> type) throws IOException {
        HttpResponse<String> r = send(builder(path).GET().build());
        return readBody(r, type);
    }

    private Map<String, Object> postJson(String path, Object body) throws IOException {
        HttpResponse<String> r = send(builder(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build());
        return readBody(r, new TypeReference<Map<String, Object>>() {});
    }

    private JsonNode postJsonRaw(String path, Object body, String overrideToken) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (overrideToken != null) b.header("Authorization", "Bearer " + overrideToken);
        HttpResponse<String> r = send(b.POST(HttpRequest.BodyPublishers.ofString(
                json.writeValueAsString(body))).build());
        ensureSuccess(r);
        return new JsonNode(json.readTree(r.body()));
    }

    private void patchJson(String path, Object body) throws IOException {
        HttpResponse<String> r = send(builder(path)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build());
        ensureSuccess(r);
    }

    private void delete(String path) throws IOException {
        HttpResponse<String> r = send(builder(path).DELETE().build());
        ensureSuccess(r);
    }

    private HttpResponse<String> send(HttpRequest req) throws IOException {
        try {
            return http().send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private <T> T readBody(HttpResponse<String> r, TypeReference<T> type) throws IOException {
        ensureSuccess(r);
        if (r.body() == null || r.body().isBlank()) return null;
        return json.readValue(r.body(), type);
    }

    private void ensureSuccess(HttpResponse<String> r) throws IOException {
        if (r.statusCode() / 100 != 2) {
            String body = r.body() == null ? "" : r.body();
            String msg;
            try {
                var node = json.readTree(body);
                msg = node.has("error") ? node.get("error").asText() : body;
            } catch (Exception ignored) { msg = body; }
            throw new IOException("HTTP " + r.statusCode() + ": " + msg);
        }
    }

    // ── Mappers ───────────────────────────────────────────────

    private static TransitShipment mapShipment(Map<String, Object> m) {
        return new TransitShipment(
                UUID.fromString((String) m.get("id")),
                (String) m.get("trackingNumber"),
                (String) m.get("carrier"),
                m.get("supplierId")   == null ? null : UUID.fromString((String) m.get("supplierId")),
                (String) m.get("supplierName"),
                (String) m.get("origin"),
                (String) m.get("destination"),
                LocalDate.parse((String) m.get("departureDate")),
                LocalDate.parse((String) m.get("expectedArrival")),
                m.get("actualArrival") == null ? null : LocalDate.parse((String) m.get("actualArrival")),
                TransitShipment.Status.valueOf((String) m.get("status")),
                (String) m.get("notes"),
                m.get("createdAt") == null ? null : LocalDateTime.parse((String) m.get("createdAt")),
                m.get("updatedAt") == null ? null : LocalDateTime.parse((String) m.get("updatedAt"))
        );
    }

    private static LogisticsSchedule mapSchedule(Map<String, Object> m) {
        return new LogisticsSchedule(
                UUID.fromString((String) m.get("id")),
                (String) m.get("driverName"),
                (String) m.get("vehicle"),
                (String) m.get("route"),
                LocalDate.parse((String) m.get("workDate")),
                LocalTime.parse((String) m.get("shiftStart")),
                LocalTime.parse((String) m.get("shiftEnd")),
                LogisticsSchedule.Status.valueOf((String) m.get("status")),
                m.get("shipmentId") == null ? null : UUID.fromString((String) m.get("shipmentId")),
                (String) m.get("shipmentTracking"),
                (String) m.get("notes"));
    }

    private static long num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Thin JsonNode wrapper to keep Jackson off the public surface. */
    public static final class JsonNode {
        private final com.fasterxml.jackson.databind.JsonNode n;
        JsonNode(com.fasterxml.jackson.databind.JsonNode n) { this.n = n; }
        public JsonNode get(String f) { return new JsonNode(n.get(f)); }
        public String asText() { return n.asText(); }
        public long asLong() { return n.asLong(); }
    }
}

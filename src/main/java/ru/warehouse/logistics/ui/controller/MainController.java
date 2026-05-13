package ru.warehouse.logistics.ui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ru.warehouse.logistics.model.LogisticsSchedule;
import ru.warehouse.logistics.model.TransitShipment;
import ru.warehouse.logistics.service.AnalyticsService;
import ru.warehouse.logistics.service.LogisticsService;
import ru.warehouse.logistics.ui.dialog.LogisticsScheduleDialog;
import ru.warehouse.logistics.ui.dialog.TransitShipmentDialog;
import ru.warehouse.logistics.util.Session;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private Label userLabel;
    @FXML private Label statusLabel;

    // Transit tab
    @FXML private TableView<TransitShipment>           transitTable;
    @FXML private TableColumn<TransitShipment, String> colTrTracking;
    @FXML private TableColumn<TransitShipment, String> colTrCarrier;
    @FXML private TableColumn<TransitShipment, String> colTrSupplier;
    @FXML private TableColumn<TransitShipment, String> colTrOrigin;
    @FXML private TableColumn<TransitShipment, String> colTrDest;
    @FXML private TableColumn<TransitShipment, String> colTrDeparture;
    @FXML private TableColumn<TransitShipment, String> colTrExpected;
    @FXML private TableColumn<TransitShipment, String> colTrActual;
    @FXML private TableColumn<TransitShipment, String> colTrStatus;
    @FXML private Button btnTransitInTransit;
    @FXML private Button btnTransitArrived;
    @FXML private Button btnTransitCancel;

    // Schedule tab
    @FXML private TableView<LogisticsSchedule>           scheduleTable;
    @FXML private TableColumn<LogisticsSchedule, String> colSchDate;
    @FXML private TableColumn<LogisticsSchedule, String> colSchDriver;
    @FXML private TableColumn<LogisticsSchedule, String> colSchVehicle;
    @FXML private TableColumn<LogisticsSchedule, String> colSchRoute;
    @FXML private TableColumn<LogisticsSchedule, String> colSchStart;
    @FXML private TableColumn<LogisticsSchedule, String> colSchEnd;
    @FXML private TableColumn<LogisticsSchedule, String> colSchStatus;
    @FXML private TableColumn<LogisticsSchedule, String> colSchShip;
    @FXML private DatePicker schedFromPicker;
    @FXML private DatePicker schedToPicker;
    @FXML private Button btnSchedStart;
    @FXML private Button btnSchedDone;
    @FXML private Button btnSchedCancel;

    // Analytics
    @FXML private Spinner<Integer> periodSpinner;
    @FXML private VBox kpiShipments;
    @FXML private VBox kpiInTransit;
    @FXML private VBox kpiDelivered;
    @FXML private VBox kpiDelayed;
    @FXML private VBox kpiSchedules;
    @FXML private VBox kpiSchedToday;
    @FXML private BarChart<String, Number> flowChart;
    @FXML private PieChart transitStatusChart;
    @FXML private PieChart scheduleStatusChart;
    @FXML private BarChart<String, Number> topCarriersChart;
    @FXML private BarChart<String, Number> topDriversChart;

    private final LogisticsService logistics = new LogisticsService();
    private final AnalyticsService analytics = new AnalyticsService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        userLabel.setText(Session.getUser().fullName()
                + " [" + Session.getUser().role().name() + "]");

        setupTransitTable();
        setupScheduleTable();
        setupAnalytics();

        loadTransit();
        loadSchedules();
        loadAnalytics();
    }

    // ── Transit ───────────────────────────────────────────────

    private void setupTransitTable() {
        colTrTracking.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().trackingNumber()));
        colTrCarrier .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().carrier()));
        colTrSupplier.setCellValueFactory(r -> new SimpleStringProperty(
                r.getValue().supplierName() != null ? r.getValue().supplierName() : "—"));
        colTrOrigin  .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().origin()));
        colTrDest    .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().destination()));
        colTrDeparture.setCellValueFactory(r -> new SimpleStringProperty(
                r.getValue().departureDate().toString()));
        colTrExpected.setCellValueFactory(r -> new SimpleStringProperty(
                r.getValue().expectedArrival().toString()));
        colTrActual  .setCellValueFactory(r -> new SimpleStringProperty(
                r.getValue().actualArrival() != null ? r.getValue().actualArrival().toString() : "—"));
        colTrStatus  .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().status().name()));

        transitTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean none = sel == null;
                    boolean done = !none && (sel.status() == TransitShipment.Status.DELIVERED
                            || sel.status() == TransitShipment.Status.CANCELLED);
                    btnTransitInTransit.setDisable(none || done);
                    btnTransitArrived  .setDisable(none || done);
                    btnTransitCancel   .setDisable(none || done);
                });
    }

    private void loadTransit() {
        Task<List<TransitShipment>> task = new Task<>() {
            @Override protected List<TransitShipment> call() throws Exception {
                return logistics.getAllShipments();
            }
        };
        task.setOnSucceeded(e ->
                transitTable.setItems(FXCollections.observableList(task.getValue())));
        task.setOnFailed(e -> showStatus("Ошибка загрузки поставок: "
                + task.getException().getMessage()));
        new Thread(task).start();
    }

    @FXML private void onRefreshTransit() { loadTransit(); }

    @FXML
    private void onNewTransit() {
        new TransitShipmentDialog(logistics).showAndWait().ifPresent(id -> {
            showStatus("Поставка зарегистрирована");
            loadTransit();
            loadAnalytics();
        });
    }

    @FXML private void onTransitInTransit() {
        changeTransitStatus(TransitShipment.Status.IN_TRANSIT, "Помечено как «в пути»");
    }

    @FXML
    private void onTransitArrived() {
        TransitShipment sel = transitTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                logistics.markArrived(sel.id(), LocalDate.now());
                return null;
            }
        };
        task.setOnSucceeded(e -> { showStatus("Доставлено"); loadTransit(); loadAnalytics(); });
        task.setOnFailed(e -> showStatus("Ошибка: " + task.getException().getMessage()));
        new Thread(task).start();
    }

    @FXML private void onTransitCancel() {
        changeTransitStatus(TransitShipment.Status.CANCELLED, "Поставка отменена");
    }

    private void changeTransitStatus(TransitShipment.Status status, String okMsg) {
        TransitShipment sel = transitTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                logistics.changeStatus(sel.id(), status);
                return null;
            }
        };
        task.setOnSucceeded(e -> { showStatus(okMsg); loadTransit(); loadAnalytics(); });
        task.setOnFailed(e -> showStatus("Ошибка: " + task.getException().getMessage()));
        new Thread(task).start();
    }

    // ── Schedules ─────────────────────────────────────────────

    private void setupScheduleTable() {
        colSchDate   .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().workDate().toString()));
        colSchDriver .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().driverName()));
        colSchVehicle.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().vehicle()));
        colSchRoute  .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().route()));
        colSchStart  .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().shiftStart().toString()));
        colSchEnd    .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().shiftEnd().toString()));
        colSchStatus .setCellValueFactory(r -> new SimpleStringProperty(r.getValue().status().name()));
        colSchShip   .setCellValueFactory(r -> new SimpleStringProperty(
                r.getValue().shipmentTracking() != null ? r.getValue().shipmentTracking() : "—"));

        scheduleTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean none = sel == null;
                    boolean done = !none && (sel.status() == LogisticsSchedule.Status.COMPLETED
                            || sel.status() == LogisticsSchedule.Status.CANCELLED);
                    btnSchedStart .setDisable(none || done);
                    btnSchedDone  .setDisable(none || done);
                    btnSchedCancel.setDisable(none || done);
                });

        schedFromPicker.setValue(LocalDate.now().minusDays(7));
        schedToPicker  .setValue(LocalDate.now().plusDays(30));
    }

    private void loadSchedules() {
        Task<List<LogisticsSchedule>> task = new Task<>() {
            @Override protected List<LogisticsSchedule> call() throws Exception {
                return logistics.getAllSchedules();
            }
        };
        task.setOnSucceeded(e ->
                scheduleTable.setItems(FXCollections.observableList(task.getValue())));
        task.setOnFailed(e -> showStatus("Ошибка загрузки графика: "
                + task.getException().getMessage()));
        new Thread(task).start();
    }

    @FXML
    private void onFilterSchedules() {
        LocalDate from = schedFromPicker.getValue();
        LocalDate to   = schedToPicker.getValue();
        if (from == null || to == null) { loadSchedules(); return; }
        Task<List<LogisticsSchedule>> task = new Task<>() {
            @Override protected List<LogisticsSchedule> call() throws Exception {
                return logistics.getSchedulesBetween(from, to);
            }
        };
        task.setOnSucceeded(e ->
                scheduleTable.setItems(FXCollections.observableList(task.getValue())));
        task.setOnFailed(e -> showStatus("Ошибка фильтра: "
                + task.getException().getMessage()));
        new Thread(task).start();
    }

    @FXML
    private void onNewSchedule() {
        new LogisticsScheduleDialog(logistics).showAndWait().ifPresent(id -> {
            showStatus("Запись графика добавлена");
            loadSchedules();
            loadAnalytics();
        });
    }

    @FXML private void onScheduleStart()  { changeScheduleStatus(LogisticsSchedule.Status.IN_PROGRESS, "Смена начата"); }
    @FXML private void onScheduleDone()   { changeScheduleStatus(LogisticsSchedule.Status.COMPLETED,   "Смена завершена"); }
    @FXML private void onScheduleCancel() { changeScheduleStatus(LogisticsSchedule.Status.CANCELLED,   "Смена отменена"); }

    private void changeScheduleStatus(LogisticsSchedule.Status status, String okMsg) {
        LogisticsSchedule sel = scheduleTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                logistics.changeScheduleStatus(sel.id(), status);
                return null;
            }
        };
        task.setOnSucceeded(e -> { showStatus(okMsg); loadSchedules(); loadAnalytics(); });
        task.setOnFailed(e -> showStatus("Ошибка: " + task.getException().getMessage()));
        new Thread(task).start();
    }

    // ── Analytics ─────────────────────────────────────────────

    private void setupAnalytics() {
        periodSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(7, 90, 14));
        periodSpinner.valueProperty().addListener((obs, o, n) -> loadAnalytics());

        fillKpi(kpiShipments,  "Поставок всего", "0", "");
        fillKpi(kpiInTransit,  "В пути",          "0", "");
        fillKpi(kpiDelivered,  "Доставлено",      "0", "");
        fillKpi(kpiDelayed,    "Задержано",       "0", "");
        fillKpi(kpiSchedules,  "Смен всего",      "0", "");
        fillKpi(kpiSchedToday, "Смен сегодня",    "0", "");
    }

    private void fillKpi(VBox tile, String title, String value, String sub) {
        tile.getChildren().clear();
        Label t = new Label(title); t.getStyleClass().add("kpi-title");
        Label v = new Label(value); v.getStyleClass().add("kpi-value");
        Label s = new Label(sub);   s.getStyleClass().add("kpi-sub");
        tile.getChildren().addAll(t, v, s);
    }

    @FXML private void onRefreshAnalytics() { loadAnalytics(); }

    private void loadAnalytics() {
        int days = periodSpinner == null || periodSpinner.getValue() == null
                ? 14 : periodSpinner.getValue();

        Task<AnalyticsBundle> task = new Task<>() {
            @Override protected AnalyticsBundle call() throws Exception {
                return new AnalyticsBundle(
                        analytics.kpiSummary(),
                        analytics.dailyShipmentFlow(days),
                        analytics.transitStatusBreakdown(),
                        analytics.scheduleStatusBreakdown(),
                        analytics.topCarriers(5),
                        analytics.topDrivers(5));
            }
        };
        task.setOnSucceeded(e -> renderAnalytics(task.getValue()));
        task.setOnFailed(e -> showStatus("Ошибка аналитики: "
                + task.getException().getMessage()));
        new Thread(task).start();
    }

    private record AnalyticsBundle(
            AnalyticsService.KpiSummary kpi,
            Map<LocalDate, AnalyticsService.DailyFlow> flow,
            Map<String, Long> trStatus,
            Map<String, Long> schStatus,
            Map<String, Long> topCarriers,
            Map<String, Long> topDrivers) {}

    private void renderAnalytics(AnalyticsBundle b) {
        AnalyticsService.KpiSummary k = b.kpi();
        fillKpi(kpiShipments,  "Поставок всего", String.valueOf(k.shipmentsTotal()), "");
        fillKpi(kpiInTransit,  "В пути",          String.valueOf(k.shipmentsInTransit()), "");
        fillKpi(kpiDelivered,  "Доставлено",      String.valueOf(k.shipmentsDelivered()), "");
        fillKpi(kpiDelayed,    "Задержано",       String.valueOf(k.shipmentsDelayed()), "");
        fillKpi(kpiSchedules,  "Смен всего",      String.valueOf(k.schedulesTotal()),
                "активных: " + k.schedulesActive() + " · завершено: " + k.schedulesCompleted());
        fillKpi(kpiSchedToday, "Смен сегодня",    String.valueOf(k.schedulesToday()), "");

        flowChart.getData().clear();
        XYChart.Series<String, Number> sDep = new XYChart.Series<>();
        XYChart.Series<String, Number> sArr = new XYChart.Series<>();
        sDep.setName("Отправлено");
        sArr.setName("Прибыло");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
        b.flow().forEach((date, df) -> {
            String label = date.format(fmt);
            sDep.getData().add(new XYChart.Data<>(label, df.planned()));
            sArr.getData().add(new XYChart.Data<>(label, df.arrived()));
        });
        flowChart.getData().addAll(sDep, sArr);
        flowChart.setTitle("Движение поставок по дням");

        transitStatusChart.setData(pieDataOf(b.trStatus(),
                Map.of("PLANNED",    "Запланировано",
                       "IN_TRANSIT", "В пути",
                       "DELIVERED",  "Доставлено",
                       "DELAYED",    "Задержка",
                       "CANCELLED",  "Отменено")));

        scheduleStatusChart.setData(pieDataOf(b.schStatus(),
                Map.of("SCHEDULED",   "Запланировано",
                       "IN_PROGRESS", "В работе",
                       "COMPLETED",   "Завершено",
                       "CANCELLED",   "Отменено")));

        topCarriersChart.getData().clear();
        XYChart.Series<String, Number> carr = new XYChart.Series<>();
        carr.setName("Поставок");
        b.topCarriers().forEach((name, cnt) ->
                carr.getData().add(new XYChart.Data<>(truncate(name, 18), cnt)));
        topCarriersChart.getData().add(carr);

        topDriversChart.getData().clear();
        XYChart.Series<String, Number> drv = new XYChart.Series<>();
        drv.setName("Смены");
        b.topDrivers().forEach((name, cnt) ->
                drv.getData().add(new XYChart.Data<>(truncate(name, 18), cnt)));
        topDriversChart.getData().add(drv);
    }

    private javafx.collections.ObservableList<PieChart.Data> pieDataOf(
            Map<String, Long> raw, Map<String, String> labels) {
        javafx.collections.ObservableList<PieChart.Data> list =
                FXCollections.observableArrayList();
        raw.forEach((k, v) -> {
            String label = labels.getOrDefault(k, k);
            list.add(new PieChart.Data(label + " (" + v + ")", v));
        });
        return list;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @FXML
    private void onLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Выйти из аккаунта?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
            Session.logout();
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/ru/warehouse/logistics/ui/fxml/Login.fxml"));
                Scene scene = new Scene(loader.load(), 400, 300);
                var css = getClass().getResource("/styles/app.css");
                if (css != null) scene.getStylesheets().add(css.toExternalForm());
                Stage login = new Stage();
                login.setTitle("Warehouse Logistics");
                login.setScene(scene);
                login.setResizable(false);
                login.show();
                ((Stage) statusLabel.getScene().getWindow()).close();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR,
                        "Не удалось открыть окно входа: " + ex.getMessage()).showAndWait();
            }
        });
    }

    private void showStatus(String msg) {
        statusLabel.setText(msg);
    }
}

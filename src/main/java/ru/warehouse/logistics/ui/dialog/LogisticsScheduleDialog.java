package ru.warehouse.logistics.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import ru.warehouse.logistics.model.LogisticsSchedule;
import ru.warehouse.logistics.model.TransitShipment;
import ru.warehouse.logistics.service.LogisticsService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class LogisticsScheduleDialog extends Dialog<UUID> {

    private final LogisticsService service;

    private TextField driverField;
    private TextField vehicleField;
    private TextField routeField;
    private DatePicker datePicker;
    private TextField startField;
    private TextField endField;
    private ComboBox<LogisticsSchedule.Status> statusCombo;
    private ComboBox<TransitShipment> shipmentCombo;
    private TextArea  notesArea;

    public LogisticsScheduleDialog(LogisticsService service) {
        this.service = service;
        setTitle("Новая запись графика");
        setHeaderText("Назначение водителя на смену / маршрут");

        ButtonType okBtn  = new ButtonType("Создать", ButtonBar.ButtonData.OK_DONE);
        ButtonType canBtn = new ButtonType("Отмена",  ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(okBtn, canBtn);
        getDialogPane().setContent(buildContent());
        loadShipments();

        setResultConverter(bt -> {
            if (bt != okBtn) return null;
            try {
                LocalTime start = LocalTime.parse(startField.getText().strip());
                LocalTime end   = LocalTime.parse(endField.getText().strip());
                TransitShipment ts = shipmentCombo.getValue();
                LogisticsSchedule s = new LogisticsSchedule(
                        null,
                        driverField.getText().strip(),
                        vehicleField.getText().strip(),
                        routeField.getText().strip(),
                        datePicker.getValue(),
                        start, end,
                        statusCombo.getValue(),
                        ts == null ? null : ts.id(),
                        ts == null ? null : ts.trackingNumber(),
                        notesArea.getText()
                );
                return service.createSchedule(s);
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR,
                        "Не удалось сохранить: " + ex.getMessage()).showAndWait();
                return null;
            }
        });
    }

    private GridPane buildContent() {
        driverField  = new TextField();
        vehicleField = new TextField();
        routeField   = new TextField();
        datePicker   = new DatePicker(LocalDate.now());
        startField   = new TextField("08:00");
        endField     = new TextField("17:00");
        statusCombo  = new ComboBox<>();
        statusCombo.getItems().setAll(LogisticsSchedule.Status.values());
        statusCombo.setValue(LogisticsSchedule.Status.SCHEDULED);
        shipmentCombo = new ComboBox<>();
        shipmentCombo.setConverter(new StringConverter<>() {
            public String toString(TransitShipment t) {
                return t == null ? "—" : t.trackingNumber() + " (" + t.destination() + ")";
            }
            public TransitShipment fromString(String s) { return null; }
        });
        notesArea = new TextArea();
        notesArea.setPrefRowCount(3);

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(16));
        int r = 0;
        g.add(new Label("Водитель:"),       0, r); g.add(driverField,   1, r++);
        g.add(new Label("Транспорт:"),       0, r); g.add(vehicleField,  1, r++);
        g.add(new Label("Маршрут:"),         0, r); g.add(routeField,    1, r++);
        g.add(new Label("Дата:"),             0, r); g.add(datePicker,    1, r++);
        g.add(new Label("Начало (HH:mm):"), 0, r); g.add(startField,    1, r++);
        g.add(new Label("Конец (HH:mm):"),  0, r); g.add(endField,      1, r++);
        g.add(new Label("Статус:"),           0, r); g.add(statusCombo,   1, r++);
        g.add(new Label("Поставка:"),         0, r); g.add(shipmentCombo, 1, r++);
        g.add(new Label("Примечание:"),      0, r); g.add(notesArea,     1, r);
        return g;
    }

    private void loadShipments() {
        try {
            List<TransitShipment> list = service.getAllShipments();
            shipmentCombo.getItems().add(null);
            shipmentCombo.getItems().addAll(list);
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR,
                    "Ошибка загрузки поставок: " + ex.getMessage()).showAndWait();
        }
    }
}

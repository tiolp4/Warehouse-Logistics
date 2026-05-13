package ru.warehouse.logistics.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import ru.warehouse.logistics.model.Supplier;
import ru.warehouse.logistics.model.TransitShipment;
import ru.warehouse.logistics.service.LogisticsService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

public class TransitShipmentDialog extends Dialog<UUID> {

    private final LogisticsService service;

    private TextField trackingField;
    private TextField carrierField;
    private ComboBox<Supplier> supplierCombo;
    private TextField originField;
    private TextField destinationField;
    private DatePicker departurePicker;
    private DatePicker expectedPicker;
    private ComboBox<TransitShipment.Status> statusCombo;
    private TextArea  notesArea;

    public TransitShipmentDialog(LogisticsService service) {
        this.service = service;
        setTitle("Новая поставка в пути");
        setHeaderText("Регистрация товара, отгруженного поставщиком");

        ButtonType okBtn  = new ButtonType("Создать", ButtonBar.ButtonData.OK_DONE);
        ButtonType canBtn = new ButtonType("Отмена",  ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(okBtn, canBtn);
        getDialogPane().setContent(buildContent());
        loadCombos();

        setResultConverter(bt -> {
            if (bt != okBtn) return null;
            try {
                Supplier sup = supplierCombo.getValue();
                TransitShipment ts = new TransitShipment(
                        null,
                        trackingField.getText().strip(),
                        carrierField.getText().strip(),
                        sup == null ? null : sup.id(),
                        sup == null ? null : sup.name(),
                        originField.getText().strip(),
                        destinationField.getText().strip(),
                        departurePicker.getValue(),
                        expectedPicker.getValue(),
                        null,
                        statusCombo.getValue(),
                        notesArea.getText(),
                        null, null
                );
                return service.createShipment(ts);
            } catch (IllegalArgumentException | IOException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                return null;
            }
        });
    }

    private GridPane buildContent() {
        trackingField    = new TextField();
        carrierField     = new TextField();
        supplierCombo    = new ComboBox<>();
        originField      = new TextField();
        destinationField = new TextField("Склад");
        departurePicker  = new DatePicker(LocalDate.now());
        expectedPicker   = new DatePicker(LocalDate.now().plusDays(3));
        statusCombo      = new ComboBox<>();
        statusCombo.getItems().setAll(TransitShipment.Status.values());
        statusCombo.setValue(TransitShipment.Status.PLANNED);
        notesArea = new TextArea();
        notesArea.setPrefRowCount(3);

        supplierCombo.setConverter(new StringConverter<>() {
            public String toString(Supplier s) { return s == null ? "" : s.name(); }
            public Supplier fromString(String s) { return null; }
        });

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(16));
        int r = 0;
        g.add(new Label("Трек-номер:"),     0, r); g.add(trackingField,    1, r++);
        g.add(new Label("Перевозчик:"),     0, r); g.add(carrierField,     1, r++);
        g.add(new Label("Поставщик:"),      0, r); g.add(supplierCombo,    1, r++);
        g.add(new Label("Откуда:"),          0, r); g.add(originField,      1, r++);
        g.add(new Label("Куда:"),            0, r); g.add(destinationField, 1, r++);
        g.add(new Label("Дата отправки:"),  0, r); g.add(departurePicker,  1, r++);
        g.add(new Label("Ожидается:"),       0, r); g.add(expectedPicker,   1, r++);
        g.add(new Label("Статус:"),          0, r); g.add(statusCombo,      1, r++);
        g.add(new Label("Примечание:"),     0, r); g.add(notesArea,        1, r);
        return g;
    }

    private void loadCombos() {
        try {
            supplierCombo.getItems().setAll(service.getAllSuppliers());
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Ошибка загрузки поставщиков: "
                    + ex.getMessage()).showAndWait();
        }
    }
}

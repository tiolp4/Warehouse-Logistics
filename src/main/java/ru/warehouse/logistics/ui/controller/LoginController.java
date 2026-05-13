package ru.warehouse.logistics.ui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import ru.warehouse.logistics.model.User;
import ru.warehouse.logistics.util.ApiClient;
import ru.warehouse.logistics.util.Session;

import java.io.IOException;

public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;
    @FXML private ProgressIndicator spinner;

    private final ApiClient api = ApiClient.getInstance();

    @FXML
    private void onLogin() {
        String username = usernameField.getText().strip();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Введите логин и пароль");
            return;
        }

        loginButton.setDisable(true);
        spinner.setVisible(true);
        errorLabel.setVisible(false);

        Task<ApiClient.LoginResult> task = new Task<>() {
            @Override
            protected ApiClient.LoginResult call() throws Exception {
                return api.login(username, password);
            }
        };

        task.setOnSucceeded(e -> {
            ApiClient.LoginResult res = task.getValue();
            if (res.user().role() != User.Role.MANAGER) {
                showError("Доступ разрешён только менеджерам");
                loginButton.setDisable(false);
                spinner.setVisible(false);
                return;
            }
            Session.login(res.user(), res.token());
            openMain();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            String msg = ex == null ? "Ошибка входа" : ex.getMessage();
            if (msg != null && msg.contains("401")) msg = "Неверный логин или пароль";
            if (msg != null && (msg.contains("ConnectException") || msg.contains("Connection refused")
                    || msg.contains("HttpConnectTimeoutException")))
                msg = "Сервер недоступен (" + api.baseUrl() + ")";
            showError(msg);
            loginButton.setDisable(false);
            spinner.setVisible(false);
        });

        new Thread(task).start();
    }

    private void openMain() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/ru/warehouse/logistics/ui/fxml/Main.fxml"));
            Scene scene = new Scene(loader.load(), 1200, 720);
            scene.getStylesheets().add(
                    getClass().getResource("/styles/app.css").toExternalForm());
            Stage stage = new Stage();
            stage.setTitle("Логистика — " + Session.getUser().fullName());
            stage.setScene(scene);
            stage.setMinWidth(960);
            stage.setMinHeight(640);
            stage.show();
            ((Stage) loginButton.getScene().getWindow()).close();
        } catch (IOException ex) {
            showError("Не удалось открыть главное окно: " + ex.getMessage());
            loginButton.setDisable(false);
            spinner.setVisible(false);
        }
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
        });
    }
}

# Warehouse Logistics — Desktop

JavaFX-приложение для логистики. Работает **через REST API** ([`warehouse-api`](../warehouse-api/))

## Модули

- **Товары в пути** — учёт поставок.
- **Графики работы** — смены водителей по маршрутам.
- **Аналитика** — KPI и графики по поставкам и сменам.

Доступ к приложению — только для роли `MANAGER` (проверка после `/v1/auth/login`).

## Требования

- Запущенный сервер `warehouse-api` (по умолчанию `http://127.0.0.1:8080/api`).
- Java 21.

## Настройка адреса API

[`src/main/resources/api.properties`](src/main/resources/api.properties):
```
api.base.url=http://127.0.0.1:8080/api
api.timeout.seconds=15
```

Или через переменную окружения `API_BASE_URL`.

## Запуск

```
mvn javafx:run
```

Или fat-jar:
```
mvn package
java -jar target/warehouse-logistics-1.0.0.jar
```

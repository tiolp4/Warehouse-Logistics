package ru.warehouse.logistics.model;

import java.util.UUID;

public record Supplier(
        UUID   id,
        String name,
        String contact,
        String phone,
        String email
) {}

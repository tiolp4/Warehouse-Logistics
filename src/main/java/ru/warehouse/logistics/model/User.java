package ru.warehouse.logistics.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record User(
        UUID          id,
        String        username,
        String        passwordHash,
        Role          role,
        String        fullName,
        LocalDateTime createdAt
) {
    public enum Role { MANAGER, PICKER }
}

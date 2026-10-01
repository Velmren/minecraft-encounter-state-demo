package dev.velmren.encounter.core;

import java.util.Objects;

public record ObjectiveDefinition(String id, int requiredProgress) {
    public ObjectiveDefinition {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("objective id must not be blank");
        }
        if (requiredProgress < 1) {
            throw new IllegalArgumentException("requiredProgress must be at least 1");
        }
    }
}

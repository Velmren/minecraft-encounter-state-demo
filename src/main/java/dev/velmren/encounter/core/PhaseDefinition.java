package dev.velmren.encounter.core;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record PhaseDefinition(String id, List<ObjectiveDefinition> objectives) {
    public PhaseDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(objectives, "objectives");
        if (id.isBlank()) {
            throw new IllegalArgumentException("phase id must not be blank");
        }
        objectives = List.copyOf(objectives);
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("phase must contain at least one objective");
        }
        Set<String> ids = objectives.stream().map(ObjectiveDefinition::id).collect(Collectors.toSet());
        if (ids.size() != objectives.size()) {
            throw new IllegalArgumentException("objective ids must be unique within a phase");
        }
    }
}

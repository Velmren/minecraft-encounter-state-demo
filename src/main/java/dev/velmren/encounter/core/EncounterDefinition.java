package dev.velmren.encounter.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EncounterDefinition(String id, List<PhaseDefinition> phases) {
    public EncounterDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(phases, "phases");
        if (id.isBlank()) {
            throw new IllegalArgumentException("encounter id must not be blank");
        }
        phases = List.copyOf(phases);
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("encounter must contain at least one phase");
        }
        Set<String> phaseIds = new HashSet<>();
        Set<String> objectiveIds = new HashSet<>();
        for (PhaseDefinition phase : phases) {
            if (!phaseIds.add(phase.id())) {
                throw new IllegalArgumentException("phase ids must be unique");
            }
            for (ObjectiveDefinition objective : phase.objectives()) {
                if (!objectiveIds.add(objective.id())) {
                    throw new IllegalArgumentException("objective ids must be unique across the encounter");
                }
            }
        }
    }
}

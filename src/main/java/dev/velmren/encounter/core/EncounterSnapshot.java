package dev.velmren.encounter.core;

import java.util.Map;

public record EncounterSnapshot(
        String encounterId,
        EncounterStatus status,
        String activePhaseId,
        int activePhaseIndex,
        Map<String, Integer> objectiveProgress,
        int completionCount) {
    public EncounterSnapshot {
        objectiveProgress = Map.copyOf(objectiveProgress);
    }
}

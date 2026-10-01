package dev.velmren.encounter.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure state machine. Bukkit/Paper concerns belong in an adapter. */
public final class EncounterEngine {
    private final EncounterDefinition definition;
    private final Map<String, Integer> progress = new LinkedHashMap<>();
    private EncounterStatus status = EncounterStatus.IDLE;
    private int phaseIndex = -1;
    private int completionCount = 0;

    public EncounterEngine(EncounterDefinition definition) {
        this.definition = definition;
        clearProgress();
    }

    public TransitionResult start() {
        if (status != EncounterStatus.IDLE) {
            return TransitionResult.rejected("encounter_not_idle");
        }
        status = EncounterStatus.ACTIVE;
        phaseIndex = 0;
        return TransitionResult.accepted(List.of(
                new EncounterEvent.Started(definition.id(), currentPhase().id())));
    }

    public TransitionResult addProgress(String objectiveId, int amount) {
        if (status != EncounterStatus.ACTIVE) {
            return TransitionResult.rejected(status == EncounterStatus.COMPLETED
                    ? "encounter_already_completed" : "encounter_not_active");
        }
        if (amount < 1) {
            return TransitionResult.rejected("progress_must_be_positive");
        }

        ObjectiveDefinition objective = currentPhase().objectives().stream()
                .filter(candidate -> candidate.id().equals(objectiveId))
                .findFirst()
                .orElse(null);
        if (objective == null) {
            return TransitionResult.rejected("objective_not_active");
        }

        int before = progress.get(objective.id());
        if (before >= objective.requiredProgress()) {
            return TransitionResult.rejected("objective_already_completed");
        }
        int remaining = objective.requiredProgress() - before;
        int after = amount >= remaining ? objective.requiredProgress() : before + amount;
        progress.put(objective.id(), after);

        List<EncounterEvent> events = new ArrayList<>();
        events.add(new EncounterEvent.Progressed(objective.id(), after, objective.requiredProgress()));
        if (currentPhaseComplete()) {
            String completedPhase = currentPhase().id();
            if (phaseIndex + 1 < definition.phases().size()) {
                phaseIndex++;
                events.add(new EncounterEvent.PhaseCompleted(completedPhase, currentPhase().id()));
            } else {
                // This is the sole transition that increments completionCount.
                status = EncounterStatus.COMPLETED;
                completionCount++;
                events.add(new EncounterEvent.PhaseCompleted(completedPhase, null));
                events.add(new EncounterEvent.EncounterCompleted(definition.id()));
            }
        }
        return TransitionResult.accepted(events);
    }

    public TransitionResult reset() {
        status = EncounterStatus.IDLE;
        phaseIndex = -1;
        completionCount = 0;
        clearProgress();
        return TransitionResult.accepted(List.of(new EncounterEvent.Reset(definition.id())));
    }

    public EncounterSnapshot snapshot() {
        return new EncounterSnapshot(
                definition.id(), status,
                phaseIndex >= 0 ? currentPhase().id() : null,
                phaseIndex,
                progress,
                completionCount);
    }

    private PhaseDefinition currentPhase() {
        return definition.phases().get(phaseIndex);
    }

    private boolean currentPhaseComplete() {
        return currentPhase().objectives().stream()
                .allMatch(objective -> progress.get(objective.id()) >= objective.requiredProgress());
    }

    private void clearProgress() {
        progress.clear();
        definition.phases().stream()
                .flatMap(phase -> phase.objectives().stream())
                .forEach(objective -> progress.put(objective.id(), 0));
    }
}

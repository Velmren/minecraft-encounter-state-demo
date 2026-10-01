package dev.velmren.encounter.core;

public sealed interface EncounterEvent permits EncounterEvent.Started, EncounterEvent.Progressed,
        EncounterEvent.PhaseCompleted, EncounterEvent.EncounterCompleted, EncounterEvent.Reset {
    record Started(String encounterId, String phaseId) implements EncounterEvent {}
    record Progressed(String objectiveId, int progress, int required) implements EncounterEvent {}
    record PhaseCompleted(String phaseId, String nextPhaseId) implements EncounterEvent {}
    record EncounterCompleted(String encounterId) implements EncounterEvent {}
    record Reset(String encounterId) implements EncounterEvent {}
}

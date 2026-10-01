package dev.velmren.encounter.core;

import java.util.List;

public record TransitionResult(boolean accepted, String reason, List<EncounterEvent> events) {
    public TransitionResult {
        events = List.copyOf(events);
    }

    public static TransitionResult accepted(List<EncounterEvent> events) {
        return new TransitionResult(true, "accepted", events);
    }

    public static TransitionResult rejected(String reason) {
        return new TransitionResult(false, reason, List.of());
    }
}

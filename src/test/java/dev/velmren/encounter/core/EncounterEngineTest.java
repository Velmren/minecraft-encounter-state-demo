package dev.velmren.encounter.core;

import java.util.List;

public final class EncounterEngineTest {
    private int checks;

    public static void main(String[] args) {
        EncounterEngineTest suite = new EncounterEngineTest();
        suite.run();
        System.out.println("PASS EncounterEngineTest: " + suite.checks + " checks");
    }

    private void run() {
        rejectsInvalidDefinitions();
        advancesOnlyWhenAllPhaseObjectivesComplete();
        capsLargeProgressWithoutOverflow();
        guardsFirstCompletion();
        resetsAllState();
    }

    private void capsLargeProgressWithoutOverflow() {
        EncounterEngine engine = example();
        engine.start();
        engine.addProgress("relay", 1);
        check(engine.addProgress("relay", Integer.MAX_VALUE).accepted(), "large progress accepted");
        check(engine.snapshot().objectiveProgress().get("relay") == 2,
                "large progress caps without integer overflow");
    }

    private void rejectsInvalidDefinitions() {
        expectThrows(() -> new ObjectiveDefinition("bad", 0));
        expectThrows(() -> new PhaseDefinition("bad", List.of()));
        expectThrows(() -> new EncounterDefinition("duplicate", List.of(
                new PhaseDefinition("one", List.of(new ObjectiveDefinition("same", 1))),
                new PhaseDefinition("two", List.of(new ObjectiveDefinition("same", 1))))));
    }

    private void advancesOnlyWhenAllPhaseObjectivesComplete() {
        EncounterEngine engine = example();
        check(!engine.addProgress("relay", 1).accepted(), "progress before start rejected");
        check(engine.start().accepted(), "start accepted");
        check(!engine.start().accepted(), "second start rejected");
        check(!engine.addProgress("boss", 1).accepted(), "future objective rejected");
        check(!engine.addProgress("relay", 0).accepted(), "zero progress rejected");
        check(engine.addProgress("relay", 50).accepted(), "progress accepted and capped");
        check(engine.snapshot().objectiveProgress().get("relay") == 2, "progress capped at requirement");
        check(engine.snapshot().activePhaseId().equals("open_gate"), "phase waits for all objectives");
        check(engine.addProgress("adds", 1).accepted(), "second objective accepted");
        check(engine.snapshot().activePhaseId().equals("boss_room"), "phase advances");
        check(engine.snapshot().status() == EncounterStatus.ACTIVE, "encounter remains active");
    }

    private void guardsFirstCompletion() {
        EncounterEngine engine = example();
        engine.start();
        engine.addProgress("relay", 2);
        engine.addProgress("adds", 1);
        TransitionResult completion = engine.addProgress("boss", 3);
        long completionEvents = completion.events().stream()
                .filter(event -> event instanceof EncounterEvent.EncounterCompleted).count();
        check(completionEvents == 1, "one completion event emitted");
        check(engine.snapshot().status() == EncounterStatus.COMPLETED, "terminal status reached");
        check(engine.snapshot().completionCount() == 1, "completion count incremented once");
        check(!engine.addProgress("boss", 1).accepted(), "post-completion progress rejected");
        check(engine.snapshot().completionCount() == 1, "completion count remains one");
    }

    private void resetsAllState() {
        EncounterEngine engine = example();
        engine.start();
        engine.addProgress("relay", 1);
        check(engine.reset().accepted(), "reset accepted");
        EncounterSnapshot reset = engine.snapshot();
        check(reset.status() == EncounterStatus.IDLE, "reset returns to idle");
        check(reset.activePhaseId() == null, "reset clears active phase");
        check(reset.objectiveProgress().values().stream().allMatch(value -> value == 0), "reset clears progress");
        check(reset.completionCount() == 0, "reset clears completion marker");
        check(engine.start().accepted(), "encounter can restart after reset");
    }

    private EncounterEngine example() {
        return new EncounterEngine(new EncounterDefinition("test_encounter", List.of(
                new PhaseDefinition("open_gate", List.of(
                        new ObjectiveDefinition("relay", 2),
                        new ObjectiveDefinition("adds", 1))),
                new PhaseDefinition("boss_room", List.of(
                        new ObjectiveDefinition("boss", 3))))));
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private void expectThrows(Runnable action) {
        checks++;
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }
}

package com.example.saga;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A tiny saga orchestrator. Each step is a forward action plus the compensating action
 * that undoes it. Steps run in order; if any action throws, the compensations of the
 * ALREADY-COMPLETED steps run in REVERSE order, and the saga reports failure.
 *
 * This is NOT atomicity — earlier steps really committed and were really undone by a
 * second (compensating) transaction. In between, the system was temporarily inconsistent.
 */
public class Saga {

    public record Step(String name, Runnable action, Runnable compensation) {
    }

    public static class SagaFailedException extends RuntimeException {
        public SagaFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final List<Step> steps = new ArrayList<>();

    public Saga step(String name, Runnable action, Runnable compensation) {
        steps.add(new Step(name, action, compensation));
        return this;
    }

    public void execute() {
        Deque<Step> completed = new ArrayDeque<>();
        try {
            for (Step step : steps) {
                step.action().run();
                completed.push(step); // record for possible compensation
            }
        } catch (RuntimeException failure) {
            // Undo what succeeded, most-recent first (the push/iterate order of a Deque is LIFO).
            for (Step step : completed) {
                step.compensation().run();
            }
            throw new SagaFailedException(
                    "saga failed; compensated " + completed.size() + " completed step(s)", failure);
        }
    }
}

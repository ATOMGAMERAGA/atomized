package xyz.atomland.atomized.core;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fail-soft incident sink (master plan §4): when a module throws during init or tick,
 * the incident is recorded here, the module is shut down, and the game keeps running.
 * The UI layer drains {@link #drainPendingNotifications()} to show a toast; nothing
 * in this class ever rethrows.
 */
public final class PanicSwitch {
    private static final Logger LOGGER = LoggerFactory.getLogger("Atomized/PanicSwitch");

    private final List<Incident> incidents = new ArrayList<>();
    private final Deque<Incident> pendingNotifications = new ArrayDeque<>();

    /** Records a module failure. Never throws. */
    public synchronized void report(String moduleId, String phase, Throwable cause) {
        Incident incident = new Incident(moduleId, phase, summarize(cause), Instant.now());
        incidents.add(incident);
        pendingNotifications.addLast(incident);
        try {
            LOGGER.error("Module '{}' failed during {} and was disabled (fail-soft). The game keeps running.",
                    moduleId, phase, cause);
        } catch (Throwable ignored) {
            // Logging must never take the game down either.
        }
    }

    public synchronized boolean hasIncident(String moduleId) {
        return incidents.stream().anyMatch(i -> i.moduleId().equals(moduleId));
    }

    public synchronized List<Incident> incidents() {
        return List.copyOf(incidents);
    }

    /** Returns and clears incidents that have not been surfaced to the player yet. */
    public synchronized List<Incident> drainPendingNotifications() {
        List<Incident> out = List.copyOf(pendingNotifications);
        pendingNotifications.clear();
        return out;
    }

    private static String summarize(Throwable cause) {
        if (cause == null) {
            return "unknown error";
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /**
     * @param moduleId the failing module
     * @param phase {@code "init"}, {@code "tick"}, {@code "config"}, ...
     * @param summary short throwable summary safe to show in a toast
     * @param at when it happened
     */
    public record Incident(String moduleId, String phase, String summary, Instant at) {
    }
}

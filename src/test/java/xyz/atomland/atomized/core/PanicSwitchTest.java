package xyz.atomland.atomized.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanicSwitchTest {

    @Test
    void reportRecordsIncident() {
        PanicSwitch panic = new PanicSwitch();
        panic.report("smart_culling", "init", new IllegalStateException("boom"));

        assertTrue(panic.hasIncident("smart_culling"));
        assertEquals(1, panic.incidents().size());
        PanicSwitch.Incident incident = panic.incidents().get(0);
        assertEquals("smart_culling", incident.moduleId());
        assertEquals("init", incident.phase());
        assertEquals("IllegalStateException: boom", incident.summary());
    }

    @Test
    void hasIncidentIsFalseForCleanModules() {
        PanicSwitch panic = new PanicSwitch();
        panic.report("gui_opt", "tick", new RuntimeException("x"));
        assertFalse(panic.hasIncident("smart_culling"));
    }

    @Test
    void nullCauseIsSummarizedSafely() {
        PanicSwitch panic = new PanicSwitch();
        panic.report("gui_opt", "init", null);
        assertEquals("unknown error", panic.incidents().get(0).summary());
    }

    @Test
    void messagelessThrowableIsSummarizedByClassName() {
        PanicSwitch panic = new PanicSwitch();
        panic.report("gui_opt", "init", new IllegalArgumentException());
        assertEquals("IllegalArgumentException", panic.incidents().get(0).summary());
    }

    @Test
    void drainReturnsPendingOnceInOrder() {
        PanicSwitch panic = new PanicSwitch();
        panic.report("a", "init", new RuntimeException("1"));
        panic.report("b", "tick", new RuntimeException("2"));

        var drained = panic.drainPendingNotifications();
        assertEquals(2, drained.size());
        assertEquals("a", drained.get(0).moduleId());
        assertEquals("b", drained.get(1).moduleId());

        assertTrue(panic.drainPendingNotifications().isEmpty());
        // The permanent incident log is unaffected by draining.
        assertEquals(2, panic.incidents().size());
    }

    @Test
    void incidentsListIsACopy() {
        PanicSwitch panic = new PanicSwitch();
        panic.report("a", "init", new RuntimeException("1"));
        var snapshot = panic.incidents();
        panic.report("b", "init", new RuntimeException("2"));
        assertEquals(1, snapshot.size());
        assertEquals(2, panic.incidents().size());
    }
}

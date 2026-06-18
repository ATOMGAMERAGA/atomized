package xyz.atomland.atomized.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionGuardTest {

    @ParameterizedTest
    @CsvSource({
            "1.21, 1.21, 0",
            "1.21, 1.21.0, 0",
            "1.21.0, 1.21, 0",
            "1.21.1, 1.21, 1",
            "1.21, 1.21.1, -1",
            "1.21.2, 1.21.10, -1",
            "1.21.10, 1.21.9, 1",
            "1.21.11, 1.21.11, 0",
            "1.20.6, 1.21, -1",
            "2.0, 1.21.11, 1"
    })
    void comparesDottedVersionsNumerically(String a, String b, int expectedSignum) {
        assertEquals(expectedSignum, Integer.signum(VersionGuard.compare(a, b)));
    }

    @Test
    void preReleaseSuffixIsIgnoredForComparison() {
        assertEquals(0, VersionGuard.compare("1.21.2-pre1", "1.21.2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.21", "1.21.1", "1.21.5", "1.21.11"})
    void wildcardMatchesEverything(String version) {
        assertTrue(VersionGuard.matches(version, "*"));
    }

    @ParameterizedTest
    @CsvSource({
            "1.21,    '>=1.21 <=1.21.1', true",
            "1.21.1,  '>=1.21 <=1.21.1', true",
            "1.21.2,  '>=1.21 <=1.21.1', false",
            "1.21.2,  '>=1.21.2 <=1.21.3', true",
            "1.21.3,  '>=1.21.2 <=1.21.3', true",
            "1.21.4,  '>=1.21.2 <=1.21.3', false",
            "1.21.4,  '=1.21.4', true",
            "1.21.5,  '=1.21.4', false",
            "1.21.6,  '>=1.21.6 <=1.21.8', true",
            "1.21.7,  '>=1.21.6 <=1.21.8', true",
            "1.21.8,  '>=1.21.6 <=1.21.8', true",
            "1.21.9,  '>=1.21.6 <=1.21.8', false",
            "1.21.9,  '>=1.21.9 <=1.21.10', true",
            "1.21.10, '>=1.21.9 <=1.21.10', true",
            "1.21.11, '=1.21.11', true",
            "1.21.10, '=1.21.11', false"
    })
    void matchesPlanTargetRanges(String version, String predicate, boolean expected) {
        assertEquals(expected, VersionGuard.matches(version, predicate));
    }

    @ParameterizedTest
    @CsvSource({
            "1.21.5, '>1.21.4', true",
            "1.21.4, '>1.21.4', false",
            "1.21.3, '<1.21.4', true",
            "1.21.4, '<1.21.4', false",
            "1.21.4, '1.21.4', true"
    })
    void supportsStrictAndBareOperators(String version, String predicate, boolean expected) {
        assertEquals(expected, VersionGuard.matches(version, predicate));
    }

    @Test
    void clausesAreAnded() {
        assertTrue(VersionGuard.matches("1.21.5", ">=1.21 <=1.21.11"));
        assertFalse(VersionGuard.matches("1.21.5", ">=1.21 <=1.21.4"));
        assertFalse(VersionGuard.matches("1.21.5", ">=1.21.6 <=1.21.11"));
    }

    @Test
    void extraWhitespaceIsTolerated() {
        assertTrue(VersionGuard.matches("1.21.5", "  >=1.21.5   <=1.21.8  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", ">=abc", "1.x.2", ">=1.21.banana"})
    void malformedPredicateThrows(String predicate) {
        assertThrows(IllegalArgumentException.class, () -> VersionGuard.matches("1.21.4", predicate));
    }

    @Test
    void malformedVersionThrows() {
        assertThrows(IllegalArgumentException.class, () -> VersionGuard.compare("not-a-version", "1.21"));
        assertThrows(IllegalArgumentException.class, () -> VersionGuard.matches("snapshot", ">=1.21"));
    }

    @Test
    void nullArgumentsThrow() {
        assertThrows(IllegalArgumentException.class, () -> VersionGuard.matches(null, "*"));
        assertThrows(IllegalArgumentException.class, () -> VersionGuard.matches("1.21", null));
    }

    @Test
    void emptySegmentRejected() {
        assertThrows(IllegalArgumentException.class, () -> VersionGuard.compare("1..2", "1.21"));
    }
}

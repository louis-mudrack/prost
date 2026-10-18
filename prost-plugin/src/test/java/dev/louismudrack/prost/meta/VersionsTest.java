package dev.louismudrack.prost.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionsTest {
    @Test
    void numbersCompareNumerically() {
        assertTrue(Versions.newer("0.10.0", "0.9.3"));
        assertTrue(Versions.newer("1.0.0", "0.9.9"));
        assertFalse(Versions.newer("0.3.0", "0.3.0"));
        assertFalse(Versions.newer("0.2.9", "0.3.0"));
    }

    @Test
    void aReleaseBeatsItsSnapshotAndAPrefixedVIsFine() {
        assertTrue(Versions.newer("0.3.0", "0.3.0-SNAPSHOT"));
        assertFalse(Versions.newer("0.3.0-SNAPSHOT", "0.3.0"));
        assertTrue(Versions.newer("v0.4.0", "0.3.0"));
    }

    @Test
    void theNewestReleaseFromModrinth() {
        String json = "[{\"version_number\":\"0.5.0-beta\",\"version_type\":\"beta\"},"
                + "{\"version_number\":\"0.4.0\",\"version_type\":\"release\"},{\"version_number\":\"0.3.0\",\"version_type\":\"release\"}]";
        assertEquals("0.4.0", Versions.latestRelease(json));
        assertNull(Versions.latestRelease("[]"));
    }

    /** Prost is built against Paper 1.21.4: older 1.21 servers load it (api-version 1.21) but must not run it */
    @Test
    void theServerVersionCheck() {
        assertTrue(Versions.atLeast("1.21.4", "1.21.4"));
        assertTrue(Versions.atLeast("1.21.11", "1.21.4"));
        assertTrue(Versions.atLeast("26.1", "1.21.4"));
        assertTrue(Versions.atLeast("26.3", "1.21.4"));
        assertFalse(Versions.atLeast("1.21.3", "1.21.4"));
        assertFalse(Versions.atLeast("1.21", "1.21.4"));
        assertFalse(Versions.atLeast("1.20.6", "1.21.4"));
    }
}

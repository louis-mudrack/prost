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

    private static String release(String tag, boolean draft, boolean prerelease) {
        return "{\"tag_name\":\"" + tag + "\",\"name\":\"Prost " + tag + "\",\"draft\":" + draft + ",\"prerelease\":" + prerelease + "}";
    }

    @Test
    void theNewestReleaseOnGitHubWithoutItsV() {
        String json = "[" + release("v0.5.0", false, true) + "," + release("v0.4.0", false, false) + ","
                + release("v0.3.1", false, false) + "]";
        assertEquals("0.4.0", Versions.latestRelease(json));
    }

    /** GitHub lists by date: a fix for an older line published later is not the newest version */
    @Test
    void theHighestVersionWinsOverTheLatestDate() {
        assertEquals("0.4.0", Versions.latestRelease("[" + release("v0.3.2", false, false) + "," + release("v0.4.0", false, false) + "]"));
    }

    @Test
    void preReleasesAndDraftsAreSkipped() {
        assertNull(Versions.latestRelease("[" + release("v0.6.0", true, false) + "," + release("v0.5.0", false, true) + "]"));
    }

    @Test
    void noReleaseYet() { assertNull(Versions.latestRelease("[]")); }

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

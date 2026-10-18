package dev.louismudrack.prost.ui;

import org.junit.jupiter.api.Test;

import static dev.louismudrack.prost.Checks.*;

class FmtTest {
    @Test
    void fmt() {
        eq("one sip, German", "1 Schluck", Fmt.sips(1, true));
        eq("three sips, German", "3 Schlucke", Fmt.sips(3, true));
        eq("one sip, English", "1 sip", Fmt.sips(1, false));
        eq("three sips, English", "3 sips", Fmt.sips(3, false));
        eq("clock", "2:05", Fmt.clock(125_000));
        eq("clock rounds up a started second", "0:01", Fmt.clock(1));
        eq("clock never negative", "0:00", Fmt.clock(-500));
    }
}

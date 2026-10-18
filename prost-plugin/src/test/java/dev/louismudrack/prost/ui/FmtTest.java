package dev.louismudrack.prost.ui;

import org.junit.jupiter.api.Test;

import static dev.louismudrack.prost.Checks.*;

class FmtTest {
    @Test
    void fmt() {
        eq("clock", "2:05", Fmt.clock(125_000));
        eq("clock rounds up a started second", "0:01", Fmt.clock(1));
        eq("clock never negative", "0:00", Fmt.clock(-500));
    }
}

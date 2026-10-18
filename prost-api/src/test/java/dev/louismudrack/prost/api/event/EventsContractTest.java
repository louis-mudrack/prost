package dev.louismudrack.prost.api.event;

import org.bukkit.event.Event;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Bukkit only accepts listeners for events with a static getHandlerList() - forgetting it fails at runtime, not compile time. */
class EventsContractTest {
    static Stream<Class<? extends Event>> events() {
        return Stream.of(RoundStartEvent.class, RoundEndEvent.class, ChallengeStartEvent.class, ChallengeEndEvent.class,
                SipsEvent.class, RouletteHitEvent.class);
    }

    @ParameterizedTest
    @MethodSource("events")
    void hasAStaticHandlerList(Class<? extends Event> type) throws Exception {
        Method m = type.getMethod("getHandlerList");
        assertTrue(Modifier.isStatic(m.getModifiers()));
        assertNotNull(m.invoke(null));
    }

    @ParameterizedTest
    @MethodSource("events")
    void isFinal(Class<? extends Event> type) { assertTrue(Modifier.isFinal(type.getModifiers())); }
}

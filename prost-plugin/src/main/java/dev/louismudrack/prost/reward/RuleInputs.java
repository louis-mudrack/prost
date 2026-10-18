package dev.louismudrack.prost.reward;

import dev.louismudrack.prost.game.Round;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open "type your own rule" windows. Read from the async chat thread, so the map is concurrent. A window
 * that ran out is dropped on the next message and does not claim it: that message stays normal chat.
 */
final class RuleInputs {
    record Input(Round round, long until) {}

    private final Map<UUID, Input> open = new ConcurrentHashMap<>();

    void open(UUID id, Round round, long until) { open.put(id, new Input(round, until)); }

    void close(UUID id) { open.remove(id); }

    /** the player's open window, consumed; null when there is none or its time ran out */
    Input take(UUID id, long now) {
        Input in = open.remove(id);
        return in == null || now > in.until() ? null : in;
    }
}

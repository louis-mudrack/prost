package dev.louismudrack.prost.storage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** All-time stats and player settings. Callbacks run on the main thread; nothing here blocks the caller. */
public interface StatsStore {
    void add(UUID id, String name, StatsField field, int n);

    void roundEnded(String code, UUID host, long started, long ended, int members, int challenges);

    /** null when the player never played */
    void row(UUID id, Consumer<StatsRow> then);

    void top(StatsField field, Consumer<List<StatsRow>> then);

    /** never blocks: the last known list (maybe empty), refreshed in the background when older than 30 s */
    List<StatsRow> cachedTop(StatsField field);

    void prefs(UUID id, Consumer<Prefs> then);

    /** null = back to automatic */
    void setLanguage(UUID id, String name, String language);

    void markNoticed(UUID id, String name);

    /** absolute values: running a migration twice changes nothing */
    CompletableFuture<Void> importRow(StatsRow row, long lastPlayed);

    CompletableFuture<Void> importRound(String code, String host, long started, long ended, int members, int challenges);

    /** completes once everything queued before it is written */
    CompletableFuture<Void> flush();

    /** inline at disable: lets the queue finish, then closes the connection */
    void close();
}

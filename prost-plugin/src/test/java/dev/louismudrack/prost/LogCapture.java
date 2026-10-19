package dev.louismudrack.prost;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** What a logger and its children log, for assertions: {@code try (LogCapture log = LogCapture.root()) { ... }}. */
public final class LogCapture extends Handler implements AutoCloseable {
    private final Logger logger;
    /** the update checker and the stats worker log from their own threads */
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
        logger.addHandler(this);
    }

    /** every logger reports to the root one: this sees Prost, Paper and the other plugins */
    public static LogCapture root() { return on(Logger.getLogger("")); }

    public static LogCapture on(Logger logger) { return new LogCapture(logger); }

    public List<LogRecord> records() { return List.copyOf(records); }

    public List<String> messages() { return records.stream().map(LogRecord::getMessage).toList(); }

    public void clear() { records.clear(); }

    /** the message plus a thrown exception, for "the log never shows X" checks */
    public static String text(LogRecord r) { return r.getMessage() + " " + (r.getThrown() == null ? "" : String.valueOf(r.getThrown())); }

    @Override public void publish(LogRecord r) { records.add(r); }

    @Override public void flush() { }

    @Override public void close() { logger.removeHandler(this); }
}

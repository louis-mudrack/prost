package dev.louismudrack.prost.config;

import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.IOException;

/**
 * A YAML error for the log. SnakeYAML's message quotes the line it failed on, and that line may be a password: only the
 * problem and its position are kept.
 */
public final class YamlErrors {
    private YamlErrors() {}

    public static String describe(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof MarkedYAMLException marked) return position(marked);
            if (t instanceof IOException io) return io.getMessage();
        }
        return error.getClass().getSimpleName();
    }

    private static String position(MarkedYAMLException e) {
        Mark mark = e.getProblemMark();
        String problem = e.getProblem() == null ? e.getClass().getSimpleName() : e.getProblem();
        return mark == null ? problem : problem + " at line " + (mark.getLine() + 1) + ", column " + (mark.getColumn() + 1);
    }
}

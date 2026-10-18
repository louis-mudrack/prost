package dev.louismudrack.prost.config;

import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.IOException;

/**
 * A YAML error for the log. SnakeYAML's texts quote the failing line and sometimes the value itself ("found undefined
 * alias s3cret"), and that value may be a password: only the kind of error and its position are kept.
 */
public final class YamlErrors {
    private YamlErrors() {}

    public static String describe(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof MarkedYAMLException marked) return position(marked);
            if (t instanceof IOException io) return io.getMessage();   // names the file or the encoding, never the content
        }
        return error.getClass().getSimpleName();
    }

    private static String position(MarkedYAMLException e) {
        String kind = e.getClass().getSimpleName();
        Mark mark = e.getProblemMark();
        return mark == null ? kind : kind + " at line " + (mark.getLine() + 1) + ", column " + (mark.getColumn() + 1);
    }
}

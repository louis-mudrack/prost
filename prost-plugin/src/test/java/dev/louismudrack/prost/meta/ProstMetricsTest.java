package dev.louismudrack.prost.meta;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProstMetricsTest {
    @Test
    void roundSizesAreBucketed() {
        assertEquals("no round", ProstMetrics.bucket(List.of()));
        assertEquals("1", ProstMetrics.bucketOf(List.of(1)));
        assertEquals("2-3", ProstMetrics.bucketOf(List.of(2, 3)));
        assertEquals("4-5", ProstMetrics.bucketOf(List.of(4, 5, 4)));
        assertEquals("6+", ProstMetrics.bucketOf(List.of(8)));
    }
}

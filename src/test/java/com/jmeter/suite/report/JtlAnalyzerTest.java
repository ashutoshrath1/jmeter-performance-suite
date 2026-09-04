package com.jmeter.suite.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies JTL parsing, including quoted fields that previously corrupted column alignment.
 */
class JtlAnalyzerTest {

    private static final String HEADER =
            "timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage";

    /**
     * Writes a JTL fixture and returns its path.
     */
    private Path writeJtl(Path dir, String... rows) throws IOException {
        Path jtl = dir.resolve("results.jtl");
        StringBuilder content = new StringBuilder(HEADER).append('\n');
        for (String row : rows) {
            content.append(row).append('\n');
        }
        Files.write(jtl, content.toString().getBytes(StandardCharsets.UTF_8));
        return jtl;
    }

    @Test
    void countsSamplesAndErrorsFromPlainRows(@TempDir Path dir) throws IOException {
        Path jtl = writeJtl(dir,
                "1000,120,Get All Posts,200,OK,thread-1,text,true,",
                "1500,140,Get All Posts,500,Server Error,thread-1,text,false,Assertion failed");

        ExecutionStats stats = JtlAnalyzer.analyze(jtl);

        assertEquals(2, stats.sampleCount());
        assertEquals(1, stats.errorCount());
        assertEquals(50.0, stats.errorRatePercent(), 0.001);
    }

    @Test
    void countsErrorsWhenFieldsContainQuotedCommas(@TempDir Path dir) throws IOException {
        // The label and failure message both contain commas. Splitting on bare commas shifts the
        // success column and silently miscounts errors - this is the regression under test.
        Path jtl = writeJtl(dir,
                "1000,120,\"Get Posts, All\",200,OK,thread-1,text,true,",
                "1500,140,\"Get Posts, All\",500,Server Error,thread-1,text,false,"
                        + "\"Expected 200, got 500\"");

        ExecutionStats stats = JtlAnalyzer.analyze(jtl);

        assertEquals(2, stats.sampleCount());
        assertEquals(1, stats.errorCount(), "quoted commas must not shift the success column");
        assertEquals(50.0, stats.errorRatePercent(), 0.001);
    }

    @Test
    void resolvesColumnsByHeaderNameNotPosition(@TempDir Path dir) throws IOException {
        Path jtl = dir.resolve("reordered.jtl");
        Files.write(jtl, ("success,elapsed,timeStamp,label\n"
                + "false,200,1000,Get Posts\n"
                + "true,100,2000,Get Posts\n").getBytes(StandardCharsets.UTF_8));

        ExecutionStats stats = JtlAnalyzer.analyze(jtl);

        assertEquals(2, stats.sampleCount());
        assertEquals(1, stats.errorCount());
    }

    @Test
    void computesP95AndThroughput(@TempDir Path dir) throws IOException {
        // Ten samples spanning exactly one second, elapsed 10..100ms.
        String[] rows = new String[10];
        for (int i = 0; i < 10; i++) {
            long timestamp = 1000L + (i * 100L);
            long elapsed = (i + 1) * 10L;
            rows[i] = timestamp + "," + elapsed + ",Get,200,OK,thread-1,text,true,";
        }
        Path jtl = writeJtl(dir, rows);

        ExecutionStats stats = JtlAnalyzer.analyze(jtl);

        assertEquals(10, stats.sampleCount());
        assertEquals(0, stats.errorCount());
        assertEquals(100, stats.p95ResponseTimeMs());
        // 10 samples across a 900ms window.
        assertTrue(stats.throughputTps() > 11.0 && stats.throughputTps() < 11.2,
                "unexpected throughput: " + stats.throughputTps());
    }

    @Test
    void emptyOrMissingFileYieldsZeroStats(@TempDir Path dir) throws IOException {
        ExecutionStats missing = JtlAnalyzer.analyze(dir.resolve("nope.jtl"));
        assertEquals(0, missing.sampleCount());
        assertEquals(100.0, missing.errorRatePercent(), 0.001);

        Path headerOnly = writeJtl(dir);
        ExecutionStats empty = JtlAnalyzer.analyze(headerOnly);
        assertEquals(0, empty.sampleCount());
    }

    @Test
    void splitsEscapedQuotesWithinQuotedFields() {
        List<String> values = JtlAnalyzer.splitCsv("a,\"say \"\"hi\"\", now\",b");
        assertEquals(Arrays.asList("a", "say \"hi\", now", "b"), values);
    }
}

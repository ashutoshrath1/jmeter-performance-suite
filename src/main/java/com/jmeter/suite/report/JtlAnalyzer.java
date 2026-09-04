package com.jmeter.suite.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses JMeter CSV result files into aggregate execution statistics.
 *
 * <p>Columns are resolved by header name rather than by fixed position, and values are split with
 * RFC4180 quoting rules, because JMeter quotes any field containing the delimiter (labels and
 * assertion failure messages routinely do). Splitting on bare commas shifts every later column and
 * silently corrupts the success flag.
 */
public final class JtlAnalyzer {

    private static final String COL_TIMESTAMP = "timeStamp";
    private static final String COL_ELAPSED = "elapsed";
    private static final String COL_SUCCESS = "success";

    /**
     * Prevents instantiation of this utility type.
     */
    private JtlAnalyzer() {
    }

    /**
     * Reads a JTL file and computes aggregate sample, error, latency, and throughput metrics.
     */
    public static ExecutionStats analyze(Path jtlPath) throws IOException {
        if (!Files.exists(jtlPath)) {
            return new ExecutionStats(0, 0, 0, 0.0);
        }

        try (BufferedReader reader = Files.newBufferedReader(jtlPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new ExecutionStats(0, 0, 0, 0.0);
            }

            Map<String, Integer> columns = indexColumns(splitCsv(headerLine));
            Integer successIdx = columns.get(COL_SUCCESS);
            Integer elapsedIdx = columns.get(COL_ELAPSED);
            Integer timestampIdx = columns.get(COL_TIMESTAMP);

            long sampleCount = 0;
            long errorCount = 0;
            long minTimestamp = Long.MAX_VALUE;
            long maxTimestamp = Long.MIN_VALUE;
            LongList elapsedValues = new LongList();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> values = splitCsv(line);
                sampleCount++;

                if (successIdx != null && successIdx < values.size()
                        && "false".equalsIgnoreCase(values.get(successIdx).trim())) {
                    errorCount++;
                }

                if (elapsedIdx != null && elapsedIdx < values.size()) {
                    long elapsed = parseLong(values.get(elapsedIdx), -1);
                    if (elapsed >= 0) {
                        elapsedValues.add(elapsed);
                    }
                }

                if (timestampIdx != null && timestampIdx < values.size()) {
                    long timestamp = parseLong(values.get(timestampIdx), -1);
                    if (timestamp >= 0) {
                        minTimestamp = Math.min(minTimestamp, timestamp);
                        maxTimestamp = Math.max(maxTimestamp, timestamp);
                    }
                }
            }

            return new ExecutionStats(
                    sampleCount,
                    errorCount,
                    percentile(elapsedValues, 95),
                    throughput(sampleCount, minTimestamp, maxTimestamp));
        }
    }

    /**
     * Maps trimmed header names to their column positions.
     */
    private static Map<String, Integer> indexColumns(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).trim(), i);
        }
        return columns;
    }

    /**
     * Splits a CSV line honouring double-quoted fields and escaped quotes.
     */
    static List<String> splitCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    /**
     * Computes throughput in samples per second across the observed sampling window.
     */
    private static double throughput(long sampleCount, long minTimestamp, long maxTimestamp) {
        if (sampleCount == 0 || minTimestamp == Long.MAX_VALUE || maxTimestamp <= minTimestamp) {
            return 0.0;
        }
        double windowSeconds = (maxTimestamp - minTimestamp) / 1000.0;
        return windowSeconds > 0 ? sampleCount / windowSeconds : 0.0;
    }

    /**
     * Returns the requested percentile using nearest-rank selection.
     */
    private static long percentile(LongList values, int percentile) {
        if (values.size() == 0) {
            return 0;
        }
        long[] sorted = values.toSortedArray();
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length);
        int index = Math.min(Math.max(rank - 1, 0), sorted.length - 1);
        return sorted[index];
    }

    /**
     * Parses a long value, returning a fallback when the text is not numeric.
     */
    private static long parseLong(String text, long defaultValue) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * Minimal growable primitive long buffer that avoids per-sample boxing.
     */
    private static final class LongList {
        private long[] items = new long[1024];
        private int size;

        /**
         * Appends a value, growing the backing array when required.
         */
        void add(long value) {
            if (size == items.length) {
                items = Arrays.copyOf(items, items.length * 2);
            }
            items[size++] = value;
        }

        /**
         * Returns the number of stored values.
         */
        int size() {
            return size;
        }

        /**
         * Returns a sorted copy of the stored values.
         */
        long[] toSortedArray() {
            long[] copy = Arrays.copyOf(items, size);
            Arrays.sort(copy);
            return copy;
        }
    }
}

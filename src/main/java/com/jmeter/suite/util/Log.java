package com.jmeter.suite.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Timestamped console logging shared by the runner and its collaborators.
 *
 * <p>Deliberately minimal. The suite's output is read by humans watching a run and by CI logs, so
 * it stays on stdout in one consistent format rather than going through the JMeter log file.
 */
public final class Log {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Prevents instantiation of this utility type.
     */
    private Log() {
    }

    /**
     * Writes a timestamped informational message to standard output.
     */
    public static void info(String message) {
        System.out.println("[" + TIMESTAMP.format(LocalDateTime.now()) + "] " + message);
    }
}

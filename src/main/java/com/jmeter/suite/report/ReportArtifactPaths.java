package com.jmeter.suite.report;

import java.nio.file.Path;

/**
 * Holds output paths for artifacts produced by a single plan run.
 */
public final class ReportArtifactPaths {

    private final Path jtlPath;
    private final Path htmlDir;
    private final Path zipPath;

    /**
     * Creates artifact paths for JTL, HTML report directory, and zip archive.
     */
    public ReportArtifactPaths(Path jtlPath, Path htmlDir, Path zipPath) {
        this.jtlPath = jtlPath;
        this.htmlDir = htmlDir;
        this.zipPath = zipPath;
    }

    /**
     * Returns the JTL output file path.
     */
    public Path jtlPath() {
        return jtlPath;
    }

    /**
     * Returns the HTML report directory path.
     */
    public Path htmlDir() {
        return htmlDir;
    }

    /**
     * Returns the zipped report artifact path.
     */
    public Path zipPath() {
        return zipPath;
    }
}

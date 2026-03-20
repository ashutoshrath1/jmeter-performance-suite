package com.jmeter.suite.report;

import java.nio.file.Path;

public final class ReportArtifactPaths {

    private final Path jtlPath;
    private final Path htmlDir;
    private final Path zipPath;

    public ReportArtifactPaths(Path jtlPath, Path htmlDir, Path zipPath) {
        this.jtlPath = jtlPath;
        this.htmlDir = htmlDir;
        this.zipPath = zipPath;
    }

    public Path jtlPath() {
        return jtlPath;
    }

    public Path htmlDir() {
        return htmlDir;
    }

    public Path zipPath() {
        return zipPath;
    }
}

package com.jmeter.suite.report;

import com.jmeter.suite.config.EnvironmentConfig;
import com.jmeter.suite.notify.ReportMailer;
import com.jmeter.suite.util.FileOps;
import com.jmeter.suite.util.Log;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Packages and distributes a generated report: archive, email, and optional browser open.
 *
 * <p>Every step here is best-effort. The run's real output is the JTL and the computed metrics; a
 * failure to zip, mail, or open a browser must never change a plan's verdict.
 */
public final class ReportPublisher {

    private final EnvironmentConfig config;

    /**
     * Creates a publisher bound to an environment.
     */
    public ReportPublisher(EnvironmentConfig config) {
        this.config = config;
    }

    /**
     * Archives the report, emails it when configured, and opens it outside CI.
     */
    public void publish(String planId, ReportArtifactPaths artifacts) {
        try {
            FileOps.zipDirectory(artifacts.htmlDir(), artifacts.zipPath());
            Log.info("Report archive created: " + artifacts.zipPath());
        } catch (Exception ex) {
            Log.info("Failed to archive report: " + ex.getMessage());
        }

        ReportMailer.sendIfConfigured(planId, artifacts);

        if (!isCi() && config.autoOpenReports()) {
            openInBrowser(artifacts.htmlDir().resolve("index.html"));
        } else {
            Log.info("Auto-open disabled or CI detected; report available at " + artifacts.htmlDir());
        }
    }

    /**
     * Returns whether the run is happening on a CI runner, where opening a browser is meaningless.
     */
    private boolean isCi() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("CI", "false"));
    }

    /**
     * Opens the report index using the desktop handler, falling back to the platform opener.
     */
    private void openInBrowser(Path index) {
        if (!Files.exists(index)) {
            Log.info("No report index to open at " + index);
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(index.toUri());
                return;
            }
            String opener = System.getProperty("os.name", "").toLowerCase().contains("mac")
                    ? "open" : "xdg-open";
            new ProcessBuilder(opener, index.toString()).start();
        } catch (IOException | UnsupportedOperationException ex) {
            Log.info("Could not open report automatically: " + ex.getMessage());
        }
    }
}

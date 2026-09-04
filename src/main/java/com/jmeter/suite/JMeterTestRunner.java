package com.jmeter.suite;

import com.jmeter.suite.config.EnvironmentConfig;
import com.jmeter.suite.config.RunnerArgs;
import com.jmeter.suite.health.HealthChecker;
import com.jmeter.suite.metrics.BackendListenerFactory;
import com.jmeter.suite.model.PlanDefinition;
import com.jmeter.suite.model.PlanRegistry;
import com.jmeter.suite.report.ExecutionStats;
import com.jmeter.suite.report.JtlAnalyzer;
import com.jmeter.suite.report.ReportArtifactPaths;
import com.jmeter.suite.report.ReportPublisher;
import com.jmeter.suite.runtime.DistributedTestRunner;
import com.jmeter.suite.runtime.JMeterRuntime;
import com.jmeter.suite.util.FileOps;
import com.jmeter.suite.util.Log;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.report.dashboard.ReportGenerator;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates end-to-end execution of configured JMeter plans: setup, health check, plan
 * execution, result analysis, and reporting.
 */
public class JMeterTestRunner {

    private static final String REPORTS_DIR = "reports";
    private static final String LOGS_DIR = "logs";

    private final RunnerArgs runnerArgs;
    private EnvironmentConfig environmentConfig;
    private ReportPublisher reportPublisher;

    /**
     * Creates a runner bound to parsed command-line arguments.
     */
    public JMeterTestRunner(RunnerArgs runnerArgs) {
        this.runnerArgs = runnerArgs;
    }

    /**
     * Parses arguments, executes the run, and exits with the resulting status code.
     */
    public static void main(String[] args) {
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF");
        System.setProperty("log4j2.statusLoggerLevel", "OFF");
        JMeterTestRunner runner = new JMeterTestRunner(RunnerArgs.from(args));

        try {
            System.exit(runner.run());
        } catch (Exception ex) {
            System.err.println("Fatal error: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Executes the selected suite for the configured environment and returns process exit status.
     */
    public int run() throws Exception {
        createWorkingDirs();
        JMeterRuntime.initialize();

        environmentConfig = EnvironmentConfig.load(runnerArgs.environment());
        environmentConfig.pushToJMeter();
        reportPublisher = new ReportPublisher(environmentConfig);

        PlanRegistry planRegistry = PlanRegistry.load();
        List<PlanDefinition> plans = planRegistry.resolveSuite(runnerArgs.suite());
        if (plans.isEmpty()) {
            Log.info("Unknown suite: " + runnerArgs.suite());
            Log.info("Available suites: " + planRegistry.supportedSuites());
            return 1;
        }

        Log.info("JMeter Performance Suite - Java Runner");
        Log.info("Environment: " + environmentConfig.name());
        Log.info("Suite: " + runnerArgs.suite());
        Log.info("Plans: " + plans.stream().map(PlanDefinition::id).collect(Collectors.toList()));

        if (!new HealthChecker(environmentConfig).check()) {
            Log.info("Health check failed - aborting");
            return 1;
        }

        List<String> failures = new ArrayList<>();
        for (PlanDefinition plan : plans) {
            if (!runPlan(plan)) {
                failures.add(plan.id());
            }
        }

        Log.info("Summary: total=" + plans.size()
                + ", passed=" + (plans.size() - failures.size())
                + ", failed=" + failures.size());
        if (!failures.isEmpty()) {
            Log.info("Failed: " + failures);
        }
        return failures.isEmpty() ? 0 : 1;
    }

    /**
     * Ensures expected output directories exist before execution starts.
     */
    private void createWorkingDirs() throws IOException {
        Files.createDirectories(Paths.get(REPORTS_DIR));
        Files.createDirectories(Paths.get(LOGS_DIR));
    }

    /**
     * Runs a single JMeter plan and evaluates it against the environment's SLA gates.
     */
    private boolean runPlan(PlanDefinition plan) {
        Path jmxPath = plan.jmxPath();
        if (!Files.exists(jmxPath)) {
            Log.info("Missing JMX: " + jmxPath);
            return false;
        }

        ReportArtifactPaths artifacts = buildArtifactPaths(plan);
        try {
            prepareOutputPaths(artifacts);
        } catch (IOException ex) {
            Log.info("Could not prepare output directories: " + ex.getMessage());
            return false;
        }

        Log.info("Running plan: " + plan.id() + " (" + jmxPath + ")");
        Instant start = Instant.now();

        try {
            HashTree testPlanTree = SaveService.loadTree(jmxPath.toFile());
            applyEnvironmentOverrides(testPlanTree);
            removeResultCollectors(testPlanTree);
            testPlanTree.add(testPlanTree.getArray()[0], createCollector(artifacts.jtlPath()));

            BackendListenerFactory.create(environmentConfig, plan.id()).ifPresent(listener -> {
                testPlanTree.add(testPlanTree.getArray()[0], listener);
                Log.info("Streaming metrics via " + listener.getClassname());
            });

            List<String> remoteHosts = environmentConfig.remoteHosts();
            if (remoteHosts.isEmpty()) {
                StandardJMeterEngine engine = new StandardJMeterEngine();
                engine.configure(testPlanTree);
                engine.run();
            } else {
                DistributedTestRunner.run(testPlanTree, remoteHosts, environmentConfig.asProperties());
            }

            // Report rendering is presentation, not result. A plan passes or fails on its SLA
            // gates; losing the HTML dashboard must not turn a healthy run into a failure.
            boolean reported = tryGenerateReport(plan, artifacts);
            ExecutionStats stats = JtlAnalyzer.analyze(artifacts.jtlPath());

            Log.info("Completed: " + plan.id() + " in "
                    + Duration.between(start, Instant.now()).getSeconds() + "s");
            Log.info("Results: " + artifacts.jtlPath()
                    + (reported ? ", HTML: " + artifacts.htmlDir() : ", HTML: not generated"));
            Log.info("Execution stats: samples=" + stats.sampleCount()
                    + ", errors=" + stats.errorCount()
                    + ", errorRate=" + String.format("%.2f", stats.errorRatePercent()) + "%"
                    + ", p95=" + stats.p95ResponseTimeMs() + "ms"
                    + ", throughput=" + String.format("%.2f", stats.throughputTps()) + "/s");

            return evaluateThresholds(plan, stats);
        } catch (Exception ex) {
            Log.info("Failed: " + plan.id() + " due to " + ex.getMessage());
            return false;
        }
    }

    /**
     * Evaluates all configured SLA gates for a completed plan and reports the first breach.
     */
    private boolean evaluateThresholds(PlanDefinition plan, ExecutionStats stats) {
        if (stats.sampleCount() == 0) {
            Log.info("Failed: " + plan.id() + " produced zero samples.");
            return false;
        }

        double maxErrorRate = environmentConfig.maxErrorRatePercent();
        if (stats.errorRatePercent() > maxErrorRate) {
            Log.info("Failed: " + plan.id() + " error rate "
                    + String.format("%.2f", stats.errorRatePercent())
                    + "% exceeded max_error_rate_percent=" + maxErrorRate);
            return false;
        }

        long p95Budget = environmentConfig.p95ResponseTimeMs();
        if (p95Budget > 0 && stats.p95ResponseTimeMs() > p95Budget) {
            Log.info("Failed: " + plan.id() + " p95 " + stats.p95ResponseTimeMs()
                    + "ms exceeded budget of " + p95Budget + "ms");
            return false;
        }

        double minThroughput = environmentConfig.minThroughputTps();
        if (minThroughput > 0 && stats.throughputTps() < minThroughput) {
            Log.info("Failed: " + plan.id() + " throughput "
                    + String.format("%.2f", stats.throughputTps())
                    + "/s below min_throughput_tps=" + minThroughput);
            return false;
        }

        return true;
    }

    /**
     * Creates a non-appending collector that streams samples to the given JTL path.
     */
    private ResultCollector createCollector(Path jtlPath) {
        ResultCollector collector = new ResultCollector(new Summariser("summary"));
        collector.setProperty("ResultCollector.append", false);
        collector.setFilename(jtlPath.toString());
        return collector;
    }

    /**
     * Builds timestamped artifact paths for a single plan execution.
     */
    private ReportArtifactPaths buildArtifactPaths(PlanDefinition plan) {
        String runId = String.valueOf(System.currentTimeMillis());
        Path jtlPath = Paths.get(REPORTS_DIR, plan.id() + "-" + runId + ".jtl");
        Path htmlDir = Paths.get(REPORTS_DIR, plan.id() + "-" + runId + "-html");
        Path zipPath = htmlDir.resolveSibling(htmlDir.getFileName() + ".zip");
        return new ReportArtifactPaths(jtlPath, htmlDir, zipPath);
    }

    /**
     * Clears any stale artifacts and prepares empty output locations for the current run.
     */
    private void prepareOutputPaths(ReportArtifactPaths artifacts) throws IOException {
        Files.deleteIfExists(artifacts.jtlPath());
        FileOps.deleteDir(artifacts.htmlDir());
        Files.deleteIfExists(artifacts.zipPath());
        FileOps.deleteDir(Paths.get("report-output"));
        Files.createDirectories(artifacts.htmlDir());
    }

    /**
     * Generates the HTML report, reporting failure without aborting the plan.
     *
     * <p>HTML generation needs JMeter's FreeMarker templates in {@code bin/report-template}, which
     * ship with the JMeter distribution rather than with this runner. When they are absent the run
     * itself is still perfectly valid, so this degrades to a warning and points at the fix.
     */
    private boolean tryGenerateReport(PlanDefinition plan, ReportArtifactPaths artifacts) {
        try {
            generateReport(artifacts);
            reportPublisher.publish(plan.id(), artifacts);
            return true;
        } catch (Exception ex) {
            Log.info("HTML report not generated for " + plan.id() + ": " + ex.getMessage());
            Log.info("Run scripts/fetch-report-template.sh to install JMeter's report templates. "
                    + "The JTL results and the metrics below are unaffected.");
            return false;
        }
    }

    /**
     * Renders the HTML dashboard from the completed results file.
     *
     * <p>The collector is deliberately not passed to {@link ReportGenerator}: supplying one selects
     * JMeter's live-generation path, which requires the JTL to still be empty and therefore always
     * fails here. Passing null generates from the completed results file, which is what we want.
     */
    private void generateReport(ReportArtifactPaths artifacts) throws Exception {
        JMeterUtils.setProperty("report.output.dir", artifacts.htmlDir().toString());
        JMeterUtils.setProperty("jmeter.reportgenerator.exporter.html.property.output_dir",
                artifacts.htmlDir().toString());
        JMeterUtils.setProperty("jmeter.reportgenerator.temp_dir",
                artifacts.htmlDir().resolve("temp").toString());

        new ReportGenerator(artifacts.jtlPath().toString(), null).generate();
    }

    /**
     * Recursively removes result collectors declared inside a loaded test tree.
     *
     * <p>Plans carry their own listeners for GUI use; the runner replaces them with a single
     * collector so every run writes exactly one JTL to a known path.
     */
    private void removeResultCollectors(HashTree tree) {
        List<Object> toRemove = new ArrayList<>();
        for (Object key : tree.keySet()) {
            if (key instanceof ResultCollector) {
                toRemove.add(key);
            }
        }
        toRemove.forEach(tree::remove);
        for (Object key : tree.keySet()) {
            removeResultCollectors(tree.getTree(key));
        }
    }

    /**
     * Recursively applies configured host and protocol overrides to HTTP samplers.
     *
     * <p>Samplers whose domain or protocol already reference a variable or property (for example
     * {@code ${host}}) are left untouched, so a plan can target more than one host. Only literal
     * values are rewritten, which preserves the behaviour of plans that hardcode a domain.
     */
    private void applyEnvironmentOverrides(HashTree tree) {
        for (Object key : tree.keySet()) {
            if (key instanceof HTTPSamplerProxy) {
                HTTPSamplerProxy sampler = (HTTPSamplerProxy) key;
                if (isLiteral(sampler.getDomain())) {
                    sampler.setDomain(environmentConfig.host());
                }
                if (isLiteral(sampler.getProtocol())) {
                    sampler.setProtocol(environmentConfig.protocol());
                }
            } else if (key instanceof TestElement) {
                HashTree childTree = tree.getTree(key);
                if (childTree != null) {
                    applyEnvironmentOverrides(childTree);
                }
            }
        }
    }

    /**
     * Returns true when a value is a plain literal rather than a JMeter variable or property call.
     */
    private boolean isLiteral(String value) {
        return value == null || value.trim().isEmpty() || !value.contains("${");
    }
}

package com.jmeter.suite;

import com.jmeter.suite.config.EnvironmentConfig;
import com.jmeter.suite.config.RunnerArgs;
import com.jmeter.suite.model.PlanDefinition;
import com.jmeter.suite.report.ReportArtifactPaths;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JMeterTestRunner {

    private static final String JMETER_PROPERTIES = "config/jmeter.properties";
    private static final String REPORTS_DIR = "reports";
    private static final String LOGS_DIR = "logs";
    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RunnerArgs runnerArgs;
    private EnvironmentConfig environmentConfig;

    public JMeterTestRunner(RunnerArgs runnerArgs) {
        this.runnerArgs = runnerArgs;
    }

    public static void main(String[] args) {
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF");
        System.setProperty("log4j2.statusLoggerLevel", "OFF");
        RunnerArgs runnerArgs = RunnerArgs.from(args);
        JMeterTestRunner runner = new JMeterTestRunner(runnerArgs);

        try {
            System.exit(runner.run());
        } catch (Exception ex) {
            System.err.println("Fatal error: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public int run() throws Exception {
        createWorkingDirs();
        initJMeter();
        environmentConfig = EnvironmentConfig.load(runnerArgs.environment());
        environmentConfig.pushToJMeter();

        List<PlanDefinition> plans = PlanDefinition.resolveSuite(runnerArgs.suite());
        if (plans.isEmpty()) {
            info("Unknown suite: " + runnerArgs.suite());
            info("Available suites: " + PlanDefinition.supportedSuites());
            return 1;
        }

        info("JMeter Performance Suite - Java Runner");
        info("Environment: " + environmentConfig.name());
        info("Suite: " + runnerArgs.suite());
        info("Plans: " + plans.stream().map(PlanDefinition::id).collect(Collectors.toList()));

        if (!performHealthCheck()) {
            info("Health check failed - aborting");
            return 1;
        }

        List<String> failures = new ArrayList<>();
        for (PlanDefinition plan : plans) {
            if (!runPlan(plan)) {
                failures.add(plan.id());
            }
        }

        info("Summary: total=" + plans.size() + ", passed=" + (plans.size() - failures.size()) + ", failed=" + failures.size());
        if (!failures.isEmpty()) {
            info("Failed: " + failures);
        }
        return failures.isEmpty() ? 0 : 1;
    }

    private void createWorkingDirs() throws IOException {
        Files.createDirectories(Paths.get(REPORTS_DIR));
        Files.createDirectories(Paths.get(LOGS_DIR));
    }

    private void initJMeter() throws IOException {
        String log4jConfig = Paths.get("bin", "log4j2.xml").toAbsolutePath().normalize().toString();
        String jmeterLogFile = Paths.get(LOGS_DIR, "jmeter.log").toString();
        System.setProperty("log4j2.configurationFile", log4jConfig);
        System.setProperty("log4j.configurationFile", log4jConfig);
        System.setProperty("log4j2.statusLevel", "error");
        System.setProperty("log4j2.disable.jmx", "true");
        System.setProperty("log_file", jmeterLogFile);
        System.setProperty("jmeter.logfile", jmeterLogFile);

        Path jmeterProps = Paths.get(JMETER_PROPERTIES);
        if (!Files.exists(jmeterProps)) {
            throw new IllegalStateException("Missing JMeter properties: " + JMETER_PROPERTIES);
        }

        JMeterUtils.setJMeterHome(Paths.get(".").toAbsolutePath().normalize().toString());
        JMeterUtils.loadJMeterProperties(jmeterProps.toString());
        JMeterUtils.setProperty("log_file", jmeterLogFile);
        ensureReportDefaults();
        JMeterUtils.initLocale();
        SaveService.loadProperties();
    }

    private void ensureReportDefaults() {
        setIfMissing("jmeter.reportgenerator.apdex_satisfied_threshold", "500");
        setIfMissing("jmeter.reportgenerator.apdex_tolerated_threshold", "1500");
    }

    private void setIfMissing(String key, String defaultValue) {
        String current = JMeterUtils.getProperty(key);
        if (current == null || current.trim().isEmpty() || current.contains("${")) {
            JMeterUtils.setProperty(key, defaultValue);
        }
    }

    private boolean performHealthCheck() {
        String skipHealthCheck = System.getenv("SKIP_HEALTH_CHECK");
        if (skipHealthCheck != null && Boolean.parseBoolean(skipHealthCheck)) {
            info("Health check disabled via SKIP_HEALTH_CHECK environment variable.");
            return true;
        }

        if (!environmentConfig.healthCheckEnabled()) {
            info("Health check disabled for environment: " + environmentConfig.name());
            return true;
        }

        String url = environmentConfig.protocol() + "://" + environmentConfig.host() + environmentConfig.healthPath();
        int timeoutMs = environmentConfig.healthTimeoutMs();
        info("Health check: GET " + url + " (timeout=" + timeoutMs + "ms)");

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                info("Health check response: HTTP " + status);
                return status >= 200 && status < 300;
            }
        } catch (IOException ex) {
            info("Health check failed: " + ex.getMessage());
            return false;
        }
    }

    private boolean runPlan(PlanDefinition plan) {
        Path jmxPath = plan.jmxPath();
        if (!Files.exists(jmxPath)) {
            info("Missing JMX: " + jmxPath);
            return false;
        }

        ReportArtifactPaths artifacts = buildArtifactPaths(plan);
        try {
            prepareOutputPaths(artifacts);
        } catch (IOException ex) {
            info("Could not prepare output directories: " + ex.getMessage());
            return false;
        }

        info("Running plan: " + plan.id() + " (" + jmxPath + ")");
        Instant start = Instant.now();

        try {
            StandardJMeterEngine engine = new StandardJMeterEngine();
            HashTree testPlanTree = SaveService.loadTree(jmxPath.toFile());

            applyEnvironmentOverrides(testPlanTree);
            removeResultCollectors(testPlanTree);
            ResultCollector collector = createCollector(artifacts.jtlPath());
            testPlanTree.add(testPlanTree.getArray()[0], collector);

            engine.configure(testPlanTree);
            engine.run();

            generateReport(plan, artifacts, collector);
            ExecutionStats stats = readExecutionStats(artifacts.jtlPath());
            boolean withinThreshold = stats.errorRatePercent() <= environmentConfig.maxErrorRatePercent();

            Duration duration = Duration.between(start, Instant.now());
            info("Completed: " + plan.id() + " in " + duration.toSeconds() + "s");
            info("Results: " + artifacts.jtlPath() + ", HTML: " + artifacts.htmlDir());
            info("Execution stats: samples=" + stats.sampleCount() + ", errors=" + stats.errorCount() +
                    ", errorRate=" + String.format("%.2f", stats.errorRatePercent()) + "%");

            if (stats.sampleCount() == 0) {
                info("Failed: " + plan.id() + " produced zero samples.");
                return false;
            }
            if (!withinThreshold) {
                info("Failed: " + plan.id() + " exceeded max_error_rate_percent=" + environmentConfig.maxErrorRatePercent());
                return false;
            }
            return true;
        } catch (Exception ex) {
            info("Failed: " + plan.id() + " due to " + ex.getMessage());
            return false;
        }
    }

    private ResultCollector createCollector(Path jtlPath) {
        Summariser summariser = new Summariser("summary");
        ResultCollector collector = new ResultCollector(summariser);
        collector.setProperty("ResultCollector.append", false);
        collector.setFilename(jtlPath.toString());
        return collector;
    }

    private ReportArtifactPaths buildArtifactPaths(PlanDefinition plan) {
        String runId = String.valueOf(System.currentTimeMillis());
        Path jtlPath = Paths.get(REPORTS_DIR, plan.id() + "-" + runId + ".jtl");
        Path htmlDir = Paths.get(REPORTS_DIR, plan.id() + "-" + runId + "-html");
        Path zipPath = htmlDir.resolveSibling(htmlDir.getFileName() + ".zip");
        return new ReportArtifactPaths(jtlPath, htmlDir, zipPath);
    }

    private void prepareOutputPaths(ReportArtifactPaths artifacts) throws IOException {
        Files.deleteIfExists(artifacts.jtlPath());
        deleteDir(artifacts.htmlDir());
        Files.deleteIfExists(artifacts.zipPath());
        deleteDir(Paths.get("report-output"));
        Files.createDirectories(artifacts.htmlDir());
    }

    private void generateReport(PlanDefinition plan, ReportArtifactPaths artifacts, ResultCollector collector) throws Exception {
        JMeterUtils.setProperty("report.output.dir", artifacts.htmlDir().toString());
        JMeterUtils.setProperty("jmeter.reportgenerator.exporter.html.property.output_dir", artifacts.htmlDir().toString());
        JMeterUtils.setProperty("jmeter.reportgenerator.temp_dir", artifacts.htmlDir().resolve("temp").toString());

        try {
            ReportGenerator generator = new ReportGenerator(artifacts.jtlPath().toString(), collector);
            generator.generate();
            postProcessReport(plan, artifacts);
        } catch (Exception primary) {
            info("Primary HTML generation failed: " + primary.getMessage());
            if (tryCliReport(artifacts.jtlPath(), artifacts.htmlDir())) {
                info("Generated report via CLI fallback.");
                postProcessReport(plan, artifacts);
                return;
            }
            throw primary;
        }
    }

    private boolean tryCliReport(Path jtlPath, Path htmlDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("jmeter", "-g", jtlPath.toString(), "-o", htmlDir.toString());
            pb.directory(Paths.get(".").toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            info("CLI fallback failed: " + ex.getMessage());
            return false;
        }
    }

    private void postProcessReport(PlanDefinition plan, ReportArtifactPaths artifacts) {
        boolean isCi = Boolean.parseBoolean(System.getenv().getOrDefault("CI", "false"));
        try {
            zipDirectory(artifacts.htmlDir(), artifacts.zipPath());
            info("Report archive created: " + artifacts.zipPath());
        } catch (Exception ex) {
            info("Failed to archive report: " + ex.getMessage());
        }

        sendEmailIfConfigured(plan.id(), artifacts);

        if (!isCi && environmentConfig.autoOpenReports()) {
            openInBrowser(artifacts.htmlDir().resolve("index.html"));
        } else {
            info("Auto-open disabled or CI detected; report available at " + artifacts.htmlDir());
        }
    }

    private void sendEmailIfConfigured(String planName, ReportArtifactPaths artifacts) {
        String smtpHost = env("SMTP_HOST");
        String to = env("SMTP_TO");
        if (smtpHost == null || to == null) {
            info("Email skipped: SMTP_HOST/SMTP_TO not set.");
            return;
        }

        Properties props = new Properties();
        String smtpPort = env("SMTP_PORT", "587");
        String smtpUser = env("SMTP_USER");
        String smtpPass = env("SMTP_PASS");

        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.starttls.enable", env("SMTP_STARTTLS", "true"));
        props.put("mail.smtp.auth", smtpUser != null ? "true" : "false");

        Session session = buildMailSession(props, smtpUser, smtpPass);

        try {
            String from = env("SMTP_FROM", smtpUser != null ? smtpUser : "jmeter@localhost");
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            for (String addr : to.split(",")) {
                if (!addr.trim().isEmpty()) {
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(addr.trim()));
                }
            }
            message.setSubject("JMeter report: " + planName);

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Attached: JMeter report for plan '" + planName + "'.\n" +
                    "JTL: " + artifacts.jtlPath().toAbsolutePath() + "\n" +
                    "HTML: " + artifacts.htmlDir().toAbsolutePath() + "\n");

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);

            if (Files.exists(artifacts.zipPath())) {
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.attachFile(artifacts.zipPath().toFile());
                attachment.setFileName(artifacts.zipPath().getFileName().toString());
                multipart.addBodyPart(attachment);
            }

            message.setContent(multipart);
            Transport.send(message);
            info("Email sent to: " + to);
        } catch (Exception ex) {
            info("Email send failed: " + ex.getMessage());
        }
    }

    private Session buildMailSession(Properties props, String smtpUser, String smtpPass) {
        if (smtpUser != null && smtpPass != null) {
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPass);
                }
            });
        }
        return Session.getInstance(props);
    }

    private void openInBrowser(Path indexHtml) {
        try {
            if (!Files.exists(indexHtml)) {
                info("Report index not found: " + indexHtml);
                return;
            }

            String command = isMac() ? "open" : "xdg-open";
            new ProcessBuilder(command, indexHtml.toAbsolutePath().toString()).start();
            info("Opened report in browser: " + indexHtml);
        } catch (Exception ex) {
            info("Unable to open browser automatically: " + ex.getMessage());
        }
    }

    private boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(sourceDir.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                    });
        } catch (java.io.UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

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

    private void applyEnvironmentOverrides(HashTree tree) {
        for (Object key : tree.keySet()) {
            if (key instanceof HTTPSamplerProxy) {
                HTTPSamplerProxy sampler = (HTTPSamplerProxy) key;
                sampler.setDomain(environmentConfig.host());
                sampler.setProtocol(environmentConfig.protocol());
            } else if (key instanceof TestElement) {
                HashTree childTree = tree.getTree(key);
                if (childTree != null) {
                    applyEnvironmentOverrides(childTree);
                }
            }
        }
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort cleanup of generated artifacts
                    }
                });
    }

    private String env(String key) {
        return System.getenv(key);
    }

    private String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private ExecutionStats readExecutionStats(Path jtlPath) throws IOException {
        try (Stream<String> lines = Files.lines(jtlPath)) {
            long[] counts = lines.skip(1).map(line -> line.split(",", -1)).reduce(
                    new long[]{0L, 0L},
                    (acc, columns) -> {
                        acc[0]++;
                        if (columns.length > 7 && "false".equalsIgnoreCase(columns[7])) {
                            acc[1]++;
                        }
                        return acc;
                    },
                    (left, right) -> new long[]{left[0] + right[0], left[1] + right[1]}
            );
            return new ExecutionStats(counts[0], counts[1]);
        }
    }

    private void info(String message) {
        System.out.println("[" + LOG_TS.format(LocalDateTime.now()) + "] " + message);
    }

    private static final class ExecutionStats {
        private final long sampleCount;
        private final long errorCount;

        private ExecutionStats(long sampleCount, long errorCount) {
            this.sampleCount = sampleCount;
            this.errorCount = errorCount;
        }

        private long sampleCount() {
            return sampleCount;
        }

        private long errorCount() {
            return errorCount;
        }

        private double errorRatePercent() {
            if (sampleCount == 0) {
                return 100.0;
            }
            return (errorCount * 100.0) / sampleCount;
        }
    }
}

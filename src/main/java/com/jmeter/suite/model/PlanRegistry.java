package com.jmeter.suite.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Discovers test plans from disk and resolves suite names to ordered plan lists.
 *
 * <p>Plans are found by scanning the plan directory for {@code *.jmx}, so dropping in a new file is
 * enough to make it runnable by id - no code change, no rebuild. Suite groupings live in a
 * properties file rather than in a switch statement, so they are editable the same way.
 */
public final class PlanRegistry {

    private static final String DEFAULT_PLAN_DIR = "test-plans";
    private static final String DEFAULT_SUITE_CONFIG = "config/suites.properties";
    private static final String ALL_SUITE = "all";

    private final Map<String, PlanDefinition> plansById;
    private final Map<String, List<String>> suites;

    /**
     * Creates a registry over the given discovered plans and suite groupings.
     */
    PlanRegistry(Map<String, PlanDefinition> plansById, Map<String, List<String>> suites) {
        this.plansById = plansById;
        this.suites = suites;
    }

    /**
     * Loads a registry using the conventional plan directory and suite configuration.
     */
    public static PlanRegistry load() throws IOException {
        return load(Paths.get(DEFAULT_PLAN_DIR), Paths.get(DEFAULT_SUITE_CONFIG));
    }

    /**
     * Loads a registry from an explicit plan directory and suite configuration file.
     */
    public static PlanRegistry load(Path planDir, Path suiteConfig) throws IOException {
        Map<String, PlanDefinition> plans = discoverPlans(planDir);
        return new PlanRegistry(plans, loadSuites(suiteConfig));
    }

    /**
     * Scans a directory for JMX plans, keyed by identifier derived from the filename.
     */
    private static Map<String, PlanDefinition> discoverPlans(Path planDir) throws IOException {
        Map<String, PlanDefinition> plans = new LinkedHashMap<>();
        if (!Files.isDirectory(planDir)) {
            return plans;
        }
        try (Stream<Path> files = Files.list(planDir)) {
            files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jmx"))
                    .sorted()
                    .forEach(path -> {
                        String id = planIdFor(path);
                        plans.put(id, new PlanDefinition(id, path));
                    });
        }
        return plans;
    }

    /**
     * Derives a plan identifier from a JMX filename, dropping the extension and any -test suffix.
     */
    static String planIdFor(Path jmxPath) {
        String name = jmxPath.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".jmx")) {
            name = name.substring(0, name.length() - ".jmx".length());
        }
        if (name.toLowerCase(Locale.ROOT).endsWith("-test")) {
            name = name.substring(0, name.length() - "-test".length());
        }
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Reads suite groupings, tolerating a missing configuration file.
     */
    private static Map<String, List<String>> loadSuites(Path suiteConfig) throws IOException {
        Map<String, List<String>> suites = new LinkedHashMap<>();
        if (suiteConfig == null || !Files.isRegularFile(suiteConfig)) {
            return suites;
        }
        Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(suiteConfig)) {
            properties.load(is);
        }
        properties.stringPropertyNames().forEach(name -> {
            List<String> members = Arrays.stream(properties.getProperty(name).split(","))
                    .map(String::trim)
                    .filter(member -> !member.isEmpty())
                    .map(member -> member.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
            if (!members.isEmpty()) {
                suites.put(name.toLowerCase(Locale.ROOT), members);
            }
        });
        return suites;
    }

    /**
     * Resolves a suite or plan name to an ordered list of plans, empty when nothing matches.
     *
     * <p>Resolution order is: a configured suite, then the implicit {@code all} suite, then a single
     * plan by id. Configured suite members that do not exist on disk are skipped.
     */
    public List<PlanDefinition> resolveSuite(String suite) {
        if (suite == null) {
            return new ArrayList<>();
        }
        String normalized = suite.trim().toLowerCase(Locale.ROOT);

        List<String> configured = suites.get(normalized);
        if (configured != null) {
            return configured.stream()
                    .map(plansById::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }

        if (ALL_SUITE.equals(normalized)) {
            return new ArrayList<>(plansById.values());
        }

        return byId(normalized)
                .map(plan -> {
                    List<PlanDefinition> single = new ArrayList<>();
                    single.add(plan);
                    return single;
                })
                .orElseGet(ArrayList::new);
    }

    /**
     * Returns a plan definition for the provided id when available.
     */
    public Optional<PlanDefinition> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plansById.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns every discovered plan, in identifier order.
     */
    public List<PlanDefinition> plans() {
        return new ArrayList<>(plansById.values());
    }

    /**
     * Returns a user-facing list of all available suite and plan names.
     */
    public String supportedSuites() {
        List<String> names = new ArrayList<>();
        if (!suites.containsKey(ALL_SUITE)) {
            names.add(ALL_SUITE);
        }
        names.addAll(suites.keySet());
        names.addAll(plansById.keySet());
        return String.join(", ", names);
    }
}

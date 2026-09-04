package com.jmeter.suite.health;

import com.jmeter.suite.config.EnvironmentConfig;
import com.jmeter.suite.util.Log;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;

/**
 * Verifies the target responds before a run starts.
 *
 * <p>Cheap insurance: without it a misconfigured host produces a full run of connection errors that
 * looks like a performance result, and the failure is only obvious once someone reads the report.
 */
public final class HealthChecker {

    private final EnvironmentConfig config;

    /**
     * Creates a checker bound to an environment.
     */
    public HealthChecker(EnvironmentConfig config) {
        this.config = config;
    }

    /**
     * Returns whether the target is reachable, honouring the environment and the skip override.
     */
    public boolean check() {
        String skip = System.getenv("SKIP_HEALTH_CHECK");
        if (skip != null && Boolean.parseBoolean(skip)) {
            Log.info("Health check disabled via SKIP_HEALTH_CHECK environment variable.");
            return true;
        }

        if (!config.healthCheckEnabled()) {
            Log.info("Health check disabled for environment: " + config.name());
            return true;
        }

        String url = config.baseUrl() + config.healthPath();
        int timeoutMs = config.healthTimeoutMs();
        Log.info("Health check: GET " + url + " (timeout=" + timeoutMs + "ms)");

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpGet get = new HttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                Log.info("Health check response: HTTP " + status);
                return status >= 200 && status < 300;
            }
        } catch (IOException ex) {
            Log.info("Health check failed: " + ex.getMessage());
            return false;
        }
    }
}

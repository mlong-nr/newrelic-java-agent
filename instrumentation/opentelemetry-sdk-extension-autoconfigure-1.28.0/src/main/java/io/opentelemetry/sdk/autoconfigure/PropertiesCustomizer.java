package io.opentelemetry.sdk.autoconfigure;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.NewRelic;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

public final class PropertiesCustomizer implements Function<ConfigProperties, Map<String, String>> {
    @Override
    public Map<String, String> apply(ConfigProperties configProperties) {
        if (configProperties.getString("otel.exporter.otlp.endpoint") == null) {
            final Agent agent = NewRelic.getAgent();
            agent.getLogger().log(Level.INFO, "Auto-initializing OpenTelemetry SDK");
            final String host = agent.getConfig().getValue("host");
            final String endpoint = "https://" + host + ":443";
            final String licenseKey = agent.getConfig().getValue("license_key");
            final Map<String, String> properties = new HashMap<>();
            properties.put("otel.exporter.otlp.headers", "api-key=" + licenseKey);
            properties.put("otel.exporter.otlp.endpoint", endpoint);
            properties.put("otel.exporter.otlp.protocol", "http/protobuf");
            properties.put("otel.span.attribute.value.length.limit", "4095");
            properties.put("otel.exporter.otlp.compression", "gzip");
            properties.put("otel.exporter.otlp.metrics.temporality.preference", "DELTA");
            properties.put("otel.exporter.otlp.metrics.default.histogram.aggregation", "BASE2_EXPONENTIAL_BUCKET_HISTOGRAM");
            properties.put("otel.experimental.exporter.otlp.retry.enabled", "true");
            properties.put("otel.experimental.resource.disabled.keys", "process.command_line");

            final Object appName = agent.getConfig().getValue("app_name");
            properties.put("otel.service.name", appName.toString());

            return properties;
        }
        return Collections.emptyMap();
    }
}

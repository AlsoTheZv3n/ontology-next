package com.nexoai.ontology.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("app", "nexo-ontology");
    }

    @Bean
    public MeterBinder nexoMetrics() {
        return registry -> {
            Counter.builder("nexo.objects.created")
                    .description("Total objects created")
                    .register(registry);

            Counter.builder("nexo.sync.runs")
                    .description("Total sync runs executed")
                    .register(registry);

            Counter.builder("nexo.agent.calls")
                    .description("Total AI agent calls")
                    .register(registry);

            Counter.builder("nexo.actions.executed")
                    .description("Total actions executed")
                    .register(registry);

            registry.gauge("nexo.tenants.active", 0);
            registry.gauge("nexo.connectors.active", 0);
        };
    }
}

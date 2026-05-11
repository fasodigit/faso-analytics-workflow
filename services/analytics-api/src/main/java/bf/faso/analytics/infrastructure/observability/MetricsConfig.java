package bf.faso.analytics.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer custom meters for analytics-api.
 *
 * <p>Wires the Prometheus-exposed counters/timers referenced by the alerts in
 * {@code infra/prometheus/alerts.yml} and the dashboards in {@code infra/grafana/dashboards/}.
 *
 * <p>All meters carry an {@code application=analytics-api} common tag injected by
 * {@link #renameApplicationFilter()}.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter renameApplicationFilter() {
        return MeterFilter.commonTags(Tags.of("application", "analytics-api"));
    }

    @Bean
    public Counter schemaDriftCounter(MeterRegistry registry) {
        return Counter.builder("analytics_schema_drift_total")
                .description("Count of schema drift detections")
                .register(registry);
    }

    @Bean
    public Counter auditChainBreakCounter(MeterRegistry registry) {
        return Counter.builder("analytics_audit_chain_break_total")
                .description("Count of audit chain hash mismatches")
                .register(registry);
    }

    @Bean
    public Timer simulationDurationTimer(MeterRegistry registry) {
        return Timer.builder("analytics_simulation_duration_seconds")
                .description("Duration of analytics simulations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    public Counter kpiThresholdBreachedCounter(MeterRegistry registry) {
        return Counter.builder("analytics_kpi_threshold_breached_total")
                .description("Count of KPI threshold breaches")
                .tag("severity", "warning")
                .register(registry);
    }

    @Bean
    public Counter deploymentRollbackFailedCounter(MeterRegistry registry) {
        return Counter.builder("analytics_deployment_rollback_failed_total")
                .description("Count of failed BLUE/GREEN rollback attempts")
                .register(registry);
    }

    @Bean
    public Counter sandboxOomKilledCounter(MeterRegistry registry) {
        return Counter.builder("analytics_sandbox_oom_killed_total")
                .description("Count of sandboxes killed by the OOM-killer")
                .register(registry);
    }

    @Bean
    public Counter vaultLeaseRenewalFailedCounter(MeterRegistry registry) {
        return Counter.builder("analytics_vault_lease_renewal_failed_total")
                .description("Count of failed Vault lease renewals")
                .register(registry);
    }
}

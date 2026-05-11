package bf.faso.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FASO-ANALYTICS-WORKFLOW — analytics-api (point d'entrée REST + STOMP).
 *
 * Architecture hexagonale :
 *   domain/         (Workflow, Step, SchemaSnapshot, AuditEntry, policies)
 *   application/    (CreateWorkflowUseCase, SimulateWorkflowUseCase, ...)
 *   infrastructure/ (persistence/yb, cache/dragonfly, messaging/redpanda,
 *                    grpc/engine, audit/blake3, auth/keto)
 *   adapters/       (rest, ws/STOMP, openapi)
 *
 * Cohérent ADR-001 (gRPC vers analytics-engine en Rust),
 * ADR-002 (JSON Schema Draft-07 pour workflow), ADR-005 (BLAKE3 + Vault).
 *
 * Phase 1 livrera le squelette bout-en-bout (1 source YB, 1 filtre, 1 KPI).
 */
@SpringBootApplication
public class AnalyticsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApiApplication.class, args);
    }
}

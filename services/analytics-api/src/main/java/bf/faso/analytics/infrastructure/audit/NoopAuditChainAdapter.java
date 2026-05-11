package bf.faso.analytics.infrastructure.audit;

import bf.faso.analytics.domain.port.AuditChainPort;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class NoopAuditChainAdapter implements AuditChainPort {

    // TODO Phase 2 : real BLAKE3 chained insert + Vault transit signature (ADR-005).
    @Override
    public Mono<Void> recordCreate(UUID workflowId, UUID versionId, String actorSubject, JsonNode payload) {
        return Mono.empty();
    }
}

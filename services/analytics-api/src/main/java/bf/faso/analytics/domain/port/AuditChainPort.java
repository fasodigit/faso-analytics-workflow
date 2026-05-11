package bf.faso.analytics.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AuditChainPort {

    Mono<Void> recordCreate(UUID workflowId,
                            UUID versionId,
                            String actorSubject,
                            JsonNode payload);
}

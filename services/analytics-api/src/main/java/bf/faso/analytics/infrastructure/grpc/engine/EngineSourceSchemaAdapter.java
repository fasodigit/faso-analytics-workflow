package bf.faso.analytics.infrastructure.grpc.engine;

import bf.faso.analytics.domain.port.SourceSchemaPort;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class EngineSourceSchemaAdapter implements SourceSchemaPort {

    // TODO Phase 3 : appel gRPC vers analytics-engine.IntrospectSource (à ajouter dans la proto).
    @Override
    public Mono<JsonNode> getCurrentSchema(String connectionRef, String tableSpec) {
        return Mono.error(new UnsupportedOperationException("introspection RPC not yet wired"));
    }
}

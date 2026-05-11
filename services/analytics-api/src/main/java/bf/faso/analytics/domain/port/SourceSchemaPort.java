package bf.faso.analytics.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

public interface SourceSchemaPort {

    Mono<JsonNode> getCurrentSchema(String connectionRef, String tableSpec);
}

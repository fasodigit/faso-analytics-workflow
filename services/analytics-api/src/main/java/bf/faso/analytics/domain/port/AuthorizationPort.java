package bf.faso.analytics.domain.port;

import reactor.core.publisher.Mono;

public interface AuthorizationPort {

    Mono<Boolean> canCreateWorkflow(String subjectId, String subProject);
}

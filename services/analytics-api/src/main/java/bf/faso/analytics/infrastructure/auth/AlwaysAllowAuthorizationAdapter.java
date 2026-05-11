package bf.faso.analytics.infrastructure.auth;

import bf.faso.analytics.domain.port.AuthorizationPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AlwaysAllowAuthorizationAdapter implements AuthorizationPort {

    // TODO Phase 2 : Ory Keto check (read-relation analytics:workflows.create_X).
    @Override
    public Mono<Boolean> canCreateWorkflow(String subjectId, String subProject) {
        return Mono.just(Boolean.TRUE);
    }
}

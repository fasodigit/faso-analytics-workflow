package bf.faso.analytics.domain.port;

import reactor.core.publisher.Mono;

public interface DomainEventPublisher {

    Mono<Void> publish(String topic, Object event);
}

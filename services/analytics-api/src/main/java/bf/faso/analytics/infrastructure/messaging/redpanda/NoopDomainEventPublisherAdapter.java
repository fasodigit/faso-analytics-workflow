package bf.faso.analytics.infrastructure.messaging.redpanda;

import bf.faso.analytics.domain.port.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnMissingBean(name = "kafkaDomainEventPublisherAdapter")
public class NoopDomainEventPublisherAdapter implements DomainEventPublisher {

    // TODO Phase 3 : Redpanda producer via Spring Cloud Stream (binder Kafka déjà inclus).
    private static final Logger LOG = LoggerFactory.getLogger(NoopDomainEventPublisherAdapter.class);

    @Override
    public Mono<Void> publish(String topic, Object event) {
        return Mono.fromRunnable(() -> LOG.info("domain-event topic={} payload={}", topic, event));
    }
}

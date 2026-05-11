package bf.faso.analytics.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Deployment(
        UUID deploymentId,
        UUID versionId,
        Strategy strategy,
        Instant startedAt,
        Instant completedAt,
        Instant rolledBackAt,
        String rollbackReason,
        Instant shadowEndsAt,
        String actorSubject,
        Status status
) {
    public enum Strategy { DIRECT, SHADOW, BLUE_GREEN }

    public enum Status { PENDING, IN_PROGRESS, COMPLETED, ROLLED_BACK, FAILED }

    public Deployment {
        if (versionId == null) {
            throw new IllegalArgumentException("versionId is required");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (actorSubject == null || actorSubject.isBlank()) {
            throw new IllegalArgumentException("actorSubject is required");
        }
    }

    public static Deployment create(UUID versionId,
                                    Strategy strategy,
                                    String actor,
                                    Integer shadowHours) {
        Instant now = Instant.now();
        Instant shadowEnds = strategy == Strategy.SHADOW && shadowHours != null && shadowHours > 0
                ? now.plus(Duration.ofHours(shadowHours))
                : null;
        return new Deployment(
                UUID.randomUUID(),
                versionId,
                strategy,
                now,
                null,
                null,
                null,
                shadowEnds,
                actor,
                Status.PENDING
        );
    }

    public Deployment withStatus(Status next) {
        return new Deployment(
                deploymentId, versionId, strategy, startedAt,
                next == Status.COMPLETED ? Instant.now() : completedAt,
                rolledBackAt, rollbackReason, shadowEndsAt, actorSubject, next
        );
    }

    public Deployment markRolledBack(String reason) {
        return new Deployment(
                deploymentId, versionId, strategy, startedAt, completedAt,
                Instant.now(), reason, shadowEndsAt, actorSubject, Status.ROLLED_BACK
        );
    }
}

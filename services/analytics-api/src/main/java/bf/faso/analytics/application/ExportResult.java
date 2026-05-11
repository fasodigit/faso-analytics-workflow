package bf.faso.analytics.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Résultat d'un job d'export (état observable côté API).
 *
 * <p>Cycle de vie :
 * <ol>
 *   <li>{@code QUEUED} — créé par {@link ExportWorkflowUseCase#execute(ExportRequest)}, retourné immédiatement au client.</li>
 *   <li>{@code RUNNING} — l'exécuteur asynchrone a démarré le {@link bf.faso.analytics.domain.port.ExportPort#render(ExportRequest)}.</li>
 *   <li>{@code SUCCEEDED} — rendu terminé avec succès, {@link #resultUri()} pointe vers MinIO/local.</li>
 *   <li>{@code FAILED} — erreur ({@link #errorMessage()} renseigné).</li>
 * </ol>
 *
 * Phase 3 : stocké en {@code ConcurrentHashMap} in-memory.
 * Phase 4 : persisté dans la table {@code export_jobs} (YugabyteDB).
 */
public record ExportResult(
        UUID jobId,
        String status,
        String resultUri,
        Instant requestedAt,
        Instant finishedAt,
        String errorMessage
) {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    public static ExportResult queued(UUID jobId, Instant requestedAt) {
        return new ExportResult(jobId, QUEUED, null, requestedAt, null, null);
    }

    public ExportResult withStatus(String newStatus) {
        return new ExportResult(jobId, newStatus, resultUri, requestedAt, finishedAt, errorMessage);
    }

    public ExportResult succeeded(String uri, Instant finishedAt) {
        return new ExportResult(jobId, SUCCEEDED, uri, requestedAt, finishedAt, null);
    }

    public ExportResult failed(String error, Instant finishedAt) {
        return new ExportResult(jobId, FAILED, null, requestedAt, finishedAt, error);
    }
}

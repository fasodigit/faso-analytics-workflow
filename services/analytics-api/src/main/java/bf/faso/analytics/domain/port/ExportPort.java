package bf.faso.analytics.domain.port;

import bf.faso.analytics.application.ExportRequest;
import bf.faso.analytics.application.ExportResult;
import reactor.core.publisher.Mono;

/**
 * Port domaine pour les adaptateurs d'export (PDF Typst, Excel POI, PPTX POI, Metabase API).
 *
 * Chaque implémentation est un {@code @Component} Spring auto-injecté dans le {@code Map<String, ExportPort>}
 * du {@code ExportWorkflowUseCase}, où la clé est le résultat de {@link #kind()}.
 *
 * Architecture hexagonale : ce port domaine n'a aucune dépendance vers le framework,
 * mais les adaptateurs implémentant l'interface peuvent dépendre de Spring/POI/Typst CLI.
 */
public interface ExportPort {

    /**
     * Identifiant de l'adaptateur d'export. Doit correspondre au discriminator {@code format}
     * de l'API {@code POST /v1/exports}.
     *
     * @return "pdf", "excel", "pptx" ou "metabase"
     */
    String kind();

    /**
     * Effectue le rendu effectif et retourne le résultat (statut SUCCEEDED + resultUri,
     * ou FAILED + errorMessage). Cette méthode est appelée de manière asynchrone par le
     * {@code ExportWorkflowUseCase} via un {@code TaskExecutor}.
     *
     * @param req requête d'export contenant la définition workflow + filtres + période
     * @return résultat avec resultUri (URI MinIO/S3 ou file://) ou message d'erreur
     */
    Mono<ExportResult> render(ExportRequest req);
}

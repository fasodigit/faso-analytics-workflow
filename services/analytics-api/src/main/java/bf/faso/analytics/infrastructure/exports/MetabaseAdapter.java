package bf.faso.analytics.infrastructure.exports;

import bf.faso.analytics.application.ExportRequest;
import bf.faso.analytics.application.ExportResult;
import bf.faso.analytics.domain.port.ExportPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Adaptateur Metabase via API HTTP {@code POST /api/card}.
 *
 * <p>Crée une "card" Metabase à partir de la {@code definition_jsonb} du workflow.
 * Retourne {@code resultUri = "<metabase_url>/question/<card_id>"}.
 *
 * <p>Configuration :
 * <ul>
 *   <li>{@code METABASE_URL} (env) ou {@code faso.exports.metabase.url} — racine de l'instance Metabase.</li>
 *   <li>{@code METABASE_TOKEN} (env) ou {@code faso.exports.metabase.token} — session token.</li>
 * </ul>
 *
 * <p>Si la configuration est absente, le rendu retourne {@code FAILED} avec
 * {@code metabase_not_configured} (Phase 3 stub — Phase 4 intègre Metabase complet).
 */
@Component
public class MetabaseAdapter implements ExportPort {

    private static final Logger LOG = LoggerFactory.getLogger(MetabaseAdapter.class);
    private static final String KIND = "metabase";

    private final ObjectMapper objectMapper;
    private final String metabaseUrl;
    private final String metabaseToken;
    private final WebClient.Builder webClientBuilder;

    public MetabaseAdapter(ObjectMapper objectMapper,
                           @Value("${faso.exports.metabase.url:${METABASE_URL:}}") String metabaseUrl,
                           @Value("${faso.exports.metabase.token:${METABASE_TOKEN:}}") String metabaseToken,
                           WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.metabaseUrl = metabaseUrl;
        this.metabaseToken = metabaseToken;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Mono<ExportResult> render(ExportRequest req) {
        UUID localJobId = UUID.randomUUID();
        Instant requested = Instant.now();

        if (metabaseUrl == null || metabaseUrl.isBlank()) {
            LOG.warn("metabase.not_configured METABASE_URL empty");
            return Mono.just(new ExportResult(
                    localJobId, ExportResult.FAILED, null, requested, Instant.now(),
                    "metabase_not_configured: set METABASE_URL + METABASE_TOKEN env vars"
            ));
        }

        ObjectNode cardPayload = buildCardPayload(req);
        WebClient client = webClientBuilder.baseUrl(metabaseUrl).build();

        return client.post()
                .uri("/api/card")
                .header("X-Metabase-Session", metabaseToken == null ? "" : metabaseToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cardPayload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    int cardId = response.path("id").asInt(0);
                    String resultUri = metabaseUrl.replaceAll("/$", "") + "/question/" + cardId;
                    LOG.info("metabase.card.created cardId={} uri={}", cardId, resultUri);
                    return new ExportResult(
                            localJobId, ExportResult.SUCCEEDED, resultUri, requested, Instant.now(), null);
                })
                .onErrorResume(err -> {
                    LOG.error("metabase.card.create.error", err);
                    return Mono.just(new ExportResult(
                            localJobId, ExportResult.FAILED, null, requested, Instant.now(),
                            "metabase_api_error: " + err.getMessage()));
                });
    }

    private ObjectNode buildCardPayload(ExportRequest req) {
        ObjectNode card = objectMapper.createObjectNode();
        JsonNode meta = req.definitionJsonb() != null ? req.definitionJsonb().get("metadata") : null;

        String name = (meta != null && meta.has("name"))
                ? meta.get("name").asText()
                : "FASO Workflow " + req.workflowId();
        card.put("name", name);
        card.put("display", "table");
        card.set("visualization_settings", objectMapper.createObjectNode());

        // dataset_query — Phase 3 stub : empty native query placeholder.
        // Phase 4 : translate workflow pipeline to Metabase query (database/source mapping).
        ObjectNode datasetQuery = objectMapper.createObjectNode();
        datasetQuery.put("type", "native");
        ObjectNode nativeQ = objectMapper.createObjectNode();
        nativeQ.put("query", "-- generated by FASO-ANALYTICS-WORKFLOW (Phase 3 stub)\nSELECT 1");
        datasetQuery.set("native", nativeQ);
        card.set("dataset_query", datasetQuery);

        return card;
    }
}

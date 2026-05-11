package bf.faso.analytics.infrastructure.exports;

import bf.faso.analytics.application.ExportRequest;
import bf.faso.analytics.application.ExportResult;
import bf.faso.analytics.domain.port.ExportPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Adaptateur PDF via Typst CLI.
 *
 * <p>Génère un PDF depuis le template {@code docs/exports/analytics-dashboard-report.typ}
 * en passant la définition workflow + données calculées comme entrée JSON via {@code --input data=...}.
 *
 * <p>Si la CLI {@code typst} n'est pas disponible (CI sans Typst), le rendu échoue proprement
 * avec un message clair. Le test {@link bf.faso.analytics.application.ExportWorkflowUseCaseTest}
 * utilise un stub pour ne pas dépendre du binaire.
 *
 * <p>Phase 3 : sortie sur disque local sous {@code /tmp/faso-analytics/exports/}, URI {@code file://}.
 * Phase 4 : upload MinIO via Spring WebClient (S3-compatible), retour d'un object key {@code s3://...}.
 */
@Component
public class PdfTypstAdapter implements ExportPort {

    private static final Logger LOG = LoggerFactory.getLogger(PdfTypstAdapter.class);
    private static final String KIND = "pdf";
    private static final long TYPST_TIMEOUT_SEC = 30;

    private final ObjectMapper objectMapper;
    private final String typstBinary;
    private final Path templatePath;
    private final Path outputDir;

    public PdfTypstAdapter(ObjectMapper objectMapper,
                           @Value("${faso.exports.typst.binary:typst}") String typstBinary,
                           @Value("${faso.exports.typst.template:docs/exports/analytics-dashboard-report.typ}") String templatePath,
                           @Value("${faso.exports.output-dir:/tmp/faso-analytics/exports}") String outputDir) {
        this.objectMapper = objectMapper;
        this.typstBinary = typstBinary;
        this.templatePath = Paths.get(templatePath);
        this.outputDir = Paths.get(outputDir);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Mono<ExportResult> render(ExportRequest req) {
        return Mono.fromCallable(() -> doRender(req));
    }

    private ExportResult doRender(ExportRequest req) {
        UUID jobId = UUID.randomUUID(); // local — usecase rewraps with the canonical jobId
        Instant requested = Instant.now();
        try {
            Files.createDirectories(outputDir);

            // 1. Build the JSON payload the template will read (data.json).
            //    Phase 3 : we inject the raw workflow definition + a minimal envelope.
            //    Phase 4 : the engine pre-computes kpis/visualizations values; here we just stub them.
            String dataJson = buildDataJson(req);
            Path dataFile = outputDir.resolve("data-" + jobId + ".json");
            Files.write(dataFile, dataJson.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Path pdfPath = outputDir.resolve("export-" + jobId + ".pdf");

            // 2. Invoke Typst CLI.
            // typst compile --input data=<path-to-json> <template> <output>
            ProcessBuilder pb = new ProcessBuilder(
                    typstBinary,
                    "compile",
                    "--input", "data=" + dataFile.toAbsolutePath(),
                    templatePath.toAbsolutePath().toString(),
                    pdfPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            Process proc;
            try {
                proc = pb.start();
            } catch (IOException e) {
                LOG.warn("typst.cli.unavailable binary={} err={}", typstBinary, e.getMessage());
                return new ExportResult(jobId, ExportResult.FAILED, null, requested, Instant.now(),
                        "typst_cli_unavailable: " + e.getMessage()
                                + " (install Typst CLI 0.12+ or set faso.exports.typst.binary)");
            }

            boolean finished = proc.waitFor(TYPST_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return new ExportResult(jobId, ExportResult.FAILED, null, requested, Instant.now(),
                        "typst_timeout after " + TYPST_TIMEOUT_SEC + "s");
            }
            int exit = proc.exitValue();
            if (exit != 0) {
                String stderr = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return new ExportResult(jobId, ExportResult.FAILED, null, requested, Instant.now(),
                        "typst_exit_" + exit + ": " + stderr.substring(0, Math.min(stderr.length(), 500)));
            }

            // 3. Phase 3 simplification : keep on local disk and return a file:// URI.
            //    Phase 4 TODO : upload to MinIO via WebClient + return s3://bucket/key.
            String resultUri = "file://" + pdfPath.toAbsolutePath();
            return new ExportResult(jobId, ExportResult.SUCCEEDED, resultUri, requested, Instant.now(), null);
        } catch (Exception e) {
            LOG.error("pdf.render.error", e);
            return new ExportResult(jobId, ExportResult.FAILED, null, requested, Instant.now(),
                    "pdf_render_error: " + e.getMessage());
        }
    }

    private String buildDataJson(ExportRequest req) {
        // Compose the data envelope the Typst template expects.
        // Falls back to a minimal stub if the definition is absent (Phase 3 — engine integration pending).
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode metadata = root.putObject("metadata");
            if (req.definitionJsonb() != null && req.definitionJsonb().has("metadata")) {
                metadata.setAll((com.fasterxml.jackson.databind.node.ObjectNode) req.definitionJsonb().get("metadata"));
            } else {
                metadata.put("name", "workflow-" + req.workflowId());
                metadata.put("subProject", "UNKNOWN");
                metadata.put("semver", "0.0.0");
            }
            // kpis : Phase 4 wiring — for now mirror the workflow definition or emit empty array.
            root.set("kpis",
                    req.definitionJsonb() != null && req.definitionJsonb().has("kpis")
                            ? req.definitionJsonb().get("kpis")
                            : objectMapper.createArrayNode());
            root.set("visualizations",
                    req.definitionJsonb() != null && req.definitionJsonb().has("visualizations")
                            ? req.definitionJsonb().get("visualizations")
                            : objectMapper.createArrayNode());
            if (req.period() != null) {
                root.put("period", req.period());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("failed to build data.json for typst", e);
        }
    }
}

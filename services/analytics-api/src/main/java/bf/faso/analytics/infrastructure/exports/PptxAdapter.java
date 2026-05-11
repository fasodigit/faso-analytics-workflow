package bf.faso.analytics.infrastructure.exports;

import bf.faso.analytics.application.ExportRequest;
import bf.faso.analytics.application.ExportResult;
import bf.faso.analytics.domain.port.ExportPort;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.awt.Rectangle;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

/**
 * Adaptateur PowerPoint via Apache POI XSLF (.pptx).
 *
 * <p>Construit un diaporama avec :
 * <ul>
 *   <li>Slide 1 : titre + métadonnées (workflow name, sub_project, semver, période).</li>
 *   <li>Slide par visualization : title, subtitle, placeholder "Visualisation rendue côté Phase 4 (headless Chrome)".</li>
 * </ul>
 *
 * Phase 3 : sortie locale {@code /tmp/faso-analytics/exports/export-<jobId>.pptx} (URI {@code file://}).
 * Phase 4 : rendu réel des charts via headless Chrome + capture PNG + upload MinIO.
 */
@Component
public class PptxAdapter implements ExportPort {

    private static final Logger LOG = LoggerFactory.getLogger(PptxAdapter.class);
    private static final String KIND = "pptx";

    private final Path outputDir;

    public PptxAdapter(@Value("${faso.exports.output-dir:/tmp/faso-analytics/exports}") String outputDir) {
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
        UUID localJobId = UUID.randomUUID();
        Instant requested = Instant.now();
        try {
            Files.createDirectories(outputDir);
            Path pptxPath = outputDir.resolve("export-" + localJobId + ".pptx");

            try (XMLSlideShow ppt = new XMLSlideShow();
                 OutputStream os = Files.newOutputStream(pptxPath)) {

                buildTitleSlide(ppt, req);
                buildVisualizationSlides(ppt, req);

                ppt.write(os);
            }

            String resultUri = "file://" + pptxPath.toAbsolutePath();
            return new ExportResult(localJobId, ExportResult.SUCCEEDED, resultUri, requested, Instant.now(), null);
        } catch (Exception e) {
            LOG.error("pptx.render.error", e);
            return new ExportResult(localJobId, ExportResult.FAILED, null, requested, Instant.now(),
                    "pptx_render_error: " + e.getMessage());
        }
    }

    private void buildTitleSlide(XMLSlideShow ppt, ExportRequest req) {
        XSLFSlide slide = ppt.createSlide();
        JsonNode meta = req.definitionJsonb() != null ? req.definitionJsonb().get("metadata") : null;
        String name = meta != null && meta.has("name") ? meta.get("name").asText() : "workflow-" + req.workflowId();

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(50, 50, 600, 80));
        XSLFTextParagraph titlePara = titleBox.addNewTextParagraph();
        titlePara.setTextAlign(TextParagraph.TextAlign.CENTER);
        XSLFTextRun titleRun = titlePara.addNewTextRun();
        titleRun.setText(name);
        titleRun.setFontSize(28.0);
        titleRun.setBold(true);

        XSLFTextBox metaBox = slide.createTextBox();
        metaBox.setAnchor(new Rectangle(50, 160, 600, 240));
        addLine(metaBox, "Sub-project: " + (meta != null ? meta.path("subProject").asText("UNKNOWN") : "UNKNOWN"));
        addLine(metaBox, "Version: " + (meta != null ? meta.path("semver").asText("0.0.0") : "0.0.0"));
        if (req.period() != null) {
            addLine(metaBox, "Période: " + req.period());
        }
        addLine(metaBox, "Généré par FASO-ANALYTICS-WORKFLOW v1.0");
    }

    private void buildVisualizationSlides(XMLSlideShow ppt, ExportRequest req) {
        JsonNode vizes = req.definitionJsonb() != null ? req.definitionJsonb().get("visualizations") : null;
        if (vizes == null || !vizes.isArray()) {
            return;
        }
        for (JsonNode viz : vizes) {
            XSLFSlide slide = ppt.createSlide();

            XSLFTextBox titleBox = slide.createTextBox();
            titleBox.setAnchor(new Rectangle(30, 20, 660, 60));
            XSLFTextParagraph para = titleBox.addNewTextParagraph();
            XSLFTextRun run = para.addNewTextRun();
            run.setText(viz.path("title").asText(viz.path("id").asText("Visualisation")));
            run.setFontSize(22.0);
            run.setBold(true);

            if (viz.has("subtitle")) {
                XSLFTextBox subtitleBox = slide.createTextBox();
                subtitleBox.setAnchor(new Rectangle(30, 80, 660, 40));
                XSLFTextRun sub = subtitleBox.addNewTextParagraph().addNewTextRun();
                sub.setText(viz.path("subtitle").asText());
                sub.setFontSize(14.0);
                sub.setItalic(true);
            }

            XSLFTextBox placeholder = slide.createTextBox();
            placeholder.setAnchor(new Rectangle(80, 180, 560, 280));
            XSLFTextRun ph = placeholder.addNewTextParagraph().addNewTextRun();
            ph.setText("[Visualisation \"" + viz.path("type").asText("chart")
                    + "\" — rendu graphique différé en Phase 4 via headless Chrome]");
            ph.setFontSize(14.0);
            ph.setItalic(true);
        }
    }

    private void addLine(XSLFTextBox box, String line) {
        XSLFTextParagraph para = box.addNewTextParagraph();
        XSLFTextRun run = para.addNewTextRun();
        run.setText(line);
        run.setFontSize(14.0);
    }
}

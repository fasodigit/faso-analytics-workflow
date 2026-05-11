package bf.faso.analytics.infrastructure.exports;

import bf.faso.analytics.application.ExportRequest;
import bf.faso.analytics.application.ExportResult;
import bf.faso.analytics.domain.port.ExportPort;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Iterator;
import java.util.UUID;

/**
 * Adaptateur Excel via Apache POI (XSSF / .xlsx).
 *
 * <p>Construit un classeur avec :
 * <ul>
 *   <li>Une feuille {@code Metadata} (nom workflow, sub_project, semver, période).</li>
 *   <li>Une feuille {@code KPIs} (id, label, valeur, unité).</li>
 *   <li>Une feuille par {@code visualization.id}, avec encoding.headers/rows.</li>
 * </ul>
 *
 * Phase 3 : sortie locale {@code /tmp/faso-analytics/exports/export-<jobId>.xlsx} (URI {@code file://}).
 * Phase 4 : upload MinIO via WebClient + retour d'un objectKey.
 */
@Component
public class ExcelAdapter implements ExportPort {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelAdapter.class);
    private static final String KIND = "excel";

    private final Path outputDir;

    public ExcelAdapter(@Value("${faso.exports.output-dir:/tmp/faso-analytics/exports}") String outputDir) {
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
            Path xlsxPath = outputDir.resolve("export-" + localJobId + ".xlsx");

            try (Workbook wb = new XSSFWorkbook();
                 OutputStream os = Files.newOutputStream(xlsxPath)) {
                CellStyle headerStyle = buildHeaderStyle(wb);

                writeMetadataSheet(wb, headerStyle, req);
                writeKpisSheet(wb, headerStyle, req);
                writeVisualizationSheets(wb, headerStyle, req);

                wb.write(os);
            }

            String resultUri = "file://" + xlsxPath.toAbsolutePath();
            return new ExportResult(localJobId, ExportResult.SUCCEEDED, resultUri, requested, Instant.now(), null);
        } catch (Exception e) {
            LOG.error("excel.render.error", e);
            return new ExportResult(localJobId, ExportResult.FAILED, null, requested, Instant.now(),
                    "excel_render_error: " + e.getMessage());
        }
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeMetadataSheet(Workbook wb, CellStyle headerStyle, ExportRequest req) {
        Sheet sheet = wb.createSheet("Metadata");
        Row header = sheet.createRow(0);
        writeCell(header, 0, "Key", headerStyle);
        writeCell(header, 1, "Value", headerStyle);

        JsonNode meta = req.definitionJsonb() != null ? req.definitionJsonb().get("metadata") : null;
        int r = 1;
        sheet.createRow(r++).createCell(0).setCellValue("workflow_id");
        sheet.getRow(r - 1).createCell(1).setCellValue(String.valueOf(req.workflowId()));

        if (meta != null) {
            Iterator<String> it = meta.fieldNames();
            while (it.hasNext()) {
                String key = it.next();
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(key);
                row.createCell(1).setCellValue(meta.get(key).asText());
            }
        }
        if (req.period() != null) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue("period");
            row.createCell(1).setCellValue(req.period());
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeKpisSheet(Workbook wb, CellStyle headerStyle, ExportRequest req) {
        Sheet sheet = wb.createSheet("KPIs");
        Row header = sheet.createRow(0);
        writeCell(header, 0, "id", headerStyle);
        writeCell(header, 1, "label", headerStyle);
        writeCell(header, 2, "value", headerStyle);
        writeCell(header, 3, "unit", headerStyle);

        JsonNode kpis = req.definitionJsonb() != null ? req.definitionJsonb().get("kpis") : null;
        int r = 1;
        if (kpis != null && kpis.isArray()) {
            for (JsonNode kpi : kpis) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(kpi.path("id").asText(""));
                row.createCell(1).setCellValue(kpi.path("label").asText(""));
                row.createCell(2).setCellValue(kpi.path("value").asDouble(0));
                row.createCell(3).setCellValue(kpi.path("unit").asText(""));
            }
        }
    }

    private void writeVisualizationSheets(Workbook wb, CellStyle headerStyle, ExportRequest req) {
        JsonNode vizes = req.definitionJsonb() != null ? req.definitionJsonb().get("visualizations") : null;
        if (vizes == null || !vizes.isArray()) {
            return;
        }
        int idx = 1;
        for (JsonNode viz : vizes) {
            String id = viz.path("id").asText("viz" + idx);
            String safeName = id.length() > 28 ? id.substring(0, 28) : id;
            Sheet sheet = wb.createSheet(safeName);

            String title = viz.path("title").asText(id);
            Row titleRow = sheet.createRow(0);
            writeCell(titleRow, 0, title, headerStyle);

            JsonNode encoding = viz.path("encoding");
            JsonNode headers = encoding.path("headers");
            JsonNode rows = encoding.path("rows");

            if (headers.isArray()) {
                Row headerRow = sheet.createRow(2);
                for (int c = 0; c < headers.size(); c++) {
                    writeCell(headerRow, c, headers.get(c).asText(), headerStyle);
                }
            }
            if (rows.isArray()) {
                int r = 3;
                for (JsonNode rowNode : rows) {
                    Row row = sheet.createRow(r++);
                    if (rowNode.isArray()) {
                        for (int c = 0; c < rowNode.size(); c++) {
                            JsonNode val = rowNode.get(c);
                            Cell cell = row.createCell(c);
                            if (val.isNumber()) {
                                cell.setCellValue(val.asDouble());
                            } else {
                                cell.setCellValue(val.asText());
                            }
                        }
                    }
                }
            }
            idx++;
        }
    }

    private void writeCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
}

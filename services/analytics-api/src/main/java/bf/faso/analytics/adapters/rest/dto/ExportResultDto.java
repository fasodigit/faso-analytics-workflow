package bf.faso.analytics.adapters.rest.dto;

import bf.faso.analytics.application.ExportResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportResultDto(
        @JsonProperty("job_id") UUID jobId,
        String status,
        @JsonProperty("result_uri") String resultUri,
        @JsonProperty("requested_at") Instant requestedAt,
        @JsonProperty("finished_at") Instant finishedAt,
        @JsonProperty("error_message") String errorMessage
) {
    public static ExportResultDto from(ExportResult r) {
        return new ExportResultDto(
                r.jobId(),
                r.status(),
                r.resultUri(),
                r.requestedAt(),
                r.finishedAt(),
                r.errorMessage()
        );
    }
}

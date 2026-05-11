package bf.faso.analytics.adapters.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PageDto<T>(
        List<T> items,
        int page,
        int size,
        long total
) {
    @JsonProperty("total_pages")
    public long totalPages() {
        return size == 0 ? 0 : (total + size - 1) / size;
    }
}

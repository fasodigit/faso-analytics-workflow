package bf.faso.analytics.domain.policy;

import java.util.List;

public record DriftDecision(boolean blocked, List<String> reasons) {

    public DriftDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}

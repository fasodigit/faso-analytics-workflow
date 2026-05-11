package bf.faso.analytics.domain.policy;

import bf.faso.analytics.domain.model.Deployment.Strategy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record DeploymentPolicy(
        boolean isCritical,
        int minApprovals,
        boolean requireSimulation,
        Duration simulationMaxAge,
        boolean shadowMandatoryForCritical
) {

    public DeploymentPolicy {
        if (minApprovals < 1) {
            throw new IllegalArgumentException("minApprovals must be >= 1");
        }
        if (simulationMaxAge == null || simulationMaxAge.isNegative() || simulationMaxAge.isZero()) {
            throw new IllegalArgumentException("simulationMaxAge must be a positive duration");
        }
    }

    public static DeploymentPolicy defaults(boolean isCritical) {
        // 4-eyes : critical workflows REQUIRE 2 distinct approvers (Q-Policy ultraplan §13).
        // SHADOW imposed on critical : forbid direct DIRECT/BLUE_GREEN promotion of critical
        // workflows without first observing a shadow window (Q10).
        return new DeploymentPolicy(
                isCritical,
                isCritical ? 2 : 1,
                true,
                Duration.ofDays(7),
                isCritical
        );
    }

    public DeploymentDecision evaluate(int actualApprovals,
                                       Instant lastSimulation,
                                       Strategy strategy) {
        List<String> reasons = new ArrayList<>();
        if (actualApprovals < minApprovals) {
            reasons.add("Approvals: %d/%d".formatted(actualApprovals, minApprovals));
        }
        if (requireSimulation && lastSimulation == null) {
            reasons.add("No simulation found for this version");
        }
        if (lastSimulation != null
                && Duration.between(lastSimulation, Instant.now()).compareTo(simulationMaxAge) > 0) {
            reasons.add("Simulation older than %d days".formatted(simulationMaxAge.toDays()));
        }
        if (shadowMandatoryForCritical && isCritical && strategy != Strategy.SHADOW) {
            reasons.add("Critical workflow requires SHADOW strategy first");
        }
        return new DeploymentDecision(reasons.isEmpty(), List.copyOf(reasons));
    }

    public record DeploymentDecision(boolean allowed, List<String> reasons) {
        public DeploymentDecision {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }
}

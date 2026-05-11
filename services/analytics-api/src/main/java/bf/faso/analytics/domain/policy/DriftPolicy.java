package bf.faso.analytics.domain.policy;

import bf.faso.analytics.domain.model.SchemaDrift;

import java.util.ArrayList;
import java.util.List;

public record DriftPolicy(
        OnNewField onNewField,
        OnRemovedField onRemovedField,
        OnTypeChange onTypeChange,
        OnRenamed onRenamed,
        double similarityThreshold
) {
    public enum OnNewField { IGNORE, REQUIRE_MAPPING, BLOCK }

    public enum OnRemovedField { WARN, BLOCK }

    public enum OnTypeChange { AUTOCAST, REQUIRE_CAST, BLOCK }

    public enum OnRenamed { AUTO_BY_SIMILARITY, SUGGEST, BLOCK }

    public DriftPolicy {
        if (onNewField == null) {
            throw new IllegalArgumentException("onNewField is required");
        }
        if (onRemovedField == null) {
            throw new IllegalArgumentException("onRemovedField is required");
        }
        if (onTypeChange == null) {
            throw new IllegalArgumentException("onTypeChange is required");
        }
        if (onRenamed == null) {
            throw new IllegalArgumentException("onRenamed is required");
        }
        if (similarityThreshold < 0.0d || similarityThreshold > 1.0d) {
            throw new IllegalArgumentException("similarityThreshold must be in [0, 1]");
        }
    }

    public static DriftPolicy defaults() {
        return new DriftPolicy(
                OnNewField.REQUIRE_MAPPING,
                OnRemovedField.BLOCK,
                OnTypeChange.REQUIRE_CAST,
                OnRenamed.SUGGEST,
                0.85d
        );
    }

    public DriftDecision evaluate(SchemaDrift drift) {
        List<String> reasons = new ArrayList<>();
        boolean blocked = false;

        if (!drift.removedFields().isEmpty() && onRemovedField == OnRemovedField.BLOCK) {
            blocked = true;
            for (String f : drift.removedFields()) {
                reasons.add("Field " + f + " removed from source — blocked by policy");
            }
        }
        if (!drift.newFields().isEmpty() && onNewField == OnNewField.BLOCK) {
            blocked = true;
            for (String f : drift.newFields()) {
                reasons.add("Field " + f + " added to source — blocked by policy");
            }
        }
        if (!drift.newFields().isEmpty() && onNewField == OnNewField.REQUIRE_MAPPING) {
            for (String f : drift.newFields()) {
                reasons.add("Field " + f + " added to source — explicit mapping required");
            }
        }
        if (!drift.typeChanges().isEmpty() && onTypeChange == OnTypeChange.BLOCK) {
            blocked = true;
            for (SchemaDrift.TypeChange tc : drift.typeChanges()) {
                reasons.add("Field " + tc.field() + " type changed " + tc.fromType()
                        + " → " + tc.toType() + " — blocked by policy");
            }
        }
        if (!drift.typeChanges().isEmpty() && onTypeChange == OnTypeChange.REQUIRE_CAST) {
            for (SchemaDrift.TypeChange tc : drift.typeChanges()) {
                reasons.add("Field " + tc.field() + " type changed " + tc.fromType()
                        + " → " + tc.toType() + " — explicit cast required");
            }
        }
        if (!drift.renamedFields().isEmpty() && onRenamed == OnRenamed.BLOCK) {
            blocked = true;
            for (SchemaDrift.RenamedField rf : drift.renamedFields()) {
                reasons.add("Field " + rf.from() + " renamed to " + rf.to()
                        + " (similarity=" + rf.similarityScore() + ") — blocked by policy");
            }
        }
        if (!drift.renamedFields().isEmpty() && onRenamed == OnRenamed.SUGGEST) {
            for (SchemaDrift.RenamedField rf : drift.renamedFields()) {
                reasons.add("Field " + rf.from() + " likely renamed to " + rf.to()
                        + " (similarity=" + rf.similarityScore() + ") — confirm mapping");
            }
        }

        return new DriftDecision(blocked, List.copyOf(reasons));
    }
}

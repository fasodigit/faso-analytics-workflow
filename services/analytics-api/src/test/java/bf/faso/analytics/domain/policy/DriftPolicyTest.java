package bf.faso.analytics.domain.policy;

import bf.faso.analytics.domain.model.SchemaDrift;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DriftPolicyTest {

    @Test
    void defaults_block_on_removed_fields() {
        DriftPolicy policy = DriftPolicy.defaults();
        SchemaDrift drift = new SchemaDrift(
                List.of(),
                List.of("phone"),
                List.of(),
                List.of()
        );

        DriftDecision decision = policy.evaluate(drift);

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.reasons()).anySatisfy(r -> assertThat(r).contains("phone"));
    }

    @Test
    void defaults_do_not_block_on_new_fields_alone_but_require_mapping() {
        DriftPolicy policy = DriftPolicy.defaults();
        SchemaDrift drift = new SchemaDrift(
                List.of("email"),
                List.of(),
                List.of(),
                List.of()
        );

        DriftDecision decision = policy.evaluate(drift);

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.reasons()).anySatisfy(r -> assertThat(r).contains("email"));
    }

    @Test
    void empty_drift_yields_no_reason_and_unblocked() {
        DriftPolicy policy = DriftPolicy.defaults();
        SchemaDrift drift = new SchemaDrift(List.of(), List.of(), List.of(), List.of());

        DriftDecision decision = policy.evaluate(drift);

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void block_policy_on_type_change_blocks() {
        DriftPolicy strict = new DriftPolicy(
                DriftPolicy.OnNewField.IGNORE,
                DriftPolicy.OnRemovedField.WARN,
                DriftPolicy.OnTypeChange.BLOCK,
                DriftPolicy.OnRenamed.SUGGEST,
                0.85
        );
        SchemaDrift drift = new SchemaDrift(
                List.of(),
                List.of(),
                List.of(),
                List.of(new SchemaDrift.TypeChange("amount", "int", "decimal"))
        );

        DriftDecision decision = strict.evaluate(drift);

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.reasons()).anySatisfy(r -> assertThat(r).contains("amount"));
    }

    @Test
    void renamed_suggest_does_not_block_but_reports() {
        DriftPolicy policy = DriftPolicy.defaults();
        SchemaDrift drift = new SchemaDrift(
                List.of(),
                List.of(),
                List.of(new SchemaDrift.RenamedField("firstName", "first_name", 0.9)),
                List.of()
        );

        DriftDecision decision = policy.evaluate(drift);

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.reasons()).anySatisfy(r -> assertThat(r).contains("firstName"));
    }
}

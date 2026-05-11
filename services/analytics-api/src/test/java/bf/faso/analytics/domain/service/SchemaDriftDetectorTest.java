package bf.faso.analytics.domain.service;

import bf.faso.analytics.domain.model.SchemaDrift;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaDriftDetectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void identical_schemas_yield_empty_drift() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                { "id": {"type":"int"}, "name": {"type":"string"} }
                """);
        JsonNode current = objectMapper.readTree("""
                { "id": {"type":"int"}, "name": {"type":"string"} }
                """);

        SchemaDrift drift = SchemaDriftDetector.detect(snapshot, current, 0.85);

        assertThat(drift.isEmpty()).isTrue();
        assertThat(drift.newFields()).isEmpty();
        assertThat(drift.removedFields()).isEmpty();
        assertThat(drift.renamedFields()).isEmpty();
        assertThat(drift.typeChanges()).isEmpty();
    }

    @Test
    void detects_new_fields() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                { "id": {"type":"int"} }
                """);
        JsonNode current = objectMapper.readTree("""
                { "id": {"type":"int"}, "email": {"type":"string"} }
                """);

        SchemaDrift drift = SchemaDriftDetector.detect(snapshot, current, 0.85);

        assertThat(drift.newFields()).containsExactly("email");
        assertThat(drift.removedFields()).isEmpty();
        assertThat(drift.typeChanges()).isEmpty();
        assertThat(drift.renamedFields()).isEmpty();
    }

    @Test
    void detects_removed_fields() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                { "id": {"type":"int"}, "phone": {"type":"string"} }
                """);
        JsonNode current = objectMapper.readTree("""
                { "id": {"type":"int"} }
                """);

        SchemaDrift drift = SchemaDriftDetector.detect(snapshot, current, 0.85);

        assertThat(drift.removedFields()).containsExactly("phone");
        assertThat(drift.newFields()).isEmpty();
    }

    @Test
    void detects_renamed_fields_via_similarity() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                { "firstName": {"type":"string"} }
                """);
        JsonNode current = objectMapper.readTree("""
                { "firstname": {"type":"string"} }
                """);

        SchemaDrift drift = SchemaDriftDetector.detect(snapshot, current, 0.85);

        assertThat(drift.renamedFields()).hasSize(1);
        SchemaDrift.RenamedField rf = drift.renamedFields().get(0);
        assertThat(rf.from()).isEqualTo("firstName");
        assertThat(rf.to()).isEqualTo("firstname");
        assertThat(rf.similarityScore()).isGreaterThanOrEqualTo(0.85);
        // Matched fields must not appear in newFields/removedFields.
        assertThat(drift.newFields()).isEmpty();
        assertThat(drift.removedFields()).isEmpty();
    }

    @Test
    void detects_type_changes() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                { "amount": {"type":"int"} }
                """);
        JsonNode current = objectMapper.readTree("""
                { "amount": {"type":"decimal"} }
                """);

        SchemaDrift drift = SchemaDriftDetector.detect(snapshot, current, 0.85);

        assertThat(drift.typeChanges()).hasSize(1);
        SchemaDrift.TypeChange tc = drift.typeChanges().get(0);
        assertThat(tc.field()).isEqualTo("amount");
        assertThat(tc.fromType()).isEqualTo("int");
        assertThat(tc.toType()).isEqualTo("decimal");
    }

    @Test
    void supports_flat_string_typed_schemas() throws Exception {
        JsonNode snapshot = objectMapper.readTree("""
                { "id": "int", "name": "string" }
                """);
        JsonNode current = objectMapper.readTree("""
                { "id": "long", "name": "string" }
                """);

        SchemaDrift drift = SchemaDriftDetector.detect(snapshot, current, 0.85);

        assertThat(drift.typeChanges()).hasSize(1);
        assertThat(drift.typeChanges().get(0).field()).isEqualTo("id");
        assertThat(drift.typeChanges().get(0).fromType()).isEqualTo("int");
        assertThat(drift.typeChanges().get(0).toType()).isEqualTo("long");
    }

    @Test
    void levenshtein_handles_edge_cases() {
        assertThat(SchemaDriftDetector.levenshtein("", "")).isZero();
        assertThat(SchemaDriftDetector.levenshtein("abc", "")).isEqualTo(3);
        assertThat(SchemaDriftDetector.levenshtein("", "abc")).isEqualTo(3);
        assertThat(SchemaDriftDetector.levenshtein("abc", "abc")).isZero();
        assertThat(SchemaDriftDetector.levenshtein("kitten", "sitting")).isEqualTo(3);
        assertThat(SchemaDriftDetector.levenshtein("flaw", "lawn")).isEqualTo(2);
    }

    @Test
    void similarity_normalized_between_zero_and_one() {
        assertThat(SchemaDriftDetector.similarity("", "")).isEqualTo(1.0);
        assertThat(SchemaDriftDetector.similarity("abc", "abc")).isEqualTo(1.0);
        assertThat(SchemaDriftDetector.similarity("abc", "abd")).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SchemaDriftDetector.similarity("abc", "xyz")).isZero();
        assertThat(SchemaDriftDetector.similarity(null, "abc")).isZero();
        assertThat(SchemaDriftDetector.similarity("abc", null)).isZero();
    }
}

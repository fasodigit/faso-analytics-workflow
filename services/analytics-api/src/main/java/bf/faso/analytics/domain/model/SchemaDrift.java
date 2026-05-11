package bf.faso.analytics.domain.model;

import java.util.List;

public record SchemaDrift(
        List<String> newFields,
        List<String> removedFields,
        List<RenamedField> renamedFields,
        List<TypeChange> typeChanges
) {
    public record RenamedField(String from, String to, double similarityScore) {}

    public record TypeChange(String field, String fromType, String toType) {}

    public SchemaDrift {
        newFields = newFields == null ? List.of() : List.copyOf(newFields);
        removedFields = removedFields == null ? List.of() : List.copyOf(removedFields);
        renamedFields = renamedFields == null ? List.of() : List.copyOf(renamedFields);
        typeChanges = typeChanges == null ? List.of() : List.copyOf(typeChanges);
    }

    public boolean isEmpty() {
        return newFields.isEmpty()
                && removedFields.isEmpty()
                && renamedFields.isEmpty()
                && typeChanges.isEmpty();
    }
}

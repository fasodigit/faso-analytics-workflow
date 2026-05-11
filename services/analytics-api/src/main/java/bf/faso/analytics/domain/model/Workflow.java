package bf.faso.analytics.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record Workflow(
        UUID workflowId,
        String subProject,
        String name,
        String description,
        String ownerSubject,
        UUID parentWorkflowId,
        boolean isCritical,
        Instant createdAt,
        Instant updatedAt
) {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");

    private static final java.util.Set<String> ALLOWED_SUB_PROJECTS = java.util.Set.of(
            "VOUCHERS", "E_TICKET", "ETAT_CIVIL", "SOGESY",
            "HOSPITAL", "FASO_KALAN", "ALT_MISSION", "E_SCHOOL"
    );

    public Workflow {
        if (subProject == null || !ALLOWED_SUB_PROJECTS.contains(subProject)) {
            throw new IllegalArgumentException("invalid subProject: " + subProject);
        }
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid name (must match ^[a-z][a-z0-9-]{2,63}$): " + name);
        }
        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException("ownerSubject is required");
        }
    }

    public static Workflow create(String subProject,
                                  String name,
                                  String description,
                                  String ownerSubject,
                                  boolean isCritical) {
        Instant now = Instant.now();
        return new Workflow(
                UUID.randomUUID(),
                subProject,
                name,
                description,
                ownerSubject,
                null,
                isCritical,
                now,
                now
        );
    }
}

package bf.faso.analytics.domain.service;

import bf.faso.analytics.domain.model.SchemaDrift;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class SchemaDriftDetector {

    private SchemaDriftDetector() {
    }

    public static SchemaDrift detect(JsonNode snapshot, JsonNode current, double similarityThreshold) {
        Set<String> snapshotFields = fieldNames(snapshot);
        Set<String> currentFields = fieldNames(current);

        Set<String> added = new LinkedHashSet<>(currentFields);
        added.removeAll(snapshotFields);
        Set<String> removed = new LinkedHashSet<>(snapshotFields);
        removed.removeAll(currentFields);

        List<SchemaDrift.RenamedField> renamed = matchRenamed(removed, added, similarityThreshold);

        Set<String> common = new LinkedHashSet<>(snapshotFields);
        common.retainAll(currentFields);
        List<SchemaDrift.TypeChange> typeChanges = new ArrayList<>();
        for (String field : common) {
            String fromType = typeOf(snapshot, field);
            String toType = typeOf(current, field);
            if (fromType != null && toType != null && !fromType.equals(toType)) {
                typeChanges.add(new SchemaDrift.TypeChange(field, fromType, toType));
            }
        }

        return new SchemaDrift(
                new ArrayList<>(added),
                new ArrayList<>(removed),
                renamed,
                typeChanges
        );
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        if (node == null || !node.isObject()) {
            return names;
        }
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }

    private static String typeOf(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null) {
            return null;
        }
        if (child.isObject()) {
            JsonNode typeNode = child.get("type");
            return typeNode == null ? null : typeNode.asText();
        }
        if (child.isTextual()) {
            return child.asText();
        }
        return null;
    }

    private static List<SchemaDrift.RenamedField> matchRenamed(Set<String> removed,
                                                               Set<String> added,
                                                               double similarityThreshold) {
        List<SchemaDrift.RenamedField> matches = new ArrayList<>();
        // Greedy matching : for each removed field, pick the most similar added field
        // whose similarity >= threshold ; once matched, neither side is reused.
        Set<String> claimedAdded = new LinkedHashSet<>();
        Set<String> claimedRemoved = new LinkedHashSet<>();
        for (String r : removed) {
            String bestMatch = null;
            double bestScore = -1.0d;
            for (String a : added) {
                if (claimedAdded.contains(a)) {
                    continue;
                }
                double score = similarity(r, a);
                if (score >= similarityThreshold && score > bestScore) {
                    bestScore = score;
                    bestMatch = a;
                }
            }
            if (bestMatch != null) {
                matches.add(new SchemaDrift.RenamedField(r, bestMatch, bestScore));
                claimedAdded.add(bestMatch);
                claimedRemoved.add(r);
            }
        }
        removed.removeAll(claimedRemoved);
        added.removeAll(claimedAdded);
        return matches;
    }

    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0d;
        }
        if (s1.equals(s2)) {
            return 1.0d;
        }
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0d;
        }
        int distance = levenshtein(s1, s2);
        return 1.0d - ((double) distance / (double) maxLen);
    }

    public static int levenshtein(String a, String b) {
        String s1 = a == null ? "" : a;
        String s2 = b == null ? "" : b;
        int n = s1.length();
        int m = s2.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ai = s1.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char bj = s2.charAt(j - 1);
                int cost = (ai == bj) ? 0 : 1;
                int del = prev[j] + 1;
                int ins = curr[j - 1] + 1;
                int sub = prev[j - 1] + cost;
                curr[j] = Math.min(Math.min(del, ins), sub);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}

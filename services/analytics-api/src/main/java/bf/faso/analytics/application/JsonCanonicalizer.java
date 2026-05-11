package bf.faso.analytics.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

final class JsonCanonicalizer {

    private JsonCanonicalizer() {
    }

    static String canonicalize(JsonNode node, ObjectMapper baseMapper) throws Exception {
        // Phase 1 : key-ordered serialization. Phase 2+ remplacera par
        // une implémentation RFC 8785 stricte (numbers à virgule unique, etc.).
        ObjectMapper sorted = baseMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return sorted.writeValueAsString(sorted.treeToValue(node, Object.class));
    }
}

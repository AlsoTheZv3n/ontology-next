package com.nexoai.ontology.core.connector;

import java.util.Map;

public record RawRecord(
        Map<String, Object> fields,
        String externalId
) {}

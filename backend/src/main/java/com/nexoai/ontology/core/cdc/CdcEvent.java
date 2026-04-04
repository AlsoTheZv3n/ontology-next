package com.nexoai.ontology.core.cdc;

import java.time.Instant;
import java.util.Map;

public record CdcEvent(
        String operation,
        String table,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant timestamp,
        String transactionId
) {
    public boolean isInsert() { return "c".equals(operation) || "r".equals(operation); }
    public boolean isUpdate() { return "u".equals(operation); }
    public boolean isDelete() { return "d".equals(operation); }
}

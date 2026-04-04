package com.nexoai.ontology.core.connector;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
public class ColumnDefinition {
    private final String name;
    private final String dataType;
    private final boolean nullable;
}

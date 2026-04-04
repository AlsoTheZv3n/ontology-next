package com.nexoai.ontology.core.domain;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkType {
    private UUID id;
    private String apiName;
    private String displayName;
    private UUID sourceObjectTypeId;
    private UUID targetObjectTypeId;
    @Builder.Default
    private Cardinality cardinality = Cardinality.ONE_TO_MANY;
    private String description;
    private Instant createdAt;
}

package com.nexoai.ontology.core.domain.action;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRule {
    private String property;
    private String rule;
    private String errorMessage;
}

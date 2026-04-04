package com.nexoai.ontology.core.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexoai.ontology.core.domain.action.ValidationResult;
import com.nexoai.ontology.core.domain.action.ValidationRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ValidationService {

    public ValidationResult validate(List<ValidationRule> rules, JsonNode params) {
        if (rules == null || rules.isEmpty()) {
            return ValidationResult.valid();
        }

        List<String> errors = new ArrayList<>();

        for (ValidationRule rule : rules) {
            JsonNode value = params.get(rule.getProperty());
            String ruleStr = rule.getRule();

            if ("NOT_NULL".equals(ruleStr)) {
                if (value == null || value.isNull()) {
                    errors.add(rule.getErrorMessage());
                }
            } else if (ruleStr.startsWith("MIN:")) {
                double min = Double.parseDouble(ruleStr.split(":")[1]);
                if (value != null && !value.isNull() && value.asDouble() < min) {
                    errors.add(rule.getErrorMessage());
                }
            } else if (ruleStr.startsWith("MAX:")) {
                double max = Double.parseDouble(ruleStr.split(":")[1]);
                if (value != null && !value.isNull() && value.asDouble() > max) {
                    errors.add(rule.getErrorMessage());
                }
            } else if (ruleStr.startsWith("REGEX:")) {
                String pattern = ruleStr.split(":", 2)[1];
                if (value != null && !value.isNull() && !value.asText().matches(pattern)) {
                    errors.add(rule.getErrorMessage());
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
}

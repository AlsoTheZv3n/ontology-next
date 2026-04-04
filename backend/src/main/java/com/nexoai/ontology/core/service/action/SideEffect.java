package com.nexoai.ontology.core.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexoai.ontology.core.domain.action.ActionType;
import com.nexoai.ontology.core.domain.object.OntologyObject;

public interface SideEffect {
    String getType();
    void triggerAsync(ActionType actionType, OntologyObject object, JsonNode newState);
}

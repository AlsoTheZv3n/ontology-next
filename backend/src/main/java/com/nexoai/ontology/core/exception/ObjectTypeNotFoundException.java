package com.nexoai.ontology.core.exception;

import java.util.UUID;

public class ObjectTypeNotFoundException extends OntologyException {
    public ObjectTypeNotFoundException(UUID id) {
        super("ObjectType not found: " + id);
    }
}

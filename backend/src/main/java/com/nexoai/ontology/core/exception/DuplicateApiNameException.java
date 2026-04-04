package com.nexoai.ontology.core.exception;

public class DuplicateApiNameException extends OntologyException {
    public DuplicateApiNameException(String apiName) {
        super("ApiName already exists: " + apiName);
    }
}

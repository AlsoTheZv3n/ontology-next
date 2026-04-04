package com.nexoai.ontology.core.connector;

import lombok.Getter;

@Getter
public class ConnectionTestResult {
    private final boolean success;
    private final String message;

    private ConnectionTestResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ConnectionTestResult success(String message) {
        return new ConnectionTestResult(true, message);
    }

    public static ConnectionTestResult failed(String message) {
        return new ConnectionTestResult(false, message);
    }
}

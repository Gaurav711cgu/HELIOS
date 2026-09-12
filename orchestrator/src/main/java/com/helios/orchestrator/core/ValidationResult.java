package com.helios.orchestrator.core;

import java.util.List;

public record ValidationResult(boolean valid, List<String> errors) {
    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(false, errors);
    }
}

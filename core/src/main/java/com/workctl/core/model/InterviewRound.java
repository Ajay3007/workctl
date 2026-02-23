package com.workctl.core.model;

public enum InterviewRound {
    HR, PHONE_SCREEN, TECHNICAL, SYSTEM_DESIGN, ONSITE, CULTURE;

    /** Human-readable label for display in UI. */
    public String label() {
        return switch (this) {
            case HR            -> "HR";
            case PHONE_SCREEN  -> "Phone Screen";
            case TECHNICAL     -> "Technical";
            case SYSTEM_DESIGN -> "System Design";
            case ONSITE        -> "Onsite";
            case CULTURE       -> "Culture Fit";
        };
    }
}

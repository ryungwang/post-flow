package com.postflow.user;

/** Thrown when an action exceeds the user's plan limit or requires a higher plan. */
public class PlanLimitException extends RuntimeException {
    public PlanLimitException(String message) {
        super(message);
    }
}

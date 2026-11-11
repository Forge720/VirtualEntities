package net.plexmc.virtualentities.api;

/** Stable failure categories returned by the public API. */
public enum FailureReason {
    NONE,
    INVALID_SPEC,
    INVALID_DEFINITION,
    INVALID_LOCATION,
    MISSING_DEPENDENCY,
    UNAVAILABLE_MODEL,
    UNSUPPORTED_OPERATION,
    LIMIT_REACHED,
    WRONG_THREAD,
    INTERNAL_INITIALIZATION_FAILURE,
    TERMINATED
}

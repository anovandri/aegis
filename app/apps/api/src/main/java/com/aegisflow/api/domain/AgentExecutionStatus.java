package com.aegisflow.api.domain;

public enum AgentExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    INVALID_OUTPUT,
    TIMED_OUT,
    CANCELLED
}

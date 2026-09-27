package com.aegisflow.api.agent;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequirementChecklist {
    public List<String> qualityChecks() {
        return List.of(
                "Requirements are clear, atomic, and testable",
                "Actors and user journeys are explicit",
                "Acceptance criteria can be derived",
                "Ambiguous terms are identified",
                "Contradictions are identified"
        );
    }

    public List<String> nonFunctionalChecks() {
        return List.of(
                "Expected TPS and peak TPS",
                "SLA, availability, and latency",
                "Timeout handling",
                "Retry behavior",
                "Reconciliation and reversal",
                "Audit and observability requirements"
        );
    }

    public List<String> securityAndComplianceChecks() {
        return List.of(
                "Authentication and authorization",
                "PII or sensitive data handling",
                "Credential and secret handling",
                "Compliance or regulatory constraints",
                "Audit log retention"
        );
    }
}

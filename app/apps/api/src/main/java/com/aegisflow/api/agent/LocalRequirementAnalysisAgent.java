package com.aegisflow.api.agent;

import com.aegisflow.api.ports.KnowledgeCitation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "aegisflow.llm", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalRequirementAnalysisAgent implements RequirementAnalysisAgentPort {
    @Override
    public String modelName() {
        return "local-requirement-heuristic";
    }

    @Override
    public RequirementAnalysis analyze(
            UUID projectId,
            String brdText,
            BrdIntakeAnalysis brdIntakeAnalysis,
            RequirementChecklist checklist,
            List<KnowledgeCitation> knowledgeCitations
    ) {
        String normalized = brdText.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>(brdIntakeAnalysis.missingInformation());
        List<String> questions = new ArrayList<>();

        requireSignal(normalized, missing, questions, "expected TPS and peak TPS", "What are the expected TPS and peak TPS?", "tps", "throughput");
        requireSignal(normalized, missing, questions, "SLA and latency", "What SLA and latency targets must the solution meet?", "sla", "latency", "seconds", "availability");
        requireSignal(normalized, missing, questions, "timeout handling", "What should happen when upstream or downstream systems timeout?", "timeout");
        requireSignal(normalized, missing, questions, "retry behavior", "Which operations are retryable, and what retry limits apply?", "retry");
        requireSignal(normalized, missing, questions, "reconciliation and reversal", "What reconciliation and reversal scenarios are required?", "reconciliation", "reversal");
        requireSignal(normalized, missing, questions, "audit requirements", "Which business events must be audited and retained?", "audit", "log");
        requireSignal(normalized, missing, questions, "security requirements", "What authentication, authorization, and data protection rules apply?", "security", "authorization", "authentication");
        addKnowledgeDrivenQuestions(knowledgeCitations, questions);

        double readiness = Math.max(0.0, (10.0 - Math.min(10, missing.size())) / 10.0);
        return new RequirementAnalysis(
                missing.isEmpty() ? "READY_FOR_ARCHITECTURE" : "NEED_CLARIFICATION",
                Math.max(0.55, readiness),
                readiness,
                "Requirement analysis found %d clarification item(s).".formatted(questions.size()),
                questions,
                deriveAcceptanceCriteria(brdIntakeAnalysis),
                extractWeakSignals(brdText, "maybe", "etc", "fast", "as soon", "normal"),
                extractWeakSignals(brdText, "but", "except", "however"),
                missing,
                merge(
                        knowledgeCitations.stream()
                                .filter(citation -> citation.excerpt().toLowerCase(Locale.ROOT).contains("status"))
                                .map(citation -> "Confirm status behavior based on cited precedent: " + citation.sourceTitle())
                                .toList(),
                        List.of(
                        "Invalid or expired request",
                        "Duplicate submission",
                        "Timeout from dependency",
                        "Partial success requiring reconciliation"
                        )
                ),
                checklist.nonFunctionalChecks(),
                checklist.securityAndComplianceChecks(),
                merge(
                        knowledgeCitations.stream()
                                .filter(citation -> citation.excerpt().toLowerCase(Locale.ROOT).contains("audit"))
                                .map(citation -> "Validate audit expectation from " + citation.sourceTitle())
                                .toList(),
                        List.of("Audit event coverage must be confirmed during system analysis")
                ),
                knowledgeCitations.stream().map(KnowledgeCitation::sourceId).toList()
        );
    }

    private void addKnowledgeDrivenQuestions(List<KnowledgeCitation> citations, List<String> questions) {
        for (KnowledgeCitation citation : citations) {
            String excerpt = citation.excerpt().toLowerCase(Locale.ROOT);
            if (excerpt.contains("reversal") && questions.stream().noneMatch(question -> question.toLowerCase(Locale.ROOT).contains("reversal"))) {
                questions.add("Previous knowledge mentions reversal. What reversal scenarios and ownership rules are required?");
            }
            if (excerpt.contains("reconciliation") && questions.stream().noneMatch(question -> question.toLowerCase(Locale.ROOT).contains("reconciliation"))) {
                questions.add("Previous knowledge mentions reconciliation. What reconciliation SLA, report, and exception handling are required?");
            }
            if (excerpt.contains("expiry") && questions.stream().noneMatch(question -> question.toLowerCase(Locale.ROOT).contains("expiry"))) {
                questions.add("Previous QR/payment knowledge mentions expiry. What expiry duration and expired-payment behavior are required?");
            }
            if (excerpt.contains("maker-checker") && questions.stream().noneMatch(question -> question.toLowerCase(Locale.ROOT).contains("approval"))) {
                questions.add("Previous merchant knowledge mentions maker-checker. Is approval required for this business process?");
            }
        }
    }

    private void requireSignal(String text, List<String> missing, List<String> questions, String label, String question, String... signals) {
        for (String signal : signals) {
            if (text.contains(signal)) {
                return;
            }
        }
        if (!missing.contains(label)) {
            missing.add(label);
        }
        questions.add(question);
    }

    private List<String> deriveAcceptanceCriteria(BrdIntakeAnalysis brdIntakeAnalysis) {
        List<String> criteria = new ArrayList<>();
        for (String requirement : brdIntakeAnalysis.functionalRequirements()) {
            criteria.add("Given the required context, when " + requirement + ", then the outcome is observable and auditable.");
            if (criteria.size() == 5) {
                return criteria;
            }
        }
        return criteria.isEmpty() ? List.of("Acceptance criteria must be elaborated from clarified functional requirements") : criteria;
    }

    private List<String> extractWeakSignals(String text, String... signals) {
        List<String> findings = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String normalized = line.toLowerCase(Locale.ROOT);
            for (String signal : signals) {
                if (normalized.contains(signal)) {
                    findings.add(line.trim());
                    break;
                }
            }
            if (findings.size() == 5) {
                break;
            }
        }
        return findings;
    }

    private List<String> merge(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first);
        merged.addAll(second);
        return merged;
    }
}

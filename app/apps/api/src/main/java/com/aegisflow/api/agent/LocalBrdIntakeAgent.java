package com.aegisflow.api.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "aegisflow.llm", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalBrdIntakeAgent implements BrdIntakeAgentPort {
    @Override
    public String modelName() {
        return "local-completeness-heuristic";
    }

    @Override
    public BrdIntakeAnalysis analyze(UUID projectId, int brdVersion, String brdText) {
        String normalized = brdText.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();

        requireSignal(normalized, missing, "business objective", "objective", "goal", "purpose");
        requireSignal(normalized, missing, "actors/users", "actor", "user", "customer", "admin", "business");
        requireSignal(normalized, missing, "functional requirements", "shall", "must", "requirement", "feature");
        requireSignal(normalized, missing, "non-functional requirements", "sla", "latency", "availability", "security", "performance");
        requireSignal(normalized, missing, "error/retry/reversal handling", "error", "retry", "timeout", "reversal", "reconciliation");
        requireSignal(normalized, missing, "audit/compliance requirements", "audit", "compliance", "regulatory", "log");

        double completeness = Math.max(0.0, (6.0 - missing.size()) / 6.0);
        return new BrdIntakeAnalysis(
                missing.isEmpty() ? "ANALYZED" : "NEED_CLARIFICATION",
                Math.max(0.55, completeness),
                completeness,
                "BRD v%d initial completeness check found %d missing area(s).".formatted(brdVersion, missing.size()),
                extractLines(brdText, "objective", "goal", "purpose"),
                extractLines(brdText, "actor", "user", "customer", "business"),
                extractLines(brdText, "shall", "must", "requirement", "feature"),
                extractLines(brdText, "sla", "latency", "availability", "security", "performance"),
                extractLines(brdText, "rule", "policy", "must not", "only if"),
                extractLines(brdText, "assume", "assumption"),
                extractLines(brdText, "depend", "integration", "system", "api"),
                missing
        );
    }

    private void requireSignal(String text, List<String> missing, String label, String... signals) {
        for (String signal : signals) {
            if (text.contains(signal)) {
                return;
            }
        }
        missing.add(label);
    }

    private List<String> extractLines(String text, String... signals) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String normalized = line.toLowerCase(Locale.ROOT);
            for (String signal : signals) {
                if (normalized.contains(signal)) {
                    lines.add(line.trim());
                    break;
                }
            }
            if (lines.size() == 5) {
                break;
            }
        }
        return lines;
    }
}

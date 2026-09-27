package com.aegisflow.api.agent;

import com.aegisflow.api.ports.KnowledgeCitation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Map.entry;

@Component
@EnableConfigurationProperties(OpenAiProperties.class)
@ConditionalOnProperty(prefix = "aegisflow.llm", name = "provider", havingValue = "openai")
public class OpenAiRequirementAnalysisAgent implements RequirementAnalysisAgentPort {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    public OpenAiRequirementAnalysisAgent(ObjectMapper objectMapper, OpenAiProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.responsesUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    @Override
    public RequirementAnalysis analyze(
            UUID projectId,
            String brdText,
            BrdIntakeAnalysis brdIntakeAnalysis,
            RequirementChecklist checklist,
            List<KnowledgeCitation> knowledgeCitations
    ) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "input", List.of(
                        Map.of("role", "system", "content", "You are the Requirement Analyst Agent for AegisFlow. Evaluate BRD completeness, ambiguity, contradictions, acceptance criteria, edge cases, NFR, security, and audit concerns. Return only schema-valid JSON."),
                        Map.of("role", "user", "content", """
                                Project ID: %s

                                BRD Intake Analysis:
                                %s

                                Requirement Checklist:
                                Quality: %s
                                NFR: %s
                                Security/Compliance: %s

                                Governed Knowledge Citations:
                                %s

                                Original BRD:
                                %s
                                """.formatted(
                                projectId,
                                toJson(brdIntakeAnalysis),
                                checklist.qualityChecks(),
                                checklist.nonFunctionalChecks(),
                                checklist.securityAndComplianceChecks(),
                                toJson(knowledgeCitations),
                                brdText
                        ))
                ),
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", "requirement_analysis",
                        "strict", true,
                        "schema", schema()
                ))
        );

        String response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            String outputText = root.path("output").findValues("text").stream()
                    .findFirst()
                    .map(JsonNode::asText)
                    .orElseThrow(() -> new IllegalStateException("OpenAI response did not contain output text"));
            return objectMapper.readValue(outputText, RequirementAnalysis.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse Requirement Analysis structured output", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize agent input", exception);
        }
    }

    private Map<String, Object> schema() {
        Map<String, Object> stringArray = Map.of("type", "array", "items", Map.of("type", "string"));
        return Map.ofEntries(
                entry("type", "object"),
                entry("additionalProperties", false),
                entry("required", List.of(
                        "status",
                        "confidence",
                        "readinessScore",
                        "summary",
                        "clarificationQuestions",
                        "acceptanceCriteria",
                        "ambiguities",
                        "contradictions",
                        "missingRequirements",
                        "edgeCases",
                        "nonFunctionalConcerns",
                        "securityConcerns",
                        "auditConcerns",
                        "sourceRefs"
                )),
                entry("properties", Map.ofEntries(
                        entry("status", Map.of("type", "string", "enum", List.of("READY_FOR_ARCHITECTURE", "NEED_CLARIFICATION"))),
                        entry("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                        entry("readinessScore", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                        entry("summary", Map.of("type", "string")),
                        entry("clarificationQuestions", stringArray),
                        entry("acceptanceCriteria", stringArray),
                        entry("ambiguities", stringArray),
                        entry("contradictions", stringArray),
                        entry("missingRequirements", stringArray),
                        entry("edgeCases", stringArray),
                        entry("nonFunctionalConcerns", stringArray),
                        entry("securityConcerns", stringArray),
                        entry("auditConcerns", stringArray),
                        entry("sourceRefs", stringArray)
                ))
        );
    }
}

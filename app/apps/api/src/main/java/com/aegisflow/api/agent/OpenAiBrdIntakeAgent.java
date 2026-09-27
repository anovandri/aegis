package com.aegisflow.api.agent;

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
public class OpenAiBrdIntakeAgent implements BrdIntakeAgentPort {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    public OpenAiBrdIntakeAgent(ObjectMapper objectMapper, OpenAiProperties properties) {
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
    public BrdIntakeAnalysis analyze(UUID projectId, int brdVersion, String brdText) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "input", List.of(
                        Map.of("role", "system", "content", "You are the BRD Intake Agent for AegisFlow. Return only schema-valid JSON."),
                        Map.of("role", "user", "content", "Project ID: %s%nBRD version: %d%nAnalyze initial BRD completeness.%n%n%s"
                                .formatted(projectId, brdVersion, brdText))
                ),
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", "brd_intake_analysis",
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
            return objectMapper.readValue(outputText, BrdIntakeAnalysis.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse BRD Intake structured output", exception);
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
                        "completenessScore",
                        "summary",
                        "businessObjectives",
                        "actors",
                        "functionalRequirements",
                        "nonFunctionalRequirements",
                        "businessRules",
                        "assumptions",
                        "dependencies",
                        "missingInformation"
                )),
                entry("properties", Map.ofEntries(
                        entry("status", Map.of("type", "string", "enum", List.of("ANALYZED", "NEED_CLARIFICATION"))),
                        entry("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                        entry("completenessScore", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                        entry("summary", Map.of("type", "string")),
                        entry("businessObjectives", stringArray),
                        entry("actors", stringArray),
                        entry("functionalRequirements", stringArray),
                        entry("nonFunctionalRequirements", stringArray),
                        entry("businessRules", stringArray),
                        entry("assumptions", stringArray),
                        entry("dependencies", stringArray),
                        entry("missingInformation", stringArray)
                ))
        );
    }
}

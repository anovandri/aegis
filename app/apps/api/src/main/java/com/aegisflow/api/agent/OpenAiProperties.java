package com.aegisflow.api.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aegisflow.llm.openai")
public record OpenAiProperties(
        String apiKey,
        String model,
        String responsesUrl
) {
}

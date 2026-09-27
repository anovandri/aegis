package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeAdapterConfiguration {
    @Bean
    KnowledgeSourceAdapter manualUploadKnowledgeSourceAdapter() {
        return ConfiguredKnowledgeSourceAdapter.manualUpload();
    }

    @Bean
    KnowledgeSourceAdapter jiraKnowledgeSourceAdapter() {
        return ConfiguredKnowledgeSourceAdapter.jira();
    }
}

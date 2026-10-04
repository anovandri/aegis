package com.aegisflow.api.infrastructure.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TikaKnowledgeContentExtractorTest {
    private final TikaKnowledgeContentExtractor extractor = new TikaKnowledgeContentExtractor();

    @Test
    void supportsEnterpriseDocumentFormatsByMediaTypeAndFileExtension() {
        assertThat(extractor.supports("architecture.pdf", "application/pdf")).isTrue();
        assertThat(extractor.supports("brd.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
        assertThat(extractor.supports("estimation.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
        assertThat(extractor.supports("legacy-requirements.doc", "application/octet-stream")).isTrue();
        assertThat(extractor.supports("plain.md", "text/markdown")).isFalse();
    }
}

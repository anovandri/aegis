package com.aegisflow.api.infrastructure.knowledge;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlainTextKnowledgeContentExtractorTest {
    private final PlainTextKnowledgeContentExtractor extractor = new PlainTextKnowledgeContentExtractor();

    @Test
    void extractsMarkdownWithSectionMetadata() {
        ExtractedKnowledgeContent content = extractor.extract(
                "payment-standard.md",
                "text/markdown",
                """
                        # Payment Standard
                        Define SLA, timeout, retry, reversal, and audit requirements.
                        """.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(content.extractionType()).isEqualTo("MARKDOWN");
        assertThat(content.text()).contains("Payment Standard", "reversal");
        assertThat(content.sections()).hasSize(1);
        assertThat(content.sections().getFirst().startLine()).isEqualTo(1);
        assertThat(content.sections().getFirst().endLine()).isEqualTo(2);
        assertThat(content.sections().getFirst().metadata()).containsEntry("extractor", "plain-text");
    }

    @Test
    void classifiesSourceJsonAndCsvDocuments() {
        assertThat(extractor.extract("handler.go", "text/x-go", "package qris".getBytes(StandardCharsets.UTF_8)).extractionType())
                .isEqualTo("SOURCE_CODE");
        assertThat(extractor.extract("rules.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8)).extractionType())
                .isEqualTo("JSON");
        assertThat(extractor.extract("sla.csv", "text/csv", "service,sla".getBytes(StandardCharsets.UTF_8)).extractionType())
                .isEqualTo("CSV");
    }

    @Test
    void rejectsBlankDocumentsAfterExtraction() {
        assertThatThrownBy(() -> extractor.extract("empty.md", "text/markdown", "   \n\t".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }
}

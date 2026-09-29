package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;

public interface CodeKnowledgeParser {
    String language();

    boolean supports(String fileName, String mediaType);

    List<CodeKnowledgeUnit> parse(String fileName, String mediaType, String sourceText);
}

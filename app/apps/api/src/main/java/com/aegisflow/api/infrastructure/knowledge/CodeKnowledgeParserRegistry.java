package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class CodeKnowledgeParserRegistry {
    private final List<CodeKnowledgeParser> parsers;

    public CodeKnowledgeParserRegistry(List<CodeKnowledgeParser> parsers) {
        this.parsers = parsers.stream()
                .sorted(Comparator.comparing(CodeKnowledgeParser::language))
                .toList();
    }

    public Optional<CodeKnowledgeParser> findParser(String fileName, String mediaType) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileName, mediaType))
                .findFirst();
    }
}

package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterGo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TreeSitterGoCodeKnowledgeParser implements CodeKnowledgeParser {
    private static final Set<String> CODE_UNIT_NODE_TYPES = Set.of(
            "function_declaration",
            "method_declaration",
            "type_spec"
    );

    @Override
    public String language() {
        return "go";
    }

    @Override
    public boolean supports(String fileName, String mediaType) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".go");
    }

    @Override
    public List<CodeKnowledgeUnit> parse(String fileName, String mediaType, String sourceText) {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterGo());
        TSTree tree = parser.parseString(null, sourceText);
        TSNode rootNode = tree.getRootNode();
        List<CodeKnowledgeUnit> units = new ArrayList<>();
        collectUnits(fileName, sourceText, rootNode, units);
        if (units.isEmpty()) {
            units.add(fileUnit(fileName, sourceText));
        }
        return units;
    }

    private void collectUnits(String fileName, String sourceText, TSNode node, List<CodeKnowledgeUnit> units) {
        if (CODE_UNIT_NODE_TYPES.contains(node.getType())) {
            units.add(toUnit(fileName, sourceText, node));
            return;
        }

        for (int index = 0; index < node.getNamedChildCount(); index++) {
            collectUnits(fileName, sourceText, node.getNamedChild(index), units);
        }
    }

    private CodeKnowledgeUnit toUnit(String fileName, String sourceText, TSNode node) {
        int startByte = Math.max(0, node.getStartByte());
        int endByte = Math.max(startByte, node.getEndByte());
        String text = sliceByByteRange(sourceText, startByte, endByte);
        return new CodeKnowledgeUnit(
                language(),
                fileName,
                toSymbolType(node.getType()),
                symbolName(sourceText, node),
                lineNumber(sourceText, startByte),
                lineNumber(sourceText, endByte),
                startByte,
                endByte,
                text,
                Map.of("treeSitterNodeType", node.getType())
        );
    }

    private CodeKnowledgeUnit fileUnit(String fileName, String sourceText) {
        byte[] bytes = sourceText.getBytes(StandardCharsets.UTF_8);
        return new CodeKnowledgeUnit(
                language(),
                fileName,
                "file",
                fileName,
                1,
                lineNumber(sourceText, bytes.length),
                0,
                bytes.length,
                sourceText,
                Map.of("treeSitterNodeType", "source_file")
        );
    }

    private String toSymbolType(String nodeType) {
        return switch (nodeType) {
            case "function_declaration" -> "function";
            case "method_declaration" -> "method";
            case "type_spec" -> "type";
            default -> nodeType;
        };
    }

    private String symbolName(String sourceText, TSNode node) {
        if (node.getType().equals("method_declaration")) {
            return findFirstIdentifier(sourceText, node, "field_identifier")
                    .orElseGet(() -> findFirstIdentifier(sourceText, node, "identifier").orElse("anonymous"));
        }
        if (node.getType().equals("type_spec")) {
            return findFirstIdentifier(sourceText, node, "type_identifier")
                    .orElseGet(() -> findFirstIdentifier(sourceText, node, "identifier").orElse("anonymous"));
        }
        if (node.getType().equals("function_declaration")) {
            return findFirstIdentifier(sourceText, node, "identifier").orElse("anonymous");
        }

        List<String> identifiers = new ArrayList<>();
        collectIdentifiers(sourceText, node, identifiers);
        if (identifiers.isEmpty()) {
            return "anonymous";
        }
        return identifiers.getFirst();
    }

    private java.util.Optional<String> findFirstIdentifier(String sourceText, TSNode node, String nodeType) {
        if (node.getType().equals(nodeType)) {
            return java.util.Optional.of(sliceByByteRange(sourceText, node.getStartByte(), node.getEndByte()));
        }
        for (int index = 0; index < node.getNamedChildCount(); index++) {
            java.util.Optional<String> found = findFirstIdentifier(sourceText, node.getNamedChild(index), nodeType);
            if (found.isPresent()) {
                return found;
            }
        }
        return java.util.Optional.empty();
    }

    private void collectIdentifiers(String sourceText, TSNode node, List<String> identifiers) {
        if (node.getType().equals("identifier") || node.getType().equals("field_identifier") || node.getType().equals("type_identifier")) {
            identifiers.add(sliceByByteRange(sourceText, node.getStartByte(), node.getEndByte()));
        }
        for (int index = 0; index < node.getNamedChildCount(); index++) {
            collectIdentifiers(sourceText, node.getNamedChild(index), identifiers);
        }
    }

    private int lineNumber(String sourceText, int byteOffset) {
        byte[] bytes = sourceText.getBytes(StandardCharsets.UTF_8);
        int safeOffset = Math.min(Math.max(0, byteOffset), bytes.length);
        int line = 1;
        for (int index = 0; index < safeOffset; index++) {
            if (bytes[index] == '\n') {
                line++;
            }
        }
        return line;
    }

    private String sliceByByteRange(String sourceText, int startByte, int endByte) {
        byte[] bytes = sourceText.getBytes(StandardCharsets.UTF_8);
        int safeStart = Math.min(Math.max(0, startByte), bytes.length);
        int safeEnd = Math.min(Math.max(safeStart, endByte), bytes.length);
        return new String(bytes, safeStart, safeEnd - safeStart, StandardCharsets.UTF_8).trim();
    }
}

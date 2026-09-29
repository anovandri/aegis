package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CodeRelationshipExtractor {
    public List<CodeRelationship> inferRelationships(List<CodeKnowledgeUnit> units) {
        List<CodeRelationship> relationships = new ArrayList<>();
        for (CodeKnowledgeUnit sourceUnit : units) {
            for (CodeKnowledgeUnit targetUnit : units) {
                if (sourceUnit == targetUnit) {
                    continue;
                }
                inferTypeDependency(sourceUnit, targetUnit).forEach(relationships::add);
                inferInterfaceMethodCall(sourceUnit, targetUnit).forEach(relationships::add);
                inferInternalFunctionCall(sourceUnit, targetUnit).forEach(relationships::add);
            }
        }
        return relationships.stream().distinct().toList();
    }

    private List<CodeRelationship> inferTypeDependency(CodeKnowledgeUnit sourceUnit, CodeKnowledgeUnit targetUnit) {
        if (!targetUnit.symbolType().equals("type")) {
            return List.of();
        }
        if (!sourceUnit.text().contains(targetUnit.symbolName())) {
            return List.of();
        }
        return List.of(new CodeRelationship(
                sourceUnit.fileName(),
                sourceUnit.symbolName(),
                "DEPENDS_ON_TYPE",
                targetUnit.fileName(),
                targetUnit.symbolName()
        ));
    }

    private List<CodeRelationship> inferInterfaceMethodCall(CodeKnowledgeUnit sourceUnit, CodeKnowledgeUnit targetUnit) {
        if (!targetUnit.symbolType().equals("type")) {
            return List.of();
        }
        return methodNames(targetUnit).stream()
                .filter(methodName -> sourceUnit.text().contains("." + methodName + "("))
                .map(methodName -> new CodeRelationship(
                        sourceUnit.fileName(),
                        sourceUnit.symbolName(),
                        "CALLS_INTERFACE_METHOD",
                        targetUnit.fileName(),
                        targetUnit.symbolName() + "." + methodName
                ))
                .toList();
    }

    private List<CodeRelationship> inferInternalFunctionCall(CodeKnowledgeUnit sourceUnit, CodeKnowledgeUnit targetUnit) {
        if (!sourceUnit.fileName().equals(targetUnit.fileName())) {
            return List.of();
        }
        if (!targetUnit.symbolType().equals("function")) {
            return List.of();
        }
        if (!sourceUnit.text().contains(targetUnit.symbolName() + "(")) {
            return List.of();
        }
        return List.of(new CodeRelationship(
                sourceUnit.fileName(),
                sourceUnit.symbolName(),
                "CALLS_FUNCTION",
                targetUnit.fileName(),
                targetUnit.symbolName()
        ));
    }

    private List<String> methodNames(CodeKnowledgeUnit unit) {
        List<String> names = new ArrayList<>();
        for (String line : unit.text().split("\\R")) {
            String trimmed = line.trim();
            int openParenIndex = trimmed.indexOf('(');
            if (openParenIndex <= 0) {
                continue;
            }
            String candidate = trimmed.substring(0, openParenIndex).trim();
            if (candidate.contains(" ") || candidate.isBlank() || !Character.isUpperCase(candidate.charAt(0))) {
                continue;
            }
            names.add(candidate);
        }
        return names;
    }
}

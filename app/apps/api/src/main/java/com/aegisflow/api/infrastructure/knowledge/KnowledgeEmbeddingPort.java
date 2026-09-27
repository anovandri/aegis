package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;

public interface KnowledgeEmbeddingPort {
    String modelName();

    List<Double> embed(String text);
}

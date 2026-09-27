package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "aegisflow.knowledge.embedding", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalHashKnowledgeEmbeddingAdapter implements KnowledgeEmbeddingPort {
    static final int DIMENSIONS = 64;

    @Override
    public String modelName() {
        return "local-hash-embedding-v1";
    }

    @Override
    public List<Double> embed(String text) {
        double[] vector = new double[DIMENSIONS];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 3) {
                continue;
            }
            int index = Math.floorMod(hash(token), DIMENSIONS);
            vector[index] += 1.0d;
        }
        normalize(vector);
        List<Double> result = new ArrayList<>(DIMENSIONS);
        for (double value : vector) {
            result.add(value);
        }
        return result;
    }

    private int hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Integer.parseUnsignedInt(HexFormat.of().formatHex(digest, 0, 4), 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private void normalize(double[] vector) {
        double sumSquares = 0.0d;
        for (double value : vector) {
            sumSquares += value * value;
        }
        if (sumSquares == 0.0d) {
            return;
        }
        double length = Math.sqrt(sumSquares);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / length;
        }
    }
}

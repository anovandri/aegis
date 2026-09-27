package com.aegisflow.api.agent;

import com.aegisflow.api.domain.ArtifactVersion;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class BrdTextExtractor {
    public String extract(ArtifactVersion artifact, byte[] content) {
        String fileName = artifact.fileName().toLowerCase(Locale.ROOT);
        String mediaType = artifact.mediaType().toLowerCase(Locale.ROOT);
        boolean supported = mediaType.startsWith("text/")
                || mediaType.equals("application/json")
                || fileName.endsWith(".md")
                || fileName.endsWith(".txt");

        if (!supported) {
            throw new UnsupportedOperationException(
                    "BRD text extraction currently supports text, markdown, and json only. Unsupported file: %s (%s)"
                            .formatted(artifact.fileName(), artifact.mediaType())
            );
        }

        String text = new String(content, StandardCharsets.UTF_8).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("BRD document is empty after text extraction");
        }
        return text;
    }
}

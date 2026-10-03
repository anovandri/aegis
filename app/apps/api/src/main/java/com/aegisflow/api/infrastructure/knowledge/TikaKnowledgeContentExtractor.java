package com.aegisflow.api.infrastructure.knowledge;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TikaKnowledgeContentExtractor implements KnowledgeContentExtractor {
    @Override
    public String extractorName() {
        return "tika";
    }

    @Override
    public boolean supports(String fileName, String mediaType) {
        String normalizedName = normalize(fileName);
        String normalizedMediaType = normalize(mediaType);
        return normalizedMediaType.equals("application/pdf")
                || normalizedMediaType.equals("application/msword")
                || normalizedMediaType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || normalizedMediaType.equals("application/vnd.ms-excel")
                || normalizedMediaType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || normalizedName.endsWith(".pdf")
                || normalizedName.endsWith(".doc")
                || normalizedName.endsWith(".docx")
                || normalizedName.endsWith(".xls")
                || normalizedName.endsWith(".xlsx");
    }

    @Override
    public ExtractedKnowledgeContent extract(String fileName, String mediaType, byte[] content) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        BodyContentHandler handler = new BodyContentHandler(-1);
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        try (TikaInputStream stream = TikaInputStream.get(new ByteArrayInputStream(content))) {
            parser.parse(stream, handler, metadata, context);
        } catch (IOException | SAXException | TikaException exception) {
            throw new IllegalStateException("Unable to extract knowledge content from %s (%s)".formatted(fileName, mediaType), exception);
        }

        String text = handler.toString().trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Knowledge document is empty after Tika extraction: " + fileName);
        }

        String extractionType = extractionType(fileName, mediaType);
        return new ExtractedKnowledgeContent(
                extractionType,
                text,
                List.of(new ExtractedKnowledgeSection(
                        extractionType,
                        fileName,
                        text,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of("extractor", extractorName())
                )),
                Map.of(
                        "extractor", extractorName(),
                        "detectedContentType", metadata.get(Metadata.CONTENT_TYPE) == null ? "" : metadata.get(Metadata.CONTENT_TYPE)
                ),
                List.of()
        );
    }

    private String extractionType(String fileName, String mediaType) {
        String normalizedName = normalize(fileName);
        String normalizedMediaType = normalize(mediaType);
        if (normalizedMediaType.equals("application/pdf") || normalizedName.endsWith(".pdf")) {
            return "PDF";
        }
        if (normalizedName.endsWith(".xls") || normalizedName.endsWith(".xlsx") || normalizedMediaType.contains("spreadsheet")) {
            return "XLSX";
        }
        if (normalizedName.endsWith(".doc") || normalizedName.endsWith(".docx") || normalizedMediaType.contains("wordprocessingml")) {
            return "DOCX";
        }
        return "TIKA";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

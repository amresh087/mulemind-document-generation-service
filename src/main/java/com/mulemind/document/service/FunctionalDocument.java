package com.mulemind.document.service;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mulemind.document.dto.MetadataGeneratedEvent;

@Service
public class FunctionalDocument {

    private static final Logger log = LoggerFactory.getLogger(FunctionalDocument.class);

    private final ObjectMapper objectMapper;

    public FunctionalDocument(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] renderFunctionalDocPdf(MetadataGeneratedEvent event) throws Exception {
        JsonNode documentation = parseDocumentation(event.getDocumentation());
        log.info("Rendering functional documentation documentId={}, fields={}", event.getDocumentId(),
                documentation.fieldNames());

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FunctionalPdfWriter writer = new FunctionalPdfWriter(
                    document, regularFont, boldFont, safe(documentation.path("applicationName").asText()));
            writer.addCover(documentation);
            writer.addBusinessFlow(documentation);
            writer.addInterface(documentation);
            writer.addLimitations(documentation);
            writer.addErrorScenariosSection(documentation);
            writer.addIntegrationsSection(documentation);
            writer.close();
            document.save(output);
            return output.toByteArray();
        }
    }

    private JsonNode parseDocumentation(String rawDocumentation) throws Exception {
        if (rawDocumentation == null || rawDocumentation.isBlank()) {
            throw new IllegalArgumentException("Generated documentation is empty");
        }

        String content = rawDocumentation.trim();
        if (content.startsWith("```")) {
            int firstLineEnd = content.indexOf('\n');
            int closingFence = content.lastIndexOf("```");
            if (firstLineEnd > 0 && closingFence > firstLineEnd) {
                content = content.substring(firstLineEnd + 1, closingFence).trim();
            }
        }

        JsonNode documentation = objectMapper.readTree(content);
        if (documentation == null || !documentation.isObject()) {
            throw new IllegalArgumentException("Generated documentation must be a JSON object");
        }
        return documentation;
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}

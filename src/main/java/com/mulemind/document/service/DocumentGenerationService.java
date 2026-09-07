package com.mulemind.document.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mulemind.document.dto.MetadataGeneratedEvent;
import com.mulemind.document.entity.ProjectDocumentResult;
import com.mulemind.document.repository.ProjectDocumentResultRepository;
import com.mulemind.document.util.DocumentationType;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentGenerationService {

    private static final float MARGIN = 50;
    private static final float FONT_SIZE = 10;
    private static final float LINE_HEIGHT = 14;

    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final ProjectDocumentResultRepository documentResultRepository;
    private final FunctionalPdfWriter functionalPdfWriter;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Generates a PDF document based on the provided MetadataGeneratedEvent, stores it in MinIO, and saves the result in the database.
     *
     * @param event The MetadataGeneratedEvent containing information for generating the PDF.
     * @return The object name of the stored PDF in MinIO.
     * @throws Exception If an error occurs during PDF generation or storage.
     */
    public String generateAndStore(MetadataGeneratedEvent event) throws Exception {
        String objectName = buildObjectName(event);
        byte[] pdf = renderPdf(event);
        ensureBucketExists();

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(pdf), pdf.length, -1)
                .contentType("application/pdf")
                .build());

        documentResultRepository.save(ProjectDocumentResult.builder()
                .documentId(event.getDocumentId())
                .documentName(event.getDocumentName())
                .tenant(event.getTenant())
                .objectName(objectName)
                .status("COMPLETED")
                .scannedAt(LocalDateTime.now())
                .build());

        return objectName;
    }

    /**
     * Saves a failed document generation result in the database.
     *
     * @param event The MetadataGeneratedEvent containing information about the failed document generation.
     */
    public void saveFailedResult(MetadataGeneratedEvent event) {
        documentResultRepository.save(ProjectDocumentResult.builder()
                .documentId(event.getDocumentId())
                .documentName(event.getDocumentName())
                .tenant(event.getTenant())
                .objectName(null)
                .status("FAILED")
                .scannedAt(LocalDateTime.now())
                .build());
    }

    /**
     * Renders a PDF document based on the provided MetadataGeneratedEvent.
     *
     * @param event The MetadataGeneratedEvent containing information for generating the PDF.
     * @return A byte array representing the generated PDF.
     * @throws Exception If an error occurs during PDF generation.
     */
    private byte[] renderPdf(MetadataGeneratedEvent event) throws Exception {

        String documentationType = event.getDocumentationType();
        if (documentationType != null && DocumentationType.FUNCTIONAL_DOC.name().equalsIgnoreCase(documentationType)) {
            return functionalPdfWriter.renderFunctionalDocPdf(event);
        } else if (documentationType != null
                && DocumentationType.TECHNICAL_DOC.name().equalsIgnoreCase(documentationType)) {
            return renderTechnicalDocPdf(event);
        } else if (documentationType != null && DocumentationType.FLOW_DOC.name().equalsIgnoreCase(documentationType)) {
            return renderFlowDocPdf(event);
        } else if (documentationType != null
                && DocumentationType.SEQUENCE_DOC.name().equalsIgnoreCase(documentationType)) {
            return renderSequenceDocPdf(event);
        } else {
            throw new IllegalArgumentException("Unsupported documentation type: " + documentationType);
        }

    }

    /**
     * Renders the technical documentation as a PDF.
     * 
     * @param event
     * @return
     * @throws Exception
     */
    public byte[] renderTechnicalDocPdf(MetadataGeneratedEvent event) throws Exception {

        JsonNode documentation = parseDocumentation(event.getDocumentation());
        String formattedDocumentation = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(documentation);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PdfWriter writer = new PdfWriter(document);
            writer.writeLine(event.getDocumentName(), boldFont, 16);
            writer.writeLine("Tenant: " + safe(event.getTenant()), boldFont, 11);
            writer.writeLine("Generated: " + safe(event.getGeneratedAt()), regularFont, 10);
            writer.writeLine("", regularFont, FONT_SIZE);
            for (String line : formattedDocumentation.split("\\R", -1)) {
                writer.writeWrappedLine(line, regularFont, FONT_SIZE);
            }
            writer.closePage();
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * Renders the flow documentation as a PDF.
     * 
     * @param event
     * @return
     * @throws Exception
     */
    public byte[] renderFlowDocPdf(MetadataGeneratedEvent event) throws Exception {

        JsonNode documentation = parseDocumentation(event.getDocumentation());
        String formattedDocumentation = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(documentation);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PdfWriter writer = new PdfWriter(document);
            writer.writeLine(event.getDocumentName(), boldFont, 16);
            writer.writeLine("Tenant: " + safe(event.getTenant()), boldFont, 11);
            writer.writeLine("Generated: " + safe(event.getGeneratedAt()), regularFont, 10);
            writer.writeLine("", regularFont, FONT_SIZE);
            for (String line : formattedDocumentation.split("\\R", -1)) {
                writer.writeWrappedLine(line, regularFont, FONT_SIZE);
            }
            writer.closePage();
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * Renders the sequence documentation as a PDF.
     * @param event
     * @return
     * @throws Exception
     */
    public byte[] renderSequenceDocPdf(MetadataGeneratedEvent event) throws Exception {

        JsonNode documentation = parseDocumentation(event.getDocumentation());
        String formattedDocumentation = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(documentation);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PdfWriter writer = new PdfWriter(document);
            writer.writeLine(event.getDocumentName(), boldFont, 16);
            writer.writeLine("Tenant: " + safe(event.getTenant()), boldFont, 11);
            writer.writeLine("Generated: " + safe(event.getGeneratedAt()), regularFont, 10);
            writer.writeLine("", regularFont, FONT_SIZE);
            for (String line : formattedDocumentation.split("\\R", -1)) {
                writer.writeWrappedLine(line, regularFont, FONT_SIZE);
            }
            writer.closePage();
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * Ensures that the specified MinIO bucket exists. If it does not exist, it creates the bucket.
     *
     * @throws Exception If an error occurs while checking or creating the bucket.
     */
    private void ensureBucketExists() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * Parses the raw documentation string into a JsonNode.
     *
     * @param rawDocumentation The raw documentation string to parse.
     * @return A JsonNode representing the parsed documentation.
     * @throws Exception If the raw documentation is empty or not a valid JSON object.
     */
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

    /**
     * Builds the object name for storing the PDF in MinIO based on the provided MetadataGeneratedEvent.
     *
     * @param event The MetadataGeneratedEvent containing information for generating the object name.
     * @return The constructed object name for storing the PDF in MinIO.
     */
    private String buildObjectName(MetadataGeneratedEvent event) {
        String documentName = sanitizePathPart(event.getDocumentName(), "documentation");
        int extensionIndex = documentName.lastIndexOf('.');
        if (extensionIndex > 0) {
            documentName = documentName.substring(0, extensionIndex);
        }
        String tenant = sanitizePathPart(event.getTenant(), "unknown");
        String documentationType = sanitizePathPart(event.getDocumentationType(), "DOCUMENTATION");
        return "tenant-" + tenant + "/reports/" + event.getDocumentId() + "/"
                + documentName + "_" + documentationType + ".pdf";
    }

    /**
     * Sanitizes a string value to be used as a part of a file path by replacing certain characters and providing a fallback value.
     *
     * @param value The string value to sanitize.
     * @param fallback The fallback value to use if the input value is null or blank.
     * @return The sanitized string value suitable for use in a file path.
     */
    private String sanitizePathPart(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("[/\\\\]+", "_").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Safely converts an object to a string, returning an empty string if the object is null.
     *
     * @param value The object to convert to a string.
     * @return The string representation of the object, or an empty string if the object is null.
     */
    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * A helper class for writing content to a PDF document using Apache PDFBox.
     */
    private static final class PdfWriter {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream contentStream;
        private float y;

        private PdfWriter(PDDocument document) {
            this.document = document;
            openPage();
        }

        /**
         * Writes a line of text to the PDF document.
         *
         * @param text The text to write.
         * @param font The font to use.
         * @param fontSize The font size.
         * @throws Exception If an error occurs while writing the text.
         */
        private void writeLine(String text, PDFont font, float fontSize) throws Exception {
            if (y < 55) {
                closePage();
                openPage();
            }
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(MARGIN, y);
            contentStream.showText(sanitize(text));
            contentStream.endText();
            y -= fontSize == 16 ? 24 : LINE_HEIGHT;
        }

        /**
         * Writes a line of text to the PDF document, wrapping it if necessary.
         *
         * @param text The text to write.
         * @param font The font to use.
         * @param fontSize The font size.
         * @throws Exception If an error occurs while writing the text.
         */
        private void writeWrappedLine(String text, PDFont font, float fontSize) throws Exception {
            String remaining = text;
            while (remaining.length() > 105) {
                int splitAt = remaining.lastIndexOf(' ', 105);
                if (splitAt < 1) {
                    splitAt = 105;
                }
                writeLine(remaining.substring(0, splitAt), font, fontSize);
                remaining = remaining.substring(splitAt).trim();
            }
            writeLine(remaining, font, fontSize);
        }

        /**
         * Opens a new page in the PDF document.
         */
        private void openPage() {
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try {
                contentStream = new PDPageContentStream(document, page);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to open PDF page", exception);
            }
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        /**
         * Closes the current page in the PDF document.
         *
         * @throws Exception If an error occurs while closing the page.
         */
        private void closePage() throws Exception {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }

        /**
         * Sanitizes a string by replacing non-ASCII characters with a question mark.
         *
         * @param text The string to sanitize.
         * @return The sanitized string.
         */
        private static String sanitize(String text) {
            return text == null ? "" : text.replaceAll("[^\\x00-\\x7F]", "?");
        }
    }
}

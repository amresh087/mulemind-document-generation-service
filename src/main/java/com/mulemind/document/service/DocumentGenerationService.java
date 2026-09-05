package com.mulemind.document.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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

    @Value("${minio.bucket-name}")
    private String bucketName;

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

    private byte[] renderPdf(MetadataGeneratedEvent event) throws Exception {

        String documentationType = event.getDocumentationType();
        if (documentationType != null && DocumentationType.FUNCTIONAL_DOC.name().equalsIgnoreCase(documentationType)) {
            return renderFunctionalDocPdf(event);
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
     * Renders the functional documentation as a PDF.
     * 
     * @param event
     * @return
     * @throws Exception
     */
    public byte[] renderFunctionalDocPdf(MetadataGeneratedEvent event) throws Exception {
        JsonNode documentation = objectMapper.readTree(event.getDocumentation());

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                FunctionalPdfWriter writer = new FunctionalPdfWriter(document, regularFont, boldFont,
                    safe(documentation.path("applicationName").asText()));
            writer.addCover(documentation);
            writer.addBusinessFlow(documentation);
            writer.addInterface(documentation);
            writer.addTransformation(documentation);
            writer.addLimitations(documentation);
            writer.close();
            document.save(output);
            return output.toByteArray();
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

        JsonNode documentation = objectMapper.readTree(event.getDocumentation());
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

        JsonNode documentation = objectMapper.readTree(event.getDocumentation());
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

        JsonNode documentation = objectMapper.readTree(event.getDocumentation());
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

    private void ensureBucketExists() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

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

    private String sanitizePathPart(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("[/\\\\]+", "_").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private static final class FunctionalPdfWriter {
        private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - (MARGIN * 2);
        private static final float CONTENT_RIGHT = MARGIN + CONTENT_WIDTH;
        private static final Color NAVY = new Color(15, 61, 102);
        private static final Color BLUE = new Color(23, 105, 170);
        private static final Color TEAL = new Color(15, 159, 168);
        private static final Color LIGHT_BLUE = new Color(234, 244, 251);
        private static final Color LIGHT_TEAL = new Color(234, 248, 248);
        private static final Color LIGHT_GREY = new Color(247, 249, 252);
        private static final Color BORDER = new Color(215, 225, 234);
        private static final Color TEXT = new Color(31, 41, 55);
        private static final Color MUTED = new Color(100, 116, 139);
        private static final Color AMBER = new Color(255, 248, 230);

        private final PDDocument document;
        private final PDFont regularFont;
        private final PDFont boldFont;
        private final String applicationName;
        private PDPageContentStream stream;
        private PDPage page;
        private float y;

        private FunctionalPdfWriter(PDDocument document, PDFont regularFont, PDFont boldFont, String applicationName) {
            this.document = document;
            this.regularFont = regularFont;
            this.boldFont = boldFont;
            this.applicationName = applicationName;
        }

        private void addCover(JsonNode data) throws Exception {
            newPage(false);
            y = 690;
            text("FUNCTIONAL DOCUMENT", MARGIN, y, boldFont, 31, NAVY);
            y -= 34;
            text(applicationName, MARGIN, y, regularFont, 22, TEAL);
            y -= 43;
            text("Business-oriented functional view derived exclusively from the supplied application metadata.",
                    MARGIN, y, regularFont, 11, TEXT);
            y -= 42;

                JsonNode interfaces = data.path("interfaces");
                String interfaceSummary = interfaces.isArray()
                    ? interfaces.size() + (interfaces.size() == 1 ? " interface" : " interfaces")
                    : "No interfaces specified";
            metadataCard(new String[][] {
                    { "APPLICATION NAME", value(data, "applicationName") },
                    { "BUSINESS CAPABILITY", value(data, "businessCapability") },
                    { "INTERFACES", interfaceSummary },
                    { "ENDPOINTS", interfaces.isArray() && !interfaces.isEmpty()
                        ? "See interface specifications" : "None specified" }
            });
            y -= 28;
            purposeCard(value(data, "purpose"));
            y -= 30;
            heading("DOCUMENT MAP", 15);
            table(new String[][] {
                    { "01", "Business Flow", "02", "Interface" },
                    { "03", "Data Transformation", "04", "Limitations & Open Question" }
                }, new float[] { 38, 209.5f, 38, 209.5f }, LIGHT_GREY);
            finishPage();
        }

        private void addBusinessFlow(JsonNode data) throws Exception {
            newPage(true);
            sectionHeading("01 | Business Flow");
            paragraph("The application flow described by the supplied data is presented below.");
            y -= 8;
            JsonNode flow = data.path("businessFlow");
            for (int index = 0; index < flow.size(); index++) {
                numberedCard(String.format("%02d", index + 1), flow.get(index).asText());
            }
            y -= 25;
            heading("Functional Flow at a Glance", 14);
            flowCards();
            finishPage();
        }

        private void addInterface(JsonNode data) throws Exception {
            JsonNode interfaces = data.path("interfaces");
            if (!interfaces.isArray() || interfaces.isEmpty()) {
            newPage(true);
            sectionHeading("02 | Interface Specification");
            paragraph("No interfaces are specified in the supplied data.");
            finishPage();
            return;
            }

            for (int index = 0; index < interfaces.size(); index++) {
            newPage(true);
            sectionHeading("02 | Interface Specification " + (index + 1) + " of " + interfaces.size());
            JsonNode api = interfaces.get(index);
            filledBanner(value(api, "type") + " | " + value(api, "method") + " | " + value(api, "path")
                + " | " + value(api, "name"));
            y -= 24;
            table(new String[][] {
                { "Interface Type", value(api, "type") },
                { "Interface Name", value(api, "name") },
                { "Method", value(api, "method") },
                { "Path", value(api, "path") },
                { "Description", value(api, "description") }
            }, new float[] { 135, 360 }, LIGHT_GREY);
            y -= 25;
            heading("Input", 14);
            tableWithHeader(new String[] { "Field", "Source", "Required" }, inputRows(api.path("inputs")),
                new float[] { 120, 225, 150 }, LIGHT_BLUE);
            y -= 22;
            heading("Processing", 14);
            paragraph(value(api, "processing"));
            y -= 15;
            heading("Output", 14);
            tableWithHeader(new String[] { "Field", "Destination" }, outputRows(api.path("outputs")),
                new float[] { 175, 320 }, LIGHT_TEAL);
            y -= 22;
            heading("Output Example", 14);
            labelledCard(null, value(api, "outputExample"), LIGHT_GREY, BORDER, 38);
            finishPage();
            }
        }

        private void addTransformation(JsonNode data) throws Exception {
            newPage(true);
            sectionHeading("03 | Data Transformation");
            if (!data.path("dataTransformations").isArray() || data.path("dataTransformations").isEmpty()) {
                paragraph("No data transformation details are specified in the supplied data.");
                finishPage();
                return;
            }
            JsonNode transformation = first(data.path("dataTransformations"));
            paragraph(value(transformation, "description"));
            y -= 22;
            transformationCards(value(transformation, "input"), value(transformation, "output"));
            y -= 28;
            heading("Transformation Details", 14);
            table(new String[][] {
                    { "Input", value(transformation, "input") },
                    { "Output", value(transformation, "output") },
                    { "Rule", value(transformation, "description") },
                    { "Rules", transformation.path("rules").isArray() && transformation.path("rules").isEmpty()
                            ? "No transformation rules are specified in the supplied data."
                            : value(transformation, "rules") }
            }, new float[] { 135, 360 }, LIGHT_GREY);
            finishPage();
        }

        private void addLimitations(JsonNode data) throws Exception {
            newPage(true);
            sectionHeading("04 | Limitations & Open Question");
            labelledCard("KNOWN LIMITATION", data.path("knownLimitations").isEmpty()
                ? "No known limitations are specified in the supplied data."
                : firstText(data.path("knownLimitations")), AMBER,
                    new Color(239, 211, 145), 45);
            y -= 25;
            labelledCard("OPEN QUESTION", data.path("openQuestions").isEmpty()
                ? "No open questions are specified in the supplied data."
                : firstText(data.path("openQuestions")), LIGHT_BLUE, BORDER, 45);
            y -= 32;
            table(new String[][] {
                    { "ERROR SCENARIOS", data.path("errorScenarios").isEmpty()
                            ? "No error scenarios are specified in the supplied data." : value(data, "errorScenarios") },
                    { "INTEGRATIONS", data.path("integrations").isEmpty()
                            ? "No integrations are specified in the supplied data." : value(data, "integrations") }
            }, new float[] { 248, 247 }, LIGHT_GREY);
            finishPage();
        }

        private void metadataCard(String[][] values) throws Exception {
            float width = CONTENT_WIDTH / 4f;
            float height = 78;
            rect(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT_GREY, BORDER);
            for (int index = 0; index < values.length; index++) {
                float x = MARGIN + index * width;
                if (index > 0) line(x, y - height, x, y, BORDER, 0.8f);
                text(values[index][0], x + 10, y - 18, regularFont, 8, MUTED);
                wrapped(values[index][1], x + 10, y - 42, width - 20, boldFont, 13, NAVY, 15);
            }
            y -= height;
        }

        private void numberedCard(String number, String description) throws Exception {
            float height = Math.max(43, wrappedHeight(description, CONTENT_WIDTH - 65, regularFont, 10, 14) + 20);
            rect(MARGIN, y - height, CONTENT_WIDTH, height, Color.WHITE, BORDER);
            rect(MARGIN, y - height, 48, height, BLUE, BLUE);
            centered(number, MARGIN + 24, y - height / 2 + 4, boldFont, 12, Color.WHITE);
            wrapped(description, MARGIN + 65, y - 22, CONTENT_WIDTH - 65, regularFont, 10, TEXT, 14);
            y -= height;
        }

        private void flowCards() throws Exception {
            float[] widths = { 112, 24, 215, 24, 120 };
            float x = MARGIN;
            String[][] cards = { { "CUSTOMER", "Submits request" }, { "APPLICATION", "Captures name\nConstructs greeting\nProcesses request" },
                    { "CALLER", "Receives message" } };
            for (int index = 0; index < widths.length; index++) {
                if (index == 1 || index == 3) {
                    rect(x, y - 105, widths[index], 105, Color.WHITE, BORDER);
                    centered("->", x + widths[index] / 2, y - 58, regularFont, 13, NAVY);
                } else {
                    int cardIndex = index / 2;
                    Color fill = cardIndex == 1 ? LIGHT_TEAL : LIGHT_BLUE;
                    rect(x, y - 105, widths[index], 105, fill, BORDER);
                    text(cards[cardIndex][0], x + 10, y - 33, boldFont, 12, NAVY);
                    wrapped(cards[cardIndex][1], x + 10, y - 64, widths[index] - 20, regularFont, 10, NAVY, 14);
                }
                x += widths[index];
            }
            y -= 105;
        }

        private void transformationCards(String input, String output) throws Exception {
            float[] widths = { 85, 25, 165, 25, 195 };
            float x = MARGIN;
            String[][] cards = { { "INPUT", input }, { "PREDEFINED\nGREETING TEXT", "" }, { "OUTPUT", output } };
            for (int index = 0; index < widths.length; index++) {
                if (index == 1 || index == 3) {
                    rect(x, y - 95, widths[index], 95, Color.WHITE, BORDER);
                    centered(index == 1 ? "+" : "->", x + widths[index] / 2, y - 52, boldFont, 13, NAVY);
                } else {
                    int cardIndex = index / 2;
                    rect(x, y - 95, widths[index], 95, cardIndex == 1 ? LIGHT_GREY : LIGHT_BLUE, BORDER);
                    wrapped(cards[cardIndex][0], x + 10, y - 35, widths[index] - 20, boldFont, 12, NAVY, 14);
                    wrapped(cards[cardIndex][1], x + 10, y - 67, widths[index] - 20, regularFont, 11, NAVY, 14);
                }
                x += widths[index];
            }
            y -= 95;
        }

        private void sectionHeading(String title) throws Exception {
            text(title, MARGIN, y, boldFont, 20, NAVY);
            y -= 34;
        }

        private void heading(String title, float size) throws Exception {
            text(title, MARGIN, y, boldFont, size, BLUE);
            y -= size + 9;
        }

        private void paragraph(String value) throws Exception {
            y -= wrapped(value, MARGIN, y, CONTENT_WIDTH, regularFont, 10, TEXT, 14);
        }

        private void filledBanner(String value) throws Exception {
            rect(MARGIN, y - 54, CONTENT_WIDTH, 54, NAVY, NAVY);
            centered(value, MARGIN + CONTENT_WIDTH / 2, y - 31, boldFont, 12, Color.WHITE);
            y -= 54;
        }

        private void purposeCard(String value) throws Exception {
            float height = Math.max(82, wrappedHeight(value, CONTENT_WIDTH - 20, regularFont, 10, 14) + 52);
            rect(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT_BLUE, BORDER);
            text("PURPOSE", MARGIN + 10, y - 22, boldFont, 10, TEXT);
            wrapped(value, MARGIN + 10, y - 50, CONTENT_WIDTH - 20, regularFont, 10, TEXT, 14);
            y -= height;
        }

        private void labelledCard(String label, String value, Color fill, Color border, float minHeight) throws Exception {
            float labelWidth = label == null ? 0 : 135;
            float height = Math.max(minHeight, wrappedHeight(value, CONTENT_WIDTH - labelWidth - 25, regularFont, 10, 14) + 24);
            rect(MARGIN, y - height, CONTENT_WIDTH, height, fill, border);
            if (label != null) {
                rect(MARGIN, y - height, labelWidth, height, fill, fill);
                text(label, MARGIN + 10, y - 22, boldFont, 10, TEXT);
            }
            wrapped(value, MARGIN + labelWidth + 15, y - 22, CONTENT_WIDTH - labelWidth - 25, regularFont, 10, TEXT, 14);
            y -= height;
        }

        private void table(String[][] rows, float[] widths, Color fill) throws Exception {
            for (String[] row : rows) {
                float height = rowHeight(row, widths, regularFont, 10);
                float x = MARGIN;
                for (int index = 0; index < row.length; index++) {
                    rect(x, y - height, widths[index], height, fill, BORDER);
                    wrapped(row[index], x + 10, y - 18, widths[index] - 20, index == 0 ? boldFont : regularFont,
                            10, TEXT, 14);
                    x += widths[index];
                }
                y -= height;
            }
        }

        private void tableWithHeader(String[] headers, List<String[]> rows, float[] widths, Color fill) throws Exception {
            table(new String[][] { headers }, widths, fill);
            for (String[] row : rows) table(new String[][] { row }, widths, Color.WHITE);
        }

        private List<String[]> inputRows(JsonNode inputs) {
            List<String[]> rows = new ArrayList<>();
            for (JsonNode input : inputs) rows.add(new String[] { value(input, "name"), value(input, "source"), value(input, "required") });
            return rows;
        }

        private List<String[]> outputRows(JsonNode outputs) {
            List<String[]> rows = new ArrayList<>();
            for (JsonNode output : outputs) rows.add(new String[] { value(output, "name"), value(output, "destination") });
            return rows;
        }

        private float rowHeight(String[] row, float[] widths, PDFont font, float size) throws Exception {
            float height = 28;
            for (int index = 0; index < row.length; index++) height = Math.max(height,
                    wrappedHeight(row[index], widths[index] - 20, font, size, 14) + 18);
            return height;
        }

        private float wrapped(String value, float x, float top, float width, PDFont font, float size,
                Color color, float lineHeight) throws Exception {
            List<String> lines = wrap(value, font, size, width);
            for (String line : lines) {
                text(line, x, top, font, size, color);
                top -= lineHeight;
            }
            return lines.size() * lineHeight;
        }

        private float wrappedHeight(String value, float width, PDFont font, float size, float lineHeight) throws Exception {
            return wrap(value, font, size, width).size() * lineHeight;
        }

        private List<String> wrap(String value, PDFont font, float size, float width) throws Exception {
            List<String> lines = new ArrayList<>();
            String safeValue = value == null ? "" : value;
            for (String paragraph : safeValue.split("\\R", -1)) {
                String remaining = paragraph;
                if (remaining.isEmpty()) {
                    lines.add("");
                    continue;
                }
                while (!remaining.isEmpty()) {
                    int split = remaining.length();
                    while (split > 0 && font.getStringWidth(remaining.substring(0, split)) / 1000 * size > width) split--;
                    if (split < remaining.length()) {
                        int space = remaining.lastIndexOf(' ', split);
                        if (space > 0) split = space;
                    }
                    if (split == 0) split = 1;
                    lines.add(remaining.substring(0, split).trim());
                    remaining = remaining.substring(split).trim();
                }
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private void newPage(boolean withHeader) throws Exception {
            finishPage();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = 770;
            if (withHeader) {
                rect(0, 819, PDRectangle.A4.getWidth(), 23, NAVY, NAVY);
                y = 770;
            }
        }

        private void finishPage() throws Exception {
            if (stream != null) {
                text(applicationName + " | Functional Documentation", MARGIN, 28, regularFont, 8, MUTED);
                text(Integer.toString(document.getNumberOfPages()), CONTENT_RIGHT - 8, 28, regularFont, 8, MUTED);
                stream.close();
                stream = null;
            }
        }

        private void close() throws Exception {
            finishPage();
        }

        private void text(String value, float x, float baseline, PDFont font, float size, Color color) throws Exception {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, baseline);
            stream.showText(sanitize(value));
            stream.endText();
        }

        private void centered(String value, float centerX, float baseline, PDFont font, float size, Color color) throws Exception {
            float width = font.getStringWidth(sanitize(value)) / 1000 * size;
            text(value, centerX - width / 2, baseline, font, size, color);
        }

        private void rect(float x, float bottom, float width, float height, Color fill, Color border) throws Exception {
            stream.setNonStrokingColor(fill);
            stream.addRect(x, bottom, width, height);
            stream.fill();
            stream.setStrokingColor(border);
            stream.setLineWidth(0.7f);
            stream.addRect(x, bottom, width, height);
            stream.stroke();
        }

        private void line(float x1, float y1, float x2, float y2, Color color, float width) throws Exception {
            stream.setStrokingColor(color);
            stream.setLineWidth(width);
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        private static JsonNode first(JsonNode array) {
            return array.isArray() && !array.isEmpty() ? array.get(0) : array;
        }

        private static String firstText(JsonNode array) {
            return array.isArray() && !array.isEmpty() ? array.get(0).asText() : "";
        }

        private static String value(JsonNode node, String field) {
            JsonNode value = node == null ? null : node.get(field);
            if (value == null || value.isNull()) return "";
            return value.isValueNode() ? value.asText() : value.toString();
        }

        private static String sanitize(String value) {
            return value == null ? "" : value.replace("→", "->").replaceAll("[^\\x00-\\x7F]", "?");
        }
    }

    private static final class PdfWriter {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream contentStream;
        private float y;

        private PdfWriter(PDDocument document) {
            this.document = document;
            openPage();
        }

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

        private void closePage() throws Exception {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }

        private static String sanitize(String text) {
            return text == null ? "" : text.replaceAll("[^\\x00-\\x7F]", "?");
        }
    }
}

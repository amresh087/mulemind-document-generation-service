package com.mulemind.document.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mulemind.document.dto.MetadataGeneratedEvent;

@Service
public class FunctionalPdfWriter extends BasePdfWriter {
    private static final Logger log = LoggerFactory.getLogger(FunctionalPdfWriter.class);
    private static final Color AMBER = new Color(255, 248, 230);
    private final ObjectMapper objectMapper;

    @Autowired
    public FunctionalPdfWriter(ObjectMapper objectMapper) {
        super(null, null, null, null);
        this.objectMapper = objectMapper;
    }

    /**
     * Private constructor used internally to create a new instance of FunctionalPdfWriter with the specified parameters.
     * @param document
     * @param regularFont
     * @param boldFont
     * @param applicationName
     */
    private FunctionalPdfWriter(PDDocument document, PDFont regularFont, PDFont boldFont, String applicationName) {
        super(document, regularFont, boldFont, applicationName);
        this.objectMapper = null;
    }

    /**
     * Renders a functional documentation PDF based on the provided MetadataGeneratedEvent.
     * @param event The MetadataGeneratedEvent containing the documentation data.
     * @return A byte array representing the generated PDF document.
     * @throws Exception If an error occurs during PDF generation or parsing the documentation.
     */

    public byte[] renderFunctionalDocPdf(MetadataGeneratedEvent event) throws Exception {
        JsonNode documentation = parseDocumentation(event.getDocumentation());
        log.info("Rendering functional documentation documentId={}, fields={}", event.getDocumentId(),
                documentation.fieldNames());

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            FunctionalPdfWriter writer = new FunctionalPdfWriter(
                    document, regularFont, boldFont, value(documentation, "applicationName"));
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


    /**
     * Parses the raw documentation string into a JsonNode.
     * @param rawDocumentation The raw documentation string to parse.
     * @return A JsonNode representing the parsed documentation.
     * @throws Exception If the documentation is empty or not a valid JSON object.
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
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void addCover(JsonNode data) throws Exception {
        newPage(false);
        text("FUNCTIONAL DOCUMENT", MARGIN, 690, boldFont, 31, NAVY);
        text(value(data, "applicationName"), MARGIN, 656, regularFont, 22, TEAL);
        float y = 613;
        y -= wrapped("A functional view that explains the application's purpose, business flow, interfaces, integrations, transformations, and error handling, derived exclusively from the evidence available in the supplied application metadata.",
                MARGIN, y, CONTENT_WIDTH, regularFont, 11, TEXT, 15);
        y -= 27;
        JsonNode interfacesJson = data.path("interfaces");
        String interfaceSummary = interfacesJson.isArray()
                ? interfacesJson.size() + (interfacesJson.size() == 1 ? " API" : " APIs")
                : "No APIs specified";
        metadataCard(new String[][] {
                { "APPLICATION NAME", value(data, "applicationName") },
                { "BUSINESS CAPABILITY", value(data, "businessCapability") },
                { "APIs", interfaceSummary },
                { "ENDPOINTS", interfacesJson.isArray() && !interfacesJson.isEmpty()
                        ? "See API specifications" : "None specified" }
        });
        purposeCard(value(data, "purpose"));
        heading("DOCUMENT MAP", 15);
        table(new String[][] {
                { "01", "Business Flow", "02", "APIs" },
                { "03", "Limitations & Open Question", "04", "Error Scenarios" },
                { "05", "Integrations", "", "" }
        }, new float[] { 38, 209.5f, 38, 209.5f }, LIGHT_GREY);
        finishPage();
    }


    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void addBusinessFlow(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("01 | Business Flow");
        paragraph("The following section presents the application flow derived from the supplied metadata, providing a clear view of the key functional activities and their execution sequence.");
        JsonNode flow = data.path("businessFlow");
        for (int index = 0; index < flow.size(); index++) {
            numberedCard(String.format("%02d", index + 1), flow.get(index).asText());
        }
        heading("Functional Flow at a Glance", 14);
        flowCards();
        finishPage();
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void addInterface(JsonNode data) throws Exception {
        JsonNode interfaces = data.path("interfaces");
        if (!interfaces.isArray() || interfaces.isEmpty()) {
            newPage(true);
            sectionHeading("02 | API Specification");
            paragraph("No interfaces are specified in the supplied data.");
            finishPage();
            return;
        }

        for (int index = 0; index < interfaces.size(); index++) {
            newPage(true);
            sectionHeading("02 | API Specification " + (index + 1) + " of " + interfaces.size());
            JsonNode api = interfaces.get(index);
            filledBanner(value(api, "type") + " | " + value(api, "method") + " | " + value(api, "path")
                    + " | " + value(api, "name"));
            table(new String[][] {
                    { "API Type", value(api, "type") },
                    { "API Name", value(api, "name") },
                    { "Method", value(api, "method") },
                    { "Path", value(api, "path") },
                    { "Description", value(api, "description") }
            }, new float[] { 135, 360 }, LIGHT_GREY);
            heading("Input", 14);
            tableWithHeader(new String[] { "Field", "Source", "Required" }, inputRows(api.path("inputs")),
                    new float[] { 120, 225, 150 }, LIGHT_BLUE);
            heading("Processing", 14);
            paragraph(value(api, "processing"));
            addTransformationDetails(data.path("dataTransformations"));
            heading("Output", 14);
            tableWithHeader(new String[] { "Field", "Destination" }, outputRows(api.path("outputs")),
                    new float[] { 175, 320 }, LIGHT_TEAL);
            heading("Output Example", 14);
            labelledCard(null, value(api, "outputExample"), LIGHT_GREY, BORDER, 38);
            finishPage();
        }
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void addTransformationDetails(JsonNode transformations) throws Exception {
        if (!transformations.isArray()) return;
        for (JsonNode transformation : transformations) {
            String description = value(transformation, "description");
            if (!description.isBlank()) paragraph(description);
        }
    }


    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void addLimitations(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("03 | Limitations & Open Question");
        labelledCard("KNOWN LIMITATION", data.path("knownLimitations").isEmpty()
                ? "No known limitations are specified in the supplied data."
                : firstText(data.path("knownLimitations")), AMBER, new Color(239, 211, 145), 45);
        labelledCard("OPEN QUESTION", data.path("openQuestions").isEmpty()
                ? "No open questions are specified in the supplied data."
                : firstText(data.path("openQuestions")), LIGHT_BLUE, BORDER, 45);
        finishPage();
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void addErrorScenariosSection(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("04 | Error Scenarios");
        heading("Error Scenarios", 15);
        JsonNode scenarios = data.path("errorScenarios");
        if (!scenarios.isArray() || scenarios.isEmpty()) {
            labelledCard(null, "No error scenarios are specified in the supplied data.", LIGHT_GREY, BORDER, 45);
        } else {
            for (JsonNode scenario : scenarios) errorScenarioCard(scenario);
        }
        finishPage();
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void addIntegrationsSection(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("05 | Integrations");
        heading("Integrations", 15);
        JsonNode integrations = data.path("integrations");
        if (!integrations.isArray() || integrations.isEmpty()) {
            labelledCard(null, "No integrations are specified in the supplied data.", LIGHT_GREY, BORDER, 45);
        } else {
            for (JsonNode integration : integrations) integrationCard(integration);
        }
        finishPage();
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void errorScenarioCard(JsonNode scenario) throws Exception {
        String[][] fields = {
                { "CONDITION", value(scenario, "condition") },
                { "BEHAVIOR", value(scenario, "behavior") },
                { "RESPONSE", value(scenario, "response") }
        };
        float height = 77;
        for (String[] field : fields) height += wrappedHeight(field[1], CONTENT_WIDTH - 25, regularFont, 10, 14) + 34;
        ensureSpace(height);
        rect(MARGIN, currentY() - height, CONTENT_WIDTH, height, AMBER, new Color(239, 211, 145));
        text(value(scenario, "scenario"), MARGIN + 16, currentY() - 28, boldFont, 16, NAVY);
        float fieldY = currentY() - 65;
        for (String[] field : fields) {
            text(field[0], MARGIN + 16, fieldY, boldFont, 10, MUTED);
            fieldY -= 17;
            fieldY -= wrapped(field[1], MARGIN + 16, fieldY, CONTENT_WIDTH - 25, regularFont, 10, TEXT, 14);
            fieldY -= 17;
        }
        setCurrentY(currentY() - height);
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private void integrationCard(JsonNode integration) throws Exception {
        String[][] fields = {
                { "TYPE", value(integration, "type") },
                { "DESCRIPTION", value(integration, "description") },
                { "SOURCE", value(integration, "source") },
                { "DESTINATION", value(integration, "destination") },
                { "BUSINESS PURPOSE", value(integration, "businessPurpose") }
        };
        float height = 77;
        for (String[] field : fields) height += wrappedHeight(field[1], CONTENT_WIDTH - 25, regularFont, 10, 14) + 34;
        ensureSpace(height);
        rect(MARGIN, currentY() - height, CONTENT_WIDTH, height, LIGHT_GREY, BORDER);
        text(value(integration, "name"), MARGIN + 16, currentY() - 28, boldFont, 16, NAVY);
        float fieldY = currentY() - 65;
        for (String[] field : fields) {
            text(field[0], MARGIN + 16, fieldY, boldFont, 10, MUTED);
            fieldY -= 17;
            fieldY -= wrapped(field[1], MARGIN + 16, fieldY, CONTENT_WIDTH - 25, regularFont, 10, TEXT, 14);
            fieldY -= 17;
        }
        setCurrentY(currentY() - height);
    }

    /**
     * Creates a metadata card with the specified values.
     * @param values The values to display in the card.
     * @throws Exception If an error occurs while creating the card.
     */
    private void metadataCard(String[][] values) throws Exception {
        float width = CONTENT_WIDTH / 4f;
        float height = 52;
        for (String[] value : values) height = Math.max(height,
                wrappedHeight(value[1], width - 20, boldFont, 13, 15) + 52);
        ensureSpace(height);
        rect(MARGIN, currentY() - height, CONTENT_WIDTH, height, LIGHT_GREY, BORDER);
        for (int index = 0; index < values.length; index++) {
            float x = MARGIN + index * width;
            if (index > 0) line(x, currentY() - height, x, currentY(), BORDER, 0.8f);
            text(values[index][0], x + 10, currentY() - 18, regularFont, 8, MUTED);
            wrapped(values[index][1], x + 10, currentY() - 42, width - 20, boldFont, 13, NAVY, 15);
        }
        setCurrentY(currentY() - height);
    }

    /**
     * Creates a numbered card with the specified number and description.
     * @param number The number to display on the card.
     * @param description The description to display on the card.
     * @throws Exception If an error occurs while creating the card.
     */
    private void numberedCard(String number, String description) throws Exception {
        float height = Math.max(43, wrappedHeight(description, CONTENT_WIDTH - 65, regularFont, 10, 14) + 20);
        ensureSpace(height);
        rect(MARGIN, currentY() - height, CONTENT_WIDTH, height, Color.WHITE, BORDER);
        rect(MARGIN, currentY() - height, 48, height, BLUE, BLUE);
        centered(number, MARGIN + 24, currentY() - height / 2 + 4, boldFont, 12, Color.WHITE);
        wrapped(description, MARGIN + 65, currentY() - 22, CONTENT_WIDTH - 65, regularFont, 10, TEXT, 14);
        setCurrentY(currentY() - height);
    }

    /**
     * Creates a flow card with the specified values.
     * @throws Exception If an error occurs while creating the card.
     */
    private void flowCards() throws Exception {
        ensureSpace(105);
        float[] widths = { 112, 24, 215, 24, 120 };
        String[][] cards = { { "CUSTOMER", "Submits request" },
                { "APPLICATION", "Captures name\nConstructs greeting\nProcesses request" },
                { "CALLER", "Receives message" } };
        float x = MARGIN;
        for (int index = 0; index < widths.length; index++) {
            if (index == 1 || index == 3) {
                rect(x, currentY() - 105, widths[index], 105, Color.WHITE, BORDER);
                centered("->", x + widths[index] / 2, currentY() - 58, regularFont, 13, NAVY);
            } else {
                int cardIndex = index / 2;
                Color fill = cardIndex == 1 ? LIGHT_TEAL : LIGHT_BLUE;
                rect(x, currentY() - 105, widths[index], 105, fill, BORDER);
                text(cards[cardIndex][0], x + 10, currentY() - 33, boldFont, 12, NAVY);
                wrapped(cards[cardIndex][1], x + 10, currentY() - 64, widths[index] - 20, regularFont, 10, NAVY, 14);
            }
            x += widths[index];
        }
        setCurrentY(currentY() - 105);
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private List<String[]> inputRows(JsonNode inputs) {
        List<String[]> rows = new ArrayList<>();
        for (JsonNode input : inputs) rows.add(new String[] { value(input, "name"), value(input, "source"), value(input, "required") });
        return rows;
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param fieldName The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    private List<String[]> outputRows(JsonNode outputs) {
        List<String[]> rows = new ArrayList<>();
        for (JsonNode output : outputs) rows.add(new String[] { value(output, "name"), value(output, "destination") });
        return rows;
    }

}

package com.mulemind.document.service;

import java.io.ByteArrayOutputStream;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mulemind.document.dto.MetadataGeneratedEvent;

@Service
public class FunctionalPdfWriter {
    private static final Logger log = LoggerFactory.getLogger(FunctionalPdfWriter.class);
    private static final float MARGIN = 50;
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
    private final ObjectMapper objectMapper;
    private PDPageContentStream stream;
    private PDPage page;
    private float y;

    @Autowired
    public FunctionalPdfWriter(ObjectMapper objectMapper) {
        this.document = null;
        this.regularFont = null;
        this.boldFont = null;
        this.applicationName = null;
        this.objectMapper = objectMapper;
    }

    /**
     * Private constructor used internally to create a FunctionalPdfWriter instance with the specified document, fonts, and application name.   
     * @param document
     * @param regularFont
     * @param boldFont
     * @param applicationName
     */
    private FunctionalPdfWriter(PDDocument document, PDFont regularFont, PDFont boldFont, String applicationName) {
        this.document = document;
        this.regularFont = regularFont;
        this.boldFont = boldFont;
        this.applicationName = applicationName;
        this.objectMapper = null;
    }


    /**
     * Renders a functional documentation PDF based on the provided MetadataGeneratedEvent. The method parses the documentation, creates a new PDF document, and adds various sections such as cover, business flow, interface specifications, limitations, error scenarios, and integrations. Finally, it saves the PDF to a byte array and returns it.
     * @param event
     * @return
     * @throws Exception
     */
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

    /**
     * Parses the raw documentation string into a JsonNode. It handles cases where the documentation is empty or not a valid JSON object. If the documentation starts with a code block (```), it extracts the content within the code block before parsing.
     * @param rawDocumentation
     * @return
     * @throws Exception
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
     * Returns a safe string representation of the given value. If the value is null, it returns an empty string; otherwise, it returns the string representation of the value.
     * @param value
     * @return
     */
    private static String safe(Object value) {
        return value == null ? "" : value.toString();
    }


    /**
     * Adds the cover page to the PDF document based on the provided data. The cover page includes the title, application name, purpose, and a summary of interfaces and endpoints. It also includes a document map with links to various sections of the functional documentation.
     * @param data
     * @throws Exception
     */

    void addCover(JsonNode data) throws Exception {
        newPage(false);
        y = 690;
        text("FUNCTIONAL DOCUMENT", MARGIN, y, boldFont, 31, NAVY);
        y -= 34;
        text(applicationName, MARGIN, y, regularFont, 22, TEAL);
        y -= 43;
            y -= wrapped("A functional view that explains the application's purpose, business flow, interfaces, integrations, transformations, and error handling, derived exclusively from the evidence available in the supplied application metadata.",
                MARGIN, y, CONTENT_WIDTH, regularFont, 11, TEXT, 15);
            y -= 27;

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
                { "03", "Limitations & Open Question", "04", "Error Scenarios" },
                { "05", "Integrations", "", "" }
            }, new float[] { 38, 209.5f, 38, 209.5f }, LIGHT_GREY);
        finishPage();
    }

    /**
     * Adds the business flow section to the PDF document based on the provided data.
     * @param data
     * @throws Exception
     */
    void addBusinessFlow(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("01 | Business Flow");
        paragraph("The following section presents the application flow derived from the supplied metadata, providing a clear view of the key functional activities and their execution sequence.");
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

    /**
     * Adds the interface section to the PDF document based on the provided data. It iterates through the interfaces specified in the data and generates detailed specifications for each interface, including input, processing, output, and output examples.
     * @param data
     * @throws Exception
     */
    void addInterface(JsonNode data) throws Exception {
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
        y -= 24;
        table(new String[][] {
            { "API Type", value(api, "type") },
            { "API Name", value(api, "name") },
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
        addTransformationDetails(data.path("dataTransformations"));
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

    /**
     * Adds transformation details to the PDF document based on the provided transformations. It iterates through the transformations specified in the data and generates a paragraph for each transformation description.
     * @param transformations
     * @throws Exception
     */
    private void addTransformationDetails(JsonNode transformations) throws Exception {
        if (!transformations.isArray() || transformations.isEmpty()) {
            return;
        }
        for (JsonNode transformation : transformations) {
            String description = value(transformation, "description");
            if (!description.isBlank()) {
                paragraph(description);
            }
        }
    }

    /**
     * Adds the limitations section to the PDF document based on the provided data.
     * @param data
     * @throws Exception
     */
    void addLimitations(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("03 | Limitations & Open Question");
        labelledCard("KNOWN LIMITATION", data.path("knownLimitations").isEmpty()
            ? "No known limitations are specified in the supplied data."
            : firstText(data.path("knownLimitations")), AMBER,
                new Color(239, 211, 145), 45);
        y -= 25;
        labelledCard("OPEN QUESTION", data.path("openQuestions").isEmpty()
            ? "No open questions are specified in the supplied data."
            : firstText(data.path("openQuestions")), LIGHT_BLUE, BORDER, 45);
        finishPage();
    }

    /**
     * Adds the error scenarios section to the PDF document based on the provided data. It iterates through the error scenarios specified in the data and generates detailed cards for each scenario, including condition, behavior, and response.
     * @param data
     * @throws Exception
     */
    void addErrorScenariosSection(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("04 | Error Scenarios");
        addErrorScenarios(data.path("errorScenarios"));
        finishPage();
    }


    /**
     * Adds the integrations section to the PDF document based on the provided data. It iterates through the integrations specified in the data and generates detailed cards for each integration, including type, description, source, destination, and business purpose.
     * @param data
     * @throws Exception
     */
    void addIntegrationsSection(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("05 | Integrations");
        addIntegrations(data.path("integrations"));
        finishPage();
    }

    /**
     * Adds error scenarios to the PDF document based on the provided scenarios. It generates a heading for the error scenarios section and creates cards for each scenario, including condition, behavior, and response. If no error scenarios are specified, it displays a message indicating that no scenarios are available.
     * @param scenarios
     * @throws Exception
     */
    private void addErrorScenarios(JsonNode scenarios) throws Exception {
        heading("Error Scenarios", 15);
        if (!scenarios.isArray() || scenarios.isEmpty()) {
            labelledCard(null, "No error scenarios are specified in the supplied data.", LIGHT_GREY, BORDER, 45);
            return;
        }
        for (JsonNode scenario : scenarios) {
            errorScenarioCard(scenario);
            y -= 18;
        }
    }

    /**
     * Adds integrations to the PDF document based on the provided integrations. It generates a heading for the integrations section and creates cards for each integration, including type, description, source, destination, and business purpose. If no integrations are specified, it displays a message indicating that no integrations are available.
     * @param integrations
     * @throws Exception
     */
    private void addIntegrations(JsonNode integrations) throws Exception {
        heading("Integrations", 15);
        if (!integrations.isArray() || integrations.isEmpty()) {
            labelledCard(null, "No integrations are specified in the supplied data.", LIGHT_GREY, BORDER, 45);
            return;
        }
        for (JsonNode integration : integrations) {
            integrationCard(integration);
            y -= 18;
        }
    }

    /**
     * Generates a card for an error scenario in the PDF document. The card includes the scenario title, condition, behavior, and response. It calculates the required height for the card based on the content and ensures that there is enough space on the page before rendering the card.
     * @param scenario
     * @throws Exception
     */

    private void errorScenarioCard(JsonNode scenario) throws Exception {
        String[][] fields = {
                { "CONDITION", value(scenario, "condition") },
                { "BEHAVIOR", value(scenario, "behavior") },
                { "RESPONSE", value(scenario, "response") }
        };
        float contentWidth = CONTENT_WIDTH - 25;
        float height = 77;
        for (String[] field : fields) {
            height += wrappedHeight(field[1], contentWidth, regularFont, 10, 14) + 34;
        }
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, AMBER, new Color(239, 211, 145));
        text(value(scenario, "scenario"), MARGIN + 16, y - 28, boldFont, 16, NAVY);
        float fieldY = y - 65;
        for (String[] field : fields) {
            text(field[0], MARGIN + 16, fieldY, boldFont, 10, MUTED);
            fieldY -= 17;
            fieldY -= wrapped(field[1], MARGIN + 16, fieldY, contentWidth, regularFont, 10, TEXT, 14);
            fieldY -= 17;
        }
        y -= height;
    }

    /**
     * Generates a card for an integration in the PDF document. The card includes the integration name, type, description, source, destination, and business purpose. It calculates the required height for the card based on the content and ensures that there is enough space on the page before rendering the card.
     * @param integration
     * @throws Exception
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
        for (String[] field : fields) {
            height += wrappedHeight(field[1], CONTENT_WIDTH - 25, regularFont, 10, 14) + 34;
        }
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT_GREY, BORDER);
        text(value(integration, "name"), MARGIN + 16, y - 28, boldFont, 16, NAVY);
        float fieldY = y - 65;
        for (String[] field : fields) {
            text(field[0], MARGIN + 16, fieldY, boldFont, 10, MUTED);
            fieldY -= 17;
            fieldY -= wrapped(field[1], MARGIN + 16, fieldY, CONTENT_WIDTH - 25, regularFont, 10, TEXT, 14);
            fieldY -= 17;
        }
        y -= height;
    }

    /**
     * Generates a metadata card in the PDF document. The card displays key-value pairs of metadata information. It calculates the required height for the card based on the content and ensures that there is enough space on the page before rendering the card.
     * @param values
     * @throws Exception
     */
    private void metadataCard(String[][] values) throws Exception {
        float width = CONTENT_WIDTH / 4f;
        float height = 52;
        for (String[] value : values) {
            height = Math.max(height, wrappedHeight(value[1], width - 20, boldFont, 13, 15) + 52);
        }
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT_GREY, BORDER);
        for (int index = 0; index < values.length; index++) {
            float x = MARGIN + index * width;
            if (index > 0) line(x, y - height, x, y, BORDER, 0.8f);
            text(values[index][0], x + 10, y - 18, regularFont, 8, MUTED);
            wrapped(values[index][1], x + 10, y - 42, width - 20, boldFont, 13, NAVY, 15);
        }
        y -= height;
    }

    /**
     * Generates a numbered card in the PDF document. The card includes a number and a description. It calculates the required height for the card based on the content and ensures that there is enough space on the page before rendering the card.
     * @param number
     * @param description
     * @throws Exception
     */
    private void numberedCard(String number, String description) throws Exception {
        float height = Math.max(43, wrappedHeight(description, CONTENT_WIDTH - 65, regularFont, 10, 14) + 20);
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, Color.WHITE, BORDER);
        rect(MARGIN, y - height, 48, height, BLUE, BLUE);
        centered(number, MARGIN + 24, y - height / 2 + 4, boldFont, 12, Color.WHITE);
        wrapped(description, MARGIN + 65, y - 22, CONTENT_WIDTH - 65, regularFont, 10, TEXT, 14);
        y -= height;
    }

    /**
     * Generates flow cards in the PDF document. Each card represents a step in the flow and includes a title and description. It calculates the required height for each card based on the content and ensures that there is enough space on the page before rendering the cards.
     * @throws Exception
     */
    private void flowCards() throws Exception {
        float[] widths = { 112, 24, 215, 24, 120 };
        float x = MARGIN;
        String[][] cards = { { "CUSTOMER", "Submits request" }, { "APPLICATION", "Captures name\nConstructs greeting\nProcesses request" },
                { "CALLER", "Receives message" } };
            ensureSpace(105);
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

    /**
     * Generates a section heading in the PDF document.
     * @param title the title of the section heading
     * @throws Exception
     */
    private void sectionHeading(String title) throws Exception {
        text(title, MARGIN, y, boldFont, 20, NAVY);
        y -= 34;
    }

    /**
     * Generates a heading in the PDF document with the specified title and font size.
     * @param title the title of the heading
     * @param size the font size of the heading
     * @throws Exception
     */
    private void heading(String title, float size) throws Exception {
        text(title, MARGIN, y, boldFont, size, BLUE);
        y -= size + 9;
    }

    /**
     * Generates a paragraph in the PDF document.
     * @param value the text of the paragraph
     * @throws Exception
     */
    private void paragraph(String value) throws Exception {
        for (String line : wrap(value, regularFont, 10, CONTENT_WIDTH)) {
            ensureSpace(14);
            text(line, MARGIN, y, regularFont, 10, TEXT);
            y -= 14;
        }
    }

    /**
     * Generates a filled banner in the PDF document.
     * @param value the text of the banner
     * @throws Exception
     */
    private void filledBanner(String value) throws Exception {
        ensureSpace(54);
        rect(MARGIN, y - 54, CONTENT_WIDTH, 54, NAVY, NAVY);
        centered(value, MARGIN + CONTENT_WIDTH / 2, y - 31, boldFont, 12, Color.WHITE);
        y -= 54;
    }

    /**
     * Generates a purpose card in the PDF document. The card displays the purpose of the application. It calculates the required height for the card based on the content and ensures that there is enough space on the page before rendering the card.
     * @param value the text of the purpose
     * @throws Exception
     */
    private void purposeCard(String value) throws Exception {
        float height = Math.max(82, wrappedHeight(value, CONTENT_WIDTH - 20, regularFont, 10, 14) + 52);
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT_BLUE, BORDER);
        text("PURPOSE", MARGIN + 10, y - 22, boldFont, 10, TEXT);
        wrapped(value, MARGIN + 10, y - 50, CONTENT_WIDTH - 20, regularFont, 10, TEXT, 14);
        y -= height;
    }

    /**
     * Generates a labelled card in the PDF document. The card displays a label and a corresponding value. It calculates the required height for the card based on the content and ensures that there is enough space on the page before rendering the card.
     * @param label the label of the card
     * @param value the value of the card
     * @param fill the fill color of the card
     * @param border the border color of the card
     * @param minHeight the minimum height of the card
     * @throws Exception
     */
    private void labelledCard(String label, String value, Color fill, Color border, float minHeight) throws Exception {
        float labelWidth = label == null ? 0 : 135;
        float height = Math.max(minHeight, wrappedHeight(value, CONTENT_WIDTH - labelWidth - 25, regularFont, 10, 14) + 24);
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, fill, border);
        if (label != null) {
            rect(MARGIN, y - height, labelWidth, height, fill, fill);
            text(label, MARGIN + 10, y - 22, boldFont, 10, TEXT);
        }
        wrapped(value, MARGIN + labelWidth + 15, y - 22, CONTENT_WIDTH - labelWidth - 25, regularFont, 10, TEXT, 14);
        y -= height;
    }

    /**
     * Generates a table in the PDF document.
     * @param rows the rows of the table
     * @param widths the widths of the columns
     * @param fill the fill color of the table
     * @throws Exception
     */
    private void table(String[][] rows, float[] widths, Color fill) throws Exception {
        for (String[] row : rows) {
            float height = rowHeight(row, widths, regularFont, 10);
            ensureSpace(height);
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

    /**
     * Generates a table with a header in the PDF document. The table includes a header row and multiple data rows. It calculates the required height for each row based on the content and ensures that there is enough space on the page before rendering the table.
     * @param headers the header row of the table
     * @param rows the data rows of the table
     * @param widths the widths of the columns
     * @param fill the fill color of the header row
     * @throws Exception
     */
    private void tableWithHeader(String[] headers, List<String[]> rows, float[] widths, Color fill) throws Exception {
        table(new String[][] { headers }, widths, fill);
        for (String[] row : rows) table(new String[][] { row }, widths, Color.WHITE);
    }

    /**
     * Generates input rows for the table in the PDF document based on the provided inputs. It extracts the name, source, and required fields from each input and creates a list of string arrays representing the rows of the table.
     * @param inputs
     * @return
     */

    private List<String[]> inputRows(JsonNode inputs) {
        List<String[]> rows = new ArrayList<>();
        for (JsonNode input : inputs) rows.add(new String[] { value(input, "name"), value(input, "source"), value(input, "required") });
        return rows;
    }

    /**
     * Generates output rows for the table in the PDF document based on the provided outputs. It extracts the name and destination fields from each output and creates a list of string arrays representing the rows of the table.
     * @param outputs
     * @return
     */

    private List<String[]> outputRows(JsonNode outputs) {
        List<String[]> rows = new ArrayList<>();
        for (JsonNode output : outputs) rows.add(new String[] { value(output, "name"), value(output, "destination") });
        return rows;
    }

    /**
     * Calculates the height of a row in the table based on the content of each cell. It determines the maximum height required for the row by considering the wrapped height of each cell's content and adding padding.
     * @param row the row of the table
     * @param widths the widths of the columns
     * @param font the font used for rendering text
     * @param size the font size used for rendering text
     * @return the calculated height of the row
     * @throws Exception
     */

    private float rowHeight(String[] row, float[] widths, PDFont font, float size) throws Exception {
        float height = 28;
        for (int index = 0; index < row.length; index++) height = Math.max(height,
                wrappedHeight(row[index], widths[index] - 20, font, size, 14) + 18);
        return height;
    }

    /**
     * Wraps the given text value within the specified width and renders it on the PDF document. It calculates the required height for the wrapped text and ensures that there is enough space on the page before rendering the text.
     * @param value the text value to be wrapped
     * @param x the x-coordinate for rendering the text
     * @param top the y-coordinate for rendering the text
     * @param width the maximum width for wrapping the text
     * @param font the font used for rendering text
     * @param size the font size used for rendering text
     * @param color the color of the text
     * @param lineHeight the line height for wrapped lines
     * @return the total height occupied by the wrapped text
     * @throws Exception
     */

    private float wrapped(String value, float x, float top, float width, PDFont font, float size,
            Color color, float lineHeight) throws Exception {
        List<String> lines = wrap(value, font, size, width);
        for (String line : lines) {
            text(line, x, top, font, size, color);
            top -= lineHeight;
        }
        return lines.size() * lineHeight;
    }

    /**
     * Calculates the height required to wrap the given text value within the specified width. It determines the number of lines needed for wrapping and multiplies it by the line height to get the total height.
     * @param value the text value to be wrapped
     * @param width the maximum width for wrapping the text
     * @param font the font used for rendering text
     * @param size the font size used for rendering text
     * @param lineHeight the line height for wrapped lines
     * @return the total height required for wrapping the text
     * @throws Exception
     */
    private float wrappedHeight(String value, float width, PDFont font, float size, float lineHeight) throws Exception {
        return wrap(value, font, size, width).size() * lineHeight;
    }

    /**
     * Wraps the given text value into multiple lines based on the specified width and font. It splits the text into paragraphs and then wraps each paragraph into lines that fit within the specified width. The method returns a list of wrapped lines.
     * @param value the text value to be wrapped
     * @param font the font used for rendering text
     * @param size the font size used for rendering text
     * @param width the maximum width for wrapping the text
     * @return a list of wrapped lines
     * @throws Exception
     */
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

    /**
     * Creates a new page in the PDF document with an optional header.
     * @param withHeader whether to include a header on the new page
     * @throws Exception
     */
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

    /**
     * Ensures that there is enough space on the current page for the specified height. If there is not enough space, it creates a new page with a header.
     * @param height the required height for the content
     * @throws Exception
     */
    private void ensureSpace(float height) throws Exception {
        if (stream != null && y - height < 52) {
            newPage(true);
        }
    }

    /**
     * Finalizes the current page by adding a footer with the application name and page number, and closes the content stream.
     * @throws Exception
     */
    private void finishPage() throws Exception {
        if (stream != null) {
            text(applicationName + " | Functional Documentation", MARGIN, 28, regularFont, 8, MUTED);
            text(Integer.toString(document.getNumberOfPages()), CONTENT_RIGHT - 8, 28, regularFont, 8, MUTED);
            stream.close();
            stream = null;
        }
    }

    /**
     * Closes the PDF writer by finalizing the current page.
     * @throws Exception
     */
    void close() throws Exception {
        finishPage();
    }

    /**
     * Renders the specified text at the given coordinates with the specified font, size, and color.
     * @param value the text to be rendered
     * @param x the x-coordinate for rendering the text
     * @param baseline the y-coordinate for rendering the text
     * @param font the font used for rendering text
     * @param size the font size used for rendering text
     * @param color the color of the text
     * @throws Exception
     */
    private void text(String value, float x, float baseline, PDFont font, float size, Color color) throws Exception {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, baseline);
        stream.showText(sanitize(value));
        stream.endText();
    }

    /**
     * Renders the specified text centered at the given x-coordinate with the specified font, size, and color.
     * @param value the text to be rendered
     * @param centerX the x-coordinate for centering the text
     * @param baseline the y-coordinate for rendering the text
     * @param font the font used for rendering text
     * @param size the font size used for rendering text
     * @param color the color of the text
     * @throws Exception
     */
    private void centered(String value, float centerX, float baseline, PDFont font, float size, Color color) throws Exception {
        float width = font.getStringWidth(sanitize(value)) / 1000 * size;
        text(value, centerX - width / 2, baseline, font, size, color);
    }


    /**
     * Draws a rectangle at the specified coordinates with the given width, height, fill color, and border color.
     * @param x the x-coordinate of the rectangle
     * @param bottom the y-coordinate of the bottom of the rectangle
     * @param width the width of the rectangle
     * @param height the height of the rectangle
     * @param fill the fill color of the rectangle
     * @param border the border color of the rectangle
     * @throws Exception
     */
    private void rect(float x, float bottom, float width, float height, Color fill, Color border) throws Exception {
        stream.setNonStrokingColor(fill);
        stream.addRect(x, bottom, width, height);
        stream.fill();
        stream.setStrokingColor(border);
        stream.setLineWidth(0.7f);
        stream.addRect(x, bottom, width, height);
        stream.stroke();
    }

    

    /**
     * Draws a line between the specified coordinates with the given color and width.
     * @param x1 the x-coordinate of the starting point
     * @param y1 the y-coordinate of the starting point
     * @param x2 the x-coordinate of the ending point
     * @param y2 the y-coordinate of the ending point
     * @param color the color of the line
     * @param width the width of the line
     * @throws Exception
     */
    private void line(float x1, float y1, float x2, float y2, Color color, float width) throws Exception {
        stream.setStrokingColor(color);
        stream.setLineWidth(width);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }


    /**
     * Retrieves the first text value from the given JSON array. If the array is empty or not an array, it returns an empty string.
     * @param array the JSON array
     * @return the first text value or an empty string
     */
    private static String firstText(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(0).asText() : "";
    }

    /**
     * Retrieves the value of the specified field from the given JSON node. If the node is null or the field is not present, it returns an empty string.
     * @param node the JSON node
     * @param field the field name
     * @return the value of the field or an empty string
     */
    private static String value(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return "";
        return value.isValueNode() ? value.asText() : value.toString();
    }

    /**
     * Sanitizes the given string value by replacing certain characters and removing non-ASCII characters. It replaces the "→" character with "->" and replaces any non-ASCII characters with a question mark ("?").
     * @param value the string value to be sanitized
     * @return the sanitized string value
     */

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("→", "->").replaceAll("[^\\x00-\\x7F]", "?");
    }
}


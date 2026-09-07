package com.mulemind.document.service;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import com.fasterxml.jackson.databind.JsonNode;

 final class FunctionalPdfWriter {
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
    private PDPageContentStream stream;
    private PDPage page;
    private float y;

    FunctionalPdfWriter(PDDocument document, PDFont regularFont, PDFont boldFont, String applicationName) {
        this.document = document;
        this.regularFont = regularFont;
        this.boldFont = boldFont;
        this.applicationName = applicationName;
    }

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

    void addErrorScenariosSection(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("04 | Error Scenarios");
        addErrorScenarios(data.path("errorScenarios"));
        finishPage();
    }

    void addIntegrationsSection(JsonNode data) throws Exception {
        newPage(true);
        sectionHeading("05 | Integrations");
        addIntegrations(data.path("integrations"));
        finishPage();
    }

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

    private void numberedCard(String number, String description) throws Exception {
        float height = Math.max(43, wrappedHeight(description, CONTENT_WIDTH - 65, regularFont, 10, 14) + 20);
        ensureSpace(height);
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

    private void sectionHeading(String title) throws Exception {
        text(title, MARGIN, y, boldFont, 20, NAVY);
        y -= 34;
    }

    private void heading(String title, float size) throws Exception {
        text(title, MARGIN, y, boldFont, size, BLUE);
        y -= size + 9;
    }

    private void paragraph(String value) throws Exception {
        for (String line : wrap(value, regularFont, 10, CONTENT_WIDTH)) {
            ensureSpace(14);
            text(line, MARGIN, y, regularFont, 10, TEXT);
            y -= 14;
        }
    }

    private void filledBanner(String value) throws Exception {
        ensureSpace(54);
        rect(MARGIN, y - 54, CONTENT_WIDTH, 54, NAVY, NAVY);
        centered(value, MARGIN + CONTENT_WIDTH / 2, y - 31, boldFont, 12, Color.WHITE);
        y -= 54;
    }

    private void purposeCard(String value) throws Exception {
        float height = Math.max(82, wrappedHeight(value, CONTENT_WIDTH - 20, regularFont, 10, 14) + 52);
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT_BLUE, BORDER);
        text("PURPOSE", MARGIN + 10, y - 22, boldFont, 10, TEXT);
        wrapped(value, MARGIN + 10, y - 50, CONTENT_WIDTH - 20, regularFont, 10, TEXT, 14);
        y -= height;
    }

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

    private void ensureSpace(float height) throws Exception {
        if (stream != null && y - height < 52) {
            newPage(true);
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

    void close() throws Exception {
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


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

abstract class BasePdfWriter {
    protected static final float MARGIN = 50;
    protected static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - (MARGIN * 2);
    protected static final float CONTENT_RIGHT = MARGIN + CONTENT_WIDTH;
    protected static final Color NAVY = new Color(15, 61, 102);
    protected static final Color BLUE = new Color(23, 105, 170);
    protected static final Color TEAL = new Color(15, 159, 168);
    protected static final Color LIGHT_BLUE = new Color(234, 244, 251);
    protected static final Color LIGHT_TEAL = new Color(234, 248, 248);
    protected static final Color LIGHT_GREY = new Color(247, 249, 252);
    protected static final Color BORDER = new Color(215, 225, 234);
    protected static final Color TEXT = new Color(31, 41, 55);
    protected static final Color MUTED = new Color(100, 116, 139);
    protected static final Color AMBER = new Color(255, 248, 230);

    protected final PDDocument document;
    protected final PDFont regularFont;
    protected final PDFont boldFont;
    protected final String applicationName;
    private PDPageContentStream stream;
    private float y;

    /**
     * Constructor for the BasePdfWriter class.
     * @param document
     * @param regularFont
     * @param boldFont
     * @param applicationName
     */
    protected BasePdfWriter(PDDocument document, PDFont regularFont, PDFont boldFont, String applicationName) {
        this.document = document;
        this.regularFont = regularFont;
        this.boldFont = boldFont;
        this.applicationName = applicationName;
    }

    /**
     * Retrieves the current Y-coordinate for content placement in the PDF.
     * @return The current Y-coordinate.
     */
    protected final float currentY() {
        return y;
    }

    /**
     * Moves the Y-coordinate for content placement in the PDF.
     * @param value The value to move the Y-coordinate by.
     */
    protected final void moveY(float value) {
        y = value;
    }

    /**
     * Sets the current Y-coordinate for content placement in the PDF.
     * @param value The value to set the Y-coordinate to.
     */
    protected final void setCurrentY(float value) {
        y = value;
    }

    /**
     * Creates a section heading in the PDF.
     * @param title The title of the section heading.
     * @throws Exception If an error occurs while creating the heading.
     */
    protected void sectionHeading(String title) throws Exception {
        text(title, MARGIN, y, boldFont, 20, NAVY);
        y -= 34;
    }

    /**
     * Creates a heading in the PDF with the specified title and size.
     * @param title
     * @param size
     * @throws Exception
     */
    protected void heading(String title, float size) throws Exception {
        text(title, MARGIN, y, boldFont, size, BLUE);
        y -= size + 9;
    }


    /**
     * Creates a paragraph in the PDF with the specified value.
     * @param value The value to display in the paragraph.
     * @throws Exception If an error occurs while creating the paragraph.
     */
    protected void paragraph(String value) throws Exception {
        for (String line : wrap(value, regularFont, 10, CONTENT_WIDTH)) {
            ensureSpace(14);
            text(line, MARGIN, y, regularFont, 10, TEXT);
            y -= 14;
        }
    }

    /**
     * Creates a filled banner in the PDF with the specified value.
     * @param value The value to display in the banner.
     * @throws Exception If an error occurs while creating the banner.
     */
    protected void filledBanner(String value) throws Exception {
        ensureSpace(54);
        rect(MARGIN, y - 54, CONTENT_WIDTH, 54, NAVY, NAVY);
        centered(value, MARGIN + CONTENT_WIDTH / 2, y - 31, boldFont, 12, Color.WHITE);
        y -= 54;
    }

    /**
     * Creates a labelled card in the PDF with the specified label, value, and colors.
     * @param label The label for the card.
     * @param value The value to display in the card.
     * @param fill The fill color for the card.
     * @param border The border color for the card.
     * @param minHeight The minimum height for the card.
     * @throws Exception If an error occurs while creating the card.
     */
    protected void labelledCard(String label, String value, Color fill, Color border, float minHeight) throws Exception {
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
     * Creates a purpose card in the PDF with the specified value.
     * @param value The value to display in the purpose card.
     * @throws Exception If an error occurs while creating the card.
     */

    protected void purposeCard(String value) throws Exception {
        float height = Math.max(82, wrappedHeight(value, CONTENT_WIDTH - 20, regularFont, 10, 14) + 52);
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT_BLUE, BORDER);
        text("PURPOSE", MARGIN + 10, y - 22, boldFont, 10, TEXT);
        wrapped(value, MARGIN + 10, y - 50, CONTENT_WIDTH - 20, regularFont, 10, TEXT, 14);
        y -= height;
    }

    /**
     * Creates a table in the PDF with the specified rows, column widths, and fill color.
     * @param rows The rows of the table.
     * @param widths The widths of the columns.
     * @param fill The fill color for the table cells.
     * @throws Exception If an error occurs while creating the table.
     */

    protected void table(String[][] rows, float[] widths, Color fill) throws Exception {
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
     * Creates a table with a header in the PDF with the specified headers, rows, column widths, and fill color.
     * @param headers The headers for the table.
     * @param rows The rows of the table.
     * @param widths The widths of the columns.
     * @param fill The fill color for the table cells.
     * @throws Exception If an error occurs while creating the table.
     */
    protected void tableWithHeader(String[] headers, List<String[]> rows, float[] widths, Color fill) throws Exception {
        table(new String[][] { headers }, widths, fill);
        for (String[] row : rows) table(new String[][] { row }, widths, Color.WHITE);
    }

    /**
     * Calculates the height of a row in the table based on the content and column widths.
     * @param row The row of data.
     * @param widths The widths of the columns.
     * @param font The font used for the text.
     * @param size The font size.
     * @return The calculated height of the row.
     * @throws Exception If an error occurs while calculating the height.
     */

    protected float rowHeight(String[] row, float[] widths, PDFont font, float size) throws Exception {
        float height = 28;
        for (int index = 0; index < row.length; index++) height = Math.max(height,
                wrappedHeight(row[index], widths[index] - 20, font, size, 14) + 18);
        return height;
    }

    /**
     * Wraps the text to fit within the specified width and draws it on the PDF.
     * @param value The text to wrap and draw.
     * @param x The x-coordinate for the text placement.
     * @param top The y-coordinate for the top of the text.
     * @param width The maximum width for the text.
     * @param font The font used for the text.
     * @param size The font size.
     * @param color The color of the text.
     * @param lineHeight The height of each line of text.
     * @return The total height of the wrapped text.
     * @throws Exception If an error occurs while wrapping or drawing the text.
     */
    protected float wrapped(String value, float x, float top, float width, PDFont font, float size,
            Color color, float lineHeight) throws Exception {
        List<String> lines = wrap(value, font, size, width);
        for (String line : lines) {
            text(line, x, top, font, size, color);
            top -= lineHeight;
        }
        return lines.size() * lineHeight;
    }

    /**
     * Calculates the height of wrapped text based on the specified width, font, size, and line height.
     * @param value The text to calculate the height for.
     * @param width The maximum width for the text.
     * @param font The font used for the text.
     * @param size The font size.
     * @param lineHeight The height of each line of text.
     * @return The total height of the wrapped text.
     * @throws Exception If an error occurs while calculating the height.
     */
    protected float wrappedHeight(String value, float width, PDFont font, float size, float lineHeight) throws Exception {
        return wrap(value, font, size, width).size() * lineHeight;
    }

    /**
     * Wraps the text into lines that fit within the specified width based on the font and size.
     * @param value The text to wrap.
     * @param font The font used for the text.
     * @param size The font size.
     * @param width The maximum width for the text.
     * @return A list of wrapped lines of text.
     * @throws Exception If an error occurs while wrapping the text.
     */
    protected List<String> wrap(String value, PDFont font, float size, float width) throws Exception {
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
     * Creates a new page in the PDF, optionally with a header.
     * @param withHeader Whether to include a header on the new page.
     * @throws Exception If an error occurs while creating the new page.
     */
    protected void newPage(boolean withHeader) throws Exception {
        finishPage();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = 770;
        if (withHeader) {
            rect(0, 819, PDRectangle.A4.getWidth(), 23, NAVY, NAVY);
        }
    }

    /**
     * Ensures that there is enough space on the current page for content of the specified height.
     * If there is not enough space, a new page is created.
     * @param height The height of the content to be added.
     * @throws Exception If an error occurs while ensuring space or creating a new page.
     */
    protected void ensureSpace(float height) throws Exception {
        if (stream != null && y - height < 52) newPage(true);
    }

    /**
     * Finishes the current page in the PDF by adding a footer and closing the content stream.
     * @throws Exception If an error occurs while finishing the page.
     */
    protected void finishPage() throws Exception {
        if (stream != null) {
            text(applicationName + " | Functional Documentation", MARGIN, 28, regularFont, 8, MUTED);
            text(Integer.toString(document.getNumberOfPages()), CONTENT_RIGHT - 8, 28, regularFont, 8, MUTED);
            stream.close();
            stream = null;
        }
    }

    /**
     * Closes the PDF writer by finishing the current page.
     * @throws Exception If an error occurs while closing the writer.
     */
    protected void close() throws Exception {
        finishPage();
    }

    /**
     * Draws text on the PDF at the specified coordinates with the given font, size, and color.
     * @param value The text to draw.
     * @param x The x-coordinate for the text placement.
     * @param baseline The y-coordinate for the baseline of the text.
     * @param font The font used for the text.
     * @param size The font size.
     * @param color The color of the text.
     * @throws Exception If an error occurs while drawing the text.
     */
    protected void text(String value, float x, float baseline, PDFont font, float size, Color color) throws Exception {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, baseline);
        stream.showText(sanitize(value));
        stream.endText();
    }

    /**
     * Draws centered text on the PDF at the specified coordinates with the given font, size, and color.
     * @param value The text to draw.
     * @param centerX The x-coordinate for the center of the text.
     * @param baseline The y-coordinate for the baseline of the text.
     * @param font The font used for the text.
     * @param size The font size.
     * @param color The color of the text.
     * @throws Exception If an error occurs while drawing the text.
     */
    protected void centered(String value, float centerX, float baseline, PDFont font, float size, Color color) throws Exception {
        float width = font.getStringWidth(sanitize(value)) / 1000 * size;
        text(value, centerX - width / 2, baseline, font, size, color);
    }

    /**
     * Draws a rectangle on the PDF at the specified coordinates with the given fill and border colors.
     * @param x The x-coordinate for the rectangle.
     * @param bottom The y-coordinate for the bottom of the rectangle.
     * @param width The width of the rectangle.
     * @param height The height of the rectangle.
     * @param fill The fill color of the rectangle.
     * @param border The border color of the rectangle.
     * @throws Exception If an error occurs while drawing the rectangle.
     */
    protected void rect(float x, float bottom, float width, float height, Color fill, Color border) throws Exception {
        stream.setNonStrokingColor(fill);
        stream.addRect(x, bottom, width, height);
        stream.fill();
        stream.setStrokingColor(border);
        stream.setLineWidth(0.7f);
        stream.addRect(x, bottom, width, height);
        stream.stroke();
    }

    /**
     * Draws a line on the PDF from the specified start coordinates to the end coordinates with the given color and width.
     * @param x1 The x-coordinate for the start of the line.
     * @param y1 The y-coordinate for the start of the line.
     * @param x2 The x-coordinate for the end of the line.
     * @param y2 The y-coordinate for the end of the line.
     * @param color The color of the line.
     * @param width The width of the line.
     * @throws Exception If an error occurs while drawing the line.
     */
    protected void line(float x1, float y1, float x2, float y2, Color color, float width) throws Exception {
        stream.setStrokingColor(color);
        stream.setLineWidth(width);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    /**
     * Retrieves the first text value from a JsonNode array.
     * @param array The JsonNode array to retrieve the value from.
     * @return The first text value in the array, or an empty string if the array is empty or not an array.
     */
    protected static String firstText(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(0).asText() : "";
    }

    /**
     * Retrieves the value of a specified field from a JsonNode.
     * @param node The JsonNode to retrieve the value from.
     * @param field The name of the field to retrieve.
     * @return The value of the specified field as a string, or an empty string if the field is missing or null.
     */
    protected static String value(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return "";
        return value.isValueNode() ? value.asText() : value.toString();
    }

    /**
     * Sanitizes a string value by replacing certain characters and removing non-ASCII characters.
     * @param value The string value to sanitize.
     * @return The sanitized string value.
     */
    protected static String sanitize(String value) {
        return value == null ? "" : value.replace("→", "->").replaceAll("[^\\x00-\\x7F]", "?");
    }
}

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

    protected BasePdfWriter(PDDocument document, PDFont regularFont, PDFont boldFont, String applicationName) {
        this.document = document;
        this.regularFont = regularFont;
        this.boldFont = boldFont;
        this.applicationName = applicationName;
    }

    protected final float currentY() {
        return y;
    }

    protected final void moveY(float value) {
        y = value;
    }

    protected final void setCurrentY(float value) {
        y = value;
    }

    protected void sectionHeading(String title) throws Exception {
        text(title, MARGIN, y, boldFont, 20, NAVY);
        y -= 34;
    }

    protected void heading(String title, float size) throws Exception {
        text(title, MARGIN, y, boldFont, size, BLUE);
        y -= size + 9;
    }

    protected void paragraph(String value) throws Exception {
        for (String line : wrap(value, regularFont, 10, CONTENT_WIDTH)) {
            ensureSpace(14);
            text(line, MARGIN, y, regularFont, 10, TEXT);
            y -= 14;
        }
    }

    protected void filledBanner(String value) throws Exception {
        ensureSpace(54);
        rect(MARGIN, y - 54, CONTENT_WIDTH, 54, NAVY, NAVY);
        centered(value, MARGIN + CONTENT_WIDTH / 2, y - 31, boldFont, 12, Color.WHITE);
        y -= 54;
    }

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

    protected void purposeCard(String value) throws Exception {
        float height = Math.max(82, wrappedHeight(value, CONTENT_WIDTH - 20, regularFont, 10, 14) + 52);
        ensureSpace(height);
        rect(MARGIN, y - height, CONTENT_WIDTH, height, LIGHT_BLUE, BORDER);
        text("PURPOSE", MARGIN + 10, y - 22, boldFont, 10, TEXT);
        wrapped(value, MARGIN + 10, y - 50, CONTENT_WIDTH - 20, regularFont, 10, TEXT, 14);
        y -= height;
    }

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

    protected void tableWithHeader(String[] headers, List<String[]> rows, float[] widths, Color fill) throws Exception {
        table(new String[][] { headers }, widths, fill);
        for (String[] row : rows) table(new String[][] { row }, widths, Color.WHITE);
    }

    protected float rowHeight(String[] row, float[] widths, PDFont font, float size) throws Exception {
        float height = 28;
        for (int index = 0; index < row.length; index++) height = Math.max(height,
                wrappedHeight(row[index], widths[index] - 20, font, size, 14) + 18);
        return height;
    }

    protected float wrapped(String value, float x, float top, float width, PDFont font, float size,
            Color color, float lineHeight) throws Exception {
        List<String> lines = wrap(value, font, size, width);
        for (String line : lines) {
            text(line, x, top, font, size, color);
            top -= lineHeight;
        }
        return lines.size() * lineHeight;
    }

    protected float wrappedHeight(String value, float width, PDFont font, float size, float lineHeight) throws Exception {
        return wrap(value, font, size, width).size() * lineHeight;
    }

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

    protected void ensureSpace(float height) throws Exception {
        if (stream != null && y - height < 52) newPage(true);
    }

    protected void finishPage() throws Exception {
        if (stream != null) {
            text(applicationName + " | Functional Documentation", MARGIN, 28, regularFont, 8, MUTED);
            text(Integer.toString(document.getNumberOfPages()), CONTENT_RIGHT - 8, 28, regularFont, 8, MUTED);
            stream.close();
            stream = null;
        }
    }

    protected void close() throws Exception {
        finishPage();
    }

    protected void text(String value, float x, float baseline, PDFont font, float size, Color color) throws Exception {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, baseline);
        stream.showText(sanitize(value));
        stream.endText();
    }

    protected void centered(String value, float centerX, float baseline, PDFont font, float size, Color color) throws Exception {
        float width = font.getStringWidth(sanitize(value)) / 1000 * size;
        text(value, centerX - width / 2, baseline, font, size, color);
    }

    protected void rect(float x, float bottom, float width, float height, Color fill, Color border) throws Exception {
        stream.setNonStrokingColor(fill);
        stream.addRect(x, bottom, width, height);
        stream.fill();
        stream.setStrokingColor(border);
        stream.setLineWidth(0.7f);
        stream.addRect(x, bottom, width, height);
        stream.stroke();
    }

    protected void line(float x1, float y1, float x2, float y2, Color color, float width) throws Exception {
        stream.setStrokingColor(color);
        stream.setLineWidth(width);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    protected static String firstText(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(0).asText() : "";
    }

    protected static String value(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return "";
        return value.isValueNode() ? value.asText() : value.toString();
    }

    protected static String sanitize(String value) {
        return value == null ? "" : value.replace("→", "->").replaceAll("[^\\x00-\\x7F]", "?");
    }
}

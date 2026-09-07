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

    // ============================================================
    // PAGE / LAYOUT CONSTANTS
    // ============================================================

    protected static final float MARGIN = 50;

    protected static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    protected static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    protected static final float CONTENT_WIDTH =
            PAGE_WIDTH - (MARGIN * 2);

    protected static final float CONTENT_RIGHT =
            MARGIN + CONTENT_WIDTH;

    /*
     * Footer safety area.
     * Content should never go below this Y position.
     */
    protected static final float BOTTOM_MARGIN = 52;

    /*
     * Default page starting position.
     */
    protected static final float START_Y = 770;

    // ============================================================
    // VERTICAL SPACING
    // ============================================================

    /*
     * Main section heading
     *
     * Example:
     *
     * 01 | Business Flow
     *
     * [space]
     *
     * Paragraph...
     */
    protected static final float SECTION_SPACE_BEFORE = 12;
    protected static final float SECTION_SPACE_AFTER = 28;

    /*
     * Normal subsection heading
     */
    protected static final float HEADING_SPACE_BEFORE = 8;
    protected static final float HEADING_SPACE_AFTER = 22;

    /*
     * Paragraph spacing
     */
    protected static final float PARAGRAPH_SPACE_BEFORE = 4;
    protected static final float PARAGRAPH_SPACE_AFTER = 10;

    /*
     * Space between cards / tables / blocks.
     */
    protected static final float BLOCK_SPACE = 14;

    /*
     * Space after banner.
     */
    protected static final float BANNER_SPACE_AFTER = 16;

    // ============================================================
    // COLORS
    // ============================================================

    protected static final Color NAVY =
            new Color(15, 61, 102);

    protected static final Color BLUE =
            new Color(23, 105, 170);

    protected static final Color TEAL =
            new Color(15, 159, 168);

    protected static final Color LIGHT_BLUE =
            new Color(234, 244, 251);

    protected static final Color LIGHT_TEAL =
            new Color(234, 248, 248);

    protected static final Color LIGHT_GREY =
            new Color(247, 249, 252);

    protected static final Color BORDER =
            new Color(215, 225, 234);

    protected static final Color TEXT =
            new Color(31, 41, 55);

    protected static final Color MUTED =
            new Color(100, 116, 139);

    protected static final Color AMBER =
            new Color(255, 248, 230);

    // ============================================================
    // PDF STATE
    // ============================================================

    protected final PDDocument document;

    protected final PDFont regularFont;

    protected final PDFont boldFont;

    protected final String applicationName;

    private PDPageContentStream stream;

    private float y;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    protected BasePdfWriter(
            PDDocument document,
            PDFont regularFont,
            PDFont boldFont,
            String applicationName) {

        this.document = document;
        this.regularFont = regularFont;
        this.boldFont = boldFont;
        this.applicationName = applicationName;
    }

    // ============================================================
    // Y POSITION
    // ============================================================

    /**
     * Returns the current Y position.
     */
    protected final float currentY() {
        return y;
    }

    /**
     * Moves the current Y position by the supplied value.
     *
     * Example:
     * moveY(-10) -> moves down 10 points.
     */
    protected final void moveY(float value) {
        y = value;
    }

    /**
     * Sets the current Y position.
     */
    protected final void setCurrentY(float value) {
        y = value;
    }

    // ============================================================
    // SECTION HEADING
    // ============================================================

    /**
     * Creates a main section heading.
     *
     * Example:
     *
     * 01 | Business Flow
     *
     * [28pt]
     *
     * Paragraph...
     */
    protected void sectionHeading(String title) throws Exception {

        /*
         * Reserve enough space for:
         * - space before heading
         * - heading itself
         * - space after heading
         */
        ensureSpace(
                SECTION_SPACE_BEFORE
                        + 20
                        + SECTION_SPACE_AFTER
        );

        // Space before heading
        y -= SECTION_SPACE_BEFORE;

        // Heading
        text(
                title,
                MARGIN,
                y,
                boldFont,
                20,
                NAVY
        );

        // Space after heading
        y -= SECTION_SPACE_AFTER;
    }

    // ============================================================
    // NORMAL HEADING
    // ============================================================

    /**
     * Creates a normal subsection heading.
     */
    protected void heading(String title, float size) throws Exception {

        ensureSpace(
                HEADING_SPACE_BEFORE
                        + size
                        + HEADING_SPACE_AFTER
        );

        // Space before heading
        y -= HEADING_SPACE_BEFORE;

        // Heading
        text(
                title,
                MARGIN,
                y,
                boldFont,
                size,
                BLUE
        );

        // Space after heading
        y -= HEADING_SPACE_AFTER;
    }

    // ============================================================
    // PARAGRAPH
    // ============================================================

    /**
     * Creates a wrapped paragraph with consistent spacing.
     */
    protected void paragraph(String value) throws Exception {

        if (value == null || value.isBlank()) {
            return;
        }

        List<String> lines =
                wrap(
                        value,
                        regularFont,
                        10,
                        CONTENT_WIDTH
                );

        /*
         * Space before paragraph.
         */
        y -= PARAGRAPH_SPACE_BEFORE;

        for (String line : lines) {

            ensureSpace(14);

            text(
                    line,
                    MARGIN,
                    y,
                    regularFont,
                    10,
                    TEXT
            );

            y -= 14;
        }

        /*
         * Space after paragraph.
         */
        y -= PARAGRAPH_SPACE_AFTER;
    }

    // ============================================================
    // FILLED BANNER
    // ============================================================

    protected void filledBanner(String value) throws Exception {

        final float height = 54;

        ensureSpace(
                height
                        + BANNER_SPACE_AFTER
        );

        rect(
                MARGIN,
                y - height,
                CONTENT_WIDTH,
                height,
                NAVY,
                NAVY
        );

        centered(
                value,
                MARGIN + CONTENT_WIDTH / 2,
                y - 31,
                boldFont,
                12,
                Color.WHITE
        );

        y -= height;

        /*
         * Space after banner.
         */
        y -= BANNER_SPACE_AFTER;
    }

    // ============================================================
    // LABELLED CARD
    // ============================================================

    protected void labelledCard(
            String label,
            String value,
            Color fill,
            Color border,
            float minHeight) throws Exception {

        float labelWidth =
                label == null ? 0 : 135;

        float contentWidth =
                CONTENT_WIDTH
                        - labelWidth
                        - 25;

        float contentHeight =
                wrappedHeight(
                        value,
                        contentWidth,
                        regularFont,
                        10,
                        14
                );

        float height =
                Math.max(
                        minHeight,
                        contentHeight + 36
                );

        /*
         * Add space between blocks.
         */
        ensureSpace(
                height
                        + BLOCK_SPACE
        );

        rect(
                MARGIN,
                y - height,
                CONTENT_WIDTH,
                height,
                fill,
                border
        );

        // Label section
        if (label != null) {

            rect(
                    MARGIN,
                    y - height,
                    labelWidth,
                    height,
                    fill,
                    fill
            );

            text(
                    label,
                    MARGIN + 10,
                    y - 22,
                    boldFont,
                    10,
                    TEXT
            );
        }

        // Card content
        wrapped(
                value,
                MARGIN + labelWidth + 15,
                y - 22,
                contentWidth,
                regularFont,
                10,
                TEXT,
                14
        );

        /*
         * Move below card.
         */
        y -= height;

        /*
         * Consistent gap after card.
         */
        y -= BLOCK_SPACE;
    }

    // ============================================================
    // PURPOSE CARD
    // ============================================================

    protected void purposeCard(String value) throws Exception {

        float height =
                Math.max(
                        82,
                        wrappedHeight(
                                value,
                                CONTENT_WIDTH - 20,
                                regularFont,
                                10,
                                14
                        ) + 58
                );

        ensureSpace(
                height
                        + BLOCK_SPACE
        );

        rect(
                MARGIN,
                y - height,
                CONTENT_WIDTH,
                height,
                LIGHT_BLUE,
                BORDER
        );

        // Label
        text(
                "PURPOSE",
                MARGIN + 10,
                y - 22,
                boldFont,
                10,
                TEXT
        );

        // Content
        wrapped(
                value,
                MARGIN + 10,
                y - 50,
                CONTENT_WIDTH - 20,
                regularFont,
                10,
                TEXT,
                14
        );

        y -= height;

        /*
         * Space after purpose card.
         */
        y -= BLOCK_SPACE;
    }

    // ============================================================
    // TABLE
    // ============================================================

    protected void table(
            String[][] rows,
            float[] widths,
            Color fill) throws Exception {

        if (rows == null || rows.length == 0) {
            return;
        }

        for (String[] row : rows) {

            if (row == null) {
                continue;
            }

            float height =
                    rowHeight(
                            row,
                            widths,
                            regularFont,
                            10
                    );

            ensureSpace(height);

            float x = MARGIN;

            for (int index = 0;
                 index < row.length;
                 index++) {

                float width = widths[index];

                rect(
                        x,
                        y - height,
                        width,
                        height,
                        fill,
                        BORDER
                );

                PDFont font =
                        index == 0
                                ? boldFont
                                : regularFont;

                wrapped(
                        row[index],
                        x + 10,
                        y - 18,
                        width - 20,
                        font,
                        10,
                        TEXT,
                        14
                );

                x += width;
            }

            y -= height;
        }

        /*
         * Small gap after complete table.
         */
        y -= BLOCK_SPACE;
    }

    // ============================================================
    // TABLE WITH HEADER
    // ============================================================

    protected void tableWithHeader(
            String[] headers,
            List<String[]> rows,
            float[] widths,
            Color fill) throws Exception {

        if (headers != null) {

            table(
                    new String[][]{
                            headers
                    },
                    widths,
                    fill
            );
        }

        if (rows != null) {

            for (String[] row : rows) {

                table(
                        new String[][]{
                                row
                        },
                        widths,
                        Color.WHITE
                );
            }
        }
    }

    // ============================================================
    // TABLE ROW HEIGHT
    // ============================================================

    protected float rowHeight(
            String[] row,
            float[] widths,
            PDFont font,
            float size) throws Exception {

        float height = 30;

        for (int index = 0;
             index < row.length;
             index++) {

            String value =
                    row[index] == null
                            ? ""
                            : row[index];

            float textHeight =
                    wrappedHeight(
                            value,
                            widths[index] - 20,
                            font,
                            size,
                            14
                    );

            height =
                    Math.max(
                            height,
                            textHeight + 20
                    );
        }

        return height;
    }

    // ============================================================
    // WRAPPED TEXT
    // ============================================================

    protected float wrapped(
            String value,
            float x,
            float top,
            float width,
            PDFont font,
            float size,
            Color color,
            float lineHeight) throws Exception {

        List<String> lines =
                wrap(
                        value,
                        font,
                        size,
                        width
                );

        for (String line : lines) {

            text(
                    line,
                    x,
                    top,
                    font,
                    size,
                    color
            );

            top -= lineHeight;
        }

        return lines.size() * lineHeight;
    }

    // ============================================================
    // WRAPPED HEIGHT
    // ============================================================

    protected float wrappedHeight(
            String value,
            float width,
            PDFont font,
            float size,
            float lineHeight) throws Exception {

        return wrap(
                value,
                font,
                size,
                width
        ).size() * lineHeight;
    }

    // ============================================================
    // TEXT WRAPPING
    // ============================================================

    protected List<String> wrap(
            String value,
            PDFont font,
            float size,
            float width) throws Exception {

        List<String> lines =
                new ArrayList<>();

        String safeValue =
                value == null
                        ? ""
                        : value;

        for (String paragraph :
                safeValue.split("\\R", -1)) {

            String remaining = paragraph;

            if (remaining.isEmpty()) {

                lines.add("");

                continue;
            }

            while (!remaining.isEmpty()) {

                int split =
                        remaining.length();

                /*
                 * Reduce the string until it fits
                 * within the available width.
                 */
                while (
                        split > 0
                                && font.getStringWidth(
                                        remaining.substring(
                                                0,
                                                split
                                        )
                                ) / 1000 * size > width
                ) {

                    split--;
                }

                /*
                 * Prefer breaking at a space.
                 */
                if (split < remaining.length()) {

                    int space =
                            remaining.lastIndexOf(
                                    ' ',
                                    split
                            );

                    if (space > 0) {
                        split = space;
                    }
                }

                /*
                 * Protect against an infinite loop
                 * for extremely long words.
                 */
                if (split == 0) {
                    split = 1;
                }

                String line =
                        remaining
                                .substring(0, split)
                                .trim();

                lines.add(line);

                remaining =
                        remaining
                                .substring(split)
                                .trim();
            }
        }

        return lines.isEmpty()
                ? List.of("")
                : lines;
    }

    // ============================================================
    // NEW PAGE
    // ============================================================

    protected void newPage(boolean withHeader) throws Exception {

        /*
         * Close the previous page first.
         */
        finishPage();

        PDPage page =
                new PDPage(
                        PDRectangle.A4
                );

        document.addPage(page);

        stream =
                new PDPageContentStream(
                        document,
                        page
                );

        /*
         * Start content below header.
         */
        y = START_Y;

        if (withHeader) {

            /*
             * Header bar.
             */
            rect(
                    0,
                    PAGE_HEIGHT - 23,
                    PAGE_WIDTH,
                    23,
                    NAVY,
                    NAVY
            );

            /*
             * Slight additional gap after header.
             */
            y = START_Y;
        }
    }

    // ============================================================
    // ENSURE SPACE
    // ============================================================

    protected void ensureSpace(float height) throws Exception {

        if (stream == null) {
            return;
        }

        /*
         * Do not allow content to enter the footer area.
         */
        if (y - height < BOTTOM_MARGIN) {

            newPage(true);
        }
    }

    // ============================================================
    // FINISH PAGE
    // ============================================================

    protected void finishPage() throws Exception {

        if (stream == null) {
            return;
        }

        /*
         * Footer left.
         */
        text(
                applicationName
                        + " | Functional Documentation",
                MARGIN,
                28,
                regularFont,
                8,
                MUTED
        );

        /*
         * Footer page number.
         */
        String pageNumber =
                Integer.toString(
                        document.getNumberOfPages()
                );

        text(
                pageNumber,
                CONTENT_RIGHT - 8,
                28,
                regularFont,
                8,
                MUTED
        );

        stream.close();

        stream = null;
    }

    // ============================================================
    // CLOSE
    // ============================================================

    protected void close() throws Exception {

        finishPage();
    }

    // ============================================================
    // DRAW TEXT
    // ============================================================

    protected void text(
            String value,
            float x,
            float baseline,
            PDFont font,
            float size,
            Color color) throws Exception {

        stream.beginText();

        stream.setFont(
                font,
                size
        );

        stream.setNonStrokingColor(
                color
        );

        stream.newLineAtOffset(
                x,
                baseline
        );

        stream.showText(
                sanitize(value)
        );

        stream.endText();
    }

    // ============================================================
    // CENTERED TEXT
    // ============================================================

    protected void centered(
            String value,
            float centerX,
            float baseline,
            PDFont font,
            float size,
            Color color) throws Exception {

        String safeValue =
                sanitize(value);

        float width =
                font.getStringWidth(
                        safeValue
                ) / 1000 * size;

        text(
                safeValue,
                centerX - width / 2,
                baseline,
                font,
                size,
                color
        );
    }

    // ============================================================
    // RECTANGLE
    // ============================================================

    protected void rect(
            float x,
            float bottom,
            float width,
            float height,
            Color fill,
            Color border) throws Exception {

        /*
         * Fill.
         */
        stream.setNonStrokingColor(
                fill
        );

        stream.addRect(
                x,
                bottom,
                width,
                height
        );

        stream.fill();

        /*
         * Border.
         */
        stream.setStrokingColor(
                border
        );

        stream.setLineWidth(
                0.7f
        );

        stream.addRect(
                x,
                bottom,
                width,
                height
        );

        stream.stroke();
    }

    // ============================================================
    // LINE
    // ============================================================

    protected void line(
            float x1,
            float y1,
            float x2,
            float y2,
            Color color,
            float width) throws Exception {

        stream.setStrokingColor(
                color
        );

        stream.setLineWidth(
                width
        );

        stream.moveTo(
                x1,
                y1
        );

        stream.lineTo(
                x2,
                y2
        );

        stream.stroke();
    }

    // ============================================================
    // FIRST TEXT
    // ============================================================

    protected static String firstText(
            JsonNode array) {

        return array != null
                && array.isArray()
                && !array.isEmpty()
                ? array.get(0).asText()
                : "";
    }

    // ============================================================
    // JSON VALUE
    // ============================================================

    protected static String value(
            JsonNode node,
            String field) {

        JsonNode value =
                node == null
                        ? null
                        : node.get(field);

        if (value == null || value.isNull()) {
            return "";
        }

        return value.isValueNode()
                ? value.asText()
                : value.toString();
    }

    // ============================================================
    // SANITIZE
    // ============================================================

    protected static String sanitize(
            String value) {

        return value == null
                ? ""
                : value
                        .replace("→", "->")
                        .replaceAll(
                                "[^\\x00-\\x7F]",
                                "?"
                        );
    }
}
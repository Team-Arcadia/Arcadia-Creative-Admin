/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.gui.kit;

/**
 * An immutable screen rectangle in GUI pixels. Layout code slices rectangles instead of adding
 * offsets by hand, and hit tests ask a rectangle instead of repeating four comparisons.
 * Width and height are never negative: slicing more than a rectangle holds yields an empty one.
 *
 * @author THEFricadelle
 */
public record Rect(int x, int y, int w, int h) {

    public Rect {
        w = Math.max(0, w);
        h = Math.max(0, h);
    }

    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public int centerY() {
        return y + h / 2;
    }

    public boolean isEmpty() {
        return w == 0 || h == 0;
    }

    /** Half-open test, so two adjacent rectangles never both claim the pixel on their shared edge. */
    public boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** Shrinks every side by {@code d}. */
    public Rect inset(int d) {
        return inset(d, d);
    }

    public Rect inset(int dx, int dy) {
        return new Rect(x + dx, y + dy, w - 2 * dx, h - 2 * dy);
    }

    /** The top {@code height} pixels. */
    public Rect top(int height) {
        return new Rect(x, y, w, Math.min(h, height));
    }

    /** The bottom {@code height} pixels. */
    public Rect bottom(int height) {
        int hh = Math.min(h, height);
        return new Rect(x, y + h - hh, w, hh);
    }

    /** The left {@code width} pixels. */
    public Rect left(int width) {
        return new Rect(x, y, Math.min(w, width), h);
    }

    /** The right {@code width} pixels. */
    public Rect right(int width) {
        int ww = Math.min(w, width);
        return new Rect(x + w - ww, y, ww, h);
    }

    /** What remains after removing the top {@code height} pixels. */
    public Rect belowTop(int height) {
        int hh = Math.min(h, height);
        return new Rect(x, y + hh, w, h - hh);
    }

    /** What remains after removing the bottom {@code height} pixels. */
    public Rect aboveBottom(int height) {
        return new Rect(x, y, w, h - Math.min(h, height));
    }

    /** What remains after removing the left {@code width} pixels. */
    public Rect afterLeft(int width) {
        int ww = Math.min(w, width);
        return new Rect(x + ww, y, w - ww, h);
    }

    /** What remains after removing the right {@code width} pixels. */
    public Rect beforeRight(int width) {
        return new Rect(x, y, w - Math.min(w, width), h);
    }

    /**
     * A row of boxes {@code gap} apart: the first takes what the others leave, the others their {@code widths}.
     * When that would leave the first narrower than an equal share, every box gets an equal share instead, so
     * no box of a narrow row shrinks to nothing or overlaps its neighbour.
     */
    public Rect[] split(int gap, int... widths) {
        int count = widths.length + 1;
        int free = w - gap * widths.length;
        int fixed = 0;
        for (int width : widths) fixed += width;
        boolean shared = free - fixed < free / count;
        Rect[] boxes = new Rect[count];
        int left = x;
        for (int i = 0; i < count; i++) {
            int width = shared ? free / count : i == 0 ? free - fixed : widths[i - 1];
            if (i == count - 1) width = x + w - left;
            boxes[i] = new Rect(left, y, width, h);
            left += width + gap;
        }
        return boxes;
    }

    /** A rectangle of the given size centred in this one. */
    public Rect centered(int width, int height) {
        int ww = Math.min(w, width);
        int hh = Math.min(h, height);
        return new Rect(x + (w - ww) / 2, y + (h - hh) / 2, ww, hh);
    }
}

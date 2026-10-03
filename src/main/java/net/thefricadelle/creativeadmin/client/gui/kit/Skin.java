/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.gui.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * How every interface element looks. Screens and widgets describe what to draw (a button, a
 * frame, a row) and this class decides the pixels, so the visual identity lives in two files:
 * this one and {@link Palette}. Element sizes come from {@link Atlas}.
 *
 * <p>Everything is drawn with flat fills, and text without shadow, which reads cleanly on the
 * dark surfaces at any GUI scale.
 *
 * @author THEFricadelle
 */
public final class Skin {

    /** Button and badge styles. {@code row} is the variant's row in the atlas button block. */
    public enum Variant {
        NEUTRAL(0, Palette.BG3, Palette.TEXT),
        ACCENT(1, Palette.ACCENT, Palette.ON_COLOR),
        GOOD(2, Palette.GOOD, Palette.ON_COLOR),
        DANGER(3, Palette.DANGER, Palette.ON_COLOR),
        /** No surface until hovered: toolbar and navigation buttons. */
        GHOST(4, 0x00000000, Palette.TEXT_DIM);

        public final int row;
        public final int fill;
        public final int text;

        Variant(int row, int fill, int text) {
            this.row = row;
            this.fill = fill;
            this.text = text;
        }
    }

    private static final String ELLIPSIS = "...";

    private Skin() {
    }

    // ------------------------------------------------------------------ surfaces

    /** Top-level window: darkest surface, strong outline, accent stripe along the top edge. */
    public static void window(GuiGraphics g, Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), Palette.BG0);
        outline(g, r, Palette.LINE_STRONG);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, Palette.ACCENT);
    }

    /** A raised block inside a window: a card, the sidebar, a details pane. */
    public static void panel(GuiGraphics g, Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), Palette.BG1);
        outline(g, r, Palette.LINE);
    }

    /** Window title bar, separated from the content by a single line. */
    public static void header(GuiGraphics g, Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), Palette.BG1);
        g.hLine(r.x(), r.right() - 1, r.bottom() - 1, Palette.LINE);
    }

    /** Status bar at the bottom of a window. */
    public static void footer(GuiGraphics g, Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), Palette.BG1);
        g.hLine(r.x(), r.right() - 1, r.y(), Palette.LINE);
    }

    public static void hDivider(GuiGraphics g, int x1, int x2, int y) {
        g.hLine(x1, x2 - 1, y, Palette.LINE);
    }

    public static void vDivider(GuiGraphics g, int x, int y1, int y2) {
        g.vLine(x, y1 - 1, y2, Palette.LINE);
    }

    /** One-pixel border drawn inside {@code r}. */
    public static void outline(GuiGraphics g, Rect r, int color) {
        if (r.isEmpty()) return;
        g.renderOutline(r.x(), r.y(), r.w(), r.h(), color);
    }

    /** Keyboard focus indicator, drawn one pixel outside the element so it never covers content. */
    public static void focusRing(GuiGraphics g, Rect r) {
        g.renderOutline(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, Palette.ACCENT_HI);
    }

    // ------------------------------------------------------------------ controls

    public static void button(GuiGraphics g, Rect r, Variant variant, boolean hovered, boolean active) {
        int fill;
        int border;
        if (!active) {
            fill = variant == Variant.GHOST ? 0 : Palette.mix(variant.fill, Palette.BG2, 0.65f);
            border = variant == Variant.GHOST ? 0 : Palette.LINE;
        } else if (variant == Variant.GHOST) {
            fill = hovered ? Palette.BG3 : 0;
            border = 0;
        } else {
            fill = hovered ? Palette.tint(variant.fill, 0.12f) : variant.fill;
            border = variant == Variant.NEUTRAL
                    ? (hovered ? Palette.LINE_STRONG : Palette.LINE)
                    : Palette.tint(variant.fill, -0.25f);
        }
        if (fill != 0) g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        if (border != 0) outline(g, r, border);
    }

    /** Text colour of a button label for its state. */
    public static int buttonText(Variant variant, boolean hovered, boolean active) {
        if (!active) return Palette.TEXT_MUTE;
        if (variant == Variant.GHOST && hovered) return Palette.TEXT;
        return variant.text;
    }

    public static void input(GuiGraphics g, Rect r, boolean focused, boolean active) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), active ? Palette.BG2 : Palette.BG1);
        outline(g, r, focused ? Palette.ACCENT : Palette.LINE);
    }

    /**
     * Vertical scrollbar.
     *
     * @param track  full height available to the thumb
     * @param thumbY top of the thumb, absolute
     * @param thumbH thumb height
     */
    public static void scrollbar(GuiGraphics g, Rect track, int thumbY, int thumbH, boolean hovered) {
        g.fill(track.x(), track.y(), track.right(), track.bottom(), Palette.BG2);
        int thumb = hovered ? Palette.TEXT_MUTE : Palette.LINE_STRONG;
        g.fill(track.x() + 1, thumbY, track.right() - 1, thumbY + thumbH, thumb);
    }

    /** Row of a list: zebra striping, hover and selection. */
    public static void row(GuiGraphics g, Rect r, int index, boolean hovered, boolean selected) {
        int fill;
        if (selected) {
            fill = Palette.mix(Palette.BG2, Palette.ACCENT, 0.22f);
        } else if (hovered) {
            fill = Palette.BG3;
        } else {
            fill = index % 2 == 0 ? Palette.BG1 : Palette.BG2;
        }
        g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        if (selected) g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), Palette.ACCENT);
    }

    /**
     * A row picked up by a drag: lifted above its neighbours with an accent edge, so the row under
     * the pointer is unmistakable while the others move out of its way.
     */
    public static void liftedRow(GuiGraphics g, Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), Palette.mix(Palette.BG3, Palette.ACCENT, 0.18f));
        outline(g, r, Palette.ACCENT);
    }

    /** Small status dot, vertically centred on {@code centerY}. */
    public static void dot(GuiGraphics g, int x, int centerY, int color) {
        int s = Atlas.DOT_SIZE;
        int y = centerY - s / 2;
        // Clipped corners give a round impression at this size.
        g.fill(x + 1, y, x + s - 1, y + s, color);
        g.fill(x, y + 1, x + s, y + s - 1, color);
    }

    /**
     * Paints an icon at its native size, merging each row's lit pixels into horizontal runs.
     * <p>
     * The runs go straight into the GUI buffer and are drawn with one flush at the end.
     * {@link GuiGraphics#fill} flushes after every quad, which made each run its own draw call;
     * the same kit in Better Creative measured a few milliseconds per frame for a list
     * of rows with icons (spark, Arcadia pack).
     */
    public static void icon(GuiGraphics g, Icon icon, int x, int y, int color) {
        int size = Atlas.ICON_SIZE;
        Matrix4f pose = g.pose().last().pose();
        VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
        for (int row = 0; row < size; row++) {
            int start = -1;
            for (int col = 0; col <= size; col++) {
                boolean lit = col < size && icon.lit(col, row);
                if (lit && start < 0) {
                    start = col;
                } else if (!lit && start >= 0) {
                    float x0 = x + start;
                    float x1 = x + col;
                    float y0 = y + row;
                    float y1 = y + row + 1;
                    // Same winding and depth as GuiGraphics.fill.
                    buffer.addVertex(pose, x0, y0, 0).setColor(color);
                    buffer.addVertex(pose, x0, y1, 0).setColor(color);
                    buffer.addVertex(pose, x1, y1, 0).setColor(color);
                    buffer.addVertex(pose, x1, y0, 0).setColor(color);
                    start = -1;
                }
            }
        }
        g.flush();
    }

    /**
     * A pill label such as {@code ALLOW} or {@code DISABLED}: tinted surface, coloured text.
     *
     * @return the badge width, so callers can lay out what follows
     */
    public static int badge(GuiGraphics g, Font font, String text, int x, int centerY, int color) {
        int w = font.width(text) + 6;
        int h = 11;
        int y = centerY - h / 2;
        g.fill(x, y, x + w, y + h, Palette.mix(Palette.BG1, color, 0.22f));
        outline(g, new Rect(x, y, w, h), Palette.mix(Palette.BG1, color, 0.55f));
        g.drawString(font, text, x + 3, y + 2, color, false);
        return w;
    }

    // ------------------------------------------------------------------ text

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /** Draws text cut to {@code maxWidth}, ending with an ellipsis when it had to be cut. */
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        g.drawString(font, ellipsize(font, text, maxWidth), x, y, color, false);
    }

    /** Text vertically centred in {@code r}, left-aligned with {@code padding}, cut to fit. */
    public static void textIn(GuiGraphics g, Font font, String text, Rect r, int padding, int color) {
        text(g, font, text, r.x() + padding, r.y() + (r.h() - 8) / 2, r.w() - 2 * padding, color);
    }

    /** Section title above a group of controls: muted, upper case, cut to fit. */
    public static void caption(GuiGraphics g, Font font, String text, int x, int y, int maxWidth) {
        text(g, font, text.toUpperCase(Locale.ROOT), x, y, maxWidth, Palette.TEXT_MUTE);
    }

    public static void centeredText(GuiGraphics g, Font font, String text, Rect r, int color) {
        String cut = ellipsize(font, text, r.w() - 4);
        g.drawString(font, cut, r.x() + (r.w() - font.width(cut)) / 2, r.y() + (r.h() - 8) / 2, color, false);
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        int room = maxWidth - font.width(ELLIPSIS);
        if (room <= 0) return "";
        return font.plainSubstrByWidth(text, room) + ELLIPSIS;
    }
}

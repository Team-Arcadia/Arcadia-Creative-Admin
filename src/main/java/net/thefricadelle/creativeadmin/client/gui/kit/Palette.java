/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.gui.kit;

/**
 * Every colour the interface uses, named by role rather than by hue, as {@code 0xAARRGGBB}.
 * Screens and widgets never write a colour literal: a change of identity is a change of this file.
 *
 * <p>Identity: dark slate surfaces with an indigo accent. Backgrounds go from {@link #BG0} (window)
 * to {@link #BG3} (inputs, hovered rows), so a surface one step lighter always reads as "on top".
 * Status colours are reserved for meaning (allowed, warning, destructive) and never used as
 * decoration.
 *
 * <p>The identity is shared with the other Arcadia mods, so their screens read as one family.
 *
 * <p>Pure Java, no Minecraft type, so the colour arithmetic is unit-tested.
 *
 * @author THEFricadelle
 */
public final class Palette {

    /** Dims the game behind an open screen. */
    public static final int SCRIM = 0xB0080A0F;
    /** Dims the screen behind a confirmation dialog. */
    public static final int MODAL_SCRIM = 0xA0000000;

    public static final int BG0 = 0xF2111419;
    public static final int BG1 = 0xFF171B22;
    public static final int BG2 = 0xFF1E232C;
    public static final int BG3 = 0xFF272D38;

    public static final int LINE = 0xFF2E3541;
    public static final int LINE_STRONG = 0xFF414A59;

    public static final int TEXT = 0xFFE7EAF0;
    public static final int TEXT_DIM = 0xFFAAB1BF;
    public static final int TEXT_MUTE = 0xFF6D7587;

    public static final int ACCENT = 0xFF7C6CF0;
    public static final int ACCENT_HI = 0xFFA497FF;
    public static final int ACCENT_LO = 0xFF4E43A8;

    public static final int GOOD = 0xFF4FB47E;
    public static final int WARN = 0xFFE0A93E;
    public static final int DANGER = 0xFFE05A5A;
    public static final int INFO = 0xFF5C9CD6;

    /** Text drawn on a filled accent, good or danger surface. */
    public static final int ON_COLOR = 0xFFFFFFFF;

    private Palette() {
    }

    /**
     * Lightens ({@code amount > 0}) or darkens ({@code amount < 0}) a colour, keeping its alpha.
     * Used for hover and pressed states, so every variant derives them the same way.
     *
     * @param amount from -1 (black) to 1 (white); values outside are clamped
     */
    public static int tint(int argb, float amount) {
        float t = Math.max(-1f, Math.min(1f, amount));
        int target = t >= 0 ? 0xFFFFFFFF : 0xFF000000;
        return (argb & 0xFF000000) | (mix(argb, target, Math.abs(t)) & 0x00FFFFFF);
    }

    /** Linear blend of two colours, alpha included; {@code t} is clamped to [0, 1]. */
    public static int mix(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = channel(from, 24, to, k);
        int r = channel(from, 16, to, k);
        int g = channel(from, 8, to, k);
        int b = channel(from, 0, to, k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Same colour with its alpha replaced, {@code alpha} clamped to [0, 255]. */
    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    private static int channel(int from, int shift, int to, float k) {
        int a = (from >>> shift) & 0xFF;
        int b = (to >>> shift) & 0xFF;
        return Math.round(a + (b - a) * k);
    }
}

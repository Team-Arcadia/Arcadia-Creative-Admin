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
 * The single place that knows the geometry of every interface element: its size, and where it
 * sits in the interface texture atlas.
 *
 * <p>The interface is drawn with flat fills today ({@link Skin}), and the atlas image does not
 * exist yet. The coordinates are still fixed here, not in the screens, so that drawing an atlas
 * later means painting {@link #TEXTURE} to this layout and switching {@link Skin} from fills to
 * blits: no screen and no widget changes. Sizes below are read by {@link Skin} and the widgets
 * now, so the flat look and a future texture share one set of dimensions.
 *
 * <p>Atlas layout (256x256):
 * <pre>
 *   y=0    FRAME 24x24 | HEADER 24x20
 *   y=24   BUTTON rows, one per {@link Skin.Variant}, columns normal | hover | disabled (40x20 each)
 *   y=24   x=120 SCROLL_TRACK 6x32, x=126 SCROLL_THUMB 6x32, x=132 DOT 6x6
 *   y=124  INPUT normal | focused (40x16 each)
 *   y=144  ICON sheet, 16 cells per row, {@link #ICON_SIZE} square, in {@link Icon} declaration order
 * </pre>
 *
 * @author THEFricadelle
 */
public final class Atlas {

    /** Resource path the atlas image will be read from once it exists. */
    public static final String TEXTURE = "creativeadmin:textures/gui/kit.png";
    public static final int TEXTURE_SIZE = 256;

    /**
     * One region of the atlas.
     *
     * @param slice nine-slice border in pixels: corners of this size are drawn unscaled
     */
    public record Region(int u, int v, int w, int h, int slice) {

        /** The same region shifted by whole cells, for state columns and variant rows. */
        public Region offset(int columns, int rows) {
            return new Region(u + columns * w, v + rows * h, w, h, slice);
        }
    }

    public static final Region FRAME = new Region(0, 0, 24, 24, 4);
    public static final Region HEADER = new Region(24, 0, 24, 20, 2);

    /** Neutral button, normal state. Other variants are rows below, other states columns right. */
    public static final Region BUTTON = new Region(0, 24, 40, 20, 3);
    public static final int BUTTON_STATE_NORMAL = 0;
    public static final int BUTTON_STATE_HOVER = 1;
    public static final int BUTTON_STATE_DISABLED = 2;
    /** Rows in the button block, one per {@link Skin.Variant}. */
    public static final int BUTTON_VARIANTS = 5;

    public static final Region SCROLL_TRACK = new Region(120, 24, 6, 32, 2);
    public static final Region SCROLL_THUMB = new Region(126, 24, 6, 32, 2);
    public static final Region DOT = new Region(132, 24, 6, 6, 0);

    /** Text input, normal state; the focused state is the next column. */
    public static final Region INPUT = new Region(0, 124, 40, 16, 2);

    public static final int ICON_SHEET_V = 144;
    public static final int ICON_SIZE = 8;
    public static final int ICONS_PER_ROW = 16;

    /** Default heights, shared by the flat skin and the atlas. */
    public static final int BUTTON_HEIGHT = BUTTON.h();
    public static final int INPUT_HEIGHT = INPUT.h();
    public static final int SCROLLBAR_WIDTH = SCROLL_TRACK.w();
    public static final int DOT_SIZE = DOT.w();

    private Atlas() {
    }

    /** Button region for a variant row and a state column. */
    public static Region button(int variantRow, int stateColumn) {
        return BUTTON.offset(stateColumn, variantRow);
    }

    /** Atlas cell of an icon, by its position in the icon sheet. */
    public static Region icon(int index) {
        return new Region((index % ICONS_PER_ROW) * ICON_SIZE,
                ICON_SHEET_V + (index / ICONS_PER_ROW) * ICON_SIZE, ICON_SIZE, ICON_SIZE, 0);
    }
}

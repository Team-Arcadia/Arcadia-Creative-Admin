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
 * The named regions of a window, computed once per resize. Screens place widgets in these
 * regions instead of computing offsets from the screen size, so every screen has the same header,
 * sidebar and footer at the same place, and a change of proportions is made here once.
 *
 * <pre>
 *   +--------------------------------------------+
 *   | header                                     |
 *   +----------+---------------------------------+
 *   | sidebar  | content                         |
 *   |          |                                 |
 *   +----------+---------------------------------+
 *   | footer                                     |
 *   +--------------------------------------------+
 * </pre>
 *
 * @param window  outer frame, centred and capped to {@link #MAX_WIDTH} x {@link #MAX_HEIGHT}
 * @param header  title bar
 * @param sidebar navigation column, empty when the screen has none
 * @param content page area, already padded
 * @param footer  status bar
 *
 * @author THEFricadelle
 */
public record WindowLayout(Rect window, Rect header, Rect sidebar, Rect content, Rect footer) {

    public static final int MAX_WIDTH = 560;
    public static final int MAX_HEIGHT = 340;
    public static final int MARGIN = 6;
    public static final int HEADER_HEIGHT = 22;
    public static final int FOOTER_HEIGHT = 16;
    public static final int SIDEBAR_WIDTH = 128;
    public static final int CONTENT_PADDING = 6;

    /** Below this window width the sidebar is dropped rather than squeezing the content to nothing. */
    public static final int MIN_WIDTH_FOR_SIDEBAR = 300;

    public static WindowLayout of(int screenWidth, int screenHeight, boolean withSidebar) {
        int w = Math.min(MAX_WIDTH, screenWidth - 2 * MARGIN);
        int h = Math.min(MAX_HEIGHT, screenHeight - 2 * MARGIN);
        Rect window = new Rect(0, 0, screenWidth, screenHeight).centered(w, h);

        // One-pixel frame around everything, then the fixed bars.
        Rect inner = window.inset(1);
        Rect header = inner.top(HEADER_HEIGHT);
        Rect footer = inner.bottom(FOOTER_HEIGHT);
        Rect body = inner.belowTop(HEADER_HEIGHT).aboveBottom(FOOTER_HEIGHT);

        boolean sidebar = withSidebar && window.w() >= MIN_WIDTH_FOR_SIDEBAR;
        Rect side = sidebar ? body.left(SIDEBAR_WIDTH) : new Rect(body.x(), body.y(), 0, body.h());
        Rect content = body.afterLeft(side.w()).inset(CONTENT_PADDING);
        return new WindowLayout(window, header, side, content, footer);
    }

    public boolean hasSidebar() {
        return !sidebar.isEmpty();
    }
}

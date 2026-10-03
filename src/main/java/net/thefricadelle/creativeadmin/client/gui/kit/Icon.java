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
 * Interface icons as {@link Atlas#ICON_SIZE}-pixel square bitmaps, one string per row, {@code #}
 * for a lit pixel. {@link Skin#icon} paints them pixel by pixel in any colour, which keeps icons
 * crisp at every GUI scale without a texture, and lets one icon take the colour of its context
 * (muted in a list, white on an accent button, red on a destructive one).
 *
 * <p>Declaration order is the icon's cell in the future atlas sheet ({@link Atlas#icon(int)}):
 * append new icons, never reorder.
 *
 * @author THEFricadelle
 */
public enum Icon {

    TAB(
            "........",
            ".###....",
            "#...####",
            "#......#",
            "#......#",
            "#......#",
            "########",
            "........"),
    STAR(
            "...##...",
            "...##...",
            "########",
            ".######.",
            "..####..",
            "..####..",
            ".##..##.",
            "##....##"),
    EYE(
            "........",
            "..####..",
            ".#....#.",
            "#..##..#",
            "#..##..#",
            ".#....#.",
            "..####..",
            "........"),
    EYE_OFF(
            "#.......",
            ".#####..",
            ".##...#.",
            "#..#...#",
            "#...#..#",
            ".#...##.",
            "..#####.",
            ".......#"),
    UP(
            "...##...",
            "..####..",
            ".######.",
            "...##...",
            "...##...",
            "...##...",
            "...##...",
            "........"),
    DOWN(
            "........",
            "...##...",
            "...##...",
            "...##...",
            "...##...",
            ".######.",
            "..####..",
            "...##..."),
    GRIP(
            "........",
            "..#..#..",
            "........",
            "..#..#..",
            "........",
            "..#..#..",
            "........",
            "........"),
    SEARCH(
            ".####...",
            "#....#..",
            "#....#..",
            "#....#..",
            ".####...",
            "....##..",
            ".....##.",
            "......##"),
    SORT(
            ".#......",
            "###.....",
            ".#...#..",
            ".#...#..",
            ".#...#..",
            ".#...#..",
            "....###.",
            ".....#.."),
    PRESETS(
            "..####..",
            ".#....#.",
            "..####..",
            ".#....#.",
            "..####..",
            ".#....#.",
            "..####..",
            "........"),
    HELP(
            "..####..",
            ".#....#.",
            "......#.",
            ".....#..",
            "...##...",
            "...##...",
            "........",
            "...##..."),
    REFRESH(
            "..####.#",
            ".#....##",
            "#....###",
            "#.......",
            "#......#",
            "#......#",
            ".#....#.",
            "..####.."),
    TRASH(
            "..####..",
            "########",
            ".######.",
            ".#.##.#.",
            ".#.##.#.",
            ".#.##.#.",
            ".#.##.#.",
            ".######."),
    PLUS(
            "........",
            "...##...",
            "...##...",
            ".######.",
            ".######.",
            "...##...",
            "...##...",
            "........"),
    CHECK(
            "........",
            ".......#",
            "......#.",
            "#....#..",
            ".#..#...",
            "..##....",
            "........",
            "........"),
    CROSS(
            "........",
            ".#....#.",
            "..#..#..",
            "...##...",
            "...##...",
            "..#..#..",
            ".#....#.",
            "........"),
    BACK(
            "........",
            "...#....",
            "..##....",
            ".#######",
            ".#######",
            "..##....",
            "...#....",
            "........"),
    WARN(
            "...##...",
            "...##...",
            "..#..#..",
            "..#..#..",
            ".#.##.#.",
            ".#....#.",
            "#..##..#",
            "########"),
    INFO(
            "...##...",
            "...##...",
            "........",
            "..###...",
            "...##...",
            "...##...",
            "...##...",
            "..####.."),
    LOCK(
            "..####..",
            ".#....#.",
            ".#....#.",
            "########",
            "###..###",
            "###..###",
            "########",
            "########"),
    /** Unticked box, the "off" state of a toggle. */
    BOX(
            "########",
            "#......#",
            "#......#",
            "#......#",
            "#......#",
            "#......#",
            "#......#",
            "########"),
    /** Ticked box, the "on" state of a toggle. */
    BOX_CHECKED(
            "########",
            "#......#",
            "#.....##",
            "##...#.#",
            "#.#.#..#",
            "#..#...#",
            "#......#",
            "########");

    private final long bits;

    Icon(String... rows) {
        if (rows.length != Atlas.ICON_SIZE) {
            throw new IllegalArgumentException(name() + ": expected " + Atlas.ICON_SIZE + " rows");
        }
        long b = 0L;
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            if (row.length() != Atlas.ICON_SIZE || !row.matches("[#.]+")) {
                throw new IllegalArgumentException(name() + ": malformed row " + y + " '" + row + "'");
            }
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == '#') b |= 1L << (y * Atlas.ICON_SIZE + x);
            }
        }
        this.bits = b;
    }

    /** Whether the pixel at column {@code x}, row {@code y} is lit. */
    public boolean lit(int x, int y) {
        if (x < 0 || y < 0 || x >= Atlas.ICON_SIZE || y >= Atlas.ICON_SIZE) return false;
        return (bits >>> (y * Atlas.ICON_SIZE + x) & 1L) != 0L;
    }

    /** The raw bitmap, row-major, bit 0 = top-left pixel. */
    public long bits() {
        return bits;
    }
}

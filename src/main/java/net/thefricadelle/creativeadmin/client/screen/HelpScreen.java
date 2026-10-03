/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.thefricadelle.creativeadmin.client.gui.kit.CaList;
import net.thefricadelle.creativeadmin.client.gui.kit.Atlas;
import net.thefricadelle.creativeadmin.client.gui.kit.Icon;
import net.thefricadelle.creativeadmin.client.gui.kit.Palette;
import net.thefricadelle.creativeadmin.client.gui.kit.Rect;
import net.thefricadelle.creativeadmin.client.gui.kit.Skin;
import net.thefricadelle.creativeadmin.network.AdminPayloads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only explanation of the admin screen and of the rules it writes: who is restricted, what is
 * checked, the files, the commands and what the mod deliberately leaves alone.
 * <p>
 * Topics are listed in the sidebar, each with an icon; the text is one continuous document on the
 * right, and picking a topic scrolls it to that heading. The text is wrapped against the current
 * width on every {@code init()}, so it reflows instead of overflowing at small GUI scales. Nothing
 * here needs the server: the page opens even while the session is read-only.
 *
 * @author THEFricadelle
 */
final class HelpScreen extends AdminPage {

    /** Each id resolves to a {@code .heading} and a {@code .body} translation key. */
    private static final List<Topic> TOPICS = List.of(
            new Topic("overview", Icon.HELP),
            new Topic("profiles", Icon.STAR),
            new Topic("items", Icon.TAB),
            new Topic("advanced", Icon.PRESETS),
            new Topic("server", Icon.BOX_CHECKED),
            new Topic("players", Icon.EYE),
            new Topic("groups", Icon.LOCK),
            new Topic("saving", Icon.CHECK),
            new Topic("files", Icon.WARN),
            new Topic("commands", Icon.SORT),
            new Topic("companion", Icon.EYE_OFF),
            new Topic("limits", Icon.INFO));

    private static final String KEY = "creativeadmin.help.";
    /** Room for a heading on two lines: the side list is narrow and French headings are long. */
    private static final int TOPIC_ROW = 22;
    private static final int TOPIC_LINE = 9;
    private static final int TOPIC_MAX_LINES = 2;
    private static final int LINE_HEIGHT = 11;
    private static final int TEXT_PADDING = 6;

    private record Topic(String id, Icon icon) {

        String heading() {
            return Component.translatable(KEY + id + ".heading").getString();
        }
    }

    /** One visual line of the document; {@code topic} is set on the first line of a heading. */
    private record Line(FormattedCharSequence text, String plain, boolean heading, Topic topic) {
    }

    private final CaList<Topic> topics;
    private final CaList<Line> body;
    private final Map<Topic, Integer> headingRows = new HashMap<>();
    /** Wrapped side-list headings, kept while the row width stays the same so a frame splits nothing. */
    private final Map<Topic, List<FormattedCharSequence>> topicLines = new HashMap<>();
    private int topicWidth = -1;

    HelpScreen(Screen parent) {
        super(Component.translatable(KEY + "title"), parent);
        this.topics = new CaList<Topic>(Component.translatable(KEY + "title"), TOPIC_ROW)
                .renderer(this::renderTopic)
                .label(Topic::heading)
                .identity(Topic::id)
                .onSelect(this::scrollTo);
        this.topics.setItems(TOPICS);
        this.body = new CaList<Line>(Component.translatable(KEY + "title"), LINE_HEIGHT)
                .renderer(HelpScreen::renderLine)
                .label(Line::plain)
                .plain();
    }

    @Override
    protected boolean hasSidebar() {
        return true;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.HELP;
    }

    @Override
    protected GuiEventListener initialFocus() {
        return this.body;
    }

    @Override
    protected void build() {
        addCloseButton();
        Rect content = layout.content();
        this.topicLines.clear();
        if (layout.hasSidebar()) {
            addRenderableWidget(this.topics.at(layout.sidebar().inset(4, 6)));
        }
        addRenderableWidget(this.body.at(content));
        wrap(content.w() - 2 * TEXT_PADDING - Atlas.SCROLLBAR_WIDTH);
    }

    /** A help page has nothing to rebuild from the server, and no notice to show over its text. */
    @Override
    void onServerState(AdminPayloads.State state, boolean replaced) {
    }

    private void wrap(int width) {
        List<Line> lines = new ArrayList<>();
        this.headingRows.clear();
        for (Topic topic : TOPICS) {
            this.headingRows.put(topic, lines.size());
            Component heading = Component.translatable(KEY + topic.id() + ".heading");
            boolean first = true;
            for (FormattedCharSequence line : this.font.split(heading, width - Atlas.ICON_SIZE - 5)) {
                lines.add(new Line(line, heading.getString(), true, first ? topic : null));
                first = false;
            }
            Component text = Component.translatable(KEY + topic.id() + ".body");
            for (FormattedCharSequence line : this.font.split(text, width)) {
                lines.add(new Line(line, text.getString(), false, null));
            }
            lines.add(new Line(FormattedCharSequence.EMPTY, "", false, null));
        }
        this.body.setItems(lines);
    }

    private void scrollTo(Topic topic) {
        Integer row = this.headingRows.get(topic);
        if (row != null) {
            this.body.scrollToRow(row);
        }
    }

    private void renderTopic(GuiGraphics g, Font font, Topic topic, Rect row, int mouseX, int mouseY,
                             boolean hovered, boolean selected) {
        int color = selected || hovered ? Palette.TEXT : Palette.TEXT_DIM;
        Skin.icon(g, topic.icon(), row.x() + 6, row.y() + (row.h() - Atlas.ICON_SIZE) / 2,
                selected ? Palette.ACCENT_HI : Palette.TEXT_MUTE);
        int x = row.x() + 6 + Atlas.ICON_SIZE + 5;
        int width = row.right() - x - 4;
        if (width != this.topicWidth) {
            this.topicLines.clear();
            this.topicWidth = width;
        }
        List<FormattedCharSequence> lines = this.topicLines.get(topic);
        if (lines == null) {
            lines = font.split(Component.translatable(KEY + topic.id() + ".heading"), width);
            this.topicLines.put(topic, lines);
        }
        int shown = Math.min(TOPIC_MAX_LINES, lines.size());
        int y = row.y() + (row.h() - shown * TOPIC_LINE) / 2 + 1;
        for (int i = 0; i < shown; i++) {
            g.drawString(font, lines.get(i), x, y + i * TOPIC_LINE, color, false);
        }
    }

    private static void renderLine(GuiGraphics g, Font font, Line line, Rect row, int mouseX, int mouseY,
                                   boolean hovered, boolean selected) {
        int x = row.x() + TEXT_PADDING;
        if (line.topic() != null) {
            Skin.icon(g, line.topic().icon(), x, row.y() + 1, Palette.ACCENT_HI);
        }
        int textX = line.heading() ? x + Atlas.ICON_SIZE + 5 : x;
        g.drawString(font, line.text(), textX, row.y() + 1, line.heading() ? Palette.TEXT : Palette.TEXT_DIM, false);
    }
}

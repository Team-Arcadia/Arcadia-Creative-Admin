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
import java.util.List;

/**
 * Who is under which profile.
 * <p>
 * An assignment made here outranks the group a permission mod gives, which outranks the default
 * profile, and each row shows all three so the admin can see why a player ends up where they are.
 * Assignments are sent at once: they live in their own file and have nothing to save with the
 * profiles.
 *
 * @author THEFricadelle
 */
final class PlayersScreen extends AdminPage {

    private static final int ROW = 18;
    private static final int HELP_LINE = 10;
    private static final List<String> HELP_EXAMPLES = List.of("groups_help_2", "groups_help_3", "groups_help_4");

    private final CaList<AdminPayloads.PlayerEntry> players;
    /** The group help under the list, wrapped to the page width; the first {@link #helpIntro} lines are the intro. */
    private List<FormattedCharSequence> help = List.of();
    private int helpIntro;

    PlayersScreen(AdminScreen parent) {
        super(Component.translatable("creativeadmin.admin.players_title"), parent);
        this.players = new CaList<AdminPayloads.PlayerEntry>(text("players"), ROW)
                .identity(AdminPayloads.PlayerEntry::id)
                .emptyText(tr("no_players"))
                .renderer(this::renderRow)
                .onRowClick((entry, row, mx, my) -> {
                    cycle(entry);
                    return true;
                });
    }

    @Override
    protected Icon titleIcon() {
        return Icon.TAB;
    }

    @Override
    protected void build() {
        addCloseButton();
        AdminPayloads.State state = AdminSession.state();
        Rect content = layout.content();
        players.setItems(state == null ? List.of() : state.players());
        players.active = state != null && !state.assignmentsBroken();
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(text("groups_help_1"), content.w()));
        helpIntro = lines.size();
        for (String key : HELP_EXAMPLES) {
            lines.addAll(font.split(text(key), content.w()));
        }
        help = lines;
        addRenderableWidget(players.at(content.aboveBottom(help.size() * HELP_LINE + GAP)));
        if (state != null && state.assignmentsBroken()) {
            warn(tr("assignments_broken"));
        } else {
            info(tr("players_help"));
        }
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect content = layout.content();
        int y = content.bottom() - help.size() * HELP_LINE + 2;
        for (int i = 0; i < help.size(); i++) {
            g.drawString(font, help.get(i), content.x(), y + i * HELP_LINE, i < helpIntro ? Palette.TEXT_DIM : Palette.TEXT_MUTE, false);
        }
    }

    private void renderRow(GuiGraphics g, Font font, AdminPayloads.PlayerEntry entry, Rect row,
                           int mouseX, int mouseY, boolean hovered, boolean selected) {
        Skin.row(g, row, 0, hovered, false);
        Skin.dot(g, row.x() + 4, row.centerY(), entry.online() ? Palette.GOOD : Palette.TEXT_MUTE);
        int x = row.x() + 4 + Atlas.DOT_SIZE + 6;
        int nameWidth = Math.min(120, row.w() / 3);
        Skin.text(g, font, entry.name(), x, row.y() + (row.h() - 8) / 2, nameWidth, Palette.TEXT);
        x += nameWidth + 6;

        String effective;
        String source;
        if (!entry.assigned().isEmpty()) {
            effective = entry.assigned();
            source = tr("source.assigned");
        } else if (!entry.granted().isEmpty()) {
            effective = entry.granted();
            source = tr("source.group");
        } else {
            effective = AdminSession.defaultProfile();
            source = tr("source.default");
        }
        String shown = effective.isEmpty() ? tr("unrestricted") : effective;
        Skin.text(g, font, shown, x, row.y() + (row.h() - 8) / 2, row.right() - x - 90,
                entry.assigned().isEmpty() ? Palette.TEXT_DIM : Palette.ACCENT_HI);
        Skin.text(g, font, source, row.right() - 84, row.y() + (row.h() - 8) / 2, 80, Palette.TEXT_MUTE);
        if (hovered && players.active) {
            Skin.icon(g, Icon.REFRESH, row.right() - Atlas.ICON_SIZE - 2, row.y() + (row.h() - Atlas.ICON_SIZE) / 2,
                    Palette.ACCENT_HI);
        }
    }

    /** No assignment, then each profile in order, then back to none. */
    private void cycle(AdminPayloads.PlayerEntry entry) {
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(AdminSession.profileNames());
        int index = options.indexOf(entry.assigned());
        String next = options.get((index + 1) % options.size());
        AdminSession.assign(entry.id(), next);
        info(tr("assigning", entry.name(), next.isEmpty() ? tr("none") : next));
    }
}

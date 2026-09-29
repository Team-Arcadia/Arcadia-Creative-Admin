/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcButton;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcEditBox;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcList;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Atlas;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Icon;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Palette;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Rect;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Skin;
import net.thefricadelle.arcadiacreativeadmin.network.AdminPayloads;
import net.thefricadelle.arcadiacreativeadmin.policy.CreativeProfile;
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyCodec;
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry page of the admin screen: server settings, the list of profiles, and the way into each
 * profile's tabs and items, its advanced rules, and the player assignments.
 *
 * @author THEFricadelle
 */
public final class AdminScreen extends AdminPage {

    private static final int FOOTER_BUTTON = 110;
    /** A label needs 3 px each side, as AbcButton draws it. */
    private static final int FOOTER_PADDING = 3;

    private static final int NOTE_LINE = 10;

    private static String selected = "";

    /** The lines under the profile buttons and where they go, set by build() so a test can measure them. */
    private List<String> notes = List.of();
    private Rect notesArea = new Rect(0, 0, 0, 0);

    private final AbcList<String> profiles;
    private final AbcEditBox newName;
    private final List<Caption> captions = new ArrayList<>();

    private record Caption(String text, int x, int y, int w) {}

    public AdminScreen() {
        super(Component.translatable("arcadiacreativeadmin.admin.title"), null);
        this.profiles = new AbcList<String>(Component.translatable("arcadiacreativeadmin.admin.profiles"), 16)
                .identity(name -> name)
                .renderer(this::renderProfileRow)
                .emptyText(tr("no_profiles"))
                .onSelect(name -> {
                    selected = name;
                    rebuildWidgets();
                });
        this.newName = new AbcEditBox(Component.translatable("arcadiacreativeadmin.admin.new_name"), 32)
                .hint(text("new_name"))
                .onSubmit(this::createProfile);
    }

    /** Routes a state from the server to whichever admin page is open, or opens this one. */
    public static void onState(Minecraft mc, AdminPayloads.State state) {
        boolean replaced = AdminSession.accept(state);
        if (mc.screen instanceof AdminPage page) {
            page.onServerState(state, replaced);
        } else if (state.open()) {
            mc.setScreen(new AdminScreen());
        }
    }

    @Override
    protected boolean hasSidebar() {
        return true;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.LOCK;
    }

    @Override
    protected void build() {
        captions.clear();
        addCloseButton();
        boolean readOnly = AdminSession.readOnly();

        // Sidebar: profiles, and the controls that add or remove one.
        Rect side = layout.sidebar().inset(GAP);
        // Name field, then Duplicate and Delete on rows of their own: side by side, neither label fits.
        Rect controls = side.bottom(Atlas.INPUT_HEIGHT + 2 * (Atlas.BUTTON_HEIGHT + GAP));
        List<String> names = AdminSession.profileNames();
        if (!names.contains(selected)) {
            selected = names.isEmpty() ? "" : names.get(0);
        }
        profiles.setItems(names);
        profiles.selectByKey(selected);
        addRenderableWidget(profiles.at(side.aboveBottom(controls.h() + GAP)));

        Rect[] nameRow = controls.top(Atlas.INPUT_HEIGHT).split(GAP, Atlas.INPUT_HEIGHT);
        addRenderableWidget(newName.at(nameRow[0]));
        addRenderableWidget(AbcButton.good(text("create"), this::createProfile)
                .iconOnly(Icon.PLUS).enabled(!readOnly).at(nameRow[1]));
        Rect delete = controls.bottom(Atlas.BUTTON_HEIGHT);
        Rect duplicate = controls.aboveBottom(Atlas.BUTTON_HEIGHT + GAP).bottom(Atlas.BUTTON_HEIGHT);
        addRenderableWidget(AbcButton.neutral(text("duplicate"), this::duplicateProfile)
                .enabled(!readOnly && !selected.isEmpty()).at(duplicate));
        addRenderableWidget(AbcButton.danger(text("delete"), this::deleteProfile)
                .icon(Icon.TRASH).enabled(!readOnly && !selected.isEmpty()).at(delete));

        // Content: server settings, then the selected profile, then save.
        Rect content = layout.content();
        Rect footerRow = content.bottom(Atlas.BUTTON_HEIGHT);
        int y = content.y();
        int half = (content.w() - GAP) / 2;

        y = caption("section.server", content, y);
        addRenderableWidget(toggle(text("enforced"), AdminSession.enforced(), () -> {
            AdminSession.setEnforced(!AdminSession.enforced());
            rebuildWidgets();
        }).tooltip(text("enforced_tooltip")).enabled(!readOnly).at(content.x(), y, half, Atlas.BUTTON_HEIGHT));
        addRenderableWidget(AbcButton.neutral(text("bypass", AdminSession.bypassOpLevel()), this::cycleBypass)
                .tooltip(text("bypass_tooltip")).enabled(!readOnly)
                .at(content.x() + half + GAP, y, content.w() - half - GAP, Atlas.BUTTON_HEIGHT));
        y += Atlas.BUTTON_HEIGHT + GAP;
        String defaultName = AdminSession.defaultProfile().isEmpty() ? tr("none") : AdminSession.defaultProfile();
        addRenderableWidget(AbcButton.neutral(text("default_profile", defaultName), this::cycleDefault)
                .tooltip(text("default_profile_tooltip")).enabled(!readOnly)
                .at(content.x(), y, half, Atlas.BUTTON_HEIGHT));
        addRenderableWidget(AbcButton.neutral(text("players"), () -> this.minecraft.setScreen(new PlayersScreen(this)))
                .icon(Icon.TAB).at(content.x() + half + GAP, y, content.w() - half - GAP, Atlas.BUTTON_HEIGHT));
        y += Atlas.BUTTON_HEIGHT + GAP * 2;

        AdminSession.Draft draft = AdminSession.profile(selected);
        if (draft != null) {
            y = caption("section.profile", content, y, draft.name);
            addRenderableWidget(AbcButton.accent(text("mode." + draft.mode.id()), () -> {
                draft.mode = draft.mode == CreativeProfile.Mode.WHITELIST
                        ? CreativeProfile.Mode.BLACKLIST : CreativeProfile.Mode.WHITELIST;
                AdminSession.markDirty();
                rebuildWidgets();
            }).tooltip(text("mode_tooltip")).enabled(!readOnly).at(content.x(), y, content.w(), Atlas.BUTTON_HEIGHT));
            y += Atlas.BUTTON_HEIGHT + GAP;
            addRenderableWidget(AbcButton.neutral(text("edit_tabs"),
                            () -> this.minecraft.setScreen(new ProfileScreen(this, draft.name)))
                    .icon(Icon.TAB).at(content.x(), y, half, Atlas.BUTTON_HEIGHT));
            addRenderableWidget(AbcButton.neutral(text("edit_advanced"),
                            () -> this.minecraft.setScreen(new AdvancedScreen(this, draft.name)))
                    .icon(Icon.PRESETS).at(content.x() + half + GAP, y, content.w() - half - GAP, Atlas.BUTTON_HEIGHT));
            y += Atlas.BUTTON_HEIGHT + GAP;
            // Right under the buttons: anchored to the bottom, they slid under them on a small window.
            notes = List.of(tr("mode_help." + draft.mode.id()),
                    tr("summary_groups", draft.tabs.size(), draft.namespaces.size(), draft.tags.size()),
                    tr("summary_items", draft.items.size(), draft.exceptions.size()));
            notesArea = new Rect(content.x(), y, content.w(), notes.size() * NOTE_LINE);
        } else {
            caption("section.no_profile", content, y);
            notes = List.of();
            notesArea = new Rect(content.x(), y, content.w(), 0);
        }

        AbcButton revert = AbcButton.neutral(text("revert"), () -> {
            AdminSession.revert();
            rebuildWidgets();
            info(tr("reverted"));
        }).icon(Icon.REFRESH).enabled(AdminSession.dirty());
        AbcButton save = AbcButton.good(text("save"), this::save)
                .icon(Icon.CHECK).enabled(!readOnly && AdminSession.dirty());
        // Right-aligned, each as wide as its label needs and never narrower than the usual 110: the French
        // "Undo" is twice the English. Rect.split would fall back to equal shares on a narrow window.
        int saveWidth = Math.max(FOOTER_BUTTON, save.preferredWidth(font, FOOTER_PADDING));
        int revertWidth = Math.max(FOOTER_BUTTON, revert.preferredWidth(font, FOOTER_PADDING));
        addRenderableWidget(save.at(footerRow.right(saveWidth)));
        addRenderableWidget(revert.at(footerRow.beforeRight(saveWidth + GAP).right(revertWidth)));
        // In the status bar rather than a fourth line of notes, which did not fit a small window.
        if (AdminSession.dirty()) {
            warn(tr("unsaved"));
        }
        showStandingNotice();
    }

    private int caption(String key, Rect inner, int y, Object... args) {
        captions.add(new Caption(tr(key, args), inner.x(), y + 2, inner.w()));
        return y + CAPTION;
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (Caption caption : captions) {
            Skin.caption(g, font, caption.text(), caption.x(), caption.y(), caption.w());
        }
        for (int i = 0; i < notes.size(); i++) {
            Skin.text(g, font, notes.get(i), notesArea.x(), notesArea.y() + i * NOTE_LINE, notesArea.w(),
                    i == 0 ? Palette.TEXT_DIM : Palette.TEXT_MUTE);
        }
    }

    private void renderProfileRow(GuiGraphics g, Font font, String name, Rect row,
                                  int mouseX, int mouseY, boolean hovered, boolean isSelected) {
        Skin.row(g, row, 0, hovered, isSelected);
        AdminSession.Draft draft = AdminSession.profile(name);
        int x = row.x() + 4;
        if (name.equals(AdminSession.defaultProfile())) {
            Skin.icon(g, Icon.STAR, x, row.y() + (row.h() - Atlas.ICON_SIZE) / 2, Palette.WARN);
        }
        x += Atlas.ICON_SIZE + 3;
        Skin.text(g, font, name, x, row.y() + (row.h() - 8) / 2, row.right() - x - 18, Palette.TEXT);
        if (draft != null) {
            Skin.icon(g, draft.mode == CreativeProfile.Mode.WHITELIST ? Icon.LOCK : Icon.EYE,
                    row.right() - Atlas.ICON_SIZE - 4, row.y() + (row.h() - Atlas.ICON_SIZE) / 2, Palette.TEXT_MUTE);
        }
    }

    // ------------------------------------------------------------------ actions

    private void createProfile() {
        String name = PolicyCodec.normalizeName(newName.getValue());
        String problem = PolicyCodec.nameProblem(name);
        if (problem != null) {
            status(tr("name_" + problem), false);
            return;
        }
        if (AdminSession.create(name, null) == null) {
            status(tr("name_taken"), false);
            return;
        }
        newName.setValue("");
        selected = name;
        rebuildWidgets();
    }

    private void duplicateProfile() {
        AdminSession.Draft source = AdminSession.profile(selected);
        if (source == null) {
            return;
        }
        String base = selected + "_copy";
        String name = base;
        for (int i = 2; AdminSession.profile(name) != null; i++) {
            name = base + i;
        }
        if (PolicyCodec.nameProblem(name) != null || AdminSession.create(name, source) == null) {
            status(tr("name_invalid"), false);
            return;
        }
        selected = name;
        rebuildWidgets();
    }

    private void deleteProfile() {
        String name = selected;
        confirm(tr("delete_title", name), tr("delete_message", name), tr("delete"), () -> {
            AdminSession.delete(name);
            selected = "";
            rebuildWidgets();
        });
    }

    private void cycleBypass() {
        int next = AdminSession.bypassOpLevel() + 1;
        AdminSession.setBypassOpLevel(next > PolicyDocument.MAX_BYPASS_OP_LEVEL ? PolicyDocument.MIN_BYPASS_OP_LEVEL : next);
        rebuildWidgets();
    }

    private void cycleDefault() {
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(AdminSession.profileNames());
        int index = options.indexOf(AdminSession.defaultProfile());
        AdminSession.setDefaultProfile(options.get((index + 1) % options.size()));
        rebuildWidgets();
    }

    private void save() {
        AdminSession.save();
        info(tr("saving"));
    }

    @Override
    public void onClose() {
        if (AdminSession.dirty() && !AdminSession.readOnly()) {
            confirm(tr("discard_title"), tr("discard_message"), tr("discard"), this::closeForGood);
            return;
        }
        closeForGood();
    }

    private void closeForGood() {
        AdminSession.revert();
        PacketDistributor.sendToServer(new AdminPayloads.Close());
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }
}

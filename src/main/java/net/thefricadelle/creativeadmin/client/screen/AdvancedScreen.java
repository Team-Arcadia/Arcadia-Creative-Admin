/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.thefricadelle.creativeadmin.client.gui.kit.CaEditBox;
import net.thefricadelle.creativeadmin.client.gui.kit.CaList;
import net.thefricadelle.creativeadmin.client.gui.kit.Atlas;
import net.thefricadelle.creativeadmin.client.gui.kit.Icon;
import net.thefricadelle.creativeadmin.client.gui.kit.Palette;
import net.thefricadelle.creativeadmin.client.gui.kit.Rect;
import net.thefricadelle.creativeadmin.client.gui.kit.Skin;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The rules that are not a page or an item: whole mods, item tags, which data components a stack
 * may carry, and the two component guards. None of them is required.
 *
 * @author THEFricadelle
 */
final class AdvancedScreen extends AdminPage {

    private static final int ROW = 14;

    private final String profileName;
    private final CaList<String> mods;
    private final CaList<String> tags;
    private final CaList<String> components;
    private final CaEditBox tagInput;
    private final CaEditBox componentInput;
    private final List<Caption> captions = new ArrayList<>();

    private record Caption(String text, int x, int y, int w) {}

    AdvancedScreen(AdminScreen parent, String profileName) {
        super(Component.translatable("creativeadmin.admin.advanced_title", profileName), parent);
        this.profileName = profileName;
        this.mods = new CaList<String>(text("mods"), ROW)
                .identity(id -> id)
                .renderer((g, font, id, row, mx, my, hovered, selected) -> {
                    AdminSession.Draft draft = draft();
                    renderToggleRow(g, font, id, row, hovered, draft != null && draft.namespaces.contains(id), modName(id));
                })
                .onRowClick((id, row, mx, my) -> {
                    AdminSession.Draft draft = draft();
                    toggle(draft == null ? null : draft.namespaces, id);
                    return true;
                });
        this.tags = removableList("tags", draft -> draft.tags);
        this.components = removableList("components", draft -> draft.allowedComponents);
        this.tagInput = new CaEditBox(text("tag_hint"), 128).hint(text("tag_hint")).onSubmit(this::addTag);
        this.componentInput = new CaEditBox(text("component_hint"), 128).hint(text("component_hint"))
                .onSubmit(this::addComponent);
    }

    private CaList<String> removableList(String key, Function<AdminSession.Draft, Set<ResourceLocation>> target) {
        return new CaList<String>(text(key), ROW)
                .identity(id -> id)
                .emptyText(tr("empty"))
                .renderer((g, font, id, row, mx, my, hovered, selected) -> {
                    Skin.row(g, row, 0, hovered, false);
                    Skin.text(g, font, id, row.x() + 4, row.y() + (row.h() - 8) / 2, row.w() - 20, Palette.TEXT);
                    if (hovered && !AdminSession.readOnly()) {
                        Skin.icon(g, Icon.TRASH, row.right() - Atlas.ICON_SIZE - 4,
                                row.y() + (row.h() - Atlas.ICON_SIZE) / 2, Palette.DANGER);
                    }
                })
                .onRowClick((id, row, mx, my) -> {
                    AdminSession.Draft draft = draft();
                    if (draft != null && !AdminSession.readOnly()) {
                        ResourceLocation parsed = ResourceLocation.tryParse(id.startsWith("#") ? id.substring(1) : id);
                        if (parsed != null && target.apply(draft).remove(parsed)) {
                            AdminSession.markDirty();
                            rebuildWidgets();
                        }
                    }
                    return true;
                });
    }

    @Nullable
    private AdminSession.Draft draft() {
        return AdminSession.profile(profileName);
    }

    @Override
    protected Icon titleIcon() {
        return Icon.PRESETS;
    }

    @Override
    protected void build() {
        captions.clear();
        addCloseButton();
        AdminSession.Draft draft = draft();
        if (draft == null) {
            onClose();
            return;
        }
        boolean readOnly = AdminSession.readOnly();
        Rect content = layout.content();
        int half = (content.w() - GAP) / 2;
        Rect left = content.left(half);
        Rect right = content.right(content.w() - half - GAP);

        String selection = tr("selection." + draft.mode.id());
        caption(tr("mods_caption", selection), left, left.y());
        Set<String> namespaces = new TreeSet<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            namespaces.add(id.getNamespace());
        }
        mods.setItems(List.copyOf(namespaces));
        mods.active = !readOnly;
        addRenderableWidget(mods.at(left.belowTop(CAPTION)));

        int listHeight = (right.h() - 2 * (CAPTION + Atlas.INPUT_HEIGHT + GAP) - 2 * (Atlas.BUTTON_HEIGHT + GAP) - GAP) / 2;
        int y = right.y();
        caption(tr("tags_caption", selection), right, y);
        y += CAPTION;
        tagInput.setEditable(!readOnly);
        addRenderableWidget(tagInput.at(new Rect(right.x(), y, right.w(), Atlas.INPUT_HEIGHT)));
        y += Atlas.INPUT_HEIGHT + GAP;
        List<String> tagIds = new ArrayList<>();
        draft.tags.stream().map(tag -> "#" + tag).sorted().forEach(tagIds::add);
        tags.setItems(tagIds);
        addRenderableWidget(tags.at(new Rect(right.x(), y, right.w(), listHeight)));
        y += listHeight + GAP;

        caption(tr("components_caption"), right, y);
        y += CAPTION;
        componentInput.setEditable(!readOnly);
        addRenderableWidget(componentInput.at(new Rect(right.x(), y, right.w(), Atlas.INPUT_HEIGHT)));
        y += Atlas.INPUT_HEIGHT + GAP;
        components.setItems(draft.allowedComponents.stream().map(ResourceLocation::toString).sorted().toList());
        addRenderableWidget(components.at(new Rect(right.x(), y, right.w(), listHeight)));
        y += listHeight + GAP;

        addRenderableWidget(toggle(text("allow_block_entity_data"), draft.allowBlockEntityData, () -> {
            draft.allowBlockEntityData = !draft.allowBlockEntityData;
            AdminSession.markDirty();
            rebuildWidgets();
        }).tooltip(text("allow_block_entity_data_tooltip")).enabled(!readOnly)
                .at(right.x(), y, right.w(), Atlas.BUTTON_HEIGHT));
        y += Atlas.BUTTON_HEIGHT + GAP;
        addRenderableWidget(toggle(text("allow_container_contents"), draft.allowContainerContents, () -> {
            draft.allowContainerContents = !draft.allowContainerContents;
            AdminSession.markDirty();
            rebuildWidgets();
        }).tooltip(text("allow_container_contents_tooltip")).enabled(!readOnly)
                .at(right.x(), y, right.w(), Atlas.BUTTON_HEIGHT));

        info(tr("advanced_help"));
        showStandingNotice();
    }

    private void caption(String text, Rect column, int y) {
        captions.add(new Caption(text, column.x(), y + 2, column.w()));
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (Caption caption : captions) {
            Skin.caption(g, font, caption.text(), caption.x(), caption.y(), caption.w());
        }
    }

    private static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    private static void renderToggleRow(GuiGraphics g, Font font, String id, Rect row, boolean hovered,
                                        boolean on, String label) {
        Skin.row(g, row, 0, hovered, false);
        Skin.icon(g, on ? Icon.BOX_CHECKED : Icon.BOX, row.x() + 4, row.y() + (row.h() - Atlas.ICON_SIZE) / 2,
                on ? Palette.ACCENT_HI : Palette.TEXT_MUTE);
        String text = label.equals(id) ? id : label + " (" + id + ")";
        Skin.text(g, font, text, row.x() + Atlas.ICON_SIZE + 10, row.y() + (row.h() - 8) / 2,
                row.w() - Atlas.ICON_SIZE - 14, on ? Palette.TEXT : Palette.TEXT_DIM);
    }

    private void toggle(@Nullable Set<String> set, String value) {
        if (set == null || AdminSession.readOnly()) {
            return;
        }
        if (!set.remove(value)) {
            set.add(value);
        }
        AdminSession.markDirty();
    }

    private void addTag() {
        AdminSession.Draft draft = draft();
        String raw = tagInput.getValue().trim().toLowerCase(Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(raw.startsWith("#") ? raw.substring(1) : raw);
        if (draft == null || id == null) {
            status(tr("invalid_id"), false);
            return;
        }
        draft.tags.add(id);
        tagInput.setValue("");
        AdminSession.markDirty();
        rebuildWidgets();
    }

    private void addComponent() {
        AdminSession.Draft draft = draft();
        ResourceLocation id = ResourceLocation.tryParse(componentInput.getValue().trim().toLowerCase(Locale.ROOT));
        if (draft == null || id == null) {
            status(tr("invalid_id"), false);
            return;
        }
        if (!BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(id)) {
            status(tr("unknown_component", id.toString()), false);
            return;
        }
        draft.allowedComponents.add(id);
        componentInput.setValue("");
        AdminSession.markDirty();
        rebuildWidgets();
    }
}

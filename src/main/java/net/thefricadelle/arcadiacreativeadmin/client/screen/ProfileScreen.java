/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyEvaluator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Locks and unlocks whole creative pages and single items of one profile.
 * <p>
 * Every click states the result the admin wants, open or locked, and the rules are adjusted to
 * reach it whatever the mode: the admin never has to think in terms of selections and exceptions.
 * The grid shows the result of every rule of the profile, mods and tags included, so what is shown
 * locked here is what players will find locked.
 *
 * @author THEFricadelle
 */
final class ProfileScreen extends AdminPage {

    private static final int ROW = 18;
    private static final int TAB_COLUMN_MIN = 130;

    private final String profileName;
    private final ClientTabs tabs = new ClientTabs();
    private final AbcList<ClientTabs.Tab> tabList;
    private final AbcEditBox search;
    private final ItemGrid grid = new ItemGrid();
    private final Map<Item, Boolean> openCache = new HashMap<>();
    @Nullable
    private ClientTabs.Tab selectedTab;
    @Nullable
    private CreativeProfile snapshot;

    ProfileScreen(AdminScreen parent, String profileName) {
        super(Component.translatable("arcadiacreativeadmin.admin.profile_title", profileName), parent);
        this.profileName = profileName;
        this.search = new AbcEditBox(text("search"), 64).hint(text("search")).onChange(value -> refreshGrid(true));
        this.tabList = new AbcList<ClientTabs.Tab>(text("tabs"), ROW)
                .identity(ClientTabs.Tab::id)
                .renderer(this::renderTabRow)
                .onRowClick(this::onTabRowClick)
                .onSelect(tab -> {
                    selectedTab = tab;
                    search.setValue("");
                    refreshGrid(true);
                });
        this.grid.setLocked(stack -> !isOpen(stack.getItem()));
        this.grid.onToggle(stack -> {
            setItemOpen(stack.getItem(), !isOpen(stack.getItem()));
            changed();
        });
        if (!tabs.tabs().isEmpty()) {
            selectedTab = tabs.tabs().get(0);
        }
    }

    @Nullable
    private AdminSession.Draft draft() {
        return AdminSession.profile(profileName);
    }

    @Override
    protected Icon titleIcon() {
        return Icon.TAB;
    }

    @Override
    protected AbcEditBox searchBox() {
        return search;
    }

    @Override
    protected GuiEventListener initialFocus() {
        return search;
    }

    @Override
    protected void build() {
        addCloseButton();
        AdminSession.Draft draft = draft();
        if (draft == null) {
            // Deleted by a state from the server while this page was open.
            onClose();
            return;
        }
        boolean readOnly = AdminSession.readOnly();
        Rect content = layout.content();
        Rect bottom = content.bottom(Atlas.BUTTON_HEIGHT);
        Rect body = content.aboveBottom(Atlas.BUTTON_HEIGHT + GAP);
        int tabColumn = Math.max(TAB_COLUMN_MIN, body.w() * 2 / 5);
        Rect left = body.left(tabColumn);
        Rect right = body.afterLeft(tabColumn + GAP);

        tabList.setItems(tabs.tabs());
        if (selectedTab != null) {
            tabList.selectByKey(selectedTab.id());
        }
        addRenderableWidget(tabList.at(left));
        addRenderableWidget(search.at(right.top(Atlas.INPUT_HEIGHT)));
        grid.setEditable(!readOnly);
        addRenderableWidget(grid.at(right.belowTop(Atlas.INPUT_HEIGHT + GAP)));
        refreshGrid(false);

        Rect[] buttons = bottom.split(GAP, 120, 120);
        addRenderableWidget(AbcButton.danger(text("lock_page"), () -> {
            if (selectedTab != null) {
                setPageOpen(selectedTab, false);
                changed();
            }
        }).icon(Icon.LOCK).enabled(!readOnly && selectedTab != null).at(buttons[1]));
        addRenderableWidget(AbcButton.good(text("open_page"), () -> {
            if (selectedTab != null) {
                setPageOpen(selectedTab, true);
                changed();
            }
        }).icon(Icon.CHECK).enabled(!readOnly && selectedTab != null).at(buttons[2]));

        info(tr("profile_help." + draft.mode.id()));
        showStandingNotice();
    }

    // ------------------------------------------------------------------ state

    private CreativeProfile profile() {
        if (snapshot == null) {
            AdminSession.Draft draft = draft();
            snapshot = draft == null ? CreativeProfile.empty(profileName) : draft.toProfile();
        }
        return snapshot;
    }

    private boolean isOpen(Item item) {
        return openCache.computeIfAbsent(item, it -> PolicyEvaluator.isOpen(profile(), it, tabs::itemsOf));
    }

    private void changed() {
        AdminSession.markDirty();
        invalidate();
        refreshGrid(false);
    }

    private void invalidate() {
        snapshot = null;
        openCache.clear();
    }

    @Override
    void onServerState(AdminPayloads.State state, boolean replaced) {
        invalidate();
        super.onServerState(state, replaced);
    }

    private void refreshGrid(boolean resetScroll) {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<ItemStack> shown;
        if (!query.isEmpty()) {
            shown = new ArrayList<>();
            for (ItemStack stack : tabs.all()) {
                if (matches(stack, query)) {
                    shown.add(stack);
                }
            }
        } else {
            shown = selectedTab == null ? List.of() : selectedTab.stacks();
        }
        grid.setStacks(shown, resetScroll);
    }

    private static boolean matches(ItemStack stack, String query) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || id.toString().contains(query);
    }

    /**
     * Brings one item to the wanted state with the least rule: its single-item entries are dropped,
     * then one is added back only if the tabs, mods and tags alone disagree with the wish.
     */
    private void setItemOpen(Item item, boolean open) {
        AdminSession.Draft draft = draft();
        if (draft == null) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        draft.items.remove(id);
        draft.exceptions.remove(id);
        invalidate();
        boolean selectedWanted = draft.mode == CreativeProfile.Mode.WHITELIST ? open : !open;
        boolean groupSelected = PolicyEvaluator.isGroupSelected(profile(), item, id, tabs::itemsOf);
        if (groupSelected != selectedWanted) {
            (selectedWanted ? draft.items : draft.exceptions).add(id);
        }
        invalidate();
    }

    /**
     * A whole page: the tab rule is set, then every item of the page that another rule still keeps
     * in the other state gets an entry of its own, so the page ends exactly as asked.
     */
    private void setPageOpen(ClientTabs.Tab tab, boolean open) {
        AdminSession.Draft draft = draft();
        if (draft == null) {
            return;
        }
        boolean selectedWanted = draft.mode == CreativeProfile.Mode.WHITELIST ? open : !open;
        if (selectedWanted) {
            draft.tabs.add(tab.id());
        } else {
            draft.tabs.remove(tab.id());
        }
        invalidate();
        for (Item item : tab.items()) {
            if (isOpen(item) != open) {
                setItemOpen(item, open);
            }
        }
    }

    private int openCount(ClientTabs.Tab tab) {
        int open = 0;
        for (Item item : tab.items()) {
            if (isOpen(item)) {
                open++;
            }
        }
        return open;
    }

    // ------------------------------------------------------------------ rendering

    private Rect toggleArea(Rect row) {
        return row.right(ROW).inset(1);
    }

    private boolean onTabRowClick(ClientTabs.Tab tab, Rect row, double mouseX, double mouseY) {
        if (!toggleArea(row).contains(mouseX, mouseY) || AdminSession.readOnly()) {
            return false;
        }
        // Anything locked on the page: open it all. Fully open: lock it all.
        setPageOpen(tab, openCount(tab) < tab.items().size());
        changed();
        return true;
    }

    private void renderTabRow(GuiGraphics g, Font font, ClientTabs.Tab tab, Rect row,
                              int mouseX, int mouseY, boolean hovered, boolean isSelected) {
        Skin.row(g, row, 0, hovered, isSelected);
        g.renderItem(tab.tab().getIconItem(), row.x() + 1, row.y() + 1);
        int open = openCount(tab);
        int total = tab.items().size();
        String count = open + "/" + total;
        int countWidth = font.width(count);
        Rect toggle = toggleArea(row);
        int textX = row.x() + 20;
        Skin.text(g, font, tab.tab().getDisplayName().getString(), textX, row.y() + (row.h() - 8) / 2,
                toggle.x() - countWidth - 6 - textX, Palette.TEXT);
        int color = open == total ? Palette.GOOD : open == 0 ? Palette.DANGER : Palette.WARN;
        Skin.text(g, font, count, toggle.x() - countWidth - 3, row.y() + (row.h() - 8) / 2, color);
        boolean toggleHovered = toggle.contains(mouseX, mouseY);
        if (toggleHovered) {
            g.fill(toggle.x(), toggle.y(), toggle.right(), toggle.bottom(), Palette.BG3);
        }
        Skin.icon(g, open == total ? Icon.CHECK : Icon.LOCK,
                toggle.x() + (toggle.w() - Atlas.ICON_SIZE) / 2, toggle.y() + (toggle.h() - Atlas.ICON_SIZE) / 2,
                open == total ? Palette.GOOD : open == 0 ? Palette.DANGER : Palette.WARN);
    }

    @Override
    protected void renderForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (dialogOpen()) {
            return;
        }
        ItemStack hovered = grid.stackAt(mouseX, mouseY);
        if (hovered != null) {
            boolean open = isOpen(hovered.getItem());
            List<Component> lines = new ArrayList<>();
            lines.add(hovered.getHoverName());
            lines.add(Component.literal(BuiltInRegistries.ITEM.getKey(hovered.getItem()).toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(text(open ? "item_open" : "item_locked")
                    .withStyle(open ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (!AdminSession.readOnly()) {
                lines.add(text(open ? "click_to_lock" : "click_to_open").withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }
}

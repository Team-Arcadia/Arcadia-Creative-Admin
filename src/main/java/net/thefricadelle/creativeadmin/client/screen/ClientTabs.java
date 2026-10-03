/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The creative tabs as this client sees them, for the admin screen to preview a profile.
 * <p>
 * The client only builds tab contents when its creative screen opens, and an admin may open the
 * admin screen straight from the command. Building them here is what the creative screen itself
 * would do; the operator flag is forced on so operator-only items can be configured too, and the
 * next opening of the creative screen rebuilds with the player's own setting.
 *
 * @author THEFricadelle
 */
final class ClientTabs {

    /** One page of the creative menu, with the items it lists, in menu order. */
    record Tab(ResourceLocation id, CreativeModeTab tab, List<ItemStack> stacks, Set<Item> items) {}

    private final List<Tab> tabs = new ArrayList<>();
    private final Map<ResourceLocation, Set<Item>> itemsById = new HashMap<>();
    private final List<ItemStack> all = new ArrayList<>();

    ClientTabs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true,
                    mc.level.registryAccess());
        }
        Set<Item> seen = new LinkedHashSet<>();
        for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
            ResourceLocation id = CreativeModeTabRegistry.getName(tab);
            if (id == null || tab.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            List<ItemStack> stacks = new ArrayList<>();
            Set<Item> items = new LinkedHashSet<>();
            for (ItemStack stack : tab.getDisplayItems()) {
                // One cell per item: the policy decides per item, not per variant.
                if (items.add(stack.getItem())) {
                    stacks.add(stack.getItem().getDefaultInstance());
                }
            }
            if (stacks.isEmpty()) {
                continue;
            }
            tabs.add(new Tab(id, tab, List.copyOf(stacks), Collections.unmodifiableSet(items)));
            itemsById.put(id, Collections.unmodifiableSet(items));
            for (ItemStack stack : stacks) {
                if (seen.add(stack.getItem())) {
                    all.add(stack);
                }
            }
        }
    }

    List<Tab> tabs() {
        return tabs;
    }

    Set<Item> itemsOf(ResourceLocation tab) {
        return itemsById.getOrDefault(tab, Set.of());
    }

    /** Every item any tab lists, once, in menu order. */
    List<ItemStack> all() {
        return all;
    }
}

/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.thefricadelle.creativeadmin.network.AdvicePayload;

import java.util.Set;

/**
 * The advice a client was last given.
 * <p>
 * Holds no client-only type on purpose, so that nothing loaded on a dedicated server can reach a
 * client class through it.
 * <p>
 * Reset on disconnect, otherwise a player who leaves a restricted server would keep its filtered
 * inventory in every singleplayer world afterwards.
 *
 * @author THEFricadelle
 */
public final class ReceivedAdvice {

    private static volatile boolean enforced;
    private static volatile String profile = "";
    private static volatile Set<ResourceLocation> tabs = Set.of();
    private static volatile boolean lockedListed = true;
    private static volatile Set<Item> items = Set.of();
    private static volatile boolean admin;

    private ReceivedAdvice() {}

    public static void accept(AdvicePayload payload) {
        // An enforced profile leaving no tab would present an empty creative screen. Treat it as no
        // display advice; the server still refuses every item, so nothing is granted by showing them.
        enforced = payload.enforced() && !payload.tabs().isEmpty();
        profile = payload.profile();
        tabs = Set.copyOf(payload.tabs());
        lockedListed = payload.lockedListed();
        items = Set.copyOf(payload.items());
        admin = payload.admin();
    }

    public static void clear() {
        enforced = false;
        profile = "";
        tabs = Set.of();
        lockedListed = true;
        items = Set.of();
        admin = false;
    }

    public static boolean enforced() {
        return enforced;
    }

    public static String profile() {
        return profile;
    }

    public static Set<ResourceLocation> visibleTabs() {
        return tabs;
    }

    public static boolean admin() {
        return admin;
    }

    public static boolean allowsTab(ResourceLocation tabId) {
        return !enforced || tabs.contains(tabId);
    }

    public static boolean isLocked(ItemStack stack) {
        if (!enforced || stack.isEmpty()) {
            return false;
        }
        boolean listed = items.contains(stack.getItem());
        return lockedListed == listed;
    }
}

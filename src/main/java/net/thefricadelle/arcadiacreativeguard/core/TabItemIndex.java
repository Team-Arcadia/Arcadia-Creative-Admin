/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.core;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves "every item of creative tab X" into a set of items, on the server.
 * <p>
 * This is what makes a whole-mod rule expressible as the tab a player actually sees, rather than as
 * a list of item ids nobody can maintain by hand.
 * <p>
 * Tab contents are not populated on a server by default: {@code CreativeModeTabs.buildAllTabContents}
 * runs when a client opens the creative screen. On a dedicated server it therefore has to be driven
 * explicitly. On an integrated server it must <em>not</em> be: the client shares this JVM and the
 * same static {@code CACHED_PARAMETERS}, and rebuilding with a forced operator flag would change
 * what the player's own creative screen shows. There the contents the client already built are used
 * as they are.
 *
 * @author THEFricadelle
 */
public final class TabItemIndex {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<ResourceLocation, Set<Item>> index = Map.of();

    private TabItemIndex() {}

    /**
     * Builds the index once the server holds a frozen registry access. Called on server start and
     * again whenever the policy is reloaded, since a reload may reference a tab not indexed before.
     */
    public static void build(MinecraftServer server) {
        try {
            if (server.isDedicatedServer()) {
                // The operator flag is forced on so that operator-only tabs are indexed too. A
                // profile has to be able to name them, if only to keep them out.
                CreativeModeTabs.tryRebuildTabContents(
                        server.getWorldData().enabledFeatures(), true, server.registryAccess());
            }

            Map<ResourceLocation, Set<Item>> built = new HashMap<>();
            for (Map.Entry<net.minecraft.resources.ResourceKey<CreativeModeTab>, CreativeModeTab> entry
                    : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
                CreativeModeTab tab = entry.getValue();
                if (tab.getType() == CreativeModeTab.Type.SEARCH) {
                    continue;
                }
                Set<Item> items = new HashSet<>();
                for (ItemStack stack : tab.getDisplayItems()) {
                    items.add(stack.getItem());
                }
                built.put(entry.getKey().location(), Set.copyOf(items));
            }
            index = Map.copyOf(built);
            LOGGER.info("Indexed {} creative tabs for policy evaluation", index.size());
        } catch (Exception e) {
            // An empty index makes every tab rule match nothing. Under a strict whitelist that
            // denies rather than allows, which is the correct direction to fail in.
            index = Map.of();
            LOGGER.error("Could not index creative tab contents; tab rules will match nothing", e);
        }
    }

    /** @return the items of that tab, empty when the tab is unknown or was never indexed */
    public static Set<Item> itemsOf(ResourceLocation tabId) {
        return index.getOrDefault(tabId, Set.of());
    }

    /** Whether a tab id names something that actually exists, for command feedback. */
    public static boolean isIndexed(ResourceLocation tabId) {
        return index.containsKey(tabId);
    }

    public static Set<ResourceLocation> indexedTabs() {
        return index.keySet();
    }
}

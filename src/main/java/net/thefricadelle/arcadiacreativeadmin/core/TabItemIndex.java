/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.core;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
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
 * what the player's own creative screen shows. There the contents the client builds are used as
 * they are, and since the client only builds them when its creative screen first opens, which is
 * after the server started, the index is rebuilt once it notices they changed.
 * <p>
 * Besides the item sets, the index keeps every stack the menu offers with components of its own (a
 * potion, a painting variant, an enchanted book). Those are what a player legitimately takes, and
 * are the reference the component check compares against.
 *
 * @author THEFricadelle
 */
public final class TabItemIndex {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<ResourceLocation, Set<Item>> index = Map.of();
    private static volatile Set<ItemStack> variants = Set.of();
    /** The search tab's contents at the last build; the client replaces them on every rebuild. */
    @Nullable
    private static volatile Collection<ItemStack> builtFrom;

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
            indexCurrentContents();
        } catch (Exception e) {
            // An empty index makes every tab rule match nothing. Under a strict whitelist that
            // denies rather than allows, which is the correct direction to fail in.
            index = Map.of();
            variants = Set.of();
            LOGGER.error("Could not index creative tab contents; tab rules will match nothing", e);
        }
    }

    /**
     * On an integrated server, re-reads the tab contents if the client rebuilt them since the last
     * build. Never triggers a rebuild itself, for the reason given on the class.
     *
     * @return whether the index changed
     */
    public static boolean refreshIfStale(MinecraftServer server) {
        if (server.isDedicatedServer() || searchContents() == builtFrom) {
            return false;
        }
        try {
            indexCurrentContents();
            return true;
        } catch (Exception e) {
            LOGGER.error("Could not re-index creative tab contents; keeping the previous index", e);
            return false;
        }
    }

    private static void indexCurrentContents() {
        Collection<ItemStack> sentinel = searchContents();
        Map<ResourceLocation, Set<Item>> built = new HashMap<>();
        Set<ItemStack> offered = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (Map.Entry<ResourceKey<CreativeModeTab>, CreativeModeTab> entry
                : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            CreativeModeTab tab = entry.getValue();
            if (tab.getType() == CreativeModeTab.Type.SEARCH) {
                continue;
            }
            Set<Item> items = new HashSet<>();
            for (ItemStack stack : tab.getDisplayItems()) {
                items.add(stack.getItem());
                collectVariant(offered, stack);
            }
            // Some variants are only listed for the search tab, and are just as legitimate.
            for (ItemStack stack : tab.getSearchTabDisplayItems()) {
                collectVariant(offered, stack);
            }
            built.put(entry.getKey().location(), Set.copyOf(items));
        }
        index = Map.copyOf(built);
        variants = Collections.unmodifiableSet(offered);
        builtFrom = sentinel;
        LOGGER.info("Indexed {} creative tabs and {} component variants for policy evaluation",
                index.size(), offered.size());
    }

    private static void collectVariant(Set<ItemStack> offered, ItemStack stack) {
        if (!stack.isComponentsPatchEmpty()) {
            // Copied because the tab's own stacks belong to the client on an integrated server.
            offered.add(stack.copyWithCount(1));
        }
    }

    /**
     * Built last by {@code CreativeModeTabs.buildAllTabContents}, after every category tab, so a
     * new collection here means the whole rebuild is done.
     */
    private static Collection<ItemStack> searchContents() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(CreativeModeTabs.SEARCH).getDisplayItems();
    }

    /** Whether the stack, components included, is exactly one the creative menu offers. */
    public static boolean isCreativeVariant(ItemStack stack) {
        return variants.contains(stack);
    }

    /** @return the items of that tab, empty when the tab is unknown or was never indexed */
    public static Set<Item> itemsOf(ResourceLocation tabId) {
        return index.getOrDefault(tabId, Set.of());
    }

    /** Whether a tab id names something that actually exists, for policy validation. */
    public static boolean isIndexed(ResourceLocation tabId) {
        return index.containsKey(tabId);
    }

    public static Set<ResourceLocation> indexedTabs() {
        return index.keySet();
    }
}

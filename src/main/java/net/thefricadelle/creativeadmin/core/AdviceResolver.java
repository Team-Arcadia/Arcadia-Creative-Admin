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
import net.thefricadelle.creativeadmin.policy.CreativeProfile;
import net.thefricadelle.creativeadmin.policy.PolicyEvaluator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the creative screen of a player under a given profile should show: the tabs that keep at
 * least one open item, and which of the offered items are locked.
 * <p>
 * This is presentation, not enforcement: the answer travels to the client, which cannot be trusted
 * with it. The refusal in the packet handler is what actually holds.
 * <p>
 * Results are cached per profile rather than per player: the answer depends only on the profile, and
 * an event where thirty players connect at once would otherwise walk the whole creative menu thirty
 * times.
 *
 * @author THEFricadelle
 */
public final class AdviceResolver {

    /**
     * @param tabs         tabs worth displaying
     * @param lockedListed whether {@code items} lists the locked items, or else the open ones
     * @param items        the shorter of the two lists
     */
    public record Advice(List<ResourceLocation> tabs, boolean lockedListed, List<Item> items) {}

    private static final Map<String, Advice> CACHE = new ConcurrentHashMap<>();

    private AdviceResolver() {}

    /** Dropped whenever the policy, the tags or the tab index change, since all three feed the answer. */
    public static void invalidate() {
        CACHE.clear();
    }

    public static Advice adviceFor(CreativeProfile profile) {
        return CACHE.computeIfAbsent(profile.name(), name -> compute(profile));
    }

    private static Advice compute(CreativeProfile profile) {
        Set<ResourceLocation> tabs = new LinkedHashSet<>();
        Set<Item> open = new LinkedHashSet<>();
        Set<Item> locked = new LinkedHashSet<>();
        for (ResourceLocation tabId : TabItemIndex.indexedTabs()) {
            for (Item item : TabItemIndex.itemsOf(tabId)) {
                // The item alone is judged on purpose: component rules apply to what a player
                // actually takes, and hiding an item for them would hide ordinary items.
                if (open.contains(item) || (!locked.contains(item) && PolicyEvaluator.isOpen(profile, item))) {
                    open.add(item);
                    tabs.add(tabId);
                } else {
                    locked.add(item);
                }
            }
        }
        boolean lockedListed = locked.size() <= open.size();
        return new Advice(List.copyOf(tabs), lockedListed, List.copyOf(lockedListed ? locked : open));
    }
}

/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.thefricadelle.arcadiacreativeguard.policy.CreativeProfile;
import net.thefricadelle.arcadiacreativeguard.policy.PolicyEvaluator;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which creative tabs are worth showing a player, given the profile they are under.
 * <p>
 * A tab is kept when at least one of its items would be allowed. Hiding a tab whose every item is
 * refused is the difference between an event inventory a player can browse and one where most of
 * what they click is rejected.
 * <p>
 * This is presentation, not enforcement — the answer travels to the client, which cannot be trusted
 * with it. The refusal in the packet handler is what actually holds.
 * <p>
 * Results are cached per profile rather than per player: the answer depends only on the profile, and
 * an event where thirty players connect at once would otherwise walk the whole item registry thirty
 * times.
 *
 * @author THEFricadelle
 */
public final class VisibleTabResolver {

    private static final Map<String, Set<ResourceLocation>> CACHE = new ConcurrentHashMap<>();

    private VisibleTabResolver() {}

    /** Dropped whenever the policy or the tab index changes, since both feed the answer. */
    public static void invalidate() {
        CACHE.clear();
    }

    public static Set<ResourceLocation> visibleTabs(CreativeProfile profile) {
        return CACHE.computeIfAbsent(profile.name(), name -> compute(profile));
    }

    private static Set<ResourceLocation> compute(CreativeProfile profile) {
        Set<ResourceLocation> visible = new LinkedHashSet<>();
        for (ResourceLocation tabId : TabItemIndex.indexedTabs()) {
            for (Item item : TabItemIndex.itemsOf(tabId)) {
                // A bare stack is evaluated here on purpose: component rules apply to what a player
                // actually takes, and judging a tab by them would hide tabs whose items are fine.
                if (PolicyEvaluator.evaluate(profile, new ItemStack(item)).allowed()) {
                    visible.add(tabId);
                    break;
                }
            }
        }
        return Set.copyOf(visible);
    }
}

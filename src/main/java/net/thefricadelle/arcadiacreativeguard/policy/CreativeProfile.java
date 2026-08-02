/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.policy;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * One named set of creative restrictions, and the unit an event is configured with.
 * <p>
 * A profile is a <strong>strict whitelist</strong>: an item nothing allows is refused. Four rule
 * shapes open it up, and they are meant to be combined — a whole mod through {@link #namespaces},
 * a whole creative tab through {@link #tabs}, a family through {@link #tags}, and single entries
 * through {@link #items}.
 * <p>
 * {@link #deniedItems} and {@link #deniedNamespaces} exist because the broad rules are blunt:
 * allowing {@code minecraft:redstone} as a tab is the natural way to run a redstone event, and the
 * command block sits in that tab. A denial always wins over every allowance, so a broad rule can be
 * opened first and trimmed afterwards rather than being expanded into hundreds of item ids.
 *
 * @param name                  profile id, lowercase, used by commands and by the assignment file
 * @param items                 item ids allowed outright
 * @param namespaces            mod ids whose every item is allowed
 * @param tags                  item tag ids whose every member is allowed, written {@code #ns:path}
 *                              in the policy file and stored here without the hash
 * @param tabs                  creative tab ids whose every item is allowed
 * @param deniedItems           item ids refused whatever else allows them
 * @param deniedNamespaces      mod ids refused whatever else allows them
 * @param allowBlockEntityData  whether a stack may carry {@code minecraft:block_entity_data}; a
 *                              command block, spawner or sign payload rides in that component, so
 *                              this is off by default even for allowed items
 * @param allowContainerContents whether a stack may carry a non-empty container or bundle; when on,
 *                              the contents are themselves evaluated against this profile
 *
 * @author THEFricadelle
 */
public record CreativeProfile(
        String name,
        Set<ResourceLocation> items,
        Set<String> namespaces,
        Set<ResourceLocation> tags,
        Set<ResourceLocation> tabs,
        Set<ResourceLocation> deniedItems,
        Set<String> deniedNamespaces,
        boolean allowBlockEntityData,
        boolean allowContainerContents) {

    public CreativeProfile {
        items = Set.copyOf(items);
        namespaces = Set.copyOf(namespaces);
        tags = Set.copyOf(tags);
        tabs = Set.copyOf(tabs);
        deniedItems = Set.copyOf(deniedItems);
        deniedNamespaces = Set.copyOf(deniedNamespaces);
    }

    /**
     * A profile that refuses everything. Used when a policy names a profile that does not exist:
     * failing closed is the only safe direction for an enforcement component, since failing open
     * would silently hand a full creative inventory to whoever the broken assignment pointed at.
     */
    public static CreativeProfile denyAll(String name) {
        return new CreativeProfile(name, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false);
    }

    /** True when no rule opens anything, meaning the profile can only ever refuse. */
    public boolean allowsNothing() {
        return items.isEmpty() && namespaces.isEmpty() && tags.isEmpty() && tabs.isEmpty();
    }
}

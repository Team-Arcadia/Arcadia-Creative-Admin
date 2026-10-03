/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;

/**
 * One named set of creative restrictions, and the unit an event is configured with.
 * <p>
 * A profile holds a <strong>selection</strong> and a {@link Mode} that says what the selection
 * means. In {@link Mode#WHITELIST} everything is locked except the selection; in
 * {@link Mode#BLACKLIST} everything is open except the selection. The selection is built from four
 * shapes that combine freely, none of them required: whole creative tabs ({@link #tabs}), whole mods
 * ({@link #namespaces}), item tags ({@link #tags}) and single items ({@link #items}).
 * <p>
 * {@link #exceptions} carve single items out of the group shapes. A tab selected in a blacklist
 * locks the whole page, and an exception on one of its items leaves that item open; the other way
 * round in a whitelist. An item listed in {@link #items} is selected whatever else says, so the
 * single-item decision always outranks the group one.
 *
 * @param name                   profile id, lowercase, used by commands, the admin screen and the
 *                               assignment file
 * @param mode                   what the selection means
 * @param tabs                   creative tab ids whose every item is selected
 * @param namespaces             mod ids whose every item is selected
 * @param tags                   item tag ids whose every member is selected, written {@code #ns:path}
 *                               in the policy file and stored here without the hash
 * @param items                  item ids selected outright
 * @param exceptions             item ids taken out of what the tabs, namespaces and tags select
 * @param allowBlockEntityData   whether a stack may carry {@code minecraft:block_entity_data}; a
 *                               command block, spawner or sign payload rides in that component, so
 *                               this is off by default even for open items
 * @param allowContainerContents whether a stack may carry a non-empty container or bundle; when on,
 *                               the contents are themselves evaluated against this profile
 * @param allowedComponents      data component ids a stack may carry on top of the harmless set
 *                               every profile accepts; everything else is refused unless the stack
 *                               is exactly one the creative menu offers
 *
 * @author THEFricadelle
 */
public record CreativeProfile(
        String name,
        Mode mode,
        Set<ResourceLocation> tabs,
        Set<String> namespaces,
        Set<ResourceLocation> tags,
        Set<ResourceLocation> items,
        Set<ResourceLocation> exceptions,
        boolean allowBlockEntityData,
        boolean allowContainerContents,
        Set<ResourceLocation> allowedComponents) {

    public CreativeProfile {
        tabs = Set.copyOf(tabs);
        namespaces = Set.copyOf(namespaces);
        tags = Set.copyOf(tags);
        items = Set.copyOf(items);
        exceptions = Set.copyOf(exceptions);
        allowedComponents = Set.copyOf(allowedComponents);
    }

    public enum Mode {
        /** Everything is locked except the selection. */
        WHITELIST,
        /** Everything is open except the selection. */
        BLACKLIST;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Mode byId(String id) {
            for (Mode mode : values()) {
                if (mode.id().equals(id)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown mode '" + id + "', expected whitelist or blacklist");
        }
    }

    /** A new profile as the admin screen creates it: everything locked, nothing selected yet. */
    public static CreativeProfile empty(String name) {
        return new CreativeProfile(name, Mode.WHITELIST, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of());
    }

    /**
     * A profile that refuses everything. Used when a policy names a profile that does not exist:
     * failing closed is the only safe direction for an enforcement component, since failing open
     * would silently hand a full creative inventory to whoever the broken assignment pointed at.
     */
    public static CreativeProfile denyAll(String name) {
        return empty(name);
    }

    public CreativeProfile withName(String newName) {
        return new CreativeProfile(newName, mode, tabs, namespaces, tags, items, exceptions,
                allowBlockEntityData, allowContainerContents, allowedComponents);
    }
}

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
import net.thefricadelle.arcadiacreativeguard.network.TabPolicyPayload;

import java.util.Set;

/**
 * The tab list a client was last told about.
 * <p>
 * Holds no client-only type on purpose. It is referenced from the payload registration, which is
 * loaded on a dedicated server too, and touching a client class from there would fail to load the
 * mod entirely.
 * <p>
 * Reset on disconnect, otherwise a player who leaves a restricted server would keep its filtered
 * inventory in every singleplayer world afterwards.
 *
 * @author THEFricadelle
 */
public final class ReceivedTabPolicy {

    private static volatile boolean enforced;
    private static volatile Set<ResourceLocation> tabs = Set.of();

    private ReceivedTabPolicy() {}

    public static void accept(TabPolicyPayload payload) {
        // An enforced policy naming no tab would leave the creative screen with nothing to show.
        // Treat it as "no advice" and let the screen behave normally; the server still refuses
        // every item, so nothing is actually granted by displaying them.
        enforced = payload.enforced() && !payload.tabs().isEmpty();
        tabs = Set.copyOf(payload.tabs());
    }

    public static void clear() {
        enforced = false;
        tabs = Set.of();
    }

    public static boolean enforced() {
        return enforced;
    }

    public static boolean allows(ResourceLocation tabId) {
        return !enforced || tabs.contains(tabId);
    }
}

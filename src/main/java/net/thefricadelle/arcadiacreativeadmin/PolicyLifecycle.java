/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin;

import net.minecraft.server.MinecraftServer;
import net.thefricadelle.arcadiacreativeadmin.core.TabItemIndex;
import net.thefricadelle.arcadiacreativeadmin.core.VisibleTabResolver;
import net.thefricadelle.arcadiacreativeadmin.network.PolicyNetwork;
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyManager;

/**
 * The order in which the policy, the tab index and the client advice are refreshed.
 * <p>
 * Each of them feeds the next: the visible tab answer depends on the policy and the index, and what
 * clients hold depends on that answer. Keeping the sequence in one place is what stops a reload
 * path from skipping a step and leaving clients with advice derived from the previous policy.
 *
 * @author THEFricadelle
 */
public final class PolicyLifecycle {

    private PolicyLifecycle() {}

    /**
     * Reads the files, re-indexes the tabs and pushes new advice to every connected client.
     *
     * @return how many rule entries name nothing on this server, already logged one by one
     */
    public static int reload(MinecraftServer server, boolean startup) {
        PolicyManager.load(startup);
        TabItemIndex.build(server);
        VisibleTabResolver.invalidate();
        int unmatched = PolicyManager.validateRules();
        PolicyNetwork.sendToAll(server.getPlayerList().getPlayers());
        return unmatched;
    }

    /** Integrated server only: picks up tab contents the client built after the server started. */
    public static void refreshTabIndexIfStale(MinecraftServer server) {
        if (TabItemIndex.refreshIfStale(server)) {
            VisibleTabResolver.invalidate();
            PolicyNetwork.sendToAll(server.getPlayerList().getPlayers());
        }
    }

    /** A datapack reload can change what a tag rule matches, and with it which tabs are worth showing. */
    public static void onDataReloaded(MinecraftServer server) {
        VisibleTabResolver.invalidate();
        PolicyManager.validateRules();
        PolicyNetwork.sendToAll(server.getPlayerList().getPlayers());
    }
}

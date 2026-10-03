/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin;

import net.minecraft.server.MinecraftServer;
import net.thefricadelle.creativeadmin.core.AdviceResolver;
import net.thefricadelle.creativeadmin.core.TabItemIndex;
import net.thefricadelle.creativeadmin.network.AdminServer;
import net.thefricadelle.creativeadmin.network.PolicyNetwork;
import net.thefricadelle.creativeadmin.policy.PolicyManager;

/**
 * The order in which the policy, the tab index, the client advice and the open admin screens are
 * refreshed.
 * <p>
 * Each of them feeds the next: the advice depends on the policy and the index, and what clients
 * hold depends on the advice. Keeping the sequence in one place is what stops a change path from
 * skipping a step and leaving clients with advice derived from the previous policy.
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
        AdviceResolver.invalidate();
        int unmatched = PolicyManager.validateRules();
        PolicyNetwork.sendToAll(server.getPlayerList().getPlayers());
        AdminServer.broadcast(server);
        return unmatched;
    }

    /** After the admin screen saved a new policy: the index is unchanged, everything derived is not. */
    public static void afterEdit(MinecraftServer server) {
        AdviceResolver.invalidate();
        PolicyManager.validateRules();
        PolicyNetwork.sendToAll(server.getPlayerList().getPlayers());
    }

    /** Integrated server only: picks up tab contents the client built after the server started. */
    public static void refreshTabIndexIfStale(MinecraftServer server) {
        if (TabItemIndex.refreshIfStale(server)) {
            AdviceResolver.invalidate();
            PolicyNetwork.sendToAll(server.getPlayerList().getPlayers());
        }
    }

    /** A datapack reload can change what a tag rule matches, and with it what is worth showing. */
    public static void onDataReloaded(MinecraftServer server) {
        AdviceResolver.invalidate();
        PolicyManager.validateRules();
        PolicyNetwork.sendToAll(server.getPlayerList().getPlayers());
    }
}

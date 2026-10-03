/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.thefricadelle.creativeadmin.PolicyLifecycle;
import net.thefricadelle.creativeadmin.policy.CreativePermissions;
import net.thefricadelle.creativeadmin.policy.PolicyManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the admin screen.
 * <p>
 * Every request re-checks {@link CreativePermissions#ADMIN}: holding the screen open proves nothing,
 * a permission can be removed while it is open, and a client can send these payloads without ever
 * having been shown the screen.
 * <p>
 * Players with the screen open are remembered so that a change made by one admin, a reload or an
 * assignment reaches the others instead of leaving them editing a stale copy.
 *
 * @author THEFricadelle
 */
public final class AdminServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<UUID> VIEWERS = new HashSet<>();

    private AdminServer() {}

    /** Opens the screen for a player, from the command or the button. */
    public static void open(ServerPlayer player) {
        if (!CreativePermissions.isAdmin(player)) {
            player.displayClientMessage(Component.translatable("creativeadmin.admin.denied")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!PolicyNetwork.hasMod(player)) {
            player.displayClientMessage(Component.translatable("creativeadmin.admin.needs_client")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        VIEWERS.add(player.getUUID());
        PolicyNetwork.send(player, state(player.server, true, "", true));
    }

    static void close(ServerPlayer player) {
        VIEWERS.remove(player.getUUID());
    }

    public static void forget(UUID player) {
        VIEWERS.remove(player);
    }

    static void save(ServerPlayer player, AdminPayloads.Save save) {
        if (!authorized(player)) {
            return;
        }
        MinecraftServer server = player.server;
        PolicyManager.SaveResult result = PolicyManager.save(save.document(), save.baseRevision());
        String status = "creativeadmin.admin.status." + result.name().toLowerCase(Locale.ROOT);
        if (result == PolicyManager.SaveResult.SAVED) {
            LOGGER.info("{} saved the creative policy from the admin screen", player.getGameProfile().getName());
            PolicyLifecycle.afterEdit(server);
            broadcastExcept(server, player.getUUID());
        }
        PolicyNetwork.send(player, state(server, false, status, result == PolicyManager.SaveResult.SAVED));
    }

    static void assign(ServerPlayer player, AdminPayloads.Assign assign) {
        if (!authorized(player)) {
            return;
        }
        MinecraftServer server = player.server;
        String status;
        boolean success = false;
        if (!PolicyManager.assignmentsWritable()) {
            status = "creativeadmin.admin.status.assignments_broken";
        } else if (!assign.profile().isEmpty() && !PolicyManager.hasProfile(assign.profile())) {
            status = "creativeadmin.admin.status.unknown_profile";
        } else {
            boolean saved = assign.profile().isEmpty()
                    ? PolicyManager.clearAssignment(assign.player())
                    : PolicyManager.assign(assign.player(), assign.profile());
            status = saved ? "creativeadmin.admin.status.assigned" : "creativeadmin.admin.status.write_failed";
            success = saved;
            ServerPlayer target = server.getPlayerList().getPlayer(assign.player());
            if (target != null) {
                PolicyNetwork.sendTo(target);
            }
            broadcastExcept(server, player.getUUID());
        }
        PolicyNetwork.send(player, state(server, false, status, success));
    }

    /** Pushes the current state to every open screen, after a change that did not come from one. */
    public static void broadcast(MinecraftServer server) {
        broadcastExcept(server, null);
    }

    private static void broadcastExcept(MinecraftServer server, UUID skipped) {
        if (VIEWERS.isEmpty()) {
            return;
        }
        AdminPayloads.State state = state(server, false, "", true);
        for (UUID viewer : List.copyOf(VIEWERS)) {
            if (viewer.equals(skipped)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player == null || !CreativePermissions.isAdmin(player)) {
                VIEWERS.remove(viewer);
                continue;
            }
            PolicyNetwork.send(player, state);
        }
    }

    private static boolean authorized(ServerPlayer player) {
        if (CreativePermissions.isAdmin(player)) {
            return true;
        }
        VIEWERS.remove(player.getUUID());
        LOGGER.warn("{} sent an admin request without the {} permission; ignored",
                player.getGameProfile().getName(), CreativePermissions.ADMIN.getNodeName());
        return false;
    }

    private static AdminPayloads.State state(MinecraftServer server, boolean open, String status, boolean success) {
        Map<UUID, AdminPayloads.PlayerEntry> players = new LinkedHashMap<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            String assigned = PolicyManager.assignment(online.getUUID());
            players.put(online.getUUID(), new AdminPayloads.PlayerEntry(online.getUUID(),
                    online.getGameProfile().getName(), true, assigned == null ? "" : assigned,
                    CreativePermissions.grantedProfile(online)));
        }
        for (Map.Entry<UUID, String> entry : PolicyManager.assignments().entrySet()) {
            if (!players.containsKey(entry.getKey())) {
                players.put(entry.getKey(), new AdminPayloads.PlayerEntry(entry.getKey(),
                        knownName(server, entry.getKey()), false, entry.getValue(), ""));
            }
        }
        List<AdminPayloads.PlayerEntry> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.comparing(AdminPayloads.PlayerEntry::online).reversed()
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return new AdminPayloads.State(open, status, success, PolicyManager.revision(),
                PolicyManager.policyBroken(), !PolicyManager.assignmentsWritable(),
                PolicyManager.document(), sorted);
    }

    private static String knownName(MinecraftServer server, UUID id) {
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(id).map(GameProfile::getName).orElse(id.toString());
        }
        return id.toString();
    }
}

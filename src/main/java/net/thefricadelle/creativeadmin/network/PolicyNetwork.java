/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.thefricadelle.creativeadmin.CreativeAdmin;
import net.thefricadelle.creativeadmin.core.AdviceResolver;
import net.thefricadelle.creativeadmin.policy.CreativePermissions;
import net.thefricadelle.creativeadmin.policy.CreativeProfile;
import net.thefricadelle.creativeadmin.policy.PolicyManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Registers every payload of the mod and pushes the creative advice to players.
 * <p>
 * The channel is registered as optional so a vanilla client, or one without this mod, can still
 * join. That is deliberate: enforcement never depends on the client having the mod, so requiring it
 * would lock out players for no gain in safety. Only the admin screen needs the mod on the client.
 * <p>
 * Client-bound handlers go through {@link ClientHandler}, installed by the client setup. This class
 * is loaded on a dedicated server too, and naming a client class here would fail to load the mod.
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = CreativeAdmin.MOD_ID)
public final class PolicyNetwork {

    /** Raised whenever a payload changes shape, so mismatched jars refuse the channel instead of misreading it. */
    private static final String PROTOCOL_VERSION = "2";

    /** What the client does with server messages; a no-op until the client setup installs the real one. */
    public interface ClientHandler {
        void onAdvice(AdvicePayload payload);

        void onAdminState(AdminPayloads.State state);
    }

    private static volatile ClientHandler clientHandler = new ClientHandler() {
        @Override
        public void onAdvice(AdvicePayload payload) {}

        @Override
        public void onAdminState(AdminPayloads.State state) {}
    };

    private PolicyNetwork() {}

    public static void setClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToClient(AdvicePayload.TYPE, AdvicePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> clientHandler.onAdvice(payload)));
        registrar.playToClient(AdminPayloads.State.TYPE, AdminPayloads.State.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> clientHandler.onAdminState(payload)));

        registrar.playToServer(AdminPayloads.Refresh.TYPE, AdminPayloads.Refresh.STREAM_CODEC,
                server((player, payload) -> refresh(player)));
        registrar.playToServer(AdminPayloads.Open.TYPE, AdminPayloads.Open.STREAM_CODEC,
                server((player, payload) -> AdminServer.open(player)));
        registrar.playToServer(AdminPayloads.Close.TYPE, AdminPayloads.Close.STREAM_CODEC,
                server((player, payload) -> AdminServer.close(player)));
        registrar.playToServer(AdminPayloads.Save.TYPE, AdminPayloads.Save.STREAM_CODEC,
                server(AdminServer::save));
        registrar.playToServer(AdminPayloads.Assign.TYPE, AdminPayloads.Assign.STREAM_CODEC,
                server(AdminServer::assign));
    }

    /** Runs a server-bound handler on the server thread, where every piece of policy state lives. */
    private static <T extends CustomPacketPayload> IPayloadHandler<T> server(
            BiConsumer<ServerPlayer, T> handler) {
        return (T payload, IPayloadContext context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                handler.accept(player, payload);
            }
        });
    }

    /** Opening the creative screen twice in a second needs one answer, not two. */
    private static final long REFRESH_INTERVAL_MS = 1000L;
    private static final Map<UUID, Long> LAST_REFRESH = new HashMap<>();

    private static void refresh(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_REFRESH.get(player.getUUID());
        if (last != null && now - last < REFRESH_INTERVAL_MS) {
            return;
        }
        LAST_REFRESH.put(player.getUUID(), now);
        sendTo(player);
    }

    public static void forget(UUID player) {
        LAST_REFRESH.remove(player);
    }

    /** Whether the player's client negotiated the channel, meaning it runs this mod. */
    public static boolean hasMod(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(AdvicePayload.TYPE);
    }

    /**
     * Sends a player the advice matching their current profile. Called on login and again whenever
     * something that changes the answer happens: a reload, an edit, an assignment, a permission
     * change.
     */
    public static void sendTo(ServerPlayer player) {
        // Registering the channel as optional only lets such a client join. Sending on a channel
        // the client never negotiated still throws, and from the login event that would take the
        // connection down with it.
        if (!hasMod(player)) {
            return;
        }
        boolean admin = CreativePermissions.isAdmin(player);
        CreativeProfile profile = PolicyManager.profileFor(player);
        if (profile == null) {
            PacketDistributor.sendToPlayer(player, AdvicePayload.unrestricted(admin));
            return;
        }
        AdviceResolver.Advice advice = AdviceResolver.adviceFor(profile);
        PacketDistributor.sendToPlayer(player, new AdvicePayload(true, profile.name(), advice.tabs(),
                advice.lockedListed(), advice.items(), admin));
    }

    /** Re-sends to everyone, after the policy itself changed. */
    public static void sendToAll(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            sendTo(player);
        }
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (hasMod(player)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
